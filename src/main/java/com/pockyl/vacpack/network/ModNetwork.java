package com.pockyl.vacpack.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.client.ClientVacuumEffects;
import com.pockyl.vacpack.client.LandingPoses;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Vacpack.id("main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(VacpackInputPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VacpackInputPayload::encode)
                .decoder(VacpackInputPayload::decode)
                .consumerMainThread(VacpackInputPayload::handle)
                .add();
        CHANNEL.messageBuilder(CycleSlotPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CycleSlotPayload::encode)
                .decoder(CycleSlotPayload::decode)
                .consumerMainThread(CycleSlotPayload::handle)
                .add();
        // Client-bound handlers live in client code; the lambdas only resolve it when a packet arrives on a client.
        CHANNEL.messageBuilder(VacuumStatePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VacuumStatePayload::encode)
                .decoder(VacuumStatePayload::decode)
                .consumerMainThread((payload, context) -> ClientVacuumEffects.handleState(payload))
                .add();
        CHANNEL.messageBuilder(CapturePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapturePayload::encode)
                .decoder(CapturePayload::decode)
                .consumerMainThread((payload, context) -> ClientVacuumEffects.handleCapture(payload))
                .add();
        CHANNEL.messageBuilder(ShotLandedPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShotLandedPayload::encode)
                .decoder(ShotLandedPayload::decode)
                .consumerMainThread((payload, context) -> LandingPoses.handleLanded(payload))
                .add();
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, Object payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), payload);
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, Object payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), payload);
    }
}
