package com.pockyl.vacpack.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.tank.VacTank;

/** Item data kept in the stack's NBT tag. Stacks without the tag hold the default value. */
public final class ModDataComponents {
    /** Contents of a vacpack tank, see {@link VacTank}. */
    public static final String TANK = Vacpack.MOD_ID + ":tank";

    private ModDataComponents() {
    }

    public static VacTank getTank(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TANK, Tag.TAG_COMPOUND) ? VacTank.load(tag.getCompound(TANK)) : VacTank.EMPTY;
    }

    public static void setTank(ItemStack stack, VacTank tank) {
        if (tank.equals(VacTank.EMPTY)) {
            stack.removeTagKey(TANK);
        } else {
            stack.getOrCreateTag().put(TANK, tank.save());
        }
    }
}
