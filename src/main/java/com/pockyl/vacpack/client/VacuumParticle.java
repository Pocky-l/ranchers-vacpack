package com.pockyl.vacpack.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Air wisp flying into the nozzle at constant speed; spawners set its velocity to arrive in {@link #LIFETIME} ticks. */
public final class VacuumParticle extends TextureSheetParticle {
    public static final int LIFETIME = 8;

    private final float spin;

    private VacuumParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = LIFETIME;
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize = 0.12F + random.nextFloat() * 0.1F;
        this.roll = random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5F) * 0.6F;
        float shade = 0.85F + random.nextFloat() * 0.15F;
        setColor(shade * 0.82F, shade * 0.95F, shade);
        setAlpha(0.0F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        // Fade in, then out as it reaches the nozzle.
        float progress = (float) age / lifetime;
        setAlpha(0.75F * Math.min(1.0F, Math.min(progress * 4.0F, (1.0F - progress) * 3.0F)));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new VacuumParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
