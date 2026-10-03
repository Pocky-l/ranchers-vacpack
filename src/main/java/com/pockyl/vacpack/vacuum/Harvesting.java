package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import com.pockyl.vacpack.registry.ModSounds;

/** Pulling fruit off plants with the vacpack, like vacuuming fruit off trees in the original game. */
public final class Harvesting {
    private Harvesting() {
    }

    public static boolean canHarvest(BlockState state) {
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return state.getValue(SweetBerryBushBlock.AGE) > 1;
        }
        return state.getBlock() instanceof CaveVines && CaveVines.hasGlowBerries(state);
    }

    /** Detaches the fruit as item entities flying towards the player, ready to be vacuumed, and resets the plant. */
    public static void harvest(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            int count = 1 + level.random.nextInt(2) + (age == SweetBerryBushBlock.MAX_AGE ? 1 : 0);
            spawnFruit(level, pos, player, new ItemStack(Items.SWEET_BERRIES, count));
            level.playSound(null, pos, ModSounds.HARVEST.get(), SoundSource.BLOCKS, 0.8F, 0.9F + level.random.nextFloat() * 0.3F);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            BlockState picked = state.setValue(SweetBerryBushBlock.AGE, 1);
            level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        } else if (state.getBlock() instanceof CaveVines && CaveVines.hasGlowBerries(state)) {
            spawnFruit(level, pos, player, new ItemStack(Items.GLOW_BERRIES));
            level.playSound(null, pos, ModSounds.HARVEST.get(), SoundSource.BLOCKS, 0.8F, 0.9F + level.random.nextFloat() * 0.3F);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            BlockState picked = state.setValue(CaveVines.BERRIES, false);
            level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
    }

    private static void spawnFruit(Level level, BlockPos pos, Player player, ItemStack stack) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 toPlayer = player.getEyePosition().subtract(center).normalize().scale(0.25);
        ItemEntity item = new ItemEntity(level, center.x, center.y, center.z, stack, toPlayer.x, toPlayer.y + 0.1, toPlayer.z);
        item.setNoPickUpDelay();
        level.addFreshEntity(item);
    }
}
