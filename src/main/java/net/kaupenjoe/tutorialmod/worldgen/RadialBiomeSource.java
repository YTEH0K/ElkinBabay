package net.kaupenjoe.tutorialmod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.stream.Stream;

public class RadialBiomeSource extends BiomeSource {

    public static final MapCodec<RadialBiomeSource> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                            RegistryOps.retrieveGetter(Registries.BIOME)
                    ).apply(instance, RadialBiomeSource::new)
            );

    private final HolderGetter<Biome> biomes;

    public RadialBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(
                biomes.getOrThrow(Biomes.PLAINS),
                biomes.getOrThrow(Biomes.OCEAN),
                biomes.getOrThrow(Biomes.FROZEN_OCEAN)
        );
    }

    @Override
    public Holder<Biome> getNoiseBiome(
            int x,
            int y,
            int z,
            Climate.Sampler sampler
    ) {
        double blockX = x * 4.0;
        double blockZ = z * 4.0;

        double distance = Math.sqrt(
                blockX * blockX + blockZ * blockZ
        );


        if (distance <= 150.0) {
            return biomes.getOrThrow(Biomes.PLAINS);
        }

        if (distance <= 650.0) {
            return biomes.getOrThrow(Biomes.OCEAN);
        }

        return biomes.getOrThrow(Biomes.FROZEN_OCEAN);

    }
}