package com.pockyl.vacpack.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModParticles;

@Mod(value = Vacpack.MOD_ID, dist = Dist.CLIENT)
public final class VacpackClient {
    public VacpackClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(ModKeyMappings::register);
        modBus.addListener(VacpackClient::registerGuiLayers);
        modBus.addListener(VacpackClient::registerParticles);
        modBus.addListener(VacpackClient::registerRenderers);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Vacpack.id("tank"), new TankHud());
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VACUUM.get(), sprites -> {
            ClientVacuumEffects.setVortexSprites(sprites);
            return new VacuumParticle.Provider(sprites);
        });
        event.registerSpriteSet(ModParticles.CAPTURE_RING.get(), BurstParticle.CaptureRing::new);
        event.registerSpriteSet(ModParticles.PULSE_RING.get(), BurstParticle.PulseRing::new);
        event.registerSpriteSet(ModParticles.SHOT_PUFF.get(), BurstParticle.ShotPuff::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TANK_SHOT.get(), TankShotRenderer::new);
    }
}
