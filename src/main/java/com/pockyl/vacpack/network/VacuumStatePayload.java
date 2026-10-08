package com.pockyl.vacpack.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server → clients: a player started or stopped vacuuming. Drives the suction sound loop and beam particles. */
public record VacuumStatePayload(int playerId, boolean vacuuming) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeBoolean(vacuuming);
    }

    public static VacuumStatePayload decode(FriendlyByteBuf buf) {
        return new VacuumStatePayload(buf.readVarInt(), buf.readBoolean());
    }
}
