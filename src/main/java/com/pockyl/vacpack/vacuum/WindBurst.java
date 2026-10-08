package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModParticles;

import java.util.ArrayList;
import java.util.List;

/**
 * The burst of a pulse wave hitting a block close by. Minecraft 1.20.1 has no wind charges, so this recreates what a
 * wind charge explosion does in later versions: no damage and no broken blocks, the same knockback (radius 1.2, twice
 * that for entities, multiplier 1.22, reduced by cover and Blast Protection), and blocks in the burst react: wooden
 * doors, trapdoors and fence gates toggle, buttons are pressed, levers flipped, bells rung and candles blown out.
 * Players thrown by the burst take no damage from their next landing.
 */
public final class WindBurst {
    private static final float RADIUS = 1.2F;
    private static final double KNOCKBACK = 1.22;
    /** A fall guard from a burst that never ended in a landing (e.g. the player fell into water) expires after this. */
    private static final int FALL_GUARD_TICKS = 200;

    private WindBurst() {
    }

    public static void explode(ServerLevel level, Player shooter, BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        double reach = RADIUS * 2.0;
        List<Entity> targets = new ArrayList<>(level.getEntities(shooter, new AABB(at, at).inflate(reach + 1.0)));
        // The shooter is pushed as well: that is the rocket jump.
        targets.add(shooter);
        for (Entity entity : targets) {
            push(entity, at, reach);
        }
        triggerBlocks(level, at, hit);
        level.sendParticles(ModParticles.PULSE_RING.get(), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.4, 0.2, 0.4, 0.05);
    }

    /** Explosion knockback as in vanilla, without the damage. */
    private static void push(Entity entity, Vec3 at, double reach) {
        if (!entity.isAlive() || entity.isSpectator() || entity instanceof Player player && player.getAbilities().flying) {
            return;
        }
        double distance = Math.sqrt(entity.distanceToSqr(at)) / reach;
        Vec3 direction = new Vec3(entity.getX() - at.x, entity.getEyeY() - at.y, entity.getZ() - at.z);
        if (distance > 1.0 || direction.lengthSqr() < 1.0E-8) {
            return;
        }
        double strength = (1.0 - distance) * Explosion.getSeenPercent(at, entity) * KNOCKBACK;
        if (entity instanceof LivingEntity living) {
            strength = ProtectionEnchantment.getExplosionKnockbackAfterDampener(living, strength);
        }
        entity.setDeltaMovement(entity.getDeltaMovement().add(direction.normalize().scale(strength)));
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        if (entity instanceof Player) {
            ModAttachments.setData(entity, ModAttachments.FALL_GUARD, entity.level().getGameTime() + FALL_GUARD_TICKS);
        }
    }

    private static void triggerBlocks(ServerLevel level, Vec3 at, BlockHitResult hit) {
        double radius = RADIUS + 0.5;
        int range = Mth.ceil(radius);
        BlockPos center = BlockPos.containing(at);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range), center.offset(range, range, range))) {
            if (Vec3.atCenterOf(pos).distanceTo(at) <= radius) {
                trigger(level, pos.immutable(), level.getBlockState(pos), hit);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void trigger(ServerLevel level, BlockPos pos, BlockState state, BlockHitResult hit) {
        boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
        if (state.getBlock() instanceof DoorBlock door) {
            // Only the lower half, so a door whose both halves are in the burst toggles once.
            if (!powered && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && DoorBlock.isWoodenDoor(state)) {
                door.setOpen(null, level, state, pos, !door.isOpen(state));
            }
        } else if (state.getBlock() instanceof FenceGateBlock) {
            if (!powered) {
                boolean open = state.getValue(FenceGateBlock.OPEN);
                level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, !open), 10);
                level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS,
                        1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
                level.gameEvent(open ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, pos, GameEvent.Context.of(state));
            }
        } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof ButtonBlock) {
            // Their "use" without a player toggles or presses them with sound; iron trapdoors refuse it.
            if (!powered) {
                state.getBlock().use(state, level, pos, null, InteractionHand.MAIN_HAND, hit);
            }
        } else if (state.getBlock() instanceof LeverBlock) {
            state.getBlock().use(state, level, pos, null, InteractionHand.MAIN_HAND, hit);
        } else if (state.getBlock() instanceof BellBlock bell) {
            bell.attemptToRing(level, pos, null);
        } else if (state.getBlock() instanceof AbstractCandleBlock && state.getValue(AbstractCandleBlock.LIT)) {
            AbstractCandleBlock.extinguish(null, state, level, pos);
        }
    }
}
