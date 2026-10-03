package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;
import com.pockyl.vacpack.network.CapturePayload;
import com.pockyl.vacpack.network.VacuumStatePayload;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.registry.ModSounds;
import com.pockyl.vacpack.registry.ModTags;
import com.pockyl.vacpack.tank.VacTank;

/** Server-side vacpack behavior: suction, capture, shooting, pulse wave and harvesting. */
public final class VacuumHandler {
    /** Ticks during which a freshly shot mob or item cannot be vacuumed again. */
    public static final int SHOT_IMMUNITY_TICKS = 20;
    private static final int EMPTY_SHOT_COOLDOWN = 10;
    private static final int FULL_WARNING_INTERVAL = 20;
    private static final int HARVEST_TICKS = 6;
    private static final double SWIRL_SPEED = 0.14;
    private static final double PULSE_CONE_DOT = Math.cos(Math.toRadians(50));

    private VacuumHandler() {
    }

    // ------------------------------------------------------------------------------------------------
    // Input and ticking
    // ------------------------------------------------------------------------------------------------

    public static void setInput(Player player, boolean vacuum, boolean shoot) {
        VacuumState state = player.getData(ModAttachments.VACUUM_STATE);
        state.shooting = shoot;
        ItemStack stack = player.getMainHandItem();
        setVacuuming(player, state, vacuum && stack.getItem() instanceof VacpackItem);
    }

