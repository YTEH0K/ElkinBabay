
package net.kaupenjoe.tutorialmod.rift;

import net.kaupenjoe.tutorialmod.compat.aeroportals.AeroPortalsRiftIntegration;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RiftLifetimeSavedData extends SavedData {

    private static final String DATA_NAME = "tutorialmod_rift_lifetimes";

    private static final int CHECK_INTERVAL_TICKS = 20 * 10;
    private static final int DESPAWN_AFTER_TICKS = 20 * 60 * 3;

    private static final double PLAYER_RADIUS = 200.0D;

    private static final SavedData.Factory<RiftLifetimeSavedData> FACTORY =
            new SavedData.Factory<>(
                    RiftLifetimeSavedData::new,
                    RiftLifetimeSavedData::load,
                    null
            );

    private final Map<UUID, RiftRecord> records = new HashMap<>();

    private static final class RiftRecord {
        private final int x;
        private final int y;
        private final int z;

        private long lastCheckGameTime;
        private int unoccupiedTicks;
        private boolean expired;

        private RiftRecord(
                int x,
                int y,
                int z,
                long lastCheckGameTime,
                int unoccupiedTicks,
                boolean expired
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastCheckGameTime = lastCheckGameTime;
            this.unoccupiedTicks = unoccupiedTicks;
            this.expired = expired;
        }
    }

    private RiftLifetimeSavedData() {
    }

    public static RiftLifetimeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                FACTORY,
                DATA_NAME
        );
    }

    public static RiftLifetimeSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        RiftLifetimeSavedData data = new RiftLifetimeSavedData();

        ListTag list = tag.getList("Rifts", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag riftTag = list.getCompound(i);

            if (!riftTag.hasUUID("UUID")) {
                continue;
            }

            UUID uuid = riftTag.getUUID("UUID");

            RiftRecord record = new RiftRecord(
                    riftTag.getInt("X"),
                    riftTag.getInt("Y"),
                    riftTag.getInt("Z"),
                    riftTag.getLong("LastCheckGameTime"),
                    Math.max(
                            0,
                            Math.min(
                                    DESPAWN_AFTER_TICKS,
                                    riftTag.getInt("UnoccupiedTicks")
                            )
                    ),
                    riftTag.getBoolean("Expired")
            );

            data.records.put(uuid, record);
        }

        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        ListTag list = new ListTag();

        for (Map.Entry<UUID, RiftRecord> entry : records.entrySet()) {
            RiftRecord record = entry.getValue();

            CompoundTag riftTag = new CompoundTag();

            riftTag.putUUID("UUID", entry.getKey());
            riftTag.putInt("X", record.x);
            riftTag.putInt("Y", record.y);
            riftTag.putInt("Z", record.z);

            riftTag.putLong(
                    "LastCheckGameTime",
                    record.lastCheckGameTime
            );

            riftTag.putInt(
                    "UnoccupiedTicks",
                    record.unoccupiedTicks
            );

            riftTag.putBoolean("Expired", record.expired);

            list.add(riftTag);
        }

        tag.put("Rifts", list);

        return tag;
    }

    public void registerIfMissing(
            UUID uuid,
            net.minecraft.core.BlockPos pos,
            long gameTime
    ) {
        if (records.containsKey(uuid)) {
            return;
        }

        records.put(
                uuid,
                new RiftRecord(
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        gameTime,
                        0,
                        false
                )
        );

        setDirty();
    }

    public int getActiveRiftCount() {
        int count = 0;

        for (RiftRecord record : records.values()) {
            if (!record.expired) {
                count++;
            }
        }

        return count;
    }

    public boolean isExpired(UUID uuid) {
        RiftRecord record = records.get(uuid);

        return record != null && record.expired;
    }

    public void resetIdleTimer(
            UUID uuid,
            net.minecraft.core.BlockPos pos,
            long gameTime
    ) {
        registerIfMissing(uuid, pos, gameTime);

        RiftRecord record = records.get(uuid);

        if (record == null || record.expired) {
            return;
        }

        record.lastCheckGameTime = gameTime;
        record.unoccupiedTicks = 0;

        setDirty();
    }

    public void remove(UUID uuid) {
        if (records.remove(uuid) != null) {
            setDirty();
        }
    }

    public void tick(ServerLevel level) {
        if (records.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        boolean changed = false;

        // Используем копию, поскольку discard() может удалить запись
        // из исходной Map через RiftEntity.remove().
        for (Map.Entry<UUID, RiftRecord> entry :
                new ArrayList<>(records.entrySet())) {

            UUID uuid = entry.getKey();
            RiftRecord record = entry.getValue();

            if (record.expired) {
                continue;
            }

            long elapsed = gameTime - record.lastCheckGameTime;

            if (elapsed < CHECK_INTERVAL_TICKS) {
                continue;
            }

            record.lastCheckGameTime = gameTime;

            Vec3 riftPosition = new Vec3(
                    record.x + 0.5D,
                    record.y + 0.5D,
                    record.z + 0.5D
            );

            if (hasNearbyPlayer(level, riftPosition)) {
                record.unoccupiedTicks = 0;
            } else {
                record.unoccupiedTicks = (int) Math.min(
                        DESPAWN_AFTER_TICKS,
                        (long) record.unoccupiedTicks + elapsed
                );
            }

            changed = true;

            if (record.unoccupiedTicks >= DESPAWN_AFTER_TICKS) {
                record.expired = true;

                // getEntity(UUID) ищет уже загруженную сущность.
                // Этот вызов не загружает чанк.
                Entity entity = level.getEntity(uuid);

                if (entity instanceof RiftEntity rift && rift.isAlive()) {
                    rift.discard();
                }
            }
        }

        if (changed) {
            setDirty();
        }
    }

    private boolean hasNearbyPlayer(
            ServerLevel level,
            Vec3 riftPosition
    ) {
        double radiusSquared = PLAYER_RADIUS * PLAYER_RADIUS;

        for (ServerPlayer player : level.players()) {
            Vec3 playerPosition = player.position();

            if (ModList.get().isLoaded("aeroportals")) {
                playerPosition =
                        AeroPortalsRiftIntegration.getEntityWorldPosition(player);
            }

            if (playerPosition.distanceToSqr(riftPosition) <= radiusSquared) {
                return true;
            }
        }

        return false;
    }
}
