package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.entity.TankShot;

/**
 * What some items do when a shot from the tank first hits something: bone meal grows plants, fire charges set blocks
 * and mobs alight, snowballs freeze water and put out fires. Wherever vanilla has the same interaction it is reused,
 * so other mods hooking bone meal or fire starting see the shot like a player using the item.
 */
public final class ShotItemEffects {
    /** Snowballs freeze a disc of water with this radius, in blocks. */
    private static final int FREEZE_RADIUS = 2;
    private static final float BURN_SECONDS = 5.0F;
    private static final float BLAZE_SNOWBALL_DAMAGE = 3.0F;

    private ShotItemEffects() {
    }

    public static boolean hasEffect(ItemStack stack) {
        return stack.is(Items.BONE_MEAL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.SNOWBALL);
    }

    /** How a flying shot of this item looks for what it hits: snowballs also stop at water to freeze it. */
    public static ClipContext.Fluid fluidMode(ItemStack stack) {
        return stack.is(Items.SNOWBALL) ? ClipContext.Fluid.WATER : ClipContext.Fluid.NONE;
    }

    /**
     * The shot runs into a block (or, for snowballs, water).
     *
     * @return whether the effect happened and the item is used up
     */
    public static boolean hitBlock(TankShot shot, BlockHitResult hit) {
        if (!Config.shotItemEffects() || !(shot.level() instanceof ServerLevel level) || !(shot.getOwner() instanceof Player shooter)) {
            return false;
        }
        ItemStack stack = shot.getItem();
        if (stack.is(Items.BONE_MEAL)) {
            return boneMeal(level, shooter, stack, hit);
        }
        if (stack.is(Items.FIRE_CHARGE)) {
            return ignite(level, shooter, stack, hit);
        }
        if (stack.is(Items.SNOWBALL)) {
            return chill(level, shooter, stack, hit);
        }
        return false;
    }

    /**
     * The shot hits a mob. When this returns true the effect has dealt the hit itself (damage, knockback) and the item
     * is used up; otherwise the shot hits like any other item.
     */
    public static boolean hitMob(TankShot shot, LivingEntity target, Vec3 velocity) {
        if (!Config.shotItemEffects() || !(shot.level() instanceof ServerLevel level) || !(shot.getOwner() instanceof Player shooter)) {
            return false;
        }
        ItemStack stack = shot.getItem();
        if (stack.is(Items.FIRE_CHARGE)) {
            if (target.fireImmune() || (target instanceof Player victim && !shooter.canHarmPlayer(victim))) {
                return false;
            }
            if (Config.shotDamage() > 0) {
                target.hurt(shot.damageSources().thrown(shot, shooter), (float) Config.shotDamage());
            }
            target.igniteForSeconds(BURN_SECONDS);
            target.knockback(0.6, -velocity.x, -velocity.z);
            playFireChargeSound(level, target.blockPosition());
            return true;
        }
        if (stack.is(Items.SNOWBALL)) {
            // Exactly a vanilla snowball: no damage except to blazes, the hurt itself gives the small knockback.
            target.hurt(shot.damageSources().thrown(shot, shooter), target instanceof Blaze ? BLAZE_SNOWBALL_DAMAGE : 0.0F);
            snowPuff(level, shot.position());
            return true;
        }
        return false;
    }

    private static boolean boneMeal(ServerLevel level, Player shooter, ItemStack stack, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (!mayChange(level, shooter, pos, face, stack)) {
            return false;
        }
        return Items.BONE_MEAL.useOn(new UseOnContext(level, shooter, InteractionHand.MAIN_HAND, stack.copy(), hit)).consumesAction();
    }

    private static boolean ignite(ServerLevel level, Player shooter, ItemStack stack, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (!mayChange(level, shooter, pos, face, stack)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TntBlock) {
            // TNT handles fire charges itself (before the item gets a say), priming instead of catching fire.
            state.onCaughtFire(level, pos, face, shooter);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
            return true;
        }
        return Items.FIRE_CHARGE.useOn(new UseOnContext(level, shooter, InteractionHand.MAIN_HAND, stack.copy(), hit)).consumesAction();
    }

    /** Freezes the water that was hit, or puts out a fire, campfire or candle like a splash of water. */
    private static boolean chill(ServerLevel level, Player shooter, ItemStack stack, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        boolean changed = level.getFluidState(pos).is(Fluids.WATER)
                ? freeze(level, shooter, stack, pos)
                : dowse(level, shooter, stack, pos, face) | dowse(level, shooter, stack, pos.relative(face), face);
        if (changed) {
            snowPuff(level, hit.getLocation());
        }
        return changed;
    }

    /** Frost Walker's freezing: water sources with air above turn into frosted ice, which melts away again. */
    private static boolean freeze(ServerLevel level, Player shooter, ItemStack stack, BlockPos center) {
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        boolean frozen = false;
        BlockPos from = center.offset(-FREEZE_RADIUS, 0, -FREEZE_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(from, center.offset(FREEZE_RADIUS, 0, FREEZE_RADIUS))) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz > FREEZE_RADIUS * FREEZE_RADIUS
                    || !level.getBlockState(pos).is(Blocks.WATER) || !level.getFluidState(pos).is(Fluids.WATER)
                    || !level.getBlockState(pos.above()).is(BlockTags.AIR)
                    || !level.isUnobstructed(ice, pos, CollisionContext.empty())
                    || !mayChange(level, shooter, pos, Direction.UP, stack)) {
                continue;
            }
            if (level.setBlockAndUpdate(pos, ice)) {
                level.gameEvent(shooter, GameEvent.BLOCK_PLACE, pos);
                frozen = true;
            }
        }
        if (frozen) {
            level.playSound(null, center, SoundEvents.POWDER_SNOW_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.3F);
        }
        return frozen;
    }

    /** Same as a splash water bottle on that block. */
    private static boolean dowse(ServerLevel level, Player shooter, ItemStack stack, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        boolean burning = state.is(BlockTags.FIRE) || AbstractCandleBlock.isLit(state) || CampfireBlock.isLitCampfire(state);
        if (!burning || !mayChange(level, shooter, pos, face, stack)) {
            return false;
        }
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
            level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
            level.gameEvent(shooter, GameEvent.BLOCK_DESTROY, pos);
        } else if (AbstractCandleBlock.isLit(state)) {
            AbstractCandleBlock.extinguish(shooter, state, level, pos);
        } else {
            level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
            CampfireBlock.dowse(shooter, level, pos, state);
            level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
        }
        return true;
    }

    /**
     * The shooter may use the item on this face of the block by hand (spawn protection, claims, adventure mode...). The
     * block in front counts too: fire is placed there, and bone meal grows seagrass and coral there under water.
     */
    private static boolean mayChange(ServerLevel level, Player shooter, BlockPos pos, Direction face, ItemStack stack) {
        BlockPos front = pos.relative(face);
        return shooter.mayInteract(level, pos) && shooter.mayInteract(level, front) && shooter.mayUseItemAt(front, face, stack);
    }

    private static void playFireChargeSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F,
                (level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F);
    }

    private static void snowPuff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.0);
    }
}
