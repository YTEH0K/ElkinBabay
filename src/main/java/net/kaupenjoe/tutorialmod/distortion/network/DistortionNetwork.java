package net.kaupenjoe.tutorialmod.distortion.network;

import net.kaupenjoe.tutorialmod.distortion.client.DistortionClientState;
import net.kaupenjoe.tutorialmod.rift.client.RiftCompassClientState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class DistortionNetwork {

    private DistortionNetwork() {
    }

    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        var registrar = event.registrar("1");

        registrar.playToClient(
                SyncDistortionPayload.TYPE,
                SyncDistortionPayload.STREAM_CODEC,
                (payload, context) ->
                        DistortionClientState.setDistortionTenths(
                                payload.valueTenths()
                        )
        );

        registrar.playToClient(
                SyncRiftCompassPayload.TYPE,
                SyncRiftCompassPayload.STREAM_CODEC,
                (payload, context) ->
                        RiftCompassClientState.setTarget(
                                payload.hasTarget(),
                                payload.x(),
                                payload.y(),
                                payload.z(),
                                payload.dimension()
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

    public static void syncRiftCompass(
            ServerPlayer player,
            boolean hasTarget,
            int x,
            int y,
            int z,
            String dimension
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new SyncRiftCompassPayload(
                        hasTarget,
                        x,
                        y,
                        z,
                        dimension
                )
        );
    }
}