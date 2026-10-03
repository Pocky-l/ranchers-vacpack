package com.pockyl.vacpack.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(VacpackInputPayload.TYPE, VacpackInputPayload.STREAM_CODEC, VacpackInputPayload::handle);
        registrar.playToServer(CycleSlotPayload.TYPE, CycleSlotPayload.STREAM_CODEC, CycleSlotPayload::handle);
    }
}
