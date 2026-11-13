
package net.kaupenjoe.tutorialmod.entity.custom;

import net.kaupenjoe.tutorialmod.rift.RiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.kaupenjoe.tutorialmod.block.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.kaupenjoe.tutorialmod.compat.aeroportals.AeroPortalsRiftIntegration;
import java.util.Set;

public class RiftEntity extends Entity {

    private static final int INITIAL_LIFETIME_TICKS = 5 * 60 * 20;
    private static final int ENTRY_LIFETIME_TICKS = 10 * 20;

    private static final String REMAINING_LIFETIME_TAG = "RiftRemainingTicks";

    private ResourceKey<Level> destinationDimension;
    private BlockPos destinationPos;
    private int remainingLifetimeTicks = INITIAL_LIFETIME_TICKS;

    public RiftEntity(EntityType<? extends RiftEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setDestination(ResourceKey<Level> dimension, BlockPos pos) {
        this.destinationDimension = dimension;
        this.destinationPos = pos.immutable();
    }

    public ResourceKey<Level> getDestinationDimension() {
        return destinationDimension;
    }

    public BlockPos getDestinationPos() {
        return destinationPos;
    }

    public void onPortalEntry() {
        this.remainingLifetimeTicks = ENTRY_LIFETIME_TICKS;
    }

    public void ensurePortalMarkers(ServerLevel level) {
        if (!ModList.get().isLoaded("aeroportals")) {
            return;
        }
        BlockPos base = this.blockPosition();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos markerPos = base.offset(dx, dy, dz);

                    // Never replace terrain or other blocks.
                    if (level.getBlockState(markerPos).isAir()) {
                        level.setBlock(
                                markerPos,
                                ModBlocks.RIFT_MARKER.get()
                                        .defaultBlockState(),
                                3
                        );
                    }
                }
            }
        }
    }

    private void removePortalMarkers(ServerLevel level) {
        BlockPos base = this.blockPosition();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos markerPos = base.offset(dx, dy, dz);

                    if (level.getBlockState(markerPos)
                            .is(ModBlocks.RIFT_MARKER.get())) {

                        level.setBlock(
                                markerPos,
                                Blocks.AIR.defaultBlockState(),
                                3
                        );
                    }
                }
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ResourceLocation dimensionId =
                ResourceLocation.tryParse(tag.getString("DestinationDimension"));

        if (dimensionId != null) {
            this.destinationDimension =
                    ResourceKey.create(Registries.DIMENSION, dimensionId);
        }

        this.destinationPos = new BlockPos(
                tag.getInt("DestinationX"),
                tag.getInt("DestinationY"),
                tag.getInt("DestinationZ")
        );

        if (tag.contains(REMAINING_LIFETIME_TAG)) {
            this.remainingLifetimeTicks = Math.max(
                    1,
                    Math.min(
                            INITIAL_LIFETIME_TICKS,
                            tag.getInt(REMAINING_LIFETIME_TAG)
                    )
            );
        } else {
            this.remainingLifetimeTicks = INITIAL_LIFETIME_TICKS;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.destinationDimension != null) {
            tag.putString(
                    "DestinationDimension",
                    this.destinationDimension.location().toString()
            );
        }

        if (this.destinationPos != null) {
            tag.putInt("DestinationX", this.destinationPos.getX());
            tag.putInt("DestinationY", this.destinationPos.getY());
            tag.putInt("DestinationZ", this.destinationPos.getZ());
        }

        tag.putInt(REMAINING_LIFETIME_TAG, this.remainingLifetimeTicks);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            spawnAmbientParticles();
            return;
        }

        if (!(this.level() instanceof ServerLevel sourceLevel)) {
            return;
        }




        boolean entityEntered = false;

        // Try to teleport entities before checking whether the lifetime expired.
        if (this.destinationDimension != null && this.destinationPos != null) {
            ServerLevel targetLevel =
                    sourceLevel.getServer().getLevel(this.destinationDimension);

            if (targetLevel != null) {
                AABB portalBounds = this.getBoundingBox().inflate(0.05D);

                for (Entity entity : sourceLevel.getEntities(
                        this,
                        portalBounds,
                        candidate -> candidate.isAlive()
                                && !candidate.isSpectator()
                                && !(candidate instanceof RiftEntity)
                )) {
                    if (entity instanceof ServerPlayer
                            && ModList.get().isLoaded("aeroportals")
                            && AeroPortalsRiftIntegration.isEntityOnAirship(entity)) {
                        continue;
                    }
                    boolean teleported = entity.teleportTo(
                            targetLevel,
                            this.destinationPos.getX() + 0.5D,
                            this.destinationPos.getY(),
                            this.destinationPos.getZ() + 0.5D,
                            Set.of(),
                            entity.getYRot(),
                            entity.getXRot()
                    );

                    if (!teleported) {
                        continue;
                    }

                    entityEntered = true;
                    entity.setDeltaMovement(Vec3.ZERO);

                    if (entity instanceof ServerPlayer player) {
                        String messageKey =
                                this.destinationDimension.equals(
                                        RiftManager.PRISON_DIMENSION
                                )
                                        ? "message.tutorialmod.rift.to_prison"
                                        : "message.tutorialmod.rift.to_overworld";

                        player.sendSystemMessage(
                                net.minecraft.network.chat.Component
                                        .translatable(messageKey)
                        );
                    }
                }
            }
        }

        if (entityEntered) {
            // Any successful entry restarts the 10-second closing timer.
            this.remainingLifetimeTicks = ENTRY_LIFETIME_TICKS;
        } else {
            // No successful entry: continue the current timer.
            this.remainingLifetimeTicks--;

            if (this.remainingLifetimeTicks <= 0) {
                this.discard();
            }
        }
    }


    private void spawnAmbientParticles() {
        // Создаём частицы раз в два игровых тика.
        if (this.tickCount % 2 != 0) {
            return;
        }

        double centerY = this.getY() + this.getBbHeight() * 0.5D;

        // Радиус окружающего энергетического поля.
        double baseRadius =
                Math.max(this.getBbWidth(), this.getBbHeight()) * 0.72D;

        // Две частицы за один вызов, на противоположных сторонах.
        for (int i = 0; i < 2; i++) {
            double angle =
                    this.tickCount * 0.075D
                            + i * Math.PI
                            + this.random.nextDouble() * 0.7D;

            // Распределяем частицы по всей высоте и глубине.
            double latitude =
                    (this.random.nextDouble() - 0.5D) * Math.PI * 0.8D;

            double horizontal = Math.cos(latitude);

            double radius =
                    baseRadius + this.random.nextDouble() * 0.35D;

            double x = this.getX()
                    + Math.cos(angle) * radius * horizontal;

            double y = centerY
                    + Math.sin(latitude) * radius;

            double z = this.getZ()
                    + Math.sin(angle) * radius * horizontal;

            // Движение по касательной создаёт эффект вращения.
            double speed = 0.025D + this.random.nextDouble() * 0.015D;

            double velocityX = -Math.sin(angle) * speed;
            double velocityZ = Math.cos(angle) * speed;

            double velocityY =
                    (this.random.nextDouble() - 0.4D) * 0.018D;

            int particleType = this.random.nextInt(10);

            if (particleType == 0) {
                // Редкие яркие искры.
                this.level().addParticle(
                        ParticleTypes.END_ROD,
                        x, y, z,
                        velocityX, velocityY, velocityZ
                );
            } else if (particleType <= 3) {
                // Фиолетовые магические частицы.
                this.level().addParticle(
                        ParticleTypes.WITCH,
                        x, y, z,
                        velocityX, velocityY, velocityZ
                );
            } else {
                // Основная портальная энергия.
                this.level().addParticle(
                        ParticleTypes.PORTAL,
                        x, y, z,
                        velocityX, velocityY, velocityZ
                );
            }
        }
    }


    @Override
    public void remove(Entity.RemovalReason reason) {
        if ((reason == Entity.RemovalReason.DISCARDED
                || reason == Entity.RemovalReason.KILLED)
                && !this.level().isClientSide
                && this.level() instanceof ServerLevel serverLevel) {

            removePortalMarkers(serverLevel);
        }

        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
