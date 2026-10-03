package com.pockyl.vacpack.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.pockyl.vacpack.Vacpack;

/** Server → clients: an entity was sucked into a player's vacpack; clients animate it shrinking into the nozzle. */
public record CapturePayload(int entityId, int playerId) implements CustomPacketPayload {
    public static final Type<CapturePayload> TYPE = new Type<>(Vacpack.id("capture"));

    public static final StreamCodec<ByteBuf, CapturePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CapturePayload::entityId,
            ByteBufCodecs.VAR_INT, CapturePayload::playerId,
            CapturePayload::new);

    @Override
    public Type<CapturePayload> type() {
        return TYPE;
    }
}
