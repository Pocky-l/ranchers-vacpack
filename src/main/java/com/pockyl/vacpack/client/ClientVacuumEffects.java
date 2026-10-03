package com.pockyl.vacpack.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.network.CapturePayload;
import com.pockyl.vacpack.network.VacuumStatePayload;
import com.pockyl.vacpack.vacuum.VacuumHandler;

import java.util.Comparator;
import java.util.List;

/** Client-side suction effects for every vacuuming player in view: humming loop and the swirling particle beam. */
@EventBusSubscriber(modid = Vacpack.MOD_ID, value = Dist.CLIENT)
public final class ClientVacuumEffects {
    private static final int RING_INTERVAL = 6;
    private static final int RING_DOTS = 10;
    private static final int MAX_WIND_ENTITIES = 8;

    private static final IntSet VACUUMING = new IntOpenHashSet();
    private static SpriteSet wispSprites;
    private static SpriteSet dotSprites;
    private static SpriteSet bubbleSprites;

    private ClientVacuumEffects() {
    }

    public static void setWispSprites(SpriteSet sprites) {
        wispSprites = sprites;
    }

    public static void setDotSprites(SpriteSet sprites) {
        dotSprites = sprites;
    }

    public static void setBubbleSprites(SpriteSet sprites) {
        bubbleSprites = sprites;
    }

    /** Bubble sprites for airflow particles under water, or null before resources are loaded. */
    public static SpriteSet bubbleSprites() {
        return bubbleSprites;
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

    /**
     * Wind streaks around every mob and item caught in the airflow (including a mob held in the stream): they start on a
     * ring around the entity, move with it and are then drawn past it into the nozzle.
     */
    private static void spawnWindAroundEntities(ClientLevel level, Player player, double range, Vec3 look) {
        RandomSource random = level.random;
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eye = player.getEyePosition();
        double minDot = Math.cos(Math.toRadians(Config.coneAngle()));
        List<Entity> caught = level.getEntities(player, player.getBoundingBox().inflate(range), entity -> {
            if (!(entity instanceof Mob || entity instanceof ItemEntity) || !entity.isAlive()) {
                return false;
            }
            Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = toEntity.length();
            return distance <= range && distance > 0.5 && toEntity.scale(1.0 / distance).dot(look) >= minDot;
        });
        caught.sort(Comparator.comparingDouble(player::distanceToSqr));
        for (Entity entity : caught.subList(0, Math.min(caught.size(), MAX_WIND_ENTITIES))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            Vec3 toEye = eye.subtract(center).normalize();
            Vec3 up = Math.abs(toEye.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 a = toEye.cross(up).normalize();
            Vec3 b = toEye.cross(a).normalize();
            double radius = entity.getBbWidth() * 0.6 + 0.25;
            int count = entity instanceof ItemEntity ? 1 : 2;
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                // Start slightly behind the entity (as seen from the player) so the stream wraps around it.
                Vec3 pos = center.add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius))
                        .subtract(toEye.scale(entity.getBbWidth() * 0.4));
                Vec3 velocity = entity.getDeltaMovement().add(toEye.scale(0.12));
                boolean wisp = random.nextBoolean();
                minecraft.particleEngine.add(new VortexParticle(level, player, wisp ? wispSprites : dotSprites,
                        wisp ? VortexParticle.Style.WISP : VortexParticle.Style.DOT, pos, velocity, range));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        VACUUMING.clear();
    }

    /**
     * Seeds the airflow: particles appear in the far part of the suction cone (well away from the player) with a slight
     * inward drift; from then on {@link VortexParticle} simulates them. Every few ticks a ring of dots is seeded at the
     * far end, which the forces draw together into the nozzle.
     */
    private static void spawnBeam(ClientLevel level, Player player) {
        if (wispSprites == null || dotSprites == null) {
            return;
        }
        RandomSource random = level.random;
        Minecraft minecraft = Minecraft.getInstance();
        double range = Config.range();
        double far = range * 0.85;
        double near = Math.min(4.0, far * 0.5);
        Vec3 look = player.getLookAngle();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize();
        Vec3 v = look.cross(u).normalize();

        for (int i = 0; i < 5; i++) {
            double distance = near + (far - near) * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Mth.TWO_PI;
            double radius = (0.2 + distance * 0.12) * Math.sqrt(random.nextDouble());
            Vec3 pos = nozzle.add(look.scale(distance)).add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
            Vec3 velocity = look.scale(-0.08).add(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.01));
            VortexParticle.Style style = i < 2 ? VortexParticle.Style.WISP : i < 4 ? VortexParticle.Style.DOT : VortexParticle.Style.MOTE;
            minecraft.particleEngine.add(new VortexParticle(level, player, style == VortexParticle.Style.WISP ? wispSprites : dotSprites,
                    style, pos, velocity, range));
        }
        spawnWindAroundEntities(level, player, range, look);
        if (level.getGameTime() % RING_INTERVAL == 0) {
            double radius = 0.2 + far * 0.12;
            double phase = level.getGameTime() * 0.35;
            for (int i = 0; i < RING_DOTS; i++) {
                double angle = phase + i * Mth.TWO_PI / RING_DOTS;
                Vec3 pos = nozzle.add(look.scale(far)).add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
                minecraft.particleEngine.add(new VortexParticle(level, player, dotSprites, VortexParticle.Style.DOT,
                        pos, look.scale(-0.08), range));
            }
        }
    }
}
