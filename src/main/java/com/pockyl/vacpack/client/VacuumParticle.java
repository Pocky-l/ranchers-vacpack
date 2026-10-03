package com.pockyl.vacpack.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Air streak flying into the nozzle at constant speed; the server sets its velocity to arrive in {@link #LIFETIME} ticks. */
public final class VacuumParticle extends TextureSheetParticle {
    private static final int LIFETIME = 8;

    private final SpriteSet sprites;

    private VacuumParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = LIFETIME;
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize *= 0.5F + random.nextFloat() * 0.4F;
        float shade = 0.85F + random.nextFloat() * 0.15F;
        setColor(shade * 0.85F, shade * 0.95F, shade);
        setAlpha(0.0F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        // Fade in, then out as it reaches the nozzle.
        float progress = (float) age / lifetime;
        setAlpha(0.6F * Math.min(1.0F, Math.min(progress * 4.0F, (1.0F - progress) * 3.0F)));
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
