package com.pockyl.vacpack;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import com.pockyl.vacpack.network.ModNetwork;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.PockyModsTab;
import com.pockyl.vacpack.registry.ModSounds;

@Mod(Vacpack.MOD_ID)
public final class Vacpack {
    public static final String MOD_ID = "vacpack";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Vacpack(IEventBus modBus, ModContainer container) {
        ModDataComponents.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModParticles.register(modBus);
        ModSounds.register(modBus);
        ModAttachments.register(modBus);
        modBus.addListener(ModNetwork::register);
        PockyModsTab.register(modBus, () -> new ItemStack(ModItems.VACPACK.get()), output -> output.accept(ModItems.VACPACK));

        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
