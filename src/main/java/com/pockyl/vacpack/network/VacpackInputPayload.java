package com.pockyl.vacpack.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.vacuum.VacuumHandler;

/** Client → server: current state of the vacuum (use) and shoot (attack) buttons. Sent only on change. */
public record VacpackInputPayload(boolean vacuum, boolean shoot) implements CustomPacketPayload {
    public static final Type<VacpackInputPayload> TYPE = new Type<>(Vacpack.id("input"));

    public static final StreamCodec<ByteBuf, VacpackInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, VacpackInputPayload::vacuum,
            ByteBufCodecs.BOOL, VacpackInputPayload::shoot,
            VacpackInputPayload::new);

    @Override
    public Type<VacpackInputPayload> type() {
        return TYPE;
    }

    public static void handle(VacpackInputPayload payload, IPayloadContext context) {
        VacuumHandler.setInput(context.player(), payload.vacuum(), payload.shoot());
    }
}
