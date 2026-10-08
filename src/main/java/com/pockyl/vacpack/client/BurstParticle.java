package com.pockyl.vacpack.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** Short animated effect that grows and fades in place: capture ring, pulse ring, shot puff. */
public final class BurstParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float startSize;
    private final float growth;

    private BurstParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                          SpriteSet sprites, int lifetime, float size, float growth, float alpha) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.8F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = lifetime;
        this.startSize = size;
        this.growth = growth;
        this.quadSize = size;
        setColor(0.85F, 0.97F, 1.0F);
        setAlpha(alpha);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float progress = (float) age / lifetime;
        quadSize = startSize * (1.0F + growth * progress);
        alpha *= 0.9F;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public record CaptureRing(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new BurstParticle(level, x, y, z, xd, yd, zd, sprites, 6, 0.07F, 1.2F, 0.8F);
        }
    }

    public record PulseRing(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new BurstParticle(level, x, y, z, xd, yd, zd, sprites, 10, 0.28F, 2.0F, 0.6F);
        }
    }

    public record ShotPuff(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new BurstParticle(level, x, y, z, xd, yd, zd, sprites, 8, 0.07F, 1.0F, 0.7F);
        }
    }
}
