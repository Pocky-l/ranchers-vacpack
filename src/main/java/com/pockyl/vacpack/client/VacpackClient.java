package com.pockyl.vacpack.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModParticles;

/** Client-only registration on the mod bus. The vacpack's renderer and arm pose come from {@code VacpackItem}. */
@Mod.EventBusSubscriber(modid = Vacpack.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VacpackClient {
    private VacpackClient() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "tank", new TankHud());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VACUUM.get(), sprites -> {
            ClientVacuumEffects.setWispSprites(sprites);
            return new VacuumParticle.Provider(sprites);
        });
        event.registerSpriteSet(ModParticles.VACUUM_BUBBLE.get(), sprites -> {
            ClientVacuumEffects.setBubbleSprites(sprites);
            return new VacuumParticle.Provider(sprites);
        });
        event.registerSpriteSet(ModParticles.VACUUM_DOT.get(), sprites -> {
            ClientVacuumEffects.setDotSprites(sprites);
            return new VacuumParticle.Provider(sprites);
        });
        event.registerSpriteSet(ModParticles.CAPTURE_RING.get(), BurstParticle.CaptureRing::new);
        event.registerSpriteSet(ModParticles.PULSE_RING.get(), BurstParticle.PulseRing::new);
        event.registerSpriteSet(ModParticles.SHOT_PUFF.get(), BurstParticle.ShotPuff::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TANK_SHOT.get(), TankShotRenderer::new);
    }
}
