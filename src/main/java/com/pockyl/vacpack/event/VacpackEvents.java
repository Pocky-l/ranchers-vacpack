package com.pockyl.vacpack.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.vacuum.ShotProtection;
import com.pockyl.vacpack.vacuum.VacuumHandler;

public final class VacpackEvents {
    private VacpackEvents() {
    }

    @Mod.EventBusSubscriber(modid = Vacpack.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
                VacuumHandler.tick(event.player);
            }
        }

        @SubscribeEvent
        public static void onAttack(LivingAttackEvent event) {
            if (ShotProtection.isInvulnerable(event.getEntity())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onFall(LivingFallEvent event) {
            if (ShotProtection.consumeFallGuard(event.getEntity())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                ShotProtection.tick();
            }
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            ShotProtection.clear();
        }

        @SubscribeEvent
        public static void onStartTracking(PlayerEvent.StartTracking event) {
            if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof Player target) {
                VacuumHandler.syncTo(tracker, target);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Vacpack.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(ModItems.VACPACK);
                event.accept(ModItems.CREATIVE_VACPACK);
            }
        }
    }
}
