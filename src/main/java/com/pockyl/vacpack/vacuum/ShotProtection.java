package com.pockyl.vacpack.vacuum;

import net.minecraft.world.entity.LivingEntity;

import com.pockyl.vacpack.registry.ModAttachments;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps mobs that were just released from a vacpack safe: briefly invulnerable, no damage from their first landing,
 * and smooth on clients while still flying. Only the released mobs are tracked, so the rest of the world costs nothing.
 */
public final class ShotProtection {
    public static final int INVULNERABLE_TICKS = 20;
    /** While airborne this long after release, position is synced every tick so the flight stays smooth on clients. */
    private static final int SMOOTH_SYNC_TICKS = 40;
    /** A fall guard that was never used expires after this long. */
    public static final int FALL_GUARD_TICKS = 200;

    /** Server-thread only. Weak, so unloaded or removed entities never leak. */
    private static final Set<LivingEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    private ShotProtection() {
    }

    public static void track(LivingEntity entity) {
        TRACKED.add(entity);
    }

    public static boolean isInvulnerable(LivingEntity entity) {
        Shot shot = ModAttachments.getExistingDataOrNull(entity, ModAttachments.SHOT);
        return shot != null && shot.age(entity.level().getGameTime()) <= INVULNERABLE_TICKS;
    }

    /** Returns whether a pending fall guard was used up by this landing. */
    public static boolean consumeFallGuard(LivingEntity entity) {
        Long until = ModAttachments.getExistingDataOrNull(entity, ModAttachments.FALL_GUARD);
        if (until == null) {
            return false;
        }
        ModAttachments.removeData(entity, ModAttachments.FALL_GUARD);
        return entity.level().getGameTime() <= until;
    }

    /** Called once per server tick. */
    public static void tick() {
        Iterator<LivingEntity> iterator = TRACKED.iterator();
        while (iterator.hasNext()) {
            LivingEntity entity = iterator.next();
            Shot shot = ModAttachments.getExistingDataOrNull(entity, ModAttachments.SHOT);
            if (entity.isRemoved() || shot == null) {
                iterator.remove();
                continue;
            }
            long age = shot.age(entity.level().getGameTime());
            if (age <= SMOOTH_SYNC_TICKS && !entity.onGround()) {
                entity.hasImpulse = true;
            }
            if (age > FALL_GUARD_TICKS) {
                ModAttachments.removeData(entity, ModAttachments.FALL_GUARD);
                ModAttachments.removeData(entity, ModAttachments.SHOT);
                iterator.remove();
            }
        }
    }

    public static void clear() {
        TRACKED.clear();
    }
}
