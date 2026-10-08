package com.pockyl.vacpack.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server → clients: an entity was sucked into a player's vacpack; clients animate it shrinking into the nozzle. */
public record CapturePayload(int entityId, int playerId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(playerId);
    }

    public static CapturePayload decode(FriendlyByteBuf buf) {
        return new CapturePayload(buf.readVarInt(), buf.readVarInt());
    }
}
