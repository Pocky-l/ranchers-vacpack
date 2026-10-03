package com.pockyl.vacpack.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.vacuum.VacuumHandler;

/** Client → server: move the selected tank slot by {@code delta}. */
public record CycleSlotPayload(int delta) implements CustomPacketPayload {
    public static final Type<CycleSlotPayload> TYPE = new Type<>(Vacpack.id("cycle_slot"));

    public static final StreamCodec<ByteBuf, CycleSlotPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            CycleSlotPayload::new, CycleSlotPayload::delta);

    @Override
    public Type<CycleSlotPayload> type() {
        return TYPE;
    }

    public static void handle(CycleSlotPayload payload, IPayloadContext context) {
        VacuumHandler.cycleSlot(context.player(), Integer.signum(payload.delta()));
    }
}
