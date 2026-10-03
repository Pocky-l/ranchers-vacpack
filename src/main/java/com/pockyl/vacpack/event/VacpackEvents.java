package com.pockyl.vacpack.event;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.vacuum.VacuumHandler;

public final class VacpackEvents {
    private VacpackEvents() {
    }

    @EventBusSubscriber(modid = Vacpack.MOD_ID)
    /** NeoForge routes each event to the mod or game bus by its type, so one subscriber annotation fits both. */
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!event.getEntity().level().isClientSide()) {
                VacuumHandler.tick(event.getEntity());
            }
        }
    }

    @EventBusSubscriber(modid = Vacpack.MOD_ID)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(ModItems.VACPACK);
            }
        }
    }
}
