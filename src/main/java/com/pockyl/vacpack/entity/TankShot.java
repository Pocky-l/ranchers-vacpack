package com.pockyl.vacpack.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.network.ShotLandedPayload;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.ModSounds;
import com.pockyl.vacpack.vacuum.Shot;

/**
 * Something shot out of a vacpack while in flight: an item or a stored mob. Projectiles are simulated on the client,
 * so the flight is smooth, unlike a real mob whose client copy only interpolates server positions. On impact or
 * after {@link #MAX_FLIGHT_TICKS} the payload is released as a real item entity or mob.
 */
public final class TankShot extends ThrowableProjectile {
    private static final int MAX_FLIGHT_TICKS = 40;
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(TankShot.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<CompoundTag> MOB = SynchedEntityData.defineId(TankShot.class, EntityDataSerializers.COMPOUND_TAG);

    /** Client-only render copy of the carried mob, created lazily from {@link #MOB}. */
    private Entity displayMob;

    // Client-only "ragdoll" pose, in degrees: a damped somersault, a decaying side wobble and the body pitch
    // following the trajectory. Previous-tick values are kept for interpolation.
    private float flip;
    private float flipO;
    private float flipSpeed;
    private float wobble;
    private float wobbleO;
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
        return shot;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(MOB, new CompoundTag());
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

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickPose();
        } else if (isAlive() && tickCount > MAX_FLIGHT_TICKS) {
            release(center(), getDeltaMovement().scale(0.3), false);
        }
    }

    private void tickPose() {
        flipO = flip;
        wobbleO = wobble;
        aimO = aim;
        if (tickCount <= 1) {
            flipSpeed = (random.nextBoolean() ? 1 : -1) * (14.0F + random.nextFloat() * 8.0F);
        }
        flip += flipSpeed;
        flipSpeed *= 0.86F;
        wobble = 16.0F * Mth.sin(tickCount * 0.75F) * (float) Math.exp(-tickCount / 14.0);
        Vec3 velocity = getDeltaMovement();
        float target = (float) Mth.clamp(-Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())), -55.0, 55.0);
        aim += (target - aim) * 0.25F;

        if (getDisplayMob() instanceof LivingEntity living) {
            // Limbs flail as if running in the air.
            living.walkAnimation.update(1.2F, 0.6F);
            living.tickCount++;
        }
        if (getDisplayMob() instanceof Slime slime) {
            slime.oSquish = slime.squish;
            slime.squish = 0.45F * Mth.sin(tickCount * 0.9F) * (float) Math.exp(-tickCount / 18.0);
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

    // The client simulates the flight itself; snapping to slightly stale server positions is what made shots jitter.
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (position().distanceToSqr(x, y, z) > 4.0) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
        }
    }

    private Vec3 center() {
        return position().add(0, getBbHeight() / 2, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide() || !(result.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Vec3 velocity = getDeltaMovement();
        if (!carriesMob() && Config.shotDamage() > 0) {
            target.hurt(damageSources().thrown(this, getOwner()), (float) Config.shotDamage());
        }
        target.knockback(carriesMob() ? 0.9 : 0.6, -velocity.x, -velocity.z);
        release(center().subtract(velocity.normalize().scale(0.4)), velocity.scale(-0.15).add(0, 0.2, 0), true);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide() || !isAlive()) {
            return;
        }
        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        Vec3 velocity = getDeltaMovement();
        // Bounce off the surface a little.
        Vec3 bounce = velocity.subtract(normal.scale(2 * velocity.dot(normal))).scale(0.25);
        release(result.getLocation().add(normal.scale(0.3)), bounce, true);
    }

    /**
     * Replaces this projectile with its payload.
     *
     * @param pos      centre of the released entity
     * @param impact   whether it hit something (plays the landing thump)
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
                mob.setData(ModAttachments.SHOT, shot);
                level.addFreshEntity(mob);
                // Lets clients ease the mob out of its mid-air pose instead of snapping upright.
                PacketDistributor.sendToPlayersTrackingEntity(this, new ShotLandedPayload(getId(), mob.getId()));
            }
        } else if (!getItem().isEmpty()) {
            ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, getItem(), velocity.x, velocity.y, velocity.z);
            item.setPickUpDelay(10);
            item.setData(ModAttachments.SHOT, shot);
            level.addFreshEntity(item);
        }
        level.sendParticles(ModParticles.SHOT_PUFF.get(), pos.x, pos.y, pos.z, 3, 0.15, 0.1, 0.15, 0.02);
        if (impact) {
            level.playSound(null, pos.x, pos.y, pos.z, ModSounds.LAND.get(), SoundSource.NEUTRAL,
                    carriesMob() ? 0.8F : 0.4F, 0.9F + random.nextFloat() * 0.3F);
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
