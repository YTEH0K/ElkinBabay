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
import net.kaupenjoe.tutorialmod.entity.custom.AggressiveRiftEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.kaupenjoe.tutorialmod.distortion.DistortionManager;
import net.kaupenjoe.tutorialmod.rift.RiftProtection;
import net.neoforged.fml.ModList;
import net.kaupenjoe.tutorialmod.compat.aeroportals.AeroPortalsRiftIntegration;

import java.util.HashSet;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RiftManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record AggressiveRiftTeleportResult(
            int playersInRange,
            int protectedPlayers,
            int teleportedPlayers,
            int failedTeleports
    ) {
        public boolean isResolved() {
            return playersInRange > 0 && failedTeleports == 0;
        }
    }

    public static BlockPos prepareAggressiveDestination(
            ServerLevel level,
            int worldRadius
    ) {
        BlockPos destination = findRandomAggressiveDestination(
                level,
                worldRadius
        );

        if (destination == null) {
            return null;
        }

        int chunkX = destination.getX() >> 4;
        int chunkZ = destination.getZ() >> 4;

        // Загружаем область 3×3 вокруг будущего места появления.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(
                        chunkX + dx,
                        chunkZ + dz
                );
            }
        }

        return destination;
    }

    public static final ResourceKey<Level> PRISON_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "prison")
    );

    private static final long SPAWN_INTERVAL_TICKS = 20L * 20L;
    private static final int SPAWN_CLUSTERS = 4;
    private static final int SPAWN_ATTEMPTS_PER_CLUSTER = 24;
    private static final int PLAYER_MIN_DISTANCE = 24;
    private static final int PLAYER_MAX_DISTANCE = 72;
    private static final int PRISON_MIN_DISTANCE = 560;
    private static final int NEARBY_RIFT_RADIUS = 16;
    private static final long DIAGNOSTIC_LOG_INTERVAL_TICKS = 20L * 10L;
    private static final long AGGRESSIVE_RIFT_INTERVAL_TICKS = 20L * 60L;

    private static final double AGGRESSIVE_RIFT_SPAWN_RADIUS = 15.0D;
    private static final double AGGRESSIVE_RIFT_TELEPORT_RADIUS = 100.0D;

    private static final int AGGRESSIVE_RIFT_DESTINATION_RADIUS = 50_000;

    private static final Map<UUID, AggressiveRiftState> aggressiveRiftStates =
            new HashMap<>();

    private static final class AggressiveRiftState {
        private long nextSpawnTick;
        private UUID activeRiftId;

        private AggressiveRiftState(long nextSpawnTick) {
            this.nextSpawnTick = nextSpawnTick;
        }
    }

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
        int minDistance = PLAYER_MIN_DISTANCE;
        int maxDistance = PLAYER_MAX_DISTANCE;

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

            int centerX = (int) Math.floor(
                    player.getX() + Math.cos(angle) * distance
            );

            int centerZ = (int) Math.floor(
                    player.getZ() + Math.sin(angle) * distance
            );

            for (int attempt = 0;
                 attempt < SPAWN_ATTEMPTS_PER_CLUSTER;
                 attempt++) {

                int x = centerX
                        + level.getRandom().nextInt(33) - 16;

                int z = centerZ
                        + level.getRandom().nextInt(33) - 16;

                checkedPositions++;

                if (isPrison) {
                    long distanceFromIslandSquared =
                            (long) x * x + (long) z * z;

                    if (distanceFromIslandSquared
                            < (long) PRISON_MIN_DISTANCE * PRISON_MIN_DISTANCE) {
                        continue;
                    }
                }

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

    private boolean hasNearbyAggressiveRift(
            ServerLevel level,
            ServerPlayer player
    ) {
        AABB bounds = new AABB(
                player.getX() - AGGRESSIVE_RIFT_TELEPORT_RADIUS,
                player.getY() - AGGRESSIVE_RIFT_TELEPORT_RADIUS,
                player.getZ() - AGGRESSIVE_RIFT_TELEPORT_RADIUS,
                player.getX() + AGGRESSIVE_RIFT_TELEPORT_RADIUS,
                player.getY() + AGGRESSIVE_RIFT_TELEPORT_RADIUS,
                player.getZ() + AGGRESSIVE_RIFT_TELEPORT_RADIUS
        );

        double radiusSquared =
                AGGRESSIVE_RIFT_TELEPORT_RADIUS
                        * AGGRESSIVE_RIFT_TELEPORT_RADIUS;

        return !level.getEntitiesOfClass(
                AggressiveRiftEntity.class,
                bounds,
                rift -> rift.isAlive()
                        && rift.position().distanceToSqr(player.position())
                        <= radiusSquared
        ).isEmpty();
    }

    private static Vec3 findAggressiveRiftSpawnPosition(
            ServerLevel level,
            ServerPlayer player
    ) {
        for (int attempt = 0; attempt < 64; attempt++) {
            double offsetX =
                    level.getRandom().nextDouble() * 30.0D - 15.0D;

            double offsetY =
                    level.getRandom().nextDouble() * 8.0D - 4.0D;

            double offsetZ =
                    level.getRandom().nextDouble() * 30.0D - 15.0D;

            double distanceSquared =
                    offsetX * offsetX
                            + offsetY * offsetY
                            + offsetZ * offsetZ;

            if (distanceSquared >
                    AGGRESSIVE_RIFT_SPAWN_RADIUS * AGGRESSIVE_RIFT_SPAWN_RADIUS) {
                continue;
            }

            double x = player.getX() + offsetX;
            double y = player.getY() + offsetY;
            double z = player.getZ() + offsetZ;

            BlockPos pos = BlockPos.containing(x, y, z);

            if (pos.getY() <= level.getMinBuildHeight() + 1
                    || pos.getY() >= level.getMaxBuildHeight() - 3) {
                continue;
            }

            if (!level.getBlockState(pos).isAir()
                    || !level.getBlockState(pos.above()).isAir()
                    || !level.getFluidState(pos).isEmpty()) {
                continue;
            }

            return new Vec3(x, y, z);
        }

        return player.position().add(0.0D, 1.0D, 0.0D);
    }

    public static AggressiveRiftTeleportResult teleportPlayersFromAggressiveRift(
            ServerLevel level,
            Vec3 riftPosition,
            double radius,
            int worldRadius,
            UUID resolvingRiftId,
            BlockPos preparedDestination
    ) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return new AggressiveRiftTeleportResult(0, 0, 0, 0);
        }

        List<ServerPlayer> playersInRadius = new ArrayList<>();
        List<ServerPlayer> teleportTargets = new ArrayList<>();

        double radiusSquared = radius * radius;

        // Используем все серверные подключения, чтобы не пропустить
        // пассажиров SubLevel.
        for (ServerPlayer player : level.players()) {
            Vec3 playerPosition = player.position();

            if (ModList.get().isLoaded("aeroportals")) {
                playerPosition =
                        AeroPortalsRiftIntegration.getEntityWorldPosition(player);
            }

            if (playerPosition.distanceToSqr(riftPosition) > radiusSquared) {
                continue;
            }

            playersInRadius.add(player);

            // Пассажиры будут перенесены вместе с кораблём.
            if (ModList.get().isLoaded("aeroportals")
                    && AeroPortalsRiftIntegration.isEntityOnAirship(player)) {
                continue;
            }

            teleportTargets.add(player);
        }

        if (playersInRadius.isEmpty()) {
            return new AggressiveRiftTeleportResult(0, 0, 0, 0);
        }

        if (teleportTargets.isEmpty()) {
            return new AggressiveRiftTeleportResult(
                    playersInRadius.size(),
                    0,
                    0,
                    0
            );
        }

        BlockPos destination = preparedDestination;

        if (destination == null) {
            destination = prepareAggressiveDestination(
                    level,
                    worldRadius
            );
        }

        if (destination == null) {
            LOGGER.error(
                    "Aggressive rift could not find a safe destination within {} blocks.",
                    worldRadius
            );

            return new AggressiveRiftTeleportResult(
                    playersInRadius.size(),
                    0,
                    0,
                    teleportTargets.size()
            );
        }

        int teleportedPlayers = 0;
        int failedTeleports = 0;

        for (ServerPlayer player : teleportTargets) {
            boolean teleported = player.teleportTo(
                    level,
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D,
                    Set.of(),
                    player.getYRot(),
                    player.getXRot()
            );

            if (!teleported) {
                failedTeleports++;
                continue;
            }

            player.setDeltaMovement(Vec3.ZERO);

            player.addEffect(
                    new MobEffectInstance(
                            MobEffects.BLINDNESS,
                            40
                    )
            );

            DistortionManager.addDistortion(player, 100);

            teleportedPlayers++;
        }

        // Если хотя бы один игрок успешно телепортирован,
        // сбрасываем таймер всем, кто находился в радиусе до телепортации.
        if (teleportedPlayers > 0) {
            for (ServerPlayer player : playersInRadius) {
                resetAggressiveRiftTimerForPlayer(
                        level,
                        player,
                        resolvingRiftId
                );
            }
        }

        LOGGER.info(
                "Aggressive rift result: teleported={}, failed={}, destination={}",
                teleportedPlayers,
                failedTeleports,
                destination
        );

        return new AggressiveRiftTeleportResult(
                playersInRadius.size(),
                0,
                teleportedPlayers,
                failedTeleports
        );
    }

