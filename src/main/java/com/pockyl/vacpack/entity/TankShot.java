package com.pockyl.vacpack.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.network.ShotLandedPayload;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.ModSounds;
import com.pockyl.vacpack.tank.MobData;
import com.pockyl.vacpack.vacuum.Shot;
import com.pockyl.vacpack.vacuum.ShotProtection;

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
    /** Ticks the ragdoll lies still before it turns back into the mob (which then gets up). */
    private static final int SETTLE_TICKS = 8;
    private static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.99;
    private static final double GROUND_FRICTION = 0.8;
    private static final double RESTITUTION = 0.35;
    /** Bounces slower than this along an axis just stop instead of jittering. */
    private static final double MIN_BOUNCE = 0.08;
    private static final int ENTITY_HIT_COOLDOWN = 10;

    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(TankShot.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<CompoundTag> MOB = SynchedEntityData.defineId(TankShot.class, ModEntities.COMPOUND_TAG.get());

    /** Client-only render copy of the carried mob, created lazily from {@link #MOB}. */
    private Entity displayMob;
    private int lastHitEntity = -1;
    private int lastHitTick;
    private int slowTicks;

    // Client-only ragdoll body: orientation and angular velocity (radians per tick) in the yaw-aligned frame, plus the
    // secondary motion of head and limbs. Previous orientation is kept for interpolation.
    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf orientationO = new Quaternionf();
    private final Vector3f spin = new Vector3f();
    private float flail;
    private float headPitch;
    private float headPitchVelocity;

    /** Client-only: remaining offset to the server position, applied gradually instead of snapping. */
    private Vec3 correction = Vec3.ZERO;
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 0) {
        @Override
        public void interpolateTo(Vec3 position, float yRot, float xRot) {
            onServerPosition(position, yRot, xRot);
        }
    };

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
        return MobData.type(getMob())
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
            displayMob = MobData.create(getMob(), level()).orElse(null);
        }
        return displayMob;
    }

    // ------------------------------------------------------------------------------------------------
    // Physics
    // ------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && correction.lengthSqr() > 1.0E-6) {
            Vec3 step = correction.scale(0.12);
            setPos(position().add(step));
            correction = correction.subtract(step);
        }
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
        if (slowTicks >= SETTLE_TICKS || isInWater() || tickCount > MAX_LIFETIME_TICKS) {
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
        if (level() instanceof ServerLevel level) {
            DamageSource source = damageSources().thrown(this, getOwner());
            float damage = carriesMob() ? 0.0F : (float) Config.shotDamage();
            if (damage > 0) {
                target.hurtServer(level, source, damage);
            }
            target.knockback(carriesMob() ? 0.9 : 0.6, -velocity.x, -velocity.z, source, damage);
            playImpactSound(0.8);
        }
        // Bounce off the mob, keeping some of the momentum sideways.
        setDeltaMovement(velocity.scale(-0.3).add(0, 0.2, 0));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof TankShot);
    }

    // The client runs the same simulation. Server positions arrive a few ticks late, so a fast ragdoll is always a bit
    // "behind" in them; snapping to them, or even partially correcting each packet, is what made the flight jerky.
    // Differences explained by that delay are ignored, real divergence is blended in over several ticks, and only a
    // large one snaps.
    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    private void onServerPosition(Vec3 position, float yRot, float xRot) {
        Vec3 offset = position.subtract(position());
        double tolerance = 0.3 + getDeltaMovement().length() * 3.0;
        if (offset.length() > 4.0) {
            correction = Vec3.ZERO;
            setPos(position);
            setRot(yRot, xRot);
        } else if (offset.length() > tolerance) {
            correction = offset;
        }
    }

    // Server velocity updates are just as stale; only take them when something pushed the ragdoll (e.g. a pulse wave).
    @Override
    public void lerpMotion(Vec3 movement) {
        if (movement.distanceTo(getDeltaMovement()) > 0.6) {
            super.lerpMotion(movement);
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

    private static final float AIR_ANGULAR_DRAG = 0.985F;
    private static final float GROUND_ANGULAR_DRAG = 0.72F;
    private static final float SETTLE_TORQUE = 0.12F;
    private static final float MAX_AIR_SPIN = 0.45F;
    private static final float MAX_GROUND_SPIN = 0.3F;
    private static final Vector3f[] BODY_AXES = {
            new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0),
            new Vector3f(0, -1, 0), new Vector3f(0, 0, 1), new Vector3f(0, 0, -1)};

    /**
     * Rigid-body tumbling: launches start a gentle somersault, impacts add torque that tips the body over in the
     * direction of travel (flips, rolls), the ground damps spinning and lets the body fall onto its nearest flat side
     * (feet, back, belly, side...). Head and limbs move with the tumbling.
     */
    private void tickPose(double impact) {
        orientationO.set(orientation);
        Vec3 velocity = getDeltaMovement();
        Vector3f local = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z).rotateY(getYRot() * Mth.DEG_TO_RAD);
        Vector3f forwardAxis = tumbleAxis(local);

        if (tickCount <= 1) {
            spin.set(forwardAxis).mul(0.12F).add(randomVector(0.03F));
            if (getDisplayMob() instanceof LivingEntity living) {
                // Face the flight direction inside the ragdoll frame.
                living.yBodyRot = living.yBodyRotO = 0;
                living.yHeadRot = living.yHeadRotO = 0;
            }
        }
        if (impact > 0.15) {
            // Friction at the contact point tips the body over along the direction of travel.
            spin.add(new Vector3f(forwardAxis).mul((float) Math.min(impact, 1.0) * 0.28F)).add(randomVector(0.05F));
        }
        if (onGround()) {
            settleOntoNearestSide();
            spin.mul(GROUND_ANGULAR_DRAG);
            double horizontal = Math.sqrt(local.x * local.x + local.z * local.z);
            if (horizontal > 0.05) {
                spin.add(new Vector3f(forwardAxis).mul((float) horizontal * 0.08F));
            }
            clampLength(spin, MAX_GROUND_SPIN);
        } else {
            spin.mul(AIR_ANGULAR_DRAG);
            clampLength(spin, MAX_AIR_SPIN);
        }
        float angle = spin.length();
        if (angle > 1.0E-5F) {
            orientation.premul(new Quaternionf().rotationAxis(angle, spin.x / angle, spin.y / angle, spin.z / angle)).normalize();
        }

        flail += ((float) Mth.clamp(angle * 3.0 + velocity.length() * 0.8, 0.0, 1.0) - flail) * 0.3F;
        animateBodyParts();
    }

    /** Axis around which moving with this velocity tips the body over (top towards the motion). */
    private static Vector3f tumbleAxis(Vector3f local) {
        Vector3f horizontal = new Vector3f(local.x, 0, local.z);
        if (horizontal.lengthSquared() < 1.0E-6F) {
            return new Vector3f(1, 0, 0);
        }
        return new Vector3f(0, 1, 0).cross(horizontal).normalize();
    }

    /** Gravity on a body lying on the ground: rotate so that the body axis closest to "up" lines up with it. */
    private void settleOntoNearestSide() {
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f best = null;
        float bestDot = -2;
        for (Vector3f axis : BODY_AXES) {
            Vector3f world = orientation.transform(new Vector3f(axis));
            float dot = world.dot(up);
            if (dot > bestDot) {
                bestDot = dot;
                best = world;
            }
        }
        Vector3f torque = best.cross(up, new Vector3f());
        float sin = torque.length();
        if (sin > 1.0E-4F) {
            float tilt = (float) Math.acos(Mth.clamp(bestDot, -1.0F, 1.0F));
            spin.add(torque.mul(tilt * SETTLE_TORQUE / sin));
        }
    }

    private void animateBodyParts() {
        Entity mob = getDisplayMob();
        if (mob instanceof LivingEntity living) {
            // Legs and arms flail with the tumbling.
            living.walkAnimation.update(flail * 0.9F, 0.5F, 1.0F);
            living.tickCount++;
            living.oAttackAnim = living.attackAnim;
            living.attackAnim = flail * (0.5F + 0.5F * Mth.sin(tickCount * 0.9F));
            // The head lags behind the body's rotation and lolls around.
            float headTarget = Mth.clamp(-spin.x * 140.0F, -60.0F, 60.0F) + Mth.sin(tickCount * 0.45F) * flail * 25.0F;
            headPitchVelocity = (headPitchVelocity + (headTarget - headPitch) * 0.25F) * 0.7F;
            headPitch += headPitchVelocity;
            living.xRotO = living.getXRot();
            living.setXRot(headPitch);
            living.yHeadRotO = living.yHeadRot;
            living.yHeadRot = Mth.sin(tickCount * 0.37F) * flail * 40.0F;
        }
        if (mob instanceof Chicken chicken) {
            chicken.oFlap = chicken.flap;
            chicken.oFlapSpeed = chicken.flapSpeed;
            chicken.flapSpeed = flail;
            chicken.flap += 1.2F * flail;
        }
        if (mob instanceof Slime slime) {
            slime.oSquish = slime.squish;
            float target = Mth.clamp(spin.length() * 1.2F, 0.0F, 0.5F) * (onGround() ? -1.0F : 1.0F);
            slime.squish += (target - slime.squish) * 0.35F;
        }
    }

    private Vector3f randomVector(float scale) {
        return new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).mul(2 * scale);
    }

    private static void clampLength(Vector3f vector, float max) {
        float length = vector.length();
        if (length > max) {
            vector.mul(max / length);
        }
    }

    /** Interpolated body orientation in the yaw-aligned frame. */
    public Quaternionf getOrientation(float partialTick) {
        return orientationO.slerp(orientation, partialTick, new Quaternionf());
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
            Entity mob = MobData.load(getMob(), level, entity -> {
                entity.snapTo(pos.x, pos.y - entity.getBbHeight() / 2, pos.z, getYRot(), 0);
                liftOutOfBlocks(entity);
                return entity;
            });
            if (mob == null) {
                Vacpack.LOGGER.warn("Discarding shot mob that can no longer be loaded: {}", getMob().getStringOr("id", "?"));
            } else {
                mob.setDeltaMovement(velocity);
                mob.resetFallDistance();
                if (mob instanceof LivingEntity living) {
                    living.setYBodyRot(getYRot());
                    living.setYHeadRot(getYRot());
                }
                mob.needsSync = true;
                mob.hurtMarked = true;
                mob.setData(ModAttachments.SHOT, shot);
                mob.setData(ModAttachments.FALL_GUARD, true);
                if (mob instanceof LivingEntity living) {
                    ShotProtection.track(living);
                }
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
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, getItem());
        }
        if (carriesMob()) {
            output.store("Mob", CompoundTag.CODEC, getMob());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(ITEM, input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        entityData.set(MOB, input.read("Mob", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
