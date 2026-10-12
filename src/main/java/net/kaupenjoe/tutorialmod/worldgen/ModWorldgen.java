package net.kaupenjoe.tutorialmod.worldgen;

import com.mojang.serialization.MapCodec;
import net.kaupenjoe.tutorialmod.TutorialMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.levelgen.DensityFunction;

public class ModWorldgen {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, TutorialMod.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends DensityFunction>>
            DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(
                    Registries.DENSITY_FUNCTION_TYPE,
                    TutorialMod.MOD_ID
            );

    public static final DeferredHolder<
            MapCodec<? extends DensityFunction>,
            MapCodec<RadialIslandDensity>
            > RADIAL_ISLAND_DENSITY =
            DENSITY_FUNCTION_TYPES.register(
                    "radial_island",
                    () -> RadialIslandDensity.CODEC
            );

    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<RadialBiomeSource>> RADIAL_BIOME_SOURCE =
            BIOME_SOURCES.register("radial", () -> RadialBiomeSource.CODEC);

    public static void register(IEventBus eventBus) {
        BIOME_SOURCES.register(eventBus);
        DENSITY_FUNCTION_TYPES.register(eventBus);
    }
}