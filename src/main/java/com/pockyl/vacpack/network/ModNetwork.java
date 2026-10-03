package com.pockyl.vacpack.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.vacpack.client.ClientVacuumEffects;
import com.pockyl.vacpack.client.LandingPoses;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "3";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(VacpackInputPayload.TYPE, VacpackInputPayload.STREAM_CODEC, VacpackInputPayload::handle);
        registrar.playToServer(CycleSlotPayload.TYPE, CycleSlotPayload.STREAM_CODEC, CycleSlotPayload::handle);
        // Client-bound handlers live in client code; the lambdas only resolve it when a packet arrives on a client.
        registrar.playToClient(VacuumStatePayload.TYPE, VacuumStatePayload.STREAM_CODEC,
                (payload, context) -> ClientVacuumEffects.handleState(payload));
        registrar.playToClient(CapturePayload.TYPE, CapturePayload.STREAM_CODEC,
                (payload, context) -> ClientVacuumEffects.handleCapture(payload));
        registrar.playToClient(ShotLandedPayload.TYPE, ShotLandedPayload.STREAM_CODEC,
                (payload, context) -> LandingPoses.handleLanded(payload));
    }
}
