package com.pockyl.vacpack.vacuum;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.ModTags;
import com.pockyl.vacpack.tank.VacTank;

/** Server-side vacpack behavior: suction, capture and shooting. */
public final class VacuumHandler {
    /** Ticks during which a freshly shot mob or item cannot be vacuumed again. */
    private static final int SHOT_IMMUNITY_TICKS = 20;
    private static final int EMPTY_SHOT_COOLDOWN = 10;
    private static final int SUCTION_SOUND_INTERVAL = 24;

    private VacuumHandler() {
    }

    public static void setInput(Player player, boolean vacuum, boolean shoot) {
        VacuumState state = player.getData(ModAttachments.VACUUM_STATE);
        state.shooting = shoot;
        if (state.vacuuming != vacuum) {
            state.vacuuming = vacuum;
            state.ticksVacuuming = 0;
            ItemStack stack = player.getMainHandItem();
            if (vacuum && stack.getItem() instanceof VacpackItem) {
                startFanAnimation(player, stack, state);
            } else {
                stopFanAnimation(player, state);
            }
        }
    }

    public static void cycleSlot(Player player, int delta) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof VacpackItem) {
            stack.set(ModDataComponents.TANK, tank(stack).cycle(delta, Config.slotCount()));
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

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof VacpackItem) || !player.isAlive() || player.isSpectator()) {
            if (state.vacuuming) {
                state.vacuuming = false;
                stopFanAnimation(player, state);
            }
            state.shooting = false;
            return;
        }

        if (state.vacuuming) {
            vacuumTick(player, stack);
            if (state.ticksVacuuming++ % SUCTION_SOUND_INTERVAL == 0) {
                playSound(player, SoundEvents.BREEZE_INHALE, 0.35F, 1.5F);
            }
        }
        if (state.shooting && state.shootCooldown == 0) {
            state.shootCooldown = shoot(player, stack) ? Config.shootCooldown() : EMPTY_SHOT_COOLDOWN;
        }
    }

    /** One tick of suction: pulls acceptable entities in the cone and stores those that reached the nozzle. */
    public static void vacuumTick(Player player, ItemStack stack) {
        Level level = player.level();
        Vec3 nozzle = nozzlePos(player);
        double range = Config.range();
        double minDot = Math.cos(Math.toRadians(Config.coneAngle()));
        double capture = Config.captureDistance();
        int slotCount = Config.slotCount();
        AABB area = player.getBoundingBox().inflate(range);
        VacTank tank = tank(stack);
        VacTank initial = tank;

        if (Config.vacuumItems()) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, e -> canVacuumItem(e) && inCone(player, e, range, minDot))) {
                if (tank.slotForItem(item.getItem(), slotCount, Config.itemCapacity()) < 0) {
                    continue;
                }
                if (center(item).distanceTo(nozzle) <= capture) {
                    VacTank.Insertion insertion = tank.insertItem(item.getItem(), slotCount, Config.itemCapacity());
                    tank = insertion.tank();
                    ItemStack remaining = item.getItem().copy();
                    remaining.shrink(insertion.inserted());
                    if (remaining.isEmpty()) {
                        item.discard();
                    } else {
                        item.setItem(remaining);
                    }
                    playSound(player, SoundEvents.ITEM_PICKUP, 0.25F, 1.2F + player.getRandom().nextFloat() * 0.6F);
                } else {
                    pull(item, nozzle);
                }
            }
        }

        if (Config.vacuumMobs()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area, e -> canVacuumMob(player, e) && inCone(player, e, range, minDot))) {
                if (tank.slotForMob(mob.getType(), slotCount, Config.mobCapacity()) < 0) {
                    continue;
                }
                if (center(mob).distanceTo(nozzle) <= capture) {
                    VacTank updated = capture(tank, mob, slotCount);
                    if (updated != null) {
                        tank = updated;
                        playSound(player, SoundEvents.SLIME_SQUISH_SMALL, 0.6F, 1.4F);
                    }
                } else {
                    pull(mob, nozzle);
                }
            }
        }

        if (!tank.equals(initial)) {
            stack.set(ModDataComponents.TANK, tank);
        }
        spawnSuctionParticles(player, nozzle, range);
    }

    /**
     * Shoots one item or mob out of the selected slot.
     *
     * @return whether something was shot
     */
    public static boolean shoot(Player player, ItemStack stack) {
        Level level = player.level();
        VacTank.Taken taken = tank(stack).takeFromSelected();
        if (taken.isEmpty()) {
            playSound(player, SoundEvents.DISPENSER_FAIL, 0.4F, 1.6F);
            return false;
        }

        Vec3 origin = safeNozzlePos(player);
        Vec3 velocity = player.getLookAngle().scale(Config.shootSpeed());

        if (!taken.item().isEmpty()) {
            ItemEntity item = new ItemEntity(level, origin.x, origin.y - 0.125, origin.z, taken.item(), velocity.x, velocity.y, velocity.z);
            item.setPickUpDelay(SHOT_IMMUNITY_TICKS);
            item.setThrower(player);
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
                mob.setData(ModAttachments.SHOT_AT, level.getGameTime());
                level.addFreshEntity(mob);
            }
        }

        stack.set(ModDataComponents.TANK, taken.tank());
        playSound(player, SoundEvents.BREEZE_SHOOT, 0.5F, 1.3F + player.getRandom().nextFloat() * 0.3F);
        if (level instanceof ServerLevel serverLevel && stack.getItem() instanceof VacpackItem vacpack) {
            vacpack.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), VacpackItem.RECOIL_CONTROLLER, VacpackItem.SHOOT_ANIM);
        }
        return true;
    }

    private static VacTank capture(VacTank tank, Mob mob, int slotCount) {
        CompoundTag data = new CompoundTag();
        if (!mob.save(data)) {
            return null;
        }
        // A fresh UUID is assigned on release, so duplicated tanks cannot spawn UUID clashes.
        data.remove("UUID");
        VacTank updated = tank.insertMob(mob.getType(), data, slotCount, Config.mobCapacity());
        if (updated != null) {
            mob.discard();
        }
        return updated;
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

    private static boolean recentlyShot(Mob mob) {
        Long shotAt = mob.getExistingDataOrNull(ModAttachments.SHOT_AT);
        return shotAt != null && mob.level().getGameTime() - shotAt <= SHOT_IMMUNITY_TICKS;
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

    private static void pull(Entity entity, Vec3 nozzle) {
        Vec3 toNozzle = nozzle.subtract(center(entity));
        double distance = toNozzle.length();
        // Stronger pull up close so entities do not hover in front of the nozzle.
        double strength = Config.pullStrength() * (1.0 + 2.0 / Math.max(distance, 0.5));
        Vec3 motion = entity.getDeltaMovement().scale(0.5).add(toNozzle.normalize().scale(strength));
        entity.setDeltaMovement(motion);
        entity.resetFallDistance();
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    /** Point slightly in front of and beside the player's eyes where the nozzle is. */
    public static Vec3 nozzlePos(Player player) {
        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        if (player.getMainArm() == HumanoidArm.LEFT) {
            right = right.scale(-1);
        }
        return player.getEyePosition().add(look.scale(0.9)).add(right.scale(0.3)).add(0, -0.25, 0);
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

    private static void spawnSuctionParticles(Player player, Vec3 nozzle, double range) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        double spread = Math.tan(Math.toRadians(Config.coneAngle())) * 0.8;
        for (int i = 0; i < 3; i++) {
            double distance = 1.5 + random.nextDouble() * (range * 0.6);
            Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(spread * distance * 0.5);
            Vec3 from = player.getEyePosition().add(look.scale(distance)).add(offset);
            // Particles live 8 ticks; this velocity makes them arrive at the nozzle.
            Vec3 velocity = nozzle.subtract(from).scale(1.0 / 8.0);
            level.sendParticles(ModParticles.VACUUM.get(), from.x, from.y, from.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
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
            VacpackItem vacpack = ModItems.VACPACK.get();
            vacpack.stopTriggeredAnim(player, state.animatedStackId, VacpackItem.FAN_CONTROLLER, VacpackItem.VACUUM_ANIM);
            state.animatedStackId = Long.MAX_VALUE;
        }
    }

    private static void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static Vec3 center(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static VacTank tank(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
    }
}
