package com.pockyl.vacpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.pockyl.vacpack.vacuum.VacuumHandler;

import java.util.List;

/** Renders a captured entity flying into the nozzle while shrinking to nothing. */
public final class VacuumCaptureParticle extends Particle {
    /** Captured entities are drawn by their own particle group, see {@link Group}. */
    public static final ParticleRenderType GROUP = new ParticleRenderType("VACPACK_CAPTURE", "VC");
    private static final int DURATION = 5;

    private final Entity entity;
    private final Player player;
    private final Vec3 start;

    public VacuumCaptureParticle(ClientLevel level, Entity entity, Player player) {
        super(level, entity.getX(), entity.getY(), entity.getZ());
        // Removed from the level right after this, so nothing else touches it during the animation.
        this.entity = entity;
        this.player = player;
        this.start = entity.position();
        this.lifetime = DURATION;
    }

    @Override
    public ParticleRenderType getGroup() {
        return GROUP;
    }

    @Override
    public void tick() {
        if (age++ >= lifetime || player.isRemoved()) {
            remove();
        }
    }

    private Instance extract(EntityRenderDispatcher dispatcher, Camera camera, float partialTick) {
        float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        float eased = progress * progress;
        Vec3 nozzle = VacuumHandler.nozzlePos(player, partialTick).subtract(0, entity.getBbHeight() * 0.5 * (1 - eased), 0);
        Vec3 pos = start.lerp(nozzle, eased).subtract(camera.position());
        EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        return new Instance(state, pos, 1.0F - eased * 0.95F, progress * 360.0F);
    }

    private record Instance(EntityRenderState state, Vec3 offset, float scale, float spin) {
    }

    /** Draws every captured entity; the animation is short, so there is no frustum culling. */
    public static final class Group extends ParticleGroup<VacuumCaptureParticle> {
        public Group(ParticleEngine engine) {
            super(engine);
        }

        @Override
        public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTick) {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            return new State(particles.stream().map(particle -> particle.extract(dispatcher, camera, partialTick)).toList());
        }
    }

    private record State(List<Instance> instances) implements ParticleGroupRenderState {
        @Override
        public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            for (Instance instance : instances) {
                PoseStack pose = new PoseStack();
                pose.translate(instance.offset().x, instance.offset().y, instance.offset().z);
                pose.scale(instance.scale(), instance.scale(), instance.scale());
                pose.mulPose(Axis.YP.rotationDegrees(instance.spin()));
                dispatcher.submit(instance.state(), camera, 0, 0, 0, pose, collector);
            }
        }
    }
}