    public static void cycleSlot(Player player, int delta) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof VacpackItem) {
            stack.set(ModDataComponents.TANK, tank(stack).cycle(delta, Config.slotCount()));
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
            stopFanAnimation(player, state);
        }
        if (player instanceof ServerPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new VacuumStatePayload(player.getId(), vacuuming));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Suction
    // ------------------------------------------------------------------------------------------------

    /**
     * One tick of suction: pulls acceptable entities in the cone along a spiral and stores those that reached the nozzle.
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
                playSound(player, ModSounds.CAPTURE.get(), 0.45F, 1.0F + player.getRandom().nextFloat() * 0.35F);
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
                    tumble(mob);
                    continue;
                }
                VacTank updated = capture(player, tank, mob, slotCount);
                if (updated != null) {
                    tank = updated;
                    playSound(player, ModSounds.CAPTURE.get(), 0.6F, 0.75F + player.getRandom().nextFloat() * 0.15F);
                    if (mob instanceof Slime) {
                        playSound(player, SoundEvents.SLIME_SQUISH_SMALL, 0.5F, 1.3F);
                    }
                }
            }
        }

        if (!tank.equals(initial)) {
            stack.set(ModDataComponents.TANK, tank);
        }
        return blocked;
    }

    private static VacTank capture(Player player, VacTank tank, Mob mob, int slotCount) {
        CompoundTag data = new CompoundTag();
        if (!mob.save(data)) {
            return null;
        }
        // A fresh UUID is assigned on release, so duplicated tanks cannot spawn UUID clashes.
        data.remove("UUID");
        data.remove("Motion");
        VacTank updated = tank.insertMob(mob.getType(), data, slotCount, Config.mobCapacity());
        if (updated != null) {
            animateCapture(player, mob);
            mob.discard();
        }
        return updated;
    }

    private static void animateCapture(Player player, Entity entity) {
        if (player.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, new CapturePayload(entity.getId(), player.getId()));
        }
    }

    public static boolean canVacuumItem(ItemEntity item) {
        return item.isAlive() && !item.hasPickUpDelay() && !item.getItem().is(ModTags.NOT_VACUUMABLE);
    }

    public static boolean canVacuumMob(Player player, Mob mob) {
        double maxSize = Config.maxMobSize();
        return mob.isAlive()
                && mob.getType().is(ModTags.VACUUMABLE)
                && mob.getBbWidth() <= maxSize && mob.getBbHeight() <= maxSize
                && !mob.isLeashed() && !mob.isPassenger() && !mob.isVehicle()
                && !(mob instanceof TamableAnimal pet && pet.isTame() && !pet.isOwnedBy(player))
                && !recentlyShot(mob);
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
        entity.resetFallDistance();
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    /** Mobs spin helplessly while being sucked in. */
    private static void tumble(Mob mob) {
        float yaw = mob.getYRot() + 23.0F;
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
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
     * Shoots one item or mob out of the selected slot, or releases a pulse wave if the slot is empty.
     *
     * @return cooldown in ticks until the next shot
     */
    public static int shoot(Player player, ItemStack stack) {
        Level level = player.level();
        VacTank.Taken taken = tank(stack).takeFromSelected();
        if (taken.isEmpty()) {
            if (Config.pulseEnabled()) {
                pulse(player, stack);
                return Config.pulseCooldown();
            }
            playSound(player, SoundEvents.DISPENSER_FAIL, 0.4F, 1.6F);
            return EMPTY_SHOT_COOLDOWN;
        }

        Vec3 origin = safeNozzlePos(player);
        Vec3 look = player.getLookAngle();
        Vec3 velocity = look.scale(Config.shootSpeed()).add(0, 0.06, 0);
        Shot shot = new Shot(player.getUUID(), level.getGameTime());

        if (!taken.item().isEmpty()) {
            ItemEntity item = new ItemEntity(level, origin.x, origin.y - 0.125, origin.z, taken.item(), velocity.x, velocity.y, velocity.z);
            item.setPickUpDelay(SHOT_IMMUNITY_TICKS);
            item.setThrower(player);
            item.setData(ModAttachments.SHOT, shot);
            level.addFreshEntity(item);
        } else {
            Entity mob = EntityType.loadEntityRecursive(taken.mob(), level, entity -> {
                entity.moveTo(origin.x, origin.y - entity.getBbHeight() / 2, origin.z, player.getYRot(), 0);
                return entity;
            });
            if (mob == null) {
                Vacpack.LOGGER.warn("Discarding stored mob that can no longer be loaded: {}", taken.mob().getString("id"));
            } else {
                mob.setDeltaMovement(velocity.scale(0.8));
                mob.resetFallDistance();
                mob.setData(ModAttachments.SHOT, shot);
                level.addFreshEntity(mob);
            }
        }

        stack.set(ModDataComponents.TANK, taken.tank());
        if (level instanceof ServerLevel serverLevel) {
            Vec3 puff = origin.add(look.scale(0.4));
            serverLevel.sendParticles(ParticleTypes.POOF, puff.x, puff.y, puff.z, 3, 0.05, 0.05, 0.05, 0.03);
        }
        playSound(player, ModSounds.SHOOT.get(), 0.7F, 0.9F + player.getRandom().nextFloat() * 0.25F);
        triggerRecoil(player, stack);
        return Config.shootCooldown();
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
            double strength = Config.pulseStrength() * (1.0 - 0.6 * distance / range);
            if (entity instanceof LivingEntity living) {
                strength *= 1.0 - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
            }
            Vec3 direction = distance > 0.5 ? toEntity.normalize() : look;
            Vec3 push = direction.scale(strength).add(0, 0.25 * strength, 0);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.2).add(push));
            entity.hasImpulse = true;
            entity.hurtMarked = true;
        }

        if (level instanceof ServerLevel serverLevel) {
            Vec3 center = nozzlePos(player).add(look.scale(1.2));
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            for (int i = 1; i <= 3; i++) {
                Vec3 p = eye.add(look.scale(i * range / 3.5));
                serverLevel.sendParticles(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 2, 0.3 * i, 0.2 * i, 0.3 * i, 0);
            }
        }
        playSound(player, ModSounds.PULSE.get(), 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
        triggerRecoil(player, stack);
    }

    private static boolean isPulseTarget(Entity entity) {
        return entity.isAlive() && !(entity instanceof Player) && !entity.isSpectator()
                && (entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Projectile || entity instanceof ExperienceOrb);
    }

    // ------------------------------------------------------------------------------------------------
    // Geometry, animation, sound
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

    /** Nozzle position pulled back from walls, so shot entities never spawn inside blocks. */
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
        if (player.level() instanceof ServerLevel serverLevel && stack.getItem() instanceof VacpackItem vacpack) {
            vacpack.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), VacpackItem.RECOIL_CONTROLLER, VacpackItem.SHOOT_ANIM);
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

    static void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static Vec3 center(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static VacTank tank(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
    }
}
