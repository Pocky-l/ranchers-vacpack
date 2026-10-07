package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;

import com.pockyl.vacpack.item.VacpackItem;

/** Server-side input and timers of one player. Not persisted. */
public final class VacuumState {
    boolean vacuuming;
    boolean shooting;
    /** Set when the shoot button is pressed, cleared by the first shot; a pulse wave needs a fresh press. */
    boolean freshPress;
    int shootCooldown;
    int fullWarningCooldown;
    BlockPos harvestPos;
    /** Entity id of the mob floating in the air stream, or -1. */
    int heldEntityId = -1;
    /** Ticks until the next search for a mob to hold. */
    int holdSearchCooldown;
    int harvestTicks;
    /** GeckoLib instance id of the stack whose fan animation is running, or {@link Long#MAX_VALUE}. */
    long animatedStackId = Long.MAX_VALUE;
    /** The vacpack (regular or creative) whose fan animation is running; GeckoLib keeps animations per item. */
    VacpackItem animatedItem;

    public boolean isVacuuming() {
        return vacuuming;
    }

    public boolean isShooting() {
        return shooting;
    }
}
