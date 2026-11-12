package net.kaupenjoe.tutorialmod.distortion.network;

import net.kaupenjoe.tutorialmod.distortion.client.DistortionClientState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DistortionNetwork {

    private DistortionNetwork() {
    }

    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        event.registrar("1").playToClient(
                SyncDistortionPayload.TYPE,
                SyncDistortionPayload.STREAM_CODEC,
                (payload, context) ->
                        DistortionClientState.setDistortionTenths(
                                payload.valueTenths()
                        )
        );
    }

    public static void sync(
            ServerPlayer player,
            int valueTenths
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new SyncDistortionPayload(valueTenths)
        );
    }
}