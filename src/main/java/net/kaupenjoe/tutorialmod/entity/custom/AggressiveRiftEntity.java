
package net.kaupenjoe.tutorialmod.entity.custom;

import net.kaupenjoe.tutorialmod.rift.RiftManager;
import net.kaupenjoe.tutorialmod.rift.RiftProtection;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;

public class AggressiveRiftEntity extends Entity {

    private static final int DURATION_TICKS = 20 * 5;
    private static final double EFFECT_RADIUS = 100.0D;

    private static final int TELEPORT_RETRY_INTERVAL_TICKS = 10;
    private static final int POST_TELEPORT_VISUAL_TICKS = 20;

    private int teleportRetryTicks = 0;
    private int postTeleportTicks = -1;

    private boolean destinationPreparationAttempted = false;
    private BlockPos preparedDestination = null;

    private static final DustParticleOptions RED_TETHER =
            new DustParticleOptions(
                    new Vector3f(0.95F, 0.035F, 0.015F),
                    0.9F
            );

    private static final DustParticleOptions RED_CORE =
            new DustParticleOptions(
                    new Vector3f(1.0F, 0.08F, 0.025F),
                    1.35F
            );

    private static final int TELEPORT_RETRY_INTERVAL_TICKS = 10;

    private int remainingTicks = DURATION_TICKS;
    private int teleportRetryTicks = 0;

