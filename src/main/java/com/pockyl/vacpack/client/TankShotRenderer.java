package com.pockyl.vacpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.vacpack.entity.TankShot;

/**
 * Draws the carried mob like a ragdoll, using the spring-driven pitch and roll computed by {@link TankShot}.
 * Items spin.
 */
public final class TankShotRenderer extends EntityRenderer<TankShot> {
    private static final float ITEM_SPIN_PER_TICK = 12.0F;

    private final ItemRenderer itemRenderer;

    public TankShotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(TankShot shot, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity mob = shot.getDisplayMob();
        pose.pushPose();
        pose.translate(0, shot.getBbHeight() / 2, 0);
        if (mob != null) {
            float half = mob.getBbHeight() / 2;
            pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
            pose.mulPose(Axis.XP.rotationDegrees(shot.getPitch(partialTick)));
            pose.mulPose(Axis.ZP.rotationDegrees(shot.getRoll(partialTick)));
            pose.translate(0, -half, 0);
            entityRenderDispatcher.render(mob, 0, 0, 0, 0, partialTick, pose, buffers, light);
        } else if (!shot.getItem().isEmpty()) {
            float spin = (shot.tickCount + partialTick) * ITEM_SPIN_PER_TICK;
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.ZP.rotationDegrees(shot.getRoll(partialTick) * 0.3F));
            itemRenderer.renderStatic(shot.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, shot.level(), shot.getId());
        }
        pose.popPose();
        super.render(shot, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TankShot shot) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
