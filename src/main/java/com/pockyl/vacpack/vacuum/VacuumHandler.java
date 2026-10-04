package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.entity.TankShot;
import com.pockyl.vacpack.item.VacpackItem;
import com.pockyl.vacpack.network.CapturePayload;
import com.pockyl.vacpack.network.VacuumStatePayload;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.ModSounds;
import com.pockyl.vacpack.registry.ModTags;
import com.pockyl.vacpack.tank.VacTank;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/** Server-side vacpack behavior: suction, capture, holding, shooting, pulse wave and harvesting. */
public final class VacuumHandler {
    /** Ticks during which a freshly shot mob or item cannot be vacuumed again. */
    public static final int SHOT_IMMUNITY_TICKS = 20;
    private static final int EMPTY_SHOT_COOLDOWN = 10;
    private static final int HELD_SHOT_COOLDOWN = 10;
    private static final int FULL_WARNING_INTERVAL = 20;
    private static final int HARVEST_TICKS = 6;
    private static final double SWIRL_SPEED = 0.14;
    private static final double HOLD_STIFFNESS = 0.45;
    private static final int HOLD_SEARCH_INTERVAL = 4;
    private static final double HOLD_MAX_SPEED = 1.2;
    private static final double PULSE_CONE_DOT = Math.cos(Math.toRadians(50));
    /** A pulse wave hitting a block closer than this bursts like a wind charge. */
    private static final double WIND_BURST_REACH = 4.0;
    private static final float WIND_BURST_RADIUS = 1.2F;
    /** Same settings as the vanilla wind charge explosion. */
    private static final ExplosionDamageCalculator WIND_BURST_DAMAGE = new SimpleExplosionDamageCalculator(
            true, false, Optional.of(1.22F),
            BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));

    private VacuumHandler() {
    }

    // ------------------------------------------------------------------------------------------------
    // Input and ticking
    // ------------------------------------------------------------------------------------------------

    public static void setInput(Player player, boolean vacuum, boolean shoot) {
        VacuumState state = player.getData(ModAttachments.VACUUM_STATE);
        if (shoot && !state.shooting) {
            state.freshPress = true;
        }
        state.shooting = shoot;
        ItemStack stack = player.getMainHandItem();
        setVacuuming(player, state, vacuum && stack.getItem() instanceof VacpackItem);
    }

    public static void cycleSlot(Player player, int delta) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof VacpackItem) {
            stack.set(ModDataComponents.TANK, tank(stack).cycle(delta, Config.slotCount()));
            playAnimation(player, stack, VacpackItem.RECOIL_CONTROLLER, VacpackItem.SWITCH_ANIM);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.playNotifySound(ModSounds.SLOT_SWITCH.get(), SoundSource.PLAYERS, 0.6F, 1.0F + 0.05F * delta);
            }
        }
    }

    /** Re-sends the vacuuming state of {@code target} to a player who just started tracking it. */
    public static void syncTo(ServerPlayer tracker, Player target) {
        VacuumState state = target.getExistingDataOrNull(ModAttachments.VACUUM_STATE);
        if (state != null && state.vacuuming) {
            PacketDistributor.sendToPlayer(tracker, new VacuumStatePayload(target.getId(), true));
        }
    }

    public static void tick(Player player) {
        VacuumState state = player.getExistingDataOrNull(ModAttachments.VACUUM_STATE);
        if (state == null) {
            return;
        }
        if (state.shootCooldown > 0) {
            state.shootCooldown--;
        }
        if (state.fullWarningCooldown > 0) {
            state.fullWarningCooldown--;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof VacpackItem) || !player.isAlive() || player.isSpectator()) {
            setVacuuming(player, state, false);
            state.shooting = false;
            return;
        }

        if (state.vacuuming) {
            boolean blockedByFullTank = vacuumTick(player, stack);
            if (blockedByFullTank && state.fullWarningCooldown == 0) {
                state.fullWarningCooldown = FULL_WARNING_INTERVAL;
                playSound(player, ModSounds.TANK_FULL.get(), 0.5F, 1.0F);
            }
            holdTick(player, stack, state);
            harvestTick(player, state);
        }
        if (state.shooting && state.shootCooldown == 0) {
            state.shootCooldown = shoot(player, stack);
        }
    }

    private static void setVacuuming(Player player, VacuumState state, boolean vacuuming) {
        if (state.vacuuming == vacuuming) {
            return;
        }
        state.vacuuming = vacuuming;
        state.harvestPos = null;
        state.harvestTicks = 0;
        if (vacuuming) {
            startFanAnimation(player, player.getMainHandItem(), state);
        } else {
            dropHeld(player, state);
            stopFanAnimation(player, state);
        }
        if (player instanceof ServerPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new VacuumStatePayload(player.getId(), vacuuming));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Suction into the tank
    // ------------------------------------------------------------------------------------------------

    /**
     * One tick of suction: pulls storable entities in the cone along a spiral and stores those that reached the nozzle.
     *
     * @return whether something in the cone was left behind because the tank had no room for it
     */
    public static boolean vacuumTick(Player player, ItemStack stack) {
        Level level = player.level();
        Vec3 nozzle = nozzlePos(player);
        Vec3 axis = player.getLookAngle();
        double range = Config.range();
        double minDot = Math.cos(Math.toRadians(Config.coneAngle()));
        double capture = Config.captureDistance();
        int slotCount = Config.slotCount();
        AABB area = player.getBoundingBox().inflate(range);
        VacTank tank = tank(stack);
        VacTank initial = tank;
        boolean blocked = false;

        if (Config.vacuumItems()) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, e -> canVacuumItem(e) && inCone(player, e, range, minDot))) {
                if (tank.slotForItem(item.getItem(), slotCount, Config.itemCapacity()) < 0) {
                    blocked = true;
                    continue;
                }
                if (center(item).distanceTo(nozzle) > capture) {
                    pull(item, nozzle, axis, range);
                    continue;
                }
                VacTank.Insertion insertion = tank.insertItem(item.getItem(), slotCount, Config.itemCapacity());
                tank = insertion.tank();
                ItemStack remaining = item.getItem().copy();
                remaining.shrink(insertion.inserted());
                if (remaining.isEmpty()) {
                    animateCapture(player, item);
                    item.discard();
                } else {
                    item.setItem(remaining);
                    blocked = true;
                }
                captureEffects(player, stack, nozzle, false);
            }
        }

        if (Config.vacuumMobs()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area, e -> canVacuumMob(player, e) && inCone(player, e, range, minDot))) {
                if (tank.slotForMob(mob.getType(), slotCount, Config.mobCapacity()) < 0) {
                    blocked = true;
                    continue;
                }
                if (center(mob).distanceTo(nozzle) > capture) {
                    pull(mob, nozzle, axis, range);
                    tumble(mob, 9.0F);
                    continue;
                }
                VacTank updated = capture(player, tank, mob, slotCount);
                if (updated != null) {
                    tank = updated;
                    captureEffects(player, stack, nozzle, mob instanceof Slime);
                }
            }
        }

        if (!tank.equals(initial)) {
            stack.set(ModDataComponents.TANK, tank);
        }
        return blocked;
    }

    private static VacTank capture(Player player, VacTank tank, Mob mob, int slotCount) {
        CompoundTag data = saveMob(mob);
        if (data == null) {
            return null;
        }
        VacTank updated = tank.insertMob(mob.getType(), data, slotCount, Config.mobCapacity());
        if (updated != null) {
            animateCapture(player, mob);
            mob.discard();
        }
        return updated;
    }

    /** Full save of a mob without its UUID (a fresh one is assigned on release, so copies never clash). */
    private static CompoundTag saveMob(Entity mob) {
        CompoundTag data = new CompoundTag();
        if (!mob.save(data)) {
            return null;
        }
        data.remove("UUID");
        data.remove("Motion");
        return data;
    }

    private static void animateCapture(Player player, Entity entity) {
        if (player.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, new CapturePayload(entity.getId(), player.getId()));
        }
    }

    private static void captureEffects(Player player, ItemStack stack, Vec3 nozzle, boolean slime) {
        playAnimation(player, stack, VacpackItem.GULP_CONTROLLER, VacpackItem.GULP_ANIM);
        if (slime) {
            playSound(player, ModSounds.CAPTURE_SLIME.get(), 0.7F, 0.9F + player.getRandom().nextFloat() * 0.3F);
            particles(player, ParticleTypes.ITEM_SLIME, nozzle, 6, 0.15, 0.05);
        } else {
            playSound(player, ModSounds.CAPTURE.get(), 0.6F, 0.9F + player.getRandom().nextFloat() * 0.35F);
        }
        particles(player, ModParticles.CAPTURE_RING.get(), nozzle.add(player.getLookAngle().scale(0.6)), 1, 0, 0);
    }

    public static boolean canVacuumItem(ItemEntity item) {
        return item.isAlive() && !item.hasPickUpDelay() && !item.getItem().is(ModTags.NOT_VACUUMABLE);
    }

    public static boolean canVacuumMob(Player player, Mob mob) {
        double maxSize = Config.maxMobSize();
        return mob.isAlive()
                && (mob.getType().is(ModTags.VACUUMABLE) || mob.isBaby() && Config.vacuumBabies())
                && mob.getBbWidth() <= maxSize && mob.getBbHeight() <= maxSize
                && isFree(player, mob)
                && !recentlyShot(mob);
    }

    /** Not tied to anything: no leash, no rider or vehicle, not someone else's pet. */
    private static boolean isFree(Player player, LivingEntity entity) {
        return !(entity instanceof Mob mob && mob.isLeashed())
                && !entity.isPassenger() && !entity.isVehicle()
                && !(entity instanceof TamableAnimal pet && pet.isTame() && !pet.isOwnedBy(player));
    }

    private static boolean recentlyShot(Entity entity) {
        Shot shot = entity.getExistingDataOrNull(ModAttachments.SHOT);
        return shot != null && shot.age(entity.level().getGameTime()) <= SHOT_IMMUNITY_TICKS;
    }

    private static boolean inCone(Player player, Entity entity, double range, double minDot) {
        Vec3 toEntity = center(entity).subtract(player.getEyePosition());
        double distance = toEntity.length();
        if (distance > range) {
            return false;
        }
        boolean aimedAt = distance < 1.0 || toEntity.normalize().dot(player.getLookAngle()) >= minDot;
        return aimedAt && player.hasLineOfSight(entity);
    }

    /**
     * Moves an entity towards the nozzle: faster the closer it gets, swirling around the beam axis and floating
     * (gravity is cancelled), which is what makes the suction look like a vortex.
     */
    private static void pull(Entity entity, Vec3 nozzle, Vec3 axis, double range) {
        Vec3 fromNozzle = center(entity).subtract(nozzle);
        double distance = fromNozzle.length();
        double closeness = 1.0 - Math.min(distance / range, 1.0);
        double speed = Math.min(Config.pullStrength() * (0.8 + 2.4 * closeness * closeness), distance * 0.6);
        Vec3 inward = fromNozzle.scale(-1.0 / Math.max(distance, 1.0E-4)).scale(speed);

        Vec3 radial = fromNozzle.subtract(axis.scale(fromNozzle.dot(axis)));
        Vec3 swirl = radial.lengthSqr() > 0.0025
                ? axis.cross(radial).normalize().scale(SWIRL_SPEED * Math.min(radial.length(), 1.5))
                : Vec3.ZERO;
        Vec3 lift = new Vec3(0, entity.getGravity(), 0);

        entity.setDeltaMovement(entity.getDeltaMovement().scale(0.25).add(inward).add(swirl).add(lift));
        markMoved(entity);
    }

    private static void tumble(LivingEntity entity, float degrees) {
        float yaw = entity.getYRot() + degrees;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
    }

    private static void markMoved(Entity entity) {
        entity.resetFallDistance();
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    // ------------------------------------------------------------------------------------------------
    // Holding in the air stream
    // ------------------------------------------------------------------------------------------------

    /**
     * Keeps one mob that cannot go into the tank (too big, not vacuumable or no room) floating in front of the nozzle,
     * like carrying a largo in the air stream. It follows the aim; shooting launches it, releasing the button drops it.
     */
    public static void holdTick(Player player, ItemStack stack, VacuumState state) {
        if (!Config.holdMobs()) {
            return;
        }
        Level level = player.level();
        LivingEntity held = level.getEntity(state.heldEntityId) instanceof LivingEntity living ? living : null;
        if (held != null && (!canHold(player, held) || center(held).distanceTo(holdPoint(player, held)) > Config.range())) {
            held = null;
        }
        if (held == null) {
            // Searching scans every living entity in range; with nothing to hold, look again only every few ticks.
            if (state.holdSearchCooldown-- > 0) {
                state.heldEntityId = -1;
                return;
            }
            held = findHoldTarget(player, stack);
            state.holdSearchCooldown = held == null ? HOLD_SEARCH_INTERVAL : 0;
            state.heldEntityId = held == null ? -1 : held.getId();
            if (held != null) {
                playSound(player, ModSounds.CAPTURE.get(), 0.5F, 0.6F);
            }
        }
        if (held == null) {
            return;
        }

        Vec3 toTarget = holdPoint(player, held).subtract(center(held));
        Vec3 velocity = toTarget.scale(HOLD_STIFFNESS);
        if (velocity.length() > HOLD_MAX_SPEED) {
            velocity = velocity.normalize().scale(HOLD_MAX_SPEED);
        }
        held.setDeltaMovement(velocity.add(0, held.getGravity(), 0));
        if (held instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        tumble(held, 4.0F);
        markMoved(held);
    }

    /** Where a held entity floats: just in front of the nozzle, further away for bigger entities. */
    public static Vec3 holdPoint(Player player, Entity entity) {
        return nozzlePos(player).add(player.getLookAngle().scale(1.4 + entity.getBbWidth() * 0.7));
    }

    private static LivingEntity findHoldTarget(Player player, ItemStack stack) {
        double range = Config.range();
        double minDot = Math.cos(Math.toRadians(Config.coneAngle()));
        VacTank tank = tank(stack);
        int slotCount = Config.slotCount();
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        e -> canHold(player, e) && inCone(player, e, range, minDot)
                                && !(e instanceof Mob mob && canVacuumMob(player, mob)
                                && tank.slotForMob(mob.getType(), slotCount, Config.mobCapacity()) >= 0))
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static boolean canHold(Player player, LivingEntity entity) {
        double maxSize = Config.maxHoldSize();
        return entity.isAlive() && entity != player && !(entity instanceof Player)
                && !entity.getType().is(Tags.EntityTypes.BOSSES)
                && entity.getBbWidth() <= maxSize && entity.getBbHeight() <= maxSize
                && isFree(player, entity)
                && !recentlyShot(entity);
    }

    private static void dropHeld(Player player, VacuumState state) {
        if (player.level().getEntity(state.heldEntityId) instanceof LivingEntity held) {
            // Let it drop gently instead of keeping the hold velocity.
            held.setDeltaMovement(held.getDeltaMovement().scale(0.3));
            markMoved(held);
        }
        state.heldEntityId = -1;
    }

    /** Launches the held mob, if any. */
    private static boolean shootHeld(Player player, ItemStack stack, VacuumState state) {
        if (!(player.level().getEntity(state.heldEntityId) instanceof LivingEntity held) || !held.isAlive()) {
            return false;
        }
        state.heldEntityId = -1;
        CompoundTag data = saveMob(held);
        if (data == null) {
            return false;
        }
        TankShot shot = TankShot.ofMob(player.level(), player, data);
        Vec3 from = center(held);
        placeShot(player, shot, from);
        shot.setDeltaMovement(player.getLookAngle().scale(Config.shootSpeed() * 0.9).add(0, 0.08, 0));
        held.discard();
        player.level().addFreshEntity(shot);
        shotEffects(player, stack, from);
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // Harvesting
    // ------------------------------------------------------------------------------------------------

    /** Picks berries from the bush or vine the player keeps the vacpack aimed at. */
    public static void harvestTick(Player player, VacuumState state) {
        if (!Config.harvestBerries()) {
            return;
        }
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(Config.range()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        if (pos == null || !Harvesting.canHarvest(level.getBlockState(pos))) {
            state.harvestPos = null;
            state.harvestTicks = 0;
            return;
        }
        if (!pos.equals(state.harvestPos)) {
            state.harvestPos = pos;
            state.harvestTicks = 0;
        }
        if (++state.harvestTicks >= HARVEST_TICKS) {
            state.harvestTicks = 0;
            Harvesting.harvest(level, pos, player);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Shooting
    // ------------------------------------------------------------------------------------------------

    /**
     * Launches the held mob if there is one; otherwise shoots one item or mob out of the selected slot, or releases a
     * pulse wave if the slot is empty.
     *
     * @return cooldown in ticks until the next shot
     */
    public static int shoot(Player player, ItemStack stack) {
        VacuumState state = player.getExistingDataOrNull(ModAttachments.VACUUM_STATE);
        if (state != null && shootHeld(player, stack, state)) {
            return HELD_SHOT_COOLDOWN;
        }

        // Holding the button empties the slot and then stops; a pulse wave only fires on a fresh press.
        boolean freshPress = state == null || state.freshPress;
        if (state != null) {
            state.freshPress = false;
        }
        VacTank.Taken taken = tank(stack).takeFromSelected();
        if (taken.isEmpty()) {
            if (!freshPress) {
                return EMPTY_SHOT_COOLDOWN;
            }
            if (Config.pulseEnabled()) {
                pulse(player, stack);
                return Config.pulseCooldown();
            }
            playSound(player, SoundEvents.DISPENSER_FAIL, 0.4F, 1.6F);
            return EMPTY_SHOT_COOLDOWN;
        }

        Level level = player.level();
        TankShot shot = taken.item().isEmpty()
                ? TankShot.ofMob(level, player, taken.mob())
                : TankShot.ofItem(level, player, taken.item());
        Vec3 origin = safeNozzlePos(player);
        placeShot(player, shot, origin);
        shot.setDeltaMovement(player.getLookAngle().scale(Config.shootSpeed()).add(0, 0.06, 0));
        level.addFreshEntity(shot);

        stack.set(ModDataComponents.TANK, taken.tank());
        shotEffects(player, stack, origin);
        return Config.shootCooldown();
    }

    /** Centres a shot on {@code center}, pulled back towards the player's eyes until it does not overlap blocks. */
    private static void placeShot(Player player, TankShot shot, Vec3 center) {
        Vec3 eye = player.getEyePosition();
        Vec3 pos = center;
        shot.moveTo(pos.x, pos.y - shot.getBbHeight() / 2, pos.z, player.getYRot(), 0);
        for (int i = 0; i < 8 && !player.level().noCollision(shot); i++) {
            pos = pos.lerp(eye, 0.25);
            shot.setPos(pos.x, pos.y - shot.getBbHeight() / 2, pos.z);
        }
    }

    private static void shotEffects(Player player, ItemStack stack, Vec3 origin) {
        // Far enough in front of the camera that first-person view is not covered.
        Vec3 puff = origin.add(player.getLookAngle().scale(0.9));
        particles(player, ModParticles.SHOT_PUFF.get(), puff, 2, 0.04, 0.02);
        playSound(player, ModSounds.SHOOT.get(), 0.8F, 0.9F + player.getRandom().nextFloat() * 0.25F);
        triggerRecoil(player, stack);
    }

    /** Pushes every item, mob and projectile in a wide cone away from the player. */
    public static void pulse(Player player, ItemStack stack) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = Config.pulseRange();
        AABB area = player.getBoundingBox().inflate(range);

        for (Entity entity : level.getEntities(player, area, VacuumHandler::isPulseTarget)) {
            Vec3 toEntity = center(entity).subtract(eye);
            double distance = toEntity.length();
            if (distance > range || (distance > 1.0 && toEntity.normalize().dot(look) < PULSE_CONE_DOT)) {
                continue;
            }
            double strength = Config.pulseStrength() * (1.0 - 0.35 * distance / range);
            if (entity instanceof LivingEntity living) {
                strength *= 1.0 - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
            }
            Vec3 direction = distance > 0.5 ? toEntity.normalize() : look;
            // Lift living things off the ground, otherwise ground friction eats the push at once.
            double lift = entity instanceof LivingEntity ? 0.3 + 0.12 * strength : 0.15 * strength;
            Vec3 push = direction.scale(strength).add(0, lift, 0);
            Vec3 velocity = entity.getDeltaMovement().scale(0.2).add(push);
            if (!ragdollify(player, entity, velocity)) {
                entity.setDeltaMovement(velocity);
                entity.hasImpulse = true;
                entity.hurtMarked = true;
            }
        }

        windBurst(player, eye, look);

        // Rings start a couple of blocks out so they do not cover the shooter's screen.
        Vec3 nozzle = nozzlePos(player);
        for (double distance = 2.5; distance <= range; distance += 1.5) {
            Vec3 p = nozzle.add(look.scale(distance));
            particles(player, ModParticles.PULSE_RING.get(), p, 1, 0, 0);
            particles(player, ParticleTypes.SMALL_GUST, p, 1, 0.15 * distance, 0);
        }
        playSound(player, ModSounds.PULSE.get(), 0.45F, 0.95F + player.getRandom().nextFloat() * 0.1F);
        playAnimation(player, stack, VacpackItem.RECOIL_CONTROLLER, VacpackItem.PULSE_ANIM);
    }

    /**
     * A pulse wave hitting a block close by bursts exactly like a vanilla wind charge hitting it: same explosion
     * (radius 1.2, no damage, 1.22 knockback, triggers doors/buttons/levers, wind-charge-proof blocks respected), so
     * shooting at your feet or a wall rocket-jumps you, with the wind charge's fall damage protection. A wind charge
     * owned by the player, never added to the world, is used as the explosion source for that.
     */
    public static void windBurst(Player player, Vec3 eye, Vec3 look) {
        if (!Config.pulseWindBurst() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 end = eye.add(look.scale(WIND_BURST_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        WindCharge source = new WindCharge(EntityType.WIND_CHARGE, level);
        source.setOwner(player);
        Vec3 at = hit.getLocation();
        level.explode(source, null, WIND_BURST_DAMAGE, at.x, at.y, at.z, WIND_BURST_RADIUS, false,
                Level.ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.WIND_CHARGE_BURST);
    }

    /**
     * Turns a mob or item blown away by the pulse wave into a tumbling ragdoll ({@link TankShot}) that becomes the real
     * entity again once it settles. Players, bosses, huge, leashed, riding or ridden mobs are only pushed.
     *
     * @return whether the entity was turned into a ragdoll
     */
    private static boolean ragdollify(Player player, Entity entity, Vec3 velocity) {
        Level level = player.level();
        TankShot shot;
        if (entity instanceof ItemEntity item) {
            shot = TankShot.ofItem(level, player, item.getItem());
        } else if (entity instanceof Mob mob && canHold(player, mob)) {
            CompoundTag data = new CompoundTag();
            if (!mob.save(data)) {
                return false;
            }
            // The original is discarded, so the ragdoll keeps its UUID and comes back as the very same mob.
            data.remove("Motion");
            shot = TankShot.ofMob(level, player, data);
        } else {
            return false;
        }
        Vec3 center = center(entity);
        shot.moveTo(center.x, center.y - shot.getBbHeight() / 2, center.z, entity.getYRot(), 0);
        shot.setDeltaMovement(velocity);
        entity.discard();
        level.addFreshEntity(shot);
        return true;
    }

    private static boolean isPulseTarget(Entity entity) {
        return entity.isAlive() && !(entity instanceof Player) && !entity.isSpectator()
                && (entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Projectile || entity instanceof ExperienceOrb);
    }

    // ------------------------------------------------------------------------------------------------
    // Geometry, animation, sound, particles
    // ------------------------------------------------------------------------------------------------

    /** Point slightly in front of and beside the player's eyes where the nozzle is. */
    public static Vec3 nozzlePos(Player player) {
        return nozzlePos(player, 1.0F);
    }

    public static Vec3 nozzlePos(Player player, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        float yaw = player.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        if (player.getMainArm() == HumanoidArm.LEFT) {
            right = right.scale(-1);
        }
        return player.getEyePosition(partialTick).add(look.scale(0.9)).add(right.scale(0.3)).add(0, -0.25, 0);
    }

    /** Nozzle position pulled back from walls, so shots never start inside blocks. */
    private static Vec3 safeNozzlePos(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 nozzle = nozzlePos(player);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, nozzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return nozzle;
        }
        Vec3 back = eye.subtract(hit.getLocation());
        return hit.getLocation().add(back.normalize().scale(Math.min(0.3, back.length())));
    }

    private static void triggerRecoil(Player player, ItemStack stack) {
        playAnimation(player, stack, VacpackItem.RECOIL_CONTROLLER, VacpackItem.SHOOT_ANIM);
    }

    private static void playAnimation(Player player, ItemStack stack, String controller, String animation) {
        if (player.level() instanceof ServerLevel serverLevel && stack.getItem() instanceof VacpackItem vacpack) {
            vacpack.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), controller, animation);
        }
    }

    private static void startFanAnimation(Player player, ItemStack stack, VacuumState state) {
        if (player.level() instanceof ServerLevel level && stack.getItem() instanceof VacpackItem vacpack) {
            state.animatedStackId = GeoItem.getOrAssignId(stack, level);
            vacpack.triggerAnim(player, state.animatedStackId, VacpackItem.FAN_CONTROLLER, VacpackItem.VACUUM_ANIM);
        }
    }

    private static void stopFanAnimation(Player player, VacuumState state) {
        if (state.animatedStackId != Long.MAX_VALUE && !player.level().isClientSide()) {
            ModItems.VACPACK.get().stopTriggeredAnim(player, state.animatedStackId, VacpackItem.FAN_CONTROLLER, VacpackItem.VACUUM_ANIM);
            state.animatedStackId = Long.MAX_VALUE;
        }
    }

    /** Plays at the nozzle: it stays in front of the player while turning, so the sound does not jump between ears. */
    static void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        Vec3 at = nozzlePos(player);
        player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void particles(Player player, ParticleOptions type, Vec3 pos, int count, double spread, double speed) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(type, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
        }
    }

    private static Vec3 center(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static VacTank tank(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
    }
}