    public AggressiveRiftEntity(
            EntityType<? extends AggressiveRiftEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder
    ) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("RemainingTicks")) {
            this.remainingTicks = Math.max(
                    0,
                    Math.min(DURATION_TICKS, tag.getInt("RemainingTicks"))
            );
        }

        this.teleportRetryTicks = Math.max(
                0,
                tag.getInt("TeleportRetryTicks")
        );

        this.teleportRetryTicks = Math.max(
                0,
                tag.getInt("TeleportRetryTicks")
        );

        this.postTeleportTicks = tag.contains("PostTeleportTicks")
                ? tag.getInt("PostTeleportTicks")
                : -1;

        this.destinationPreparationAttempted =
                tag.getBoolean("DestinationPreparationAttempted");

        if (tag.getBoolean("HasPreparedDestination")) {
            this.preparedDestination = new BlockPos(
                    tag.getInt("PreparedDestinationX"),
                    tag.getInt("PreparedDestinationY"),
                    tag.getInt("PreparedDestinationZ")
            );
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RemainingTicks", this.remainingTicks);
        tag.putInt("TeleportRetryTicks", this.teleportRetryTicks);
        tag.putInt("TeleportRetryTicks", this.teleportRetryTicks);
        tag.putInt("PostTeleportTicks", this.postTeleportTicks);

        tag.putBoolean(
                "DestinationPreparationAttempted",
                this.destinationPreparationAttempted
        );

        tag.putBoolean(
                "HasPreparedDestination",
                this.preparedDestination != null
        );

        if (this.preparedDestination != null) {
            tag.putInt("PreparedDestinationX", this.preparedDestination.getX());
            tag.putInt("PreparedDestinationY", this.preparedDestination.getY());
            tag.putInt("PreparedDestinationZ", this.preparedDestination.getZ());
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // После завершения телепортации оставляем портал видимым ещё на секунду.
        if (postTeleportTicks >= 0) {
            if (postTeleportTicks == 0) {
                this.discard();
            } else {
                postTeleportTicks--;
            }

            return;
        }

        int elapsedTicks = DURATION_TICKS - remainingTicks;

        // Продолжаем притягивать игроков даже после окончания таймера.
        applyPullAndTethers(serverLevel, elapsedTicks);

        // Звуки обратного отсчёта.
        if (remainingTicks > 0 && elapsedTicks % 20 == 0) {
            serverLevel.playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.BELL_RESONATE,
                    SoundSource.AMBIENT,
                    6.0F,
                    0.65F
            );
        }

        if (remainingTicks > 0 &&
                (elapsedTicks == 0 || elapsedTicks == 60)) {
            serverLevel.playSound(
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER,
                    6.0F,
                    0.75F
            );
        }

        // Красные частицы вокруг самого разлома.
        if (elapsedTicks % 2 == 0) {
            double rotation = elapsedTicks * 0.35D;

            for (int i = 0; i < 4; i++) {
                double angle = rotation + i * Math.PI / 2.0D;

                double particleX =
                        this.getX() + Math.cos(angle) * 1.2D;

                double particleZ =
                        this.getZ() + Math.sin(angle) * 1.2D;

                double particleY =
                        this.getY() + 0.5D + (i % 2) * 1.2D;

                serverLevel.sendParticles(
                        RED_TETHER,
                        particleX,
                        particleY,
                        particleZ,
                        2,
                        0.05D,
                        0.1D,
                        0.05D,
                        0.01D
                );
            }
        }

        if (elapsedTicks % 5 == 0) {
            serverLevel.sendParticles(
                    RED_CORE,
                    this.getX(),
                    this.getY() + 1.5D,
                    this.getZ(),
                    4,
                    0.7D,
                    1.0D,
                    0.7D,
                    0.015D
            );
        }

        // Основной отсчёт пяти секунд.
        if (remainingTicks > 0) {
            remainingTicks--;
        }

// За секунду до телепортации заранее выбираем место назначения
// и загружаем область вокруг него.
        if (remainingTicks <= 20 && !destinationPreparationAttempted) {
            destinationPreparationAttempted = true;

            preparedDestination =
                    RiftManager.prepareAggressiveDestination(
                            serverLevel,
                            50000
                    );
        }

// После окончания пяти секунд начинаем телепортацию.
        if (remainingTicks <= 0) {
            if (teleportRetryTicks > 0) {
                teleportRetryTicks--;
                return;
            }

            teleportRetryTicks = TELEPORT_RETRY_INTERVAL_TICKS;

            RiftManager.AggressiveRiftTeleportResult result =
                    RiftManager.teleportPlayersFromAggressiveRift(
                            serverLevel,
                            this.position(),
                            EFFECT_RADIUS,
                            50000,
                            this.getUUID(),
                            preparedDestination
                    );

            if (result.isResolved()) {
                // Не удаляем портал мгновенно.
                postTeleportTicks = POST_TELEPORT_VISUAL_TICKS;
            }
        }
    }

    private void applyPullAndTethers(
            ServerLevel level,
            int elapsedTicks
    ) {
        AABB searchBounds =
                this.getBoundingBox().inflate(EFFECT_RADIUS);

        Vec3 riftCenter = this.position().add(
                0.0D,
                this.getBbHeight() * 0.5D,
                0.0D
        );

        double progress = Math.min(
                1.0D,
                elapsedTicks / (double) (DURATION_TICKS - 1)
        );

// Минимальная скорость притяжения постепенно растёт.
// К концу таймера достигает 0.42 блока за тик.
        double desiredPullSpeed =
                0.07D + 0.35D * progress * progress;

        for (ServerPlayer player : level.getEntitiesOfClass(
                ServerPlayer.class,
                searchBounds
        )) {
            Vec3 playerCenter = player.position().add(
                    0.0D,
                    player.getBbHeight() * 0.5D,
                    0.0D
            );

            Vec3 toRift = riftCenter.subtract(playerCenter);
            double distance = toRift.length();

            if (distance > EFFECT_RADIUS || distance < 0.001D) {
                continue;
            }

            // Защита полностью отменяет воздействие.
            if (RiftProtection.isProtected(player)) {
                continue;
            }

            // Линия частиц создаётся четыре раза в секунду.
            if (elapsedTicks % 4 == 0) {
                sendTetherParticles(
                        level,
                        player,
                        riftCenter,
                        playerCenter,
                        distance
                );
            }

// Не вмешиваемся в управление во время творческого полёта.
            if (distance > 1.6D && !player.getAbilities().flying) {
                Vec3 pullDirection = toRift.normalize();

                Vec3 currentVelocity = player.getDeltaMovement();

                // Определяем, с какой скоростью игрок движется к разлому.
                double currentTowardSpeed =
                        currentVelocity.dot(pullDirection);

                // Если скорости недостаточно, принудительно усиливаем притяжение.
                if (currentTowardSpeed < desiredPullSpeed) {
                    double velocityDifference =
                            desiredPullSpeed - currentTowardSpeed;

                    player.setDeltaMovement(
                            currentVelocity.add(
                                    pullDirection.scale(velocityDifference)
                            )
                    );

                    player.connection.send(
                            new ClientboundSetEntityMotionPacket(player)
                    );
                }
            }
        }
    }

    private void sendTetherParticles(
            ServerLevel level,
            ServerPlayer player,
            Vec3 start,
            Vec3 end,
            double distance
    ) {
        int points = Math.max(
                6,
                Math.min(
                        12,
                        (int) Math.ceil(distance / 8.0D)
                )
        );

        for (int i = 0; i <= points; i++) {
            Vec3 point = start.lerp(
                    end,
                    i / (double) points
            );

            level.sendParticles(
                    player,
                    RED_TETHER,
                    true,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
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
