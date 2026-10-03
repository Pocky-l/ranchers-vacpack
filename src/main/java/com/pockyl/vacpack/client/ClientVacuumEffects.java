package com.pockyl.vacpack.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.vacuum.VacuumHandler;

/** Client-side suction effects for every vacuuming player in view: humming loop and the swirling particle beam. */
@EventBusSubscriber(modid = Vacpack.MOD_ID, value = Dist.CLIENT)
public final class ClientVacuumEffects {
    private static final int PARTICLE_LIFETIME = 8;
    private static final int PARTICLES_PER_TICK = 4;

    private static final IntSet VACUUMING = new IntOpenHashSet();

    private ClientVacuumEffects() {
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

    /** Particles start on a spiral inside the suction cone and fly into the nozzle over their lifetime. */
    private static void spawnBeam(ClientLevel level, Player player) {
        RandomSource random = level.random;
        Vec3 look = player.getLookAngle();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = look.cross(up).normalize();
        Vec3 v = look.cross(u).normalize();
        double range = Config.range() * 0.75;
        double spread = Math.tan(Math.toRadians(Config.coneAngle()));
        double phase = level.getGameTime() * 0.7;

        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double distance = 1.2 + random.nextDouble() * range;
            double angle = phase + distance * 1.3 + i * (Math.PI * 2 / PARTICLES_PER_TICK);
            double radius = distance * spread * (0.35 + random.nextDouble() * 0.5);
            Vec3 from = nozzle.add(look.scale(distance))
                    .add(u.scale(Math.cos(angle) * radius))
                    .add(v.scale(Math.sin(angle) * radius));
            Vec3 velocity = nozzle.subtract(from).scale(1.0 / PARTICLE_LIFETIME);
            level.addParticle(ModParticles.VACUUM.get(), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
    }
}
