package net.kaupenjoe.tutorialmod.distortion;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(
                    NeoForgeRegistries.ATTACHMENT_TYPES,
                    TutorialMod.MOD_ID
            );

    public static final Supplier<AttachmentType<DistortionData>> DISTORTION =
            ATTACHMENTS.register(
                    "distortion",
                    () -> AttachmentType.serializable(DistortionData::new)
                            .copyOnDeath()
                            .build()
            );

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}