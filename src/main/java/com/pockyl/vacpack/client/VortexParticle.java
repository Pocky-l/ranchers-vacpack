package com.pockyl.vacpack.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.pockyl.vacpack.vacuum.VacuumHandler;

/**
 * An air wisp of the suction vortex. It is attached to the vacuuming player: every tick its position is recomputed
 * along a narrowing spiral around the current aim, so the whole funnel turns with the player and wisps swirl into the
 * nozzle, speeding up as they get closer.
 */
public final class VortexParticle extends TextureSheetParticle {
    private final Player player;
    private final float maxDistance;
    private final float maxRadius;
    private final float spin;
    private float distance;
    private float angle;
    private float speed;
    private final float baseSize;

    public VortexParticle(ClientLevel level, Player player, SpriteSet sprites, float distance, float angle, boolean mote) {
        super(level, player.getX(), player.getEyeY(), player.getZ());
        RandomSource random = level.random;
        this.player = player;
        this.maxDistance = distance;
        this.distance = distance;
        this.angle = angle;
        // The funnel is narrow: a little over a block wide at its far end, closing in to the nozzle.
        this.maxRadius = (0.12F + distance * 0.13F) * (mote ? 0.5F : 0.7F + random.nextFloat() * 0.3F);
        this.spin = (0.22F + random.nextFloat() * 0.1F) * (mote ? 1.6F : 1.0F);
        this.speed = 0.12F + distance * 0.02F;
        this.lifetime = 60;
        this.hasPhysics = false;
        this.gravity = 0;
        this.baseSize = mote ? 0.035F + random.nextFloat() * 0.02F : 0.08F + random.nextFloat() * 0.06F;
        this.quadSize = baseSize;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        float shade = 0.88F + random.nextFloat() * 0.12F;
        if (mote) {
            setColor(shade, shade, shade);
        } else {
            setColor(shade * 0.78F, shade * 0.93F, shade);
        }
        setAlpha(0);
        pickSprite(sprites);
        placeOnSpiral();
        xo = x;
        yo = y;
        zo = z;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (player.isRemoved() || !ClientVacuumEffects.isVacuuming(player) || distance <= 0.25F || age++ >= lifetime) {
            remove();
            return;
        }
        // Accelerate towards the nozzle like air rushing into it.
        speed *= 1.12F;
        distance -= speed;
        angle += spin * (1.0F + 1.5F * (1.0F - distance / maxDistance));
        roll += spin * 0.5F;
        placeOnSpiral();

        float progress = 1.0F - Math.max(distance, 0) / maxDistance;
        quadSize = baseSize * (1.0F - 0.6F * progress);
        float fadeIn = Math.min(1.0F, age / 3.0F);
        float fadeOut = Math.min(1.0F, distance / 0.8F);
        setAlpha(0.7F * fadeIn * fadeOut);
    }

    private void placeOnSpiral() {
        Vec3 look = player.getLookAngle();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize();
        Vec3 v = look.cross(u).normalize();
        float d = Math.max(distance, 0);
        float radius = maxRadius * (d / maxDistance);
        Vec3 pos = nozzle.add(look.scale(d))
                .add(u.scale(Mth.cos(angle) * radius))
                .add(v.scale(Mth.sin(angle) * radius));
        setPos(pos.x, pos.y, pos.z);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
