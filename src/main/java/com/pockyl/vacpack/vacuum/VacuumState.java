package com.pockyl.vacpack.vacuum;

import net.minecraft.core.BlockPos;

/** Server-side input and timers of one player. Not persisted. */
public final class VacuumState {
    boolean vacuuming;
    boolean shooting;
    int shootCooldown;
    int fullWarningCooldown;
    BlockPos harvestPos;
    int harvestTicks;
    /** GeckoLib instance id of the stack whose fan animation is running, or {@link Long#MAX_VALUE}. */
    long animatedStackId = Long.MAX_VALUE;

    public boolean isVacuuming() {
        return vacuuming;
    }

    public boolean isShooting() {
        return shooting;
    }
}
