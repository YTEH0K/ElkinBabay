package net.kaupenjoe.tutorialmod.distortion.network;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncRiftCompassPayload(
        boolean hasTarget,
        int x,
        int y,
        int z,
        String dimension
) implements CustomPacketPayload {

    public static final Type<SyncRiftCompassPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TutorialMod.MOD_ID,
                            "sync_rift_compass"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SyncRiftCompassPayload
            > STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.hasTarget());
                buffer.writeVarInt(payload.x());
                buffer.writeVarInt(payload.y());
                buffer.writeVarInt(payload.z());
                buffer.writeUtf(payload.dimension());
            },
            buffer -> new SyncRiftCompassPayload(
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(256)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}