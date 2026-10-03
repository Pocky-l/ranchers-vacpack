package com.pockyl.vacpack.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.entity.TankShot;
import com.pockyl.vacpack.network.ShotLandedPayload;

/**
 * After a shot mob lands, it keeps its last mid-air tilt for a moment and rolls back upright, instead of snapping from
 * the tumbling projectile pose to standing.
 */
@EventBusSubscriber(modid = Vacpack.MOD_ID, value = Dist.CLIENT)
public final class LandingPoses {
    private static final float RECOVERY_TICKS = 7.0F;

    private record Tilt(float flip, float wobble, float yaw, long startTick) {
    }

    private static final Int2ObjectMap<Tilt> TILTS = new Int2ObjectOpenHashMap<>();
    private static final IntSet PUSHED = new IntOpenHashSet();

    private LandingPoses() {
    }

    public static void handleLanded(ShotLandedPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(payload.shotId()) instanceof TankShot shot) {
            float flip = Mth.wrapDegrees(shot.getFlip(1.0F) + shot.getAim(1.0F));
            TILTS.put(payload.mobId(), new Tilt(flip, shot.getWobble(1.0F), shot.getYRot(), level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Tilt tilt = TILTS.get(entity.getId());
        if (tilt == null) {
            return;
        }
        float elapsed = (entity.level().getGameTime() - tilt.startTick()) + event.getPartialTick();
        if (elapsed >= RECOVERY_TICKS) {
            TILTS.remove(entity.getId());
            return;
        }
        float remaining = 1.0F - elapsed / RECOVERY_TICKS;
        float ease = remaining * remaining * (3.0F - 2.0F * remaining);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float half = entity.getBbHeight() / 2;
        pose.translate(0, half, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-tilt.yaw()));
        pose.mulPose(Axis.XP.rotationDegrees(tilt.flip() * ease));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt.wobble() * ease));
        pose.mulPose(Axis.YP.rotationDegrees(tilt.yaw()));
        pose.translate(0, -half, 0);
        PUSHED.add(entity.getId());
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TILTS.clear();
        PUSHED.clear();
    }
}
