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

/** Draws the carried item or mob, tumbling through the air. */
public final class TankShotRenderer extends EntityRenderer<TankShot> {
    private static final float SPIN_DEGREES_PER_TICK = 25.0F;

    private final ItemRenderer itemRenderer;

    public TankShotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(TankShot shot, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float spin = (shot.tickCount + partialTick) * SPIN_DEGREES_PER_TICK;
        Entity mob = shot.getDisplayMob();
        pose.pushPose();
        if (mob != null) {
            // Centre the mob on the projectile and let it somersault.
            pose.translate(0, shot.getBbHeight() / 2, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
            pose.mulPose(Axis.XP.rotationDegrees(spin * 0.6F));
            pose.translate(0, -mob.getBbHeight() / 2, 0);
            entityRenderDispatcher.render(mob, 0, 0, 0, 0, partialTick, pose, buffers, light);
        } else if (!shot.getItem().isEmpty()) {
            pose.translate(0, shot.getBbHeight() / 2, 0);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.XP.rotationDegrees(spin * 0.5F));
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
