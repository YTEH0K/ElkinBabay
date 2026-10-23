package net.kaupenjoe.tutorialmod.rift;

import com.mojang.logging.LogUtils;
import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.entity.ModEntities;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

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
    private static final int SPAWN_CLUSTERS = 128;
    private static final int SPAWN_ATTEMPTS_PER_CLUSTER = 24;
    private static final int OVERWORLD_MIN_DISTANCE = 24;
    private static final int OVERWORLD_MAX_DISTANCE = 72;
    private static final int PRISON_MIN_DISTANCE = 560;
    private static final int PRISON_MAX_DISTANCE = 70000;
    private static final int NEARBY_RIFT_RADIUS = 16;

    private static final long DIAGNOSTIC_LOG_INTERVAL_TICKS = 20L * 10L;

    private final Map<ResourceKey<Level>, Long> nextSpawnTick = new HashMap<>();

    private final Map<ResourceKey<Level>, Long> nextDiagnosticLogTick =
            new HashMap<>();

    private final Map<ResourceKey<Level>, Long> nextExceptionLogTick =
            new HashMap<>();

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

        nextSpawnTick.put(
                dimension,
                gameTime + SPAWN_INTERVAL_TICKS
        );

        ServerPlayer player =
                players.get(level.getRandom().nextInt(players.size()));

        try {
            trySpawnRift(level, player, isPrison);
        } catch (RuntimeException exception) {
            if (shouldLog(nextExceptionLogTick, level)) {
                LOGGER.error(
                        "Unexpected exception while spawning a rift in dimension "
                                + dimension.location()
                                + " near player at "
                                + player.blockPosition(),
                        exception
                );
            }
        }
    }

    private boolean trySpawnRift(
            ServerLevel level,
            ServerPlayer player,
            boolean isPrison
    ) {
        int minDistance = isPrison
                ? PRISON_MIN_DISTANCE
                : OVERWORLD_MIN_DISTANCE;

        int maxDistance = isPrison
                ? PRISON_MAX_DISTANCE
                : OVERWORLD_MAX_DISTANCE;

        int checkedPositions = 0;
        int noSurface = 0;
        int wrongBiome = 0;
        int unsuitableSurface = 0;
        int nearbyRift = 0;
        int noDestination = 0;
        int entityTypeUnavailable = 0;
        int addEntityRejected = 0;

        ResourceKey<Level> destinationDimension =
                isPrison ? Level.OVERWORLD : PRISON_DIMENSION;

        for (int cluster = 0; cluster < SPAWN_CLUSTERS; cluster++) {
            double angle =
                    level.getRandom().nextDouble() * Math.PI * 2.0;

            int distance = minDistance
                    + level.getRandom().nextInt(
                    maxDistance - minDistance + 1
            );

            int centerX;
            int centerZ;

            if (isPrison) {
                centerX = (int) Math.floor(Math.cos(angle) * distance);
                centerZ = (int) Math.floor(Math.sin(angle) * distance);
            } else {
                centerX = (int) Math.floor(
                        player.getX() + Math.cos(angle) * distance
                );
                centerZ = (int) Math.floor(
                        player.getZ() + Math.sin(angle) * distance
                );
            }

            for (int attempt = 0;
                 attempt < SPAWN_ATTEMPTS_PER_CLUSTER;
                 attempt++) {

                int x = centerX
                        + level.getRandom().nextInt(33) - 16;

                int z = centerZ
                        + level.getRandom().nextInt(33) - 16;

                checkedPositions++;

                BlockPos pos = getSurfacePosition(level, x, z);

                if (pos == null) {
                    noSurface++;
                    continue;
                }

                if (isPrison && !isFrozenOcean(level, pos)) {
                    wrongBiome++;
                    continue;
                }

                if (!isSuitableRiftSurface(level, pos)) {
                    unsuitableSurface++;
                    continue;
                }

                if (hasNearbyRift(level, pos)) {
                    nearbyRift++;
                    continue;
                }

                ServerLevel targetLevel =
                        level.getServer().getLevel(destinationDimension);

                if (targetLevel == null) {
                    noDestination++;

                    logSpawnFailure(
                            level,
                            player,
                            "Target dimension is not available: "
                                    + destinationDimension.location(),
                            checkedPositions,
                            noSurface,
                            wrongBiome,
                            unsuitableSurface,
                            nearbyRift,
                            noDestination,
                            entityTypeUnavailable,
                            addEntityRejected
                    );

                    return false;
                }

                BlockPos destination =
                        findDestination(level, isPrison);

                if (destination == null) {
                    noDestination++;

                    logSpawnFailure(
                            level,
                            player,
                            "Could not find a safe destination in "
                                    + destinationDimension.location()
                                    + "; source candidate=" + pos,
                            checkedPositions,
                            noSurface,
                            wrongBiome,
                            unsuitableSurface,
                            nearbyRift,
                            noDestination,
                            entityTypeUnavailable,
                            addEntityRejected
                    );

                    return false;
                }

                RiftEntity rift = ModEntities.RIFT.get().create(level);

                if (rift == null) {
                    entityTypeUnavailable++;
                    continue;
                }

                rift.setDestination(destinationDimension, destination);

                rift.setPos(
                        pos.getX() + 0.5D,
                        pos.getY(),
                        pos.getZ() + 0.5D
                );

                if (level.addFreshEntity(rift)) {
                    rift.ensurePortalMarkers(level);
                    return true;
                }

                addEntityRejected++;
            }
        }

        logSpawnFailure(
                level,
                player,
                "Exhausted all spawn attempts without creating a rift",
                checkedPositions,
                noSurface,
                wrongBiome,
                unsuitableSurface,
                nearbyRift,
                noDestination,
                entityTypeUnavailable,
                addEntityRejected
        );

        return false;
    }

    private boolean shouldLog(
            Map<ResourceKey<Level>, Long> cooldowns,
            ServerLevel level
    ) {
        ResourceKey<Level> dimension = level.dimension();
        long gameTime = level.getGameTime();

        if (gameTime < cooldowns.getOrDefault(dimension, 0L)) {
            return false;
        }

        cooldowns.put(
                dimension,
                gameTime + DIAGNOSTIC_LOG_INTERVAL_TICKS
        );

        return true;
    }

    private void logSpawnFailure(
            ServerLevel level,
            ServerPlayer player,
            String reason,
            int checkedPositions,
            int noSurface,
            int wrongBiome,
            int unsuitableSurface,
            int nearbyRift,
            int noDestination,
            int entityTypeUnavailable,
            int addEntityRejected
    ) {
        if (!shouldLog(nextDiagnosticLogTick, level)) {
            return;
        }

        LOGGER.warn(
                "Rift spawn failed: "
                        + "dimension={}, playerPos={}, reason={}, "
                        + "checkedPositions={}, noSurface={}, wrongBiome={}, "
                        + "unsuitableSurface={}, nearbyRift={}, "
                        + "noDestination={}, entityTypeUnavailable={}, "
                        + "addFreshEntityRejected={}",
                level.dimension().location(),
                player.blockPosition(),
                reason,
                checkedPositions,
                noSurface,
                wrongBiome,
                unsuitableSurface,
                nearbyRift,
                noDestination,
                entityTypeUnavailable,
                addEntityRejected
        );
    }

    private BlockPos findDestination(ServerLevel sourceLevel, boolean isPrison) {
        ResourceKey<Level> targetDimension = isPrison ? Level.OVERWORLD : PRISON_DIMENSION;
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetDimension);
        if (targetLevel == null) {
            LOGGER.error("Rift target dimension {} is not available.", targetDimension.location());
            return null;
        }

        if (isPrison) {
            BlockPos overworldSpawn = targetLevel.getSharedSpawnPos();
            return findRandomSafePosition(
                    targetLevel,
                    overworldSpawn.getX(),
                    overworldSpawn.getZ(),
                    10000000,
                    300
            );
        }

        return findRandomSafePosition(targetLevel, 0, 0, 220, 300);
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
        AABB bounds = new AABB(center).inflate(NEARBY_RIFT_RADIUS);
        return !level.getEntitiesOfClass(RiftEntity.class, bounds).isEmpty();
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
                    targetLevel.dimension().equals(PRISON_DIMENSION) ? 220 : 10000000
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
