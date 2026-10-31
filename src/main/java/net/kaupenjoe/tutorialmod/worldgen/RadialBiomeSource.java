
package net.kaupenjoe.tutorialmod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public class RadialBiomeSource extends BiomeSource {

    private static final ResourceKey<Biome> FORGOTTEN_FOREST =
            createBiomeKey("biomeswevegone", "forgotten_forest");

    private static final ResourceKey<Biome> CRAG_GARDENS =
            createBiomeKey("biomeswevegone", "crag_gardens");

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

    private static ResourceKey<Biome> createBiomeKey(
            String modId,
            String biomeId
    ) {
        return ResourceKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(modId, biomeId)
        );
    }

    private Holder<Biome> getBiomeOrOcean(ResourceKey<Biome> biomeKey) {
        return biomes.get(biomeKey)
                .orElseGet(() -> biomes.getOrThrow(Biomes.OCEAN));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(
                getBiomeOrOcean(FORGOTTEN_FOREST),
                getBiomeOrOcean(CRAG_GARDENS),
                biomes.getOrThrow(Biomes.DEEP_FROZEN_OCEAN)
        ).distinct();
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

        if (distance <= 800) {
            return getBiomeOrOcean(FORGOTTEN_FOREST);
        }


        if (distance <= 1000) {
            return getBiomeOrOcean(CRAG_GARDENS);
        }

        return biomes.getOrThrow(Biomes.DEEP_FROZEN_OCEAN);
    }
}
