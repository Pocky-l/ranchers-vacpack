package com.pockyl.vacpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;
import com.pockyl.vacpack.network.CycleSlotPayload;
import com.pockyl.vacpack.network.ModNetwork;
import com.pockyl.vacpack.network.VacpackInputPayload;

/**
 * Turns the use/attack buttons into vacuum/shoot while a vacpack is in the main hand.
 * Vanilla handling of those buttons is suppressed, except sneak + use on a block, so chests and doors stay usable.
 */
@Mod.EventBusSubscriber(modid = Vacpack.MOD_ID, value = Dist.CLIENT)
public final class ClientInputHandler {
    private static boolean sentVacuum;
    private static boolean sentShoot;

    private ClientInputHandler() {
    }

    static boolean holdsVacpack(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof VacpackItem;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !holdsVacpack(player)) {
            return;
        }
        boolean useOnBlock = event.isUseItem() && player.isShiftKeyDown()
                && minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK;
        if ((event.isAttack() || event.isUseItem()) && !useOnBlock) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            sentVacuum = false;
            sentShoot = false;
            return;
        }

        boolean active = minecraft.screen == null && holdsVacpack(player) && !player.isSpectator();
        boolean vacuum = active && minecraft.options.keyUse.isDown();
        boolean shoot = active && minecraft.options.keyAttack.isDown();
        if (vacuum != sentVacuum || shoot != sentShoot) {
            sentVacuum = vacuum;
            sentShoot = shoot;
            ModNetwork.sendToServer(new VacpackInputPayload(vacuum, shoot));
        }

        while (ModKeyMappings.CYCLE_SLOT.consumeClick()) {
            if (active) {
                ModNetwork.sendToServer(new CycleSlotPayload(1));
            }
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.screen == null && player.isShiftKeyDown() && holdsVacpack(player)
                && event.getScrollDelta() != 0) {
            event.setCanceled(true);
            ModNetwork.sendToServer(new CycleSlotPayload(event.getScrollDelta() > 0 ? -1 : 1));
        }
    }
}
