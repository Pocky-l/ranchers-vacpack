package com.pockyl.vacpack.vacuum;

import net.minecraft.world.entity.LivingEntity;

import com.pockyl.vacpack.registry.ModAttachments;

/** Keeps mobs that were just shot out of a vacpack safe: briefly invulnerable, no damage from their first landing. */
public final class ShotProtection {
    public static final int INVULNERABLE_TICKS = 20;
    /** While airborne this long after release, position is synced every tick so the flight stays smooth on clients. */
    private static final int SMOOTH_SYNC_TICKS = 40;
    /** A fall guard that was never used expires after this long. */
    private static final int FALL_GUARD_TICKS = 200;

    private ShotProtection() {
    }

    public static boolean isInvulnerable(LivingEntity entity) {
        Shot shot = entity.getExistingDataOrNull(ModAttachments.SHOT);
        return shot != null && shot.age(entity.level().getGameTime()) <= INVULNERABLE_TICKS;
    }

    /** Returns whether a pending fall guard was used up by this landing. */
    public static boolean consumeFallGuard(LivingEntity entity) {
        if (entity.getExistingDataOrNull(ModAttachments.FALL_GUARD) == null) {
            return false;
        }
        entity.removeData(ModAttachments.FALL_GUARD);
        return true;
    }

    public static void tick(LivingEntity entity) {
        Shot shot = entity.getExistingDataOrNull(ModAttachments.SHOT);
        if (shot == null) {
            return;
        }
        long age = shot.age(entity.level().getGameTime());
        if (age <= SMOOTH_SYNC_TICKS && !entity.onGround()) {
            entity.hasImpulse = true;
        }
        if (age > FALL_GUARD_TICKS) {
            entity.removeData(ModAttachments.FALL_GUARD);
            entity.removeData(ModAttachments.SHOT);
        }
    }
}
