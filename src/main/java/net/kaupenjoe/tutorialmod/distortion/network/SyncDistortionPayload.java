package net.kaupenjoe.tutorialmod.distortion.network;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncDistortionPayload(int valueTenths)
        implements CustomPacketPayload {

    public static final Type<SyncDistortionPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TutorialMod.MOD_ID,
                            "sync_distortion"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SyncDistortionPayload
            > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SyncDistortionPayload::valueTenths,
            SyncDistortionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}