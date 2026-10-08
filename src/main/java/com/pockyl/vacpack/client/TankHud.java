package com.pockyl.vacpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.item.VacpackItem;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.tank.TankSlot;
import com.pockyl.vacpack.tank.VacTank;

/** Tank slots drawn next to the hotbar while a vacpack is held. */
public final class TankHud implements IGuiOverlay {
    private static final int SLOT_SIZE = 20;
    private static final int GAP_TO_HOTBAR = 10;
    private static final int SLOT_BACKGROUND = 0x90000000;
    private static final int SLOT_BORDER = 0x60FFFFFF;
    private static final int SELECTED_BORDER = 0xFF6FD3FF;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int FULL_COLOR = 0xFFD86F;
    private static final int GAUGE_BACKGROUND = 0xFF202830;
    private static final int GAUGE_FILL = 0xFF6FD3FF;
    private static final int GAUGE_FULL = 0xFFFFD86F;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof VacpackItem)) {
            return;
        }

        VacTank tank = ModDataComponents.getTank(stack);
        int slots = Config.slotCount();
        int width = slots * SLOT_SIZE;
        int center = screenWidth / 2;
        // Stay clear of the offhand slot, which sits on the side opposite to the main arm.
        boolean rightSide = player.getMainArm() == HumanoidArm.RIGHT;
        int x0 = rightSide ? center + 91 + GAP_TO_HOTBAR : center - 91 - GAP_TO_HOTBAR - width;
        int y = screenHeight - SLOT_SIZE - 1;
        Font font = minecraft.font;

        for (int i = 0; i < slots; i++) {
            int x = x0 + i * SLOT_SIZE;
            TankSlot slot = tank.slot(i);
            graphics.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_BACKGROUND);
            graphics.renderOutline(x, y, SLOT_SIZE - 1, SLOT_SIZE - 1, i == tank.selected() ? SELECTED_BORDER : SLOT_BORDER);
            if (slot.isEmpty()) {
                continue;
            }
            // Mobs are shown as small 3D models (the one that would be shot next); items and unknown mobs as icons.
            boolean drawnAsModel = slot.holdsMobs() && MobIcons.render(graphics, slot.lastMob(), x + 1, y + 1,
                    SLOT_SIZE - 3, partialTick);
            if (!drawnAsModel) {
                graphics.renderItem(icon(slot), x + 1, y + 1);
            }
            int capacity = slot.holdsMobs() ? VacpackItem.mobCapacity(stack) : VacpackItem.itemCapacity(stack);
            // Fill gauge along the bottom edge of the slot; a creative tank never fills up, so it has none.
            if (!VacpackItem.isCreative(stack)) {
                int gaugeWidth = SLOT_SIZE - 3;
                int filled = Math.max(1, Math.round(gaugeWidth * Math.min(1.0F, (float) slot.amount() / capacity)));
                graphics.fill(x + 1, y + SLOT_SIZE - 3, x + 1 + gaugeWidth, y + SLOT_SIZE - 2, GAUGE_BACKGROUND);
                graphics.fill(x + 1, y + SLOT_SIZE - 3, x + 1 + filled, y + SLOT_SIZE - 2,
                        slot.amount() >= capacity ? GAUGE_FULL : GAUGE_FILL);
            }
            String count = slot.amount() >= 1000 ? slot.amount() / 1000 + "k" : String.valueOf(slot.amount());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, count, x + SLOT_SIZE - 1 - font.width(count), y + SLOT_SIZE - 11,
                    slot.amount() >= capacity ? FULL_COLOR : TEXT_COLOR, true);
            graphics.pose().popPose();
        }

        TankSlot selected = tank.selectedSlot();
        if (!selected.isEmpty()) {
            Component name = VacpackItem.contentName(selected);
            int textX = rightSide ? x0 : x0 + width - font.width(name);
            graphics.drawString(font, name, textX, y - 10, TEXT_COLOR, true);
        }
    }

    private static ItemStack icon(TankSlot slot) {
        if (slot.holdsItems()) {
            return slot.item();
        }
        SpawnEggItem egg = slot.mobType().map(SpawnEggItem::byId).orElse(null);
        return new ItemStack(egg != null ? egg : Items.SLIME_BALL);
    }
}
