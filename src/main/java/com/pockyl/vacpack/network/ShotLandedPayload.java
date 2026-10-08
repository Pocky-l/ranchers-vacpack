package com.pockyl.vacpack.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server → clients: a shot mob landed and was released as {@code mobId}; clients ease it out of its flight pose. */
public record ShotLandedPayload(int shotId, int mobId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(shotId);
        buf.writeVarInt(mobId);
    }

    public static ShotLandedPayload decode(FriendlyByteBuf buf) {
        return new ShotLandedPayload(buf.readVarInt(), buf.readVarInt());
    }
}
