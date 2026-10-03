package com.pockyl.vacpack.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.network.CapturePayload;
import com.pockyl.vacpack.network.VacuumStatePayload;

/** Client-side suction effects for every vacuuming player in view: humming loop and the swirling particle beam. */
@EventBusSubscriber(modid = Vacpack.MOD_ID, value = Dist.CLIENT)
public final class ClientVacuumEffects {
    private static final int ARMS = 3;
    private static final int RING_INTERVAL = 5;
    private static final int RING_DOTS = 10;

    private static final IntSet VACUUMING = new IntOpenHashSet();
    private static SpriteSet wispSprites;
    private static SpriteSet dotSprites;

    private ClientVacuumEffects() {
    }

    public static void setWispSprites(SpriteSet sprites) {
        wispSprites = sprites;
    }

    public static void setDotSprites(SpriteSet sprites) {
        dotSprites = sprites;
    }

    public static boolean isVacuuming(Entity player) {
        return VACUUMING.contains(player.getId());
    }

    public static void handleState(VacuumStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.vacuuming()) {
            VACUUMING.remove(payload.playerId());
            return;
        }
        if (VACUUMING.add(payload.playerId()) && minecraft.level != null
                && minecraft.level.getEntity(payload.playerId()) instanceof Player player) {
            minecraft.getSoundManager().play(new VacuumSoundInstance(player));
        }
    }

    public static void handleCapture(CapturePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(payload.entityId());
        if (entity != null && level.getEntity(payload.playerId()) instanceof Player player) {
            minecraft.particleEngine.add(new VacuumCaptureParticle(level, entity, player));
            level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || VACUUMING.isEmpty()) {
            return;
        }
        VACUUMING.removeIf(id -> !(level.getEntity(id) instanceof Player));
        for (int id : VACUUMING) {
            if (level.getEntity(id) instanceof Player player) {
                spawnBeam(level, player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        VACUUMING.clear();
    }

    /**
     * Feeds the suction vortex: wisps and glowing dots on a few rotating spiral arms, small motes racing in close to the
     * nozzle, and every few ticks a ring that contracts down the funnel, which makes the direction of the airflow obvious.
     */
    private static void spawnBeam(ClientLevel level, Player player) {
        if (wispSprites == null || dotSprites == null) {
            return;
        }
        RandomSource random = level.random;
        Minecraft minecraft = Minecraft.getInstance();
        float maxDistance = (float) Math.min(Config.range(), 12.0) * 0.7F;
        float phase = level.getGameTime() * 0.35F;

        for (int i = 0; i < 4; i++) {
            // Bias towards the far end so the funnel has a visible mouth.
            float distance = 1.5F + (maxDistance - 1.5F) * (float) Math.sqrt(random.nextFloat());
            float angle = phase + (i % ARMS) * Mth.TWO_PI / ARMS + (random.nextFloat() - 0.5F) * 0.4F;
            boolean wisp = i < 2;
            minecraft.particleEngine.add(new VortexParticle(level, player, wisp ? wispSprites : dotSprites,
                    wisp ? VortexParticle.Style.WISP : VortexParticle.Style.DOT, distance, angle));
        }
        if (random.nextBoolean()) {
            minecraft.particleEngine.add(new VortexParticle(level, player, dotSprites, VortexParticle.Style.MOTE,
                    1.0F + random.nextFloat() * 1.8F, random.nextFloat() * Mth.TWO_PI));
        }
        if (level.getGameTime() % RING_INTERVAL == 0) {
            for (int i = 0; i < RING_DOTS; i++) {
                minecraft.particleEngine.add(new VortexParticle(level, player, dotSprites, VortexParticle.Style.RING,
                        maxDistance, phase + i * Mth.TWO_PI / RING_DOTS));
            }
        }
    }
}
