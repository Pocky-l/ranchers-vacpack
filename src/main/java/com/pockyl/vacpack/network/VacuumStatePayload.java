package com.pockyl.vacpack.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.pockyl.vacpack.Vacpack;

/** Server → clients: a player started or stopped vacuuming. Drives the suction sound loop and beam particles. */
public record VacuumStatePayload(int playerId, boolean vacuuming) implements CustomPacketPayload {
    public static final Type<VacuumStatePayload> TYPE = new Type<>(Vacpack.id("vacuum_state"));

    public static final StreamCodec<ByteBuf, VacuumStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VacuumStatePayload::playerId,
            ByteBufCodecs.BOOL, VacuumStatePayload::vacuuming,
            VacuumStatePayload::new);

    @Override
    public Type<VacuumStatePayload> type() {
        return TYPE;
    }
}
