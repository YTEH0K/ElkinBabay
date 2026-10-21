package net.kaupenjoe.tutorialmod.rift;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RiftManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> PRISON_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "prison")
    );

    private static final long SPAWN_INTERVAL_TICKS = 2L * 2L;
    private static final int RIFT_LIFETIME_TICKS = 20 * 180;
    private static final int SPAWN_CLUSTERS = 128;
    private static final int SPAWN_ATTEMPTS_PER_CLUSTER = 24;
    private static final int OVERWORLD_MIN_DISTANCE = 24;
    private static final int OVERWORLD_MAX_DISTANCE = 72;
    private static final int PRISON_MIN_DISTANCE = 560;
    private static final int PRISON_MAX_DISTANCE = 70000;
    private static final int NEARBY_RIFT_RADIUS = 16;

    private final Map<ResourceKey<Level>, Long> nextSpawnTick = new HashMap<>();

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        boolean isPrison = dimension.equals(PRISON_DIMENSION);
        if (!isPrison && !dimension.equals(Level.OVERWORLD)) {
            return;
        }

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime < nextSpawnTick.getOrDefault(dimension, 0L)) {
            return;
        }
        nextSpawnTick.put(dimension, gameTime + SPAWN_INTERVAL_TICKS);

        ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
        trySpawnRift(level, player, isPrison);
    }

    private boolean trySpawnRift(ServerLevel level, ServerPlayer player, boolean isPrison) {
        int minDistance = isPrison ? PRISON_MIN_DISTANCE : OVERWORLD_MIN_DISTANCE;
        int maxDistance = isPrison ? PRISON_MAX_DISTANCE : OVERWORLD_MAX_DISTANCE;

        for (int cluster = 0; cluster < SPAWN_CLUSTERS; cluster++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = minDistance + level.getRandom().nextInt(maxDistance - minDistance + 1);
            int centerX;
            int centerZ;

            if (isPrison) {
                centerX = (int) Math.floor(Math.cos(angle) * distance);
                centerZ = (int) Math.floor(Math.sin(angle) * distance);
            } else {
                centerX = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
                centerZ = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            }

            for (int attempt = 0; attempt < SPAWN_ATTEMPTS_PER_CLUSTER; attempt++) {
                int x = centerX + level.getRandom().nextInt(33) - 16;
                int z = centerZ + level.getRandom().nextInt(33) - 16;
                BlockPos pos = getSurfacePosition(level, x, z);
                if (pos == null) {
                    continue;
                }

                if (isPrison && !isFrozenOcean(level, pos)) {
                    continue;
                }

                if (!isSuitableRiftSurface(level, pos) || hasNearbyRift(level, pos)) {
                    continue;
                }

                level.setBlock(pos, ModBlocks.RIFT.get().defaultBlockState(), 3);
                level.scheduleTick(pos, ModBlocks.RIFT.get(), RIFT_LIFETIME_TICKS);
                level.scheduleTick(pos, ModBlocks.RIFT.get(), RIFT_LIFETIME_TICKS);
                return true;
            }
        }

        return false;
    }

    private static BlockPos getSurfacePosition(ServerLevel level, int x, int z) {
        BlockPos heightmapPos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, level.getMinBuildHeight(), z)
        );

        if (heightmapPos.getY() <= level.getMinBuildHeight() + 1
                || heightmapPos.getY() >= level.getMaxBuildHeight() - 2) {
            return null;
        }
        return heightmapPos;
    }

    private static boolean isSuitableRiftSurface(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.above()).isAir()
                || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        BlockPos below = pos.below();
        BlockState support = level.getBlockState(below);
        return support.isFaceSturdy(level, below, Direction.UP)
                || !level.getFluidState(below).isEmpty();
    }

    private static boolean isFrozenOcean(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN);
    }

    private static boolean hasNearbyRift(ServerLevel level, BlockPos center) {
        for (int x = -NEARBY_RIFT_RADIUS; x <= NEARBY_RIFT_RADIUS; x += 4) {
            for (int y = -4; y <= 4; y += 4) {
                for (int z = -NEARBY_RIFT_RADIUS; z <= NEARBY_RIFT_RADIUS; z += 4) {
                    if (level.getBlockState(center.offset(x, y, z)).is(ModBlocks.RIFT.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean teleportPlayer(ServerPlayer player, ServerLevel sourceLevel) {
        ServerLevel targetLevel;
        BlockPos destination;
        String messageKey;

        if (sourceLevel.dimension().equals(PRISON_DIMENSION)) {
            targetLevel = sourceLevel.getServer().getLevel(Level.OVERWORLD);
            if (targetLevel == null) {
                LOGGER.error("Rift target dimension {} is not available.", Level.OVERWORLD.location());
                return false;
            }

            BlockPos overworldSpawn = targetLevel.getSharedSpawnPos();
            destination = findRandomSafePosition(
                    targetLevel,
                    overworldSpawn.getX(),
                    overworldSpawn.getZ(),
                    10000000,
                    300
            );
            messageKey = "message.tutorialmod.rift.to_overworld";
        } else if (sourceLevel.dimension().equals(Level.OVERWORLD)) {
            targetLevel = sourceLevel.getServer().getLevel(PRISON_DIMENSION);
            if (targetLevel == null) {
                LOGGER.error("Rift target dimension {} is not available.", PRISON_DIMENSION.location());
                return false;
            }

            destination = findRandomSafePosition(targetLevel, 0, 0, 220, 300);
            messageKey = "message.tutorialmod.rift.to_prison";
        } else {
            return false;
        }

        if (destination == null) {
            LOGGER.error(
                    "Could not find a safe destination in dimension {} (search center {}, {}, radius {}).",
                    targetLevel.dimension().location(),
                    targetLevel.dimension().equals(PRISON_DIMENSION) ? 0 : targetLevel.getSharedSpawnPos().getX(),
                    targetLevel.dimension().equals(PRISON_DIMENSION) ? 0 : targetLevel.getSharedSpawnPos().getZ(),
                    targetLevel.dimension().equals(PRISON_DIMENSION) ? 220 : 10_000
            );
            return false;
        }

        boolean teleported = player.teleportTo(
                targetLevel,
                destination.getX() + 0.5,
                destination.getY(),
                destination.getZ() + 0.5,
                Set.of(),
                player.getYRot(),
                player.getXRot()
        );

        if (!teleported) {
            LOGGER.error(
                    "Teleportation to dimension {} at {} was rejected by the server.",
                    targetLevel.dimension().location(),
                    destination
            );
            return false;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(messageKey));
        return true;
    }

    private static BlockPos findRandomSafePosition(
            ServerLevel level,
            int centerX,
            int centerZ,
            int radius,
            int attempts
    ) {
        // Search random areas first. Unlike the old version, this honours most of the
        // requested attempt count instead of silently capping the search at 96 tries.
        int clusters = Math.min(16, Math.max(1, (attempts + 15) / 16));
        int attemptsPerCluster = Math.max(16, (attempts + clusters - 1) / clusters);

        for (int cluster = 0; cluster < clusters; cluster++) {
            int searchCenterX = centerX + level.getRandom().nextInt(radius * 2 + 1) - radius;
            int searchCenterZ = centerZ + level.getRandom().nextInt(radius * 2 + 1) - radius;

            BlockPos found = searchSafeCluster(level, searchCenterX, searchCenterZ, attemptsPerCluster);
            if (found != null) {
                return found;
            }
        }

        // Fallback: look close to a reliable reference point, where chunks are more
        // likely to be generated already. This avoids failing just because the random
        // candidates landed in ocean or otherwise unsuitable terrain.
        BlockPos sharedSpawn = level.getSharedSpawnPos();
        int fallbackCenterX = level.dimension().equals(Level.OVERWORLD)
                ? sharedSpawn.getX()
                : centerX;
        int fallbackCenterZ = level.dimension().equals(Level.OVERWORLD)
                ? sharedSpawn.getZ()
                : centerZ;
        int fallbackRadius = Math.min(radius, level.dimension().equals(Level.OVERWORLD) ? 256 : 128);

        for (int cluster = 0; cluster < 12; cluster++) {
            int searchRadius = Math.min(fallbackRadius, 32 << Math.min(cluster / 3, 3));
            int searchCenterX = fallbackCenterX
                    + level.getRandom().nextInt(searchRadius * 2 + 1) - searchRadius;
            int searchCenterZ = fallbackCenterZ
                    + level.getRandom().nextInt(searchRadius * 2 + 1) - searchRadius;

            BlockPos found = searchSafeCluster(level, searchCenterX, searchCenterZ, 16);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private static BlockPos searchSafeCluster(
            ServerLevel level,
            int centerX,
            int centerZ,
            int attempts
    ) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = centerX + level.getRandom().nextInt(33) - 16;
            int z = centerZ + level.getRandom().nextInt(33) - 16;

            BlockPos horizontalPos = new BlockPos(x, level.getMinBuildHeight(), z);
            if (!level.getWorldBorder().isWithinBounds(horizontalPos)) {
                continue;
            }

            // Ensure the candidate's chunk is generated before querying its heightmap.
            level.getChunk(x >> 4, z >> 4);

            BlockPos pos = getSurfacePosition(level, x, z);
            if (pos == null || !isSafeDestination(level, pos)) {
                continue;
            }

            return pos;
        }

        return null;
    }

    private static boolean isSafeDestination(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() + 1
                || pos.getY() >= level.getMaxBuildHeight() - 2
                || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!feet.isAir()
                || !head.isAir()
                || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        BlockPos floorPos = pos.below();
        BlockState floor = level.getBlockState(floorPos);
        return floor.isFaceSturdy(level, floorPos, Direction.UP)
                && level.getFluidState(floorPos).isEmpty();
    }
}