//    private static void resetAggressiveRiftTimer(
//            ServerLevel level,
//            ServerPlayer player,
//            UUID resolvingRiftId
//    ) {
//        AggressiveRiftState state = aggressiveRiftStates.computeIfAbsent(
//                player.getUUID(),
//                uuid -> new AggressiveRiftState(
//                        level.getGameTime() + AGGRESSIVE_RIFT_INTERVAL_TICKS
//                )
//        );
//
//        if (state.activeRiftId != null) {
//            // Не удаляем разлом, который сейчас выполняет телепортацию.
//            // Он сам завершит работу после получения результата.
//            if (state.activeRiftId.equals(resolvingRiftId)) {
//                state.activeRiftId = null;
//            } else {
//                discardActiveAggressiveRift(level, state);
//            }
//        }
//
//        state.nextSpawnTick =
//                level.getGameTime() + AGGRESSIVE_RIFT_INTERVAL_TICKS;
//    }

    private static void discardActiveAggressiveRift(
            ServerLevel level,
            AggressiveRiftState state
    ) {
        if (state.activeRiftId == null) {
            return;
        }

        Entity activeRift = level.getEntity(state.activeRiftId);

        if (activeRift instanceof AggressiveRiftEntity) {
            activeRift.discard();
        }

        state.activeRiftId = null;
    }

    private static BlockPos findRandomAggressiveDestination(
            ServerLevel level,
            int radius
    ) {
        BlockPos spawn = level.getSharedSpawnPos();

        for (int attempt = 0; attempt < 16; attempt++) {
            double angle =
                    level.getRandom().nextDouble() * Math.PI * 2.0D;

            double distance =
                    Math.sqrt(level.getRandom().nextDouble()) * radius;

            int x = spawn.getX()
                    + (int) Math.floor(Math.cos(angle) * distance);

            int z = spawn.getZ()
                    + (int) Math.floor(Math.sin(angle) * distance);

            BlockPos column = new BlockPos(
                    x,
                    level.getMinBuildHeight(),
                    z
            );

            if (!level.getWorldBorder().isWithinBounds(column)) {
                continue;
            }

            // Загружаем или генерируем чанк перед поиском поверхности.
            level.getChunk(x >> 4, z >> 4);

            BlockPos pos = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING,
                    column
            );

            if (isSuitableAggressiveDestination(level, pos)) {
                return pos;
            }
        }

        return null;
    }

    private static boolean isSuitableAggressiveDestination(
            ServerLevel level,
            BlockPos pos
    ) {
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
                || level.getFluidState(floorPos).is(Fluids.WATER);
    }

    @SubscribeEvent
    public void onAggressiveRiftTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        List<ServerPlayer> players = level.players();
        long gameTime = level.getGameTime();

        Set<UUID> currentPlayerIds = new HashSet<>();

        for (ServerPlayer player : players) {
            currentPlayerIds.add(player.getUUID());
        }

        // Удаляем таймеры игроков, покинувших Верхний мир или отключившихся.
        for (UUID playerId : new ArrayList<>(aggressiveRiftStates.keySet())) {
            if (!currentPlayerIds.contains(playerId)) {
                AggressiveRiftState state = aggressiveRiftStates.remove(playerId);
                discardActiveAggressiveRift(level, state);
            }
        }

        for (ServerPlayer player : players) {
            UUID playerId = player.getUUID();

            AggressiveRiftState state = aggressiveRiftStates.get(playerId);

            // Игрок только вошёл в Верхний мир — запускаем его личный таймер.
            if (state == null) {
                aggressiveRiftStates.put(
                        playerId,
                        new AggressiveRiftState(
                                gameTime + AGGRESSIVE_RIFT_INTERVAL_TICKS
                        )
                );

                continue;
            }

            // Таймер ещё не закончился.
            if (gameTime < state.nextSpawnTick) {
                continue;
            }

            // Проверяем, не существует ли ещё разлома этого игрока.
            if (state.activeRiftId != null) {
                Entity activeRift = level.getEntity(state.activeRiftId);

                if (activeRift instanceof AggressiveRiftEntity
                        && activeRift.isAlive()) {
                    continue;
                }

                state.activeRiftId = null;
            }

            // Если игрок уже находится возле другого разлома,
            // собственный разлом ему не создаём.
            if (hasNearbyAggressiveRift(level, player)) {
                continue;
            }

            Vec3 spawnPosition =
                    findAggressiveRiftSpawnPosition(level, player);

            AggressiveRiftEntity rift =
                    ModEntities.AGGRESSIVE_RIFT.get().create(level);

            if (rift == null) {
                continue;
            }

            rift.setPos(
                    spawnPosition.x,
                    spawnPosition.y,
                    spawnPosition.z
            );

            if (level.addFreshEntity(rift)) {
                state.activeRiftId = rift.getUUID();

                // Защита от повторного появления разлома,
                // если предыдущая попытка телепортации завершилась неудачно.
                state.nextSpawnTick =
                        gameTime + AGGRESSIVE_RIFT_INTERVAL_TICKS;

                LOGGER.info(
                        "Aggressive rift spawned near player {} at {}",
                        player.getName().getString(),
                        spawnPosition
                );
            }
        }
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
                    50000,
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

    public static boolean teleportToPrison(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            return false;
        }

        // Если игрок уже возродился в тюрьме, ничего делать не нужно.
        if (sourceLevel.dimension().equals(PRISON_DIMENSION)) {
            return true;
        }

        ServerLevel prisonLevel =
                sourceLevel.getServer().getLevel(PRISON_DIMENSION);

        if (prisonLevel == null) {
            LOGGER.error("Prison dimension is not available.");
            return false;
        }

        BlockPos destination = findRandomSafePosition(
                prisonLevel,
                0,
                0,
                220,
                300
        );

        if (destination == null) {
            LOGGER.error("Could not find a safe respawn position in prison.");
            return false;
        }

        boolean teleported = player.teleportTo(
                prisonLevel,
                destination.getX() + 0.5,
                destination.getY(),
                destination.getZ() + 0.5,
                Set.of(),
                player.getYRot(),
                player.getXRot()
        );

        if (!teleported) {
            LOGGER.error("Failed to teleport player to prison after death.");
            return false;
        }

        player.setDeltaMovement(Vec3.ZERO);

        player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.tutorialmod.rift.to_prison"
                )
        );

        return true;
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
    public static void resetAggressiveRiftTimerForPlayer(
            ServerLevel level,
            ServerPlayer player,
            UUID resolvingRiftId
    ) {
        AggressiveRiftState state = aggressiveRiftStates.computeIfAbsent(
                player.getUUID(),
                uuid -> new AggressiveRiftState(
                        level.getGameTime() + AGGRESSIVE_RIFT_INTERVAL_TICKS
                )
        );

        // Убираем ссылку только на тот портал, который сейчас завершается.
        // Другой активный портал этого игрока не трогаем.
        if (resolvingRiftId != null
                && resolvingRiftId.equals(state.activeRiftId)) {
            state.activeRiftId = null;
        }

        state.nextSpawnTick =
                level.getGameTime() + AGGRESSIVE_RIFT_INTERVAL_TICKS;
    }
    public static void resetAggressiveRiftTimersInRadius(
            ServerLevel level,
            Vec3 center,
            double radius,
            UUID resolvingRiftId
    ) {
        double radiusSquared = radius * radius;

        for (ServerPlayer player : level.players()) {
            Vec3 playerPosition = player.position();

            if (ModList.get().isLoaded("aeroportals")) {
                playerPosition =
                        AeroPortalsRiftIntegration.getEntityWorldPosition(player);
            }

            if (playerPosition.distanceToSqr(center) > radiusSquared) {
                continue;
            }

            resetAggressiveRiftTimerForPlayer(
                    level,
                    player,
                    resolvingRiftId
            );
        }
    }

    public static boolean cancelNearestAggressiveRift(
            ServerPlayer player
    ) {
        ServerLevel level = player.serverLevel();

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }

        Vec3 playerPosition = player.position();

        if (ModList.get().isLoaded("aeroportals")) {
            playerPosition =
                    AeroPortalsRiftIntegration.getEntityWorldPosition(player);
        }

        double radius = AggressiveRiftEntity.EFFECT_RADIUS;
        double radiusSquared = radius * radius;

        AABB searchBounds = new AABB(
                playerPosition.x - radius,
                playerPosition.y - radius,
                playerPosition.z - radius,
                playerPosition.x + radius,
                playerPosition.y + radius,
                playerPosition.z + radius
        );

        AggressiveRiftEntity nearestRift = null;
        double nearestDistance = Double.MAX_VALUE;

        for (AggressiveRiftEntity rift : level.getEntitiesOfClass(
                AggressiveRiftEntity.class,
                searchBounds
        )) {
            if (!rift.isAlive()) {
                continue;
            }

            double distance = rift.position()
                    .distanceToSqr(playerPosition);

            if (distance > radiusSquared || distance >= nearestDistance) {
                continue;
            }

            nearestRift = rift;
            nearestDistance = distance;
        }

        if (nearestRift == null) {
            return false;
        }

        // Сначала сбрасываем таймеры всем рядом находящимся игрокам.
        resetAggressiveRiftTimersInRadius(
                level,
                nearestRift.position(),
                radius,
                nearestRift.getUUID()
        );

        LOGGER.info(
                "Aggressive rift {} was cancelled by player {}.",
                nearestRift.getUUID(),
                player.getName().getString()
        );

        // discard() вызовет remove(), который уберёт маркеры AeroPortals.
        nearestRift.discard();

        return true;
    }
}
