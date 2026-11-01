package net.kaupenjoe.tutorialmod.entity.custom;

import net.kaupenjoe.tutorialmod.rift.RiftManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class AggressiveRiftEntity extends Entity {

    private static final int DURATION_TICKS = 20 * 5;

    private int remainingTicks = DURATION_TICKS;

    public AggressiveRiftEntity(
            EntityType<? extends AggressiveRiftEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("RemainingTicks")) {
            this.remainingTicks = Math.max(
                    1,
                    Math.min(DURATION_TICKS, tag.getInt("RemainingTicks"))
            );
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RemainingTicks", this.remainingTicks);
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

        int elapsedTicks = DURATION_TICKS - remainingTicks;

        if (elapsedTicks % 20 == 0) {
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

        if (elapsedTicks == 0 || elapsedTicks == 60) {
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

        if (elapsedTicks % 2 == 0) {
            double rotation = elapsedTicks * 0.35D;

            for (int i = 0; i < 4; i++) {
                double angle = rotation + i * Math.PI / 2.0D;

                double particleX = this.getX() + Math.cos(angle) * 1.2D;
                double particleZ = this.getZ() + Math.sin(angle) * 1.2D;
                double particleY = this.getY() + 0.5D + (i % 2) * 1.2D;

                serverLevel.sendParticles(
                        ParticleTypes.PORTAL,
                        particleX,
                        particleY,
                        particleZ,
                        2,
                        0.05D,
                        0.1D,
                        0.05D,
                        0.02D
                );
            }
        }

        if (elapsedTicks % 5 == 0) {
            serverLevel.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    this.getX(),
                    this.getY() + 1.5D,
                    this.getZ(),
                    4,
                    0.7D,
                    1.0D,
                    0.7D,
                    0.03D
            );
        }

        remainingTicks--;

        if (remainingTicks <= 0) {
            RiftManager.teleportPlayersFromAggressiveRift(
                    serverLevel,
                    this.position(),
                    100.0D,
                    50_000
            );

            this.discard();
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