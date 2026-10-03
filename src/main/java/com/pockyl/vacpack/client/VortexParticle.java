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
 * A physically simulated particle of the suction airflow. While its player keeps vacuuming, it is accelerated towards
 * the nozzle (harder the closer it gets), pulled towards the aim axis and swirled around it, against air drag; it is
 * swallowed when it reaches the nozzle. When the player lets go, the forces stop: the particle coasts on its momentum,
 * slows down in the air, drifts and fades out. Rendered full-bright so the airflow glows at night.
 */
public final class VortexParticle extends TextureSheetParticle {
    /** Visual flavour; physics are the same for all. */
    public enum Style {
        /** Pixel air wisp. */
        WISP,
        /** Soft glowing dot. */
        DOT,
        /** Tiny bright speck. */
        MOTE
    }

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double PULL_FAR = 0.05;
    private static final double PULL_NEAR = 0.15;
    private static final double AXIS_PULL = 0.04;
    private static final double SWIRL = 0.015;
    private static final double DRAG = 0.86;
    private static final double RELEASED_DRAG = 0.9;
    private static final double MAX_SPEED = 0.9;
    private static final double SWALLOW_DISTANCE = 0.35;
    private static final int FADE_AFTER_RELEASE = 18;

    private final Player player;
    private final double range;
    private final float baseSize;
    private final float baseAlpha;
    private final float spin;
    private boolean released;
    private int releasedAt;
    private float swallowFade = 1.0F;

    public VortexParticle(ClientLevel level, Player player, SpriteSet sprites, Style style, Vec3 pos, Vec3 velocity, double range) {
        super(level, pos.x, pos.y, pos.z);
        RandomSource random = level.random;
        this.player = player;
        this.range = range;
        this.xd = velocity.x;
        this.yd = velocity.y;
        this.zd = velocity.z;
        this.lifetime = 90;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1.0F;
        switch (style) {
            case WISP -> {
                baseSize = 0.08F + random.nextFloat() * 0.05F;
                baseAlpha = 0.6F;
            }
            case DOT -> {
                baseSize = 0.05F + random.nextFloat() * 0.04F;
                baseAlpha = 0.5F;
            }
            default -> {
                baseSize = 0.025F + random.nextFloat() * 0.015F;
                baseAlpha = 0.8F;
            }
        }
        this.spin = style == Style.WISP ? (random.nextFloat() - 0.5F) * 0.4F : 0.0F;
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
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        roll += spin;
        if (age++ >= lifetime) {
            remove();
            return;
        }

        Vec3 pos = new Vec3(x, y, z);
        Vec3 velocity = new Vec3(xd, yd, zd);
        boolean sucking = !player.isRemoved() && ClientVacuumEffects.isVacuuming(player);
        if (sucking && !released) {
            Vec3 nozzle = VacuumHandler.nozzlePos(player);
            Vec3 axis = player.getLookAngle();
            Vec3 toNozzle = nozzle.subtract(pos);
            double distance = toNozzle.length();
            if (distance < SWALLOW_DISTANCE) {
                remove();
                return;
            }
            double closeness = 1.0 - Math.min(distance / range, 1.0);
            Vec3 fromNozzle = pos.subtract(nozzle);
            Vec3 radial = fromNozzle.subtract(axis.scale(fromNozzle.dot(axis)));
            Vec3 acceleration = toNozzle.scale((PULL_FAR + PULL_NEAR * closeness * closeness) / distance)
                    .subtract(radial.scale(AXIS_PULL));
            if (radial.lengthSqr() > 1.0E-4) {
                acceleration = acceleration.add(axis.cross(radial).normalize().scale(SWIRL * Math.min(radial.length(), 1.5)));
            }
            velocity = velocity.scale(DRAG).add(acceleration);
            // Fade out just before being swallowed, so nothing pops into the camera.
            swallowFade = (float) Mth.clamp((distance - SWALLOW_DISTANCE) / 1.2, 0.0, 1.0);
        } else {
            if (!released) {
                released = true;
                releasedAt = age;
            }
            // No more suction: coast on momentum, slow down in the air and drift up a little.
            velocity = velocity.scale(RELEASED_DRAG).add(0, 0.002, 0);
            if (age - releasedAt > FADE_AFTER_RELEASE) {
                remove();
                return;
            }
        }
        if (velocity.length() > MAX_SPEED) {
            velocity = velocity.normalize().scale(MAX_SPEED);
        }
        xd = velocity.x;
        yd = velocity.y;
        zd = velocity.z;
        setPos(x + xd, y + yd, z + zd);

        float fadeIn = Math.min(1.0F, age / 4.0F);
        float releaseFade = released ? 1.0F - (age - releasedAt) / (float) FADE_AFTER_RELEASE : 1.0F;
        setAlpha(baseAlpha * fadeIn * swallowFade * Math.max(releaseFade, 0.0F));
        quadSize = baseSize * (0.55F + 0.45F * swallowFade);
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
