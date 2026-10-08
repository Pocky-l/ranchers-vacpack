package com.pockyl.vacpack.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.joml.Quaternionf;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.entity.TankShot;
import com.pockyl.vacpack.network.ShotLandedPayload;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * After a ragdoll turns back into the mob, the mob starts in the ragdoll's last orientation (lying on its side, on its
 * back...) and gets up smoothly, instead of snapping to standing.
 */
@EventBusSubscriber(modid = Vacpack.MOD_ID, value = Dist.CLIENT)
public final class LandingPoses {
    private static final float RECOVERY_TICKS = 12.0F;

    private record Tilt(Quaternionf orientation, float yaw, long startTick) {
    }

    /** The eased tilt of one frame, handed from render state extraction to rendering. */
    private record Pose(Quaternionf rotation, float yaw, float halfHeight) {
    }

    private static final ContextKey<Pose> POSE = new ContextKey<>(Vacpack.id("landing_pose"));
    private static final Int2ObjectMap<Tilt> TILTS = new Int2ObjectOpenHashMap<>();
    private static final Set<LivingEntityRenderState> PUSHED = Collections.newSetFromMap(new IdentityHashMap<>());

    private LandingPoses() {
    }

    public static void handleLanded(ShotLandedPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(payload.shotId()) instanceof TankShot shot) {
            TILTS.put(payload.mobId(), new Tilt(shot.getOrientation(1.0F), shot.getYRot(), level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
        }, LandingPoses::extractPose);
    }

    private static void extractPose(LivingEntity entity, LivingEntityRenderState state) {
        state.setRenderData(POSE, null);
        Tilt tilt = TILTS.get(entity.getId());
        if (tilt == null) {
            return;
        }
        float elapsed = (entity.level().getGameTime() - tilt.startTick()) + state.partialTick;
        if (elapsed >= RECOVERY_TICKS) {
            TILTS.remove(entity.getId());
            return;
        }
        float remaining = 1.0F - elapsed / RECOVERY_TICKS;
        float ease = remaining * remaining * (3.0F - 2.0F * remaining);
        state.setRenderData(POSE, new Pose(new Quaternionf().slerp(tilt.orientation(), ease), tilt.yaw(), entity.getBbHeight() / 2));
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        Pose tilt = state.getRenderData(POSE);
        if (tilt == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, tilt.halfHeight(), 0);
        pose.mulPose(Axis.YP.rotationDegrees(-tilt.yaw()));
        pose.mulPose(tilt.rotation());
        pose.mulPose(Axis.YP.rotationDegrees(tilt.yaw()));
        pose.translate(0, -tilt.halfHeight(), 0);
        PUSHED.add(state);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (PUSHED.remove(event.getRenderState())) {
            event.getPoseStack().popPose();
        }
    }

    /** Drops recoveries of mobs that were never rendered (e.g. out of view), so the map cannot grow. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && !TILTS.isEmpty()) {
            long now = level.getGameTime();
            TILTS.values().removeIf(tilt -> now - tilt.startTick() > RECOVERY_TICKS * 4);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TILTS.clear();
        PUSHED.clear();
        MobIcons.clear();
    }
}
