package com.pockyl.vacpack.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.pockyl.vacpack.vacuum.VacuumHandler;

import java.util.function.Supplier;

/** Client → server: current state of the vacuum (use) and shoot (attack) buttons. Sent only on change. */
public record VacpackInputPayload(boolean vacuum, boolean shoot) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(vacuum);
        buf.writeBoolean(shoot);
    }

    public static VacpackInputPayload decode(FriendlyByteBuf buf) {
        return new VacpackInputPayload(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(VacpackInputPayload payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            VacuumHandler.setInput(player, payload.vacuum(), payload.shoot());
        }
    }
}
