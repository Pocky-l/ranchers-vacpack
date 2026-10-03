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
 * A particle of the suction vortex. It is attached to the vacuuming player: every tick its position is recomputed on a
 * narrowing spiral around the current aim, so the whole funnel turns with the player and everything swirls into the
 * nozzle, speeding up as it gets closer. Rendered full-bright so the vortex glows at night.
 */
public final class VortexParticle extends TextureSheetParticle {
    /** What the particle is: shapes the funnel radius, spin, size and color. */
    public enum Style {
        /** Pixel air wisp on a spiral arm. */
        WISP,
        /** Soft glowing dot on a spiral arm. */
        DOT,
        /** Tiny fast dot close to the nozzle. */
        MOTE,
        /** Dot of a ring that contracts down the funnel together with its siblings. */
        RING
    }

    private static final int FULL_BRIGHT = 0xF000F0;

    private final Player player;
    private final Style style;
    private final float maxDistance;
    private final float maxRadius;
    private final float spin;
    private final float baseSize;
    private final float baseAlpha;
    private float distance;
    private float angle;
    private float speed;

    public VortexParticle(ClientLevel level, Player player, SpriteSet sprites, Style style, float distance, float angle) {
        super(level, player.getX(), player.getEyeY(), player.getZ());
        RandomSource random = level.random;
        this.player = player;
        this.style = style;
        this.maxDistance = distance;
        this.distance = distance;
        this.angle = angle;
        float funnel = 0.12F + distance * 0.13F;
        float jitter = 0.75F + random.nextFloat() * 0.25F;
        switch (style) {
            case WISP -> {
                maxRadius = funnel * jitter;
                spin = 0.2F + random.nextFloat() * 0.08F;
                baseSize = 0.08F + random.nextFloat() * 0.05F;
                baseAlpha = 0.65F;
            }
            case DOT -> {
                maxRadius = funnel * 0.8F * jitter;
                spin = 0.24F + random.nextFloat() * 0.08F;
                baseSize = 0.05F + random.nextFloat() * 0.04F;
                baseAlpha = 0.55F;
            }
            case MOTE -> {
                maxRadius = funnel * 0.4F;
                spin = 0.45F;
                baseSize = 0.025F + random.nextFloat() * 0.015F;
                baseAlpha = 0.8F;
            }
            default -> {
                maxRadius = funnel;
                spin = 0.18F;
                baseSize = 0.04F;
                baseAlpha = 0.45F;
            }
        }
        this.speed = 0.11F + distance * 0.02F;
        this.lifetime = 60;
        this.hasPhysics = false;
        this.gravity = 0;
        this.quadSize = baseSize;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        float shade = 0.9F + random.nextFloat() * 0.1F;
        if (style == Style.MOTE) {
            setColor(shade, shade, shade);
        } else {
            setColor(shade * 0.72F, shade * 0.92F, shade);
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
        // Accelerate towards the nozzle like air rushing into it; spin faster where the funnel is narrow.
        speed *= 1.11F;
        distance -= speed;
        float progress = 1.0F - Math.max(distance, 0) / maxDistance;
        angle += spin * (1.0F + 1.5F * progress);
        if (style == Style.WISP) {
            roll += spin * 0.5F;
        }
        placeOnSpiral();

        quadSize = baseSize * (1.0F - 0.55F * progress);
        float fadeIn = Math.min(1.0F, age / 3.0F);
        float fadeOut = Math.min(1.0F, distance / 0.9F);
        setAlpha(baseAlpha * fadeIn * fadeOut);
        // Brighten towards white as it nears the nozzle.
        float white = 0.72F + 0.28F * progress;
        if (style != Style.MOTE) {
            setColor(white, 0.92F + 0.08F * progress, 1.0F);
        }
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
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
