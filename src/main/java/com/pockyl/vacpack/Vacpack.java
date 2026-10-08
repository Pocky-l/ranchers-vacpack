package com.pockyl.vacpack;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.pockyl.vacpack.network.ModNetwork;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModEntities;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.registry.ModParticles;
import com.pockyl.vacpack.registry.ModSounds;
import com.pockyl.vacpack.registry.PockyModsTab;

@Mod(Vacpack.MOD_ID)
public final class Vacpack {
    public static final String MOD_ID = "vacpack";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The no-argument constructor with the static loading context also works on NeoForge 1.20.1 and older Forge 47 builds.
    @SuppressWarnings("removal")
    public Vacpack() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModParticles.register(modBus);
        ModSounds.register(modBus);
        ModAttachments.register(modBus);
        ModNetwork.register();
        PockyModsTab.register(modBus, () -> new ItemStack(ModItems.VACPACK.get()), output -> {
            output.accept(ModItems.VACPACK.get());
            output.accept(ModItems.CREATIVE_VACPACK.get());
        });

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
