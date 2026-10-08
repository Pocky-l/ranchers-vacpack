package com.pockyl.vacpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.pockyl.vacpack.vacuum.VacuumHandler;

/** Renders a captured entity flying into the nozzle while shrinking to nothing. */
public final class VacuumCaptureParticle extends Particle {
    private static final int DURATION = 5;

    private final Entity entity;
    private final Player player;
    private final Vec3 start;

    public VacuumCaptureParticle(ClientLevel level, Entity entity, Player player) {
        super(level, entity.getX(), entity.getY(), entity.getZ());
        this.entity = entity instanceof ItemEntity item ? item.copy() : entity;
        this.player = player;
        this.start = entity.position();
        this.lifetime = DURATION;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    @Override
    public void tick() {
        if (age++ >= lifetime || player.isRemoved()) {
            remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        float eased = progress * progress;
        Vec3 nozzle = VacuumHandler.nozzlePos(player, partialTick).subtract(0, entity.getBbHeight() * 0.5 * (1 - eased), 0);
        Vec3 pos = start.lerp(nozzle, eased);
        float scale = 1.0F - eased * 0.95F;

        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = new PoseStack();
        pose.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(progress * 360.0F));
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0, 0, 0, entity.getYRot(), partialTick, pose, buffers, dispatcher.getPackedLightCoords(entity, partialTick));
        dispatcher.setRenderShadow(true);
        buffers.endBatch();
    }

    // Always render: the animation is short and computing exact bounds is not worth it.
    @Override
    public boolean shouldCull() {
        return false;
    }
}
