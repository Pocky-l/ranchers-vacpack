package com.pockyl.vacpack.vacuum;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.registry.ModAttachments;

import java.util.List;

/** Shot items act as projectiles for a moment: the first mob they hit takes damage and knockback. */
public final class ShotImpacts {
    private static final int FLIGHT_TICKS = 30;
    private static final double MIN_IMPACT_SPEED = 0.4;

    private ShotImpacts() {
    }

    /** Called every tick for item entities on the server. */
    public static void tick(ItemEntity item) {
        Shot shot = item.getExistingDataOrNull(ModAttachments.SHOT);
        if (shot == null) {
            return;
        }
        Level level = item.level();
        Vec3 velocity = item.getDeltaMovement();
        if (shot.age(level.getGameTime()) > FLIGHT_TICKS || item.onGround() || velocity.length() < MIN_IMPACT_SPEED) {
            // Keep the marker until suction immunity ends; afterwards the item is an ordinary drop.
            if (shot.age(level.getGameTime()) > VacuumHandler.SHOT_IMMUNITY_TICKS) {
                item.removeData(ModAttachments.SHOT);
            }
            return;
        }

        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, item.getBoundingBox().inflate(0.3).expandTowards(velocity),
                target -> target.isAlive() && !target.isSpectator() && !target.getUUID().equals(shot.shooter())
                        && !(target instanceof Player player && player.isCreative()));
        if (hit.isEmpty()) {
            return;
        }

        LivingEntity target = hit.getFirst();
        Player shooter = level.getPlayerByUUID(shot.shooter());
        double damage = Config.shotDamage();
        if (damage > 0) {
            target.hurt(level.damageSources().thrown(item, shooter), (float) damage);
        }
        target.knockback(0.6, -velocity.x, -velocity.z);
        item.setDeltaMovement(velocity.scale(-0.15).add(0, 0.15, 0));
        item.removeData(ModAttachments.SHOT);
    }
}
