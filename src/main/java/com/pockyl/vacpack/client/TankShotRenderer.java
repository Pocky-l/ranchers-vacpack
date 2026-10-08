package com.pockyl.vacpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.pockyl.vacpack.entity.TankShot;

/**
 * Draws the carried mob or item with the tumbling rigid-body orientation computed by {@link TankShot}; the mob's head
 * and limbs are animated by the ragdoll as well.
 */
public final class TankShotRenderer extends EntityRenderer<TankShot, TankShotRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public TankShotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TankShot shot, State state, float partialTick) {
        super.extractRenderState(shot, state, partialTick);
        state.yRot = shot.getYRot(partialTick);
        state.orientation.set(shot.getOrientation(partialTick));
        Entity mob = shot.getDisplayMob();
        if (mob != null) {
            EntityRenderState mobState = entityRenderDispatcher.extractEntity(mob, partialTick);
            // Ragdolls cast no shadow: the nested render would otherwise draw the mob's own one.
            mobState.shadowPieces.clear();
            // The render copy is not in the level; light it like the ragdoll.
            mobState.lightCoords = state.lightCoords;
            state.mob = mobState;
            state.mobHeight = mob.getBbHeight();
            state.item.clear();
        } else {
            state.mob = null;
            itemModelResolver.updateForNonLiving(state.item, shot.getItem(), ItemDisplayContext.GROUND, shot);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0, state.boundingBoxHeight / 2, 0);
        if (state.mob != null) {
            pose.mulPose(Axis.YP.rotationDegrees(-state.yRot));
            pose.mulPose(state.orientation);
            pose.translate(0, -state.mobHeight / 2, 0);
            entityRenderDispatcher.submit(state.mob, camera, 0, 0, 0, pose, collector);
        } else if (!state.item.isEmpty()) {
            pose.mulPose(Axis.YP.rotationDegrees(-state.yRot));
            pose.mulPose(state.orientation);
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }

    public static final class State extends EntityRenderState {
        float yRot;
        final Quaternionf orientation = new Quaternionf();
        @Nullable EntityRenderState mob;
        float mobHeight;
        final ItemStackRenderState item = new ItemStackRenderState();
    }
}
