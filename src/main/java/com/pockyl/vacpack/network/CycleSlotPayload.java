package com.pockyl.vacpack.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.pockyl.vacpack.vacuum.VacuumHandler;

import java.util.function.Supplier;

/** Client → server: move the selected tank slot by {@code delta}. */
public record CycleSlotPayload(int delta) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(delta);
    }

    public static CycleSlotPayload decode(FriendlyByteBuf buf) {
        return new CycleSlotPayload(buf.readVarInt());
    }

    public static void handle(CycleSlotPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            VacuumHandler.cycleSlot(player, Integer.signum(payload.delta()));
        }
    }
}
