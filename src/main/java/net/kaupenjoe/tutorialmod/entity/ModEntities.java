package net.kaupenjoe.tutorialmod.entity;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.kaupenjoe.tutorialmod.entity.custom.AggressiveRiftEntity;

import java.util.function.Supplier;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TutorialMod.MOD_ID);

    public static final Supplier<EntityType<RiftEntity>> RIFT = ENTITY_TYPES.register(
            "rift",
            () -> EntityType.Builder.<RiftEntity>of(RiftEntity::new, MobCategory.MISC)
                    .sized(3.0F, 4.0F)
                    .clientTrackingRange(12)
                    .updateInterval(20)
                    .build("rift")
    );

    public static final Supplier<EntityType<AggressiveRiftEntity>> AGGRESSIVE_RIFT =
            ENTITY_TYPES.register(
                    "aggressive_rift",
                    () -> EntityType.Builder.<AggressiveRiftEntity>of(
                                    AggressiveRiftEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(2.5F, 3.5F)
                            .clientTrackingRange(16)
                            .updateInterval(20)
                            .build("aggressive_rift")
            );

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
