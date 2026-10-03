package com.pockyl.vacpack.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.pockyl.vacpack.Vacpack;

/** Server → clients: a shot mob landed and was released as {@code mobId}; clients ease it out of its flight pose. */
public record ShotLandedPayload(int shotId, int mobId) implements CustomPacketPayload {
    public static final Type<ShotLandedPayload> TYPE = new Type<>(Vacpack.id("shot_landed"));

    public static final StreamCodec<ByteBuf, ShotLandedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShotLandedPayload::shotId,
            ByteBufCodecs.VAR_INT, ShotLandedPayload::mobId,
            ShotLandedPayload::new);

    @Override
    public Type<ShotLandedPayload> type() {
        return TYPE;
    }
}
