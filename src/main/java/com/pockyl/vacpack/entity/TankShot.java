package com.pockyl.vacpack.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.network.ShotLandedPayload;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.ModSounds;
import com.pockyl.vacpack.vacuum.Shot;

/**
 * Something shot out of a vacpack, behaving like a ragdoll: an item or a stored mob that flies, bounces off blocks
 * and mobs, slides and rolls until it has almost stopped, and only then turns back into a real item entity or mob.
 * The physics run on both sides, so the client sees a smooth simulation instead of interpolated server positions.
 */
public final class TankShot extends Projectile {
    /** Safety limit; normally a shot settles long before. */
    private static final int MAX_LIFETIME_TICKS = 300;
    /** Below this speed (blocks per tick) on the ground the ragdoll settles. */
    private static final double SETTLE_SPEED = 0.06;
    private static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.99;
    private static final double GROUND_FRICTION = 0.8;
    private static final double RESTITUTION = 0.35;
    /** Bounces slower than this along an axis just stop instead of jittering. */
    private static final double MIN_BOUNCE = 0.08;
    private static final int ENTITY_HIT_COOLDOWN = 10;

    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(TankShot.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<CompoundTag> MOB = SynchedEntityData.defineId(TankShot.class, EntityDataSerializers.COMPOUND_TAG);

    /** Client-only render copy of the carried mob, created lazily from {@link #MOB}. */
    private Entity displayMob;
    private int lastHitEntity = -1;
    private int lastHitTick;
    private int slowTicks;

    // Client-only "ragdoll" pose, in degrees: a somersault that is kicked by bounces and rolls with the movement on the
    // ground, a decaying side wobble and the body pitch following the trajectory. Previous values for interpolation.
    private float flip;
    private float flipO;
    private float flipSpeed;
    private float wobble;
    private float wobbleO;
    private float wobbleEnergy = 16.0F;
    private float aim;
    private float aimO;

    public TankShot(EntityType<? extends TankShot> type, Level level) {
        super(type, level);
    }

    public static TankShot ofItem(Level level, Player shooter, ItemStack item) {
        TankShot shot = new TankShot(ModEntities.TANK_SHOT.get(), level);
        shot.setOwner(shooter);
        shot.entityData.set(ITEM, item.copy());
        return shot;
    }

    public static TankShot ofMob(Level level, Player shooter, CompoundTag mob) {
        TankShot shot = new TankShot(ModEntities.TANK_SHOT.get(), level);
        shot.setOwner(shooter);
        shot.entityData.set(MOB, mob.copy());
        shot.refreshDimensions();
        return shot;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(MOB, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MOB.equals(key)) {
            refreshDimensions();
        }
    }

    /** A tumbling mob takes roughly a cube between its width and height; items stay small. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (!carriesMob()) {
            return super.getDimensions(pose);
        }
        return EntityType.by(getMob())
                .map(type -> {
                    EntityDimensions mob = type.getDimensions();
                    float size = Mth.clamp((mob.width() + mob.height()) / 2, 0.3F, 0.9F);
                    return EntityDimensions.scalable(size, size);
                })
                .orElse(super.getDimensions(pose));
    }

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    public CompoundTag getMob() {
        return entityData.get(MOB);
    }

    public boolean carriesMob() {
        return !getMob().isEmpty();
    }

    public Entity getDisplayMob() {
        if (displayMob == null && carriesMob()) {
            displayMob = EntityType.create(getMob(), level()).orElse(null);
        }
        return displayMob;
    }

    // ------------------------------------------------------------------------------------------------
    // Physics
    // ------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        Vec3 before = position();
        Vec3 velocity = getDeltaMovement();

        hitEntities(velocity);
        velocity = getDeltaMovement();
        if (!isNoGravity()) {
            velocity = velocity.add(0, -GRAVITY, 0);
        }

        move(MoverType.SELF, velocity);
        Vec3 moved = position().subtract(before);
        Vec3 bounced = new Vec3(
                bounceAxis(velocity.x, moved.x),
                bounceAxis(velocity.y, moved.y),
                bounceAxis(velocity.z, moved.z));
        double impact = velocity.subtract(bounced).length();
        if (onGround()) {
            bounced = new Vec3(bounced.x * GROUND_FRICTION, bounced.y, bounced.z * GROUND_FRICTION);
        }
        bounced = bounced.scale(isInWater() ? 0.8 : AIR_DRAG);
        setDeltaMovement(bounced);

        if (level().isClientSide()) {
            tickPose(impact);
        } else {
            if (impact > 0.35) {
                playImpactSound(Math.min(1.0, impact));
            }
            tickSettle();
        }
    }

    /** Reflects the velocity along an axis where movement was blocked; small bounces die out. */
    private static double bounceAxis(double intended, double actual) {
        if (Math.abs(intended - actual) < 1.0E-4) {
            return intended;
        }
        double reflected = -intended * RESTITUTION;
        return Math.abs(reflected) < MIN_BOUNCE ? 0 : reflected;
    }

    private void tickSettle() {
        double speed = getDeltaMovement().length();
        boolean slow = onGround() && speed < SETTLE_SPEED;
        slowTicks = slow ? slowTicks + 1 : 0;
        // A short moment of calm on the ground (not exactly zero speed) ends the ragdoll.
        if (slowTicks >= 3 || isInWater() || tickCount > MAX_LIFETIME_TICKS) {
            release(center(), getDeltaMovement(), false);
        }
    }

    /** Hits each mob in the path at most once in a while: items deal damage, everything knocks back and bounces off. */
    private void hitEntities(Vec3 velocity) {
        if (velocity.lengthSqr() < 0.04) {
            return;
        }
        Vec3 from = position();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, from, from.add(velocity),
                getBoundingBox().expandTowards(velocity).inflate(0.2), this::canHitEntity);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target.getId() == lastHitEntity && tickCount - lastHitTick < ENTITY_HIT_COOLDOWN) {
            return;
        }
        lastHitEntity = target.getId();
        lastHitTick = tickCount;
        if (!level().isClientSide()) {
            if (!carriesMob() && Config.shotDamage() > 0) {
                target.hurt(damageSources().thrown(this, getOwner()), (float) Config.shotDamage());
            }
            target.knockback(carriesMob() ? 0.9 : 0.6, -velocity.x, -velocity.z);
            playImpactSound(0.8);
        }
        // Bounce off the mob, keeping some of the momentum sideways.
        setDeltaMovement(velocity.scale(-0.3).add(0, 0.2, 0));
        flipSpeed += 25.0F;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof TankShot);
    }

    // The client simulates the movement itself; hard snapping to slightly stale server positions makes ragdolls jitter,
    // so small differences are corrected softly and only large ones snap.
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        double distanceSqr = position().distanceToSqr(x, y, z);
        if (distanceSqr > 9.0) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
        } else if (distanceSqr > 0.04) {
            setPos(position().lerp(new Vec3(x, y, z), 0.3));
        }
    }

    private Vec3 center() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    private void playImpactSound(double strength) {
        level().playSound(null, getX(), getY(), getZ(), ModSounds.LAND.get(), SoundSource.NEUTRAL,
                (float) (carriesMob() ? 0.7 * strength : 0.35 * strength), 0.9F + random.nextFloat() * 0.3F);
    }

    // ------------------------------------------------------------------------------------------------
    // Client pose
    // ------------------------------------------------------------------------------------------------

    private void tickPose(double impact) {
        flipO = flip;
        wobbleO = wobble;
        aimO = aim;
        if (tickCount <= 1) {
            flipSpeed = (random.nextBoolean() ? 1 : -1) * (14.0F + random.nextFloat() * 8.0F);
        }
        Vec3 velocity = getDeltaMovement();
        if (impact > 0.15) {
            // Bounces kick the ragdoll into a new tumble and wobble.
            flipSpeed += (random.nextBoolean() ? 1 : -1) * (float) impact * 40.0F;
            wobbleEnergy = Math.min(24.0F, wobbleEnergy + (float) impact * 20.0F);
        }
        if (onGround()) {
            // Rolling along the ground: spin follows the horizontal speed.
            float roll = (float) velocity.horizontalDistance() * 60.0F;
            flipSpeed += (Math.copySign(roll, flipSpeed == 0 ? 1 : flipSpeed) - flipSpeed) * 0.3F;
        }
        flip += flipSpeed;
        flipSpeed *= 0.88F;
        wobbleEnergy *= 0.93F;
        wobble = wobbleEnergy * Mth.sin(tickCount * 0.75F);
        float target = onGround() ? 0.0F
                : (float) Mth.clamp(-Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())), -55.0, 55.0);
        aim += (target - aim) * 0.25F;

        if (getDisplayMob() instanceof LivingEntity living) {
            // Limbs flail while flying, calm down when barely moving.
            float flail = (float) Mth.clamp(velocity.length() * 2.0, 0.2, 1.2);
            living.walkAnimation.update(flail, 0.6F);
            living.tickCount++;
        }
        if (getDisplayMob() instanceof Slime slime) {
            slime.oSquish = slime.squish;
            slime.squish = wobbleEnergy / 40.0F * Mth.sin(tickCount * 0.9F);
        }
    }

    public float getFlip(float partialTick) {
        return Mth.lerp(partialTick, flipO, flip);
    }

    public float getWobble(float partialTick) {
        return Mth.lerp(partialTick, wobbleO, wobble);
    }

    public float getAim(float partialTick) {
        return Mth.lerp(partialTick, aimO, aim);
    }

    // ------------------------------------------------------------------------------------------------
    // Release
    // ------------------------------------------------------------------------------------------------

    /**
     * Replaces this ragdoll with its payload.
     *
     * @param pos    centre of the released entity
     * @param impact whether it hit something (plays the landing thump)
     */
    public void release(Vec3 pos, Vec3 velocity, boolean impact) {
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        Shot shot = new Shot(getOwner() != null ? getOwner().getUUID() : Shot.NONE.shooter(), level.getGameTime());
        if (carriesMob()) {
            Entity mob = EntityType.loadEntityRecursive(getMob(), level, entity -> {
                entity.moveTo(pos.x, pos.y - entity.getBbHeight() / 2, pos.z, getYRot(), 0);
                liftOutOfBlocks(entity);
                return entity;
            });
            if (mob == null) {
                Vacpack.LOGGER.warn("Discarding shot mob that can no longer be loaded: {}", getMob().getString("id"));
            } else {
                mob.setDeltaMovement(velocity);
                mob.resetFallDistance();
                mob.hasImpulse = true;
                mob.hurtMarked = true;
                mob.setData(ModAttachments.SHOT, shot);
                mob.setData(ModAttachments.FALL_GUARD, true);
                level.addFreshEntity(mob);
                // Lets clients ease the mob out of its ragdoll pose instead of snapping upright.
                PacketDistributor.sendToPlayersTrackingEntity(this, new ShotLandedPayload(getId(), mob.getId()));
            }
        } else if (!getItem().isEmpty()) {
            ItemEntity item = new ItemEntity(level, pos.x, pos.y - 0.125, pos.z, getItem(), velocity.x, velocity.y, velocity.z);
            item.setPickUpDelay(10);
            item.setData(ModAttachments.SHOT, shot);
            level.addFreshEntity(item);
        }
        level.sendParticles(ModParticles.SHOT_PUFF.get(), pos.x, pos.y, pos.z, 2, 0.15, 0.1, 0.15, 0.01);
        if (impact) {
            playImpactSound(1.0);
        }
        discard();
    }

    /** Moves a released mob up until it no longer intersects blocks (at most its own height). */
    private static void liftOutOfBlocks(Entity entity) {
        double step = 0.125;
        for (double lifted = 0; lifted < entity.getBbHeight() && !entity.level().noCollision(entity); lifted += step) {
            entity.setPos(entity.getX(), entity.getY() + step, entity.getZ());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!getItem().isEmpty()) {
            tag.put("Item", getItem().save(registryAccess()));
        }
        if (carriesMob()) {
            tag.put("Mob", getMob());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ITEM, ItemStack.parseOptional(registryAccess(), tag.getCompound("Item")));
        entityData.set(MOB, tag.getCompound("Mob"));
    }
}
