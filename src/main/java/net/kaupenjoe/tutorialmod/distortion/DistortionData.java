
package net.kaupenjoe.tutorialmod.distortion;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class DistortionData implements INBTSerializable<CompoundTag> {

    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int MAX_DISTORTION = 1000;
    private static final int DEATH_COUNTDOWN_TICKS = 20 * 3;

    private int distortionTenths;
    private int overworldTicks;
    private int prisonTicks;

    private int darknessCycleTicks;
    private int deathCountdownTicks = -1;
    private boolean distortionDeathPending;

    private transient float foodExhaustionAtTickStart;
    private transient float foodSaturationAtTickStart;
    private transient int foodLevelAtTickStart;
    private transient boolean foodSnapshotCaptured;

    public int getDistortionTenths() {
        return distortionTenths;
    }

    public boolean addTenths(int amount) {
        if (amount <= 0) {
            return false;
        }

        int previous = distortionTenths;

        distortionTenths = Math.min(
                MAX_DISTORTION,
                distortionTenths + amount
        );

        return previous != distortionTenths;
    }

    public boolean removeTenths(int amount) {
        if (amount <= 0) {
            return false;
        }

        int previous = distortionTenths;

        distortionTenths = Math.max(
                0,
                distortionTenths - amount
        );

        if (distortionTenths < MAX_DISTORTION) {
            deathCountdownTicks = -1;
            distortionDeathPending = false;
        }

        if (distortionTenths < 800) {
            darknessCycleTicks = 0;
        }

        return previous != distortionTenths;
    }

    public boolean tickInOverworld() {
        overworldTicks++;

        if (overworldTicks < TICKS_PER_MINUTE) {
            return false;
        }

        overworldTicks -= TICKS_PER_MINUTE;

        return addTenths(1);
    }

    public boolean tickInPrison() {
        prisonTicks++;

        if (prisonTicks < TICKS_PER_MINUTE) {
            return false;
        }

        prisonTicks -= TICKS_PER_MINUTE;

        return removeTenths(10);
    }

    public boolean tickDarknessCycle() {
        if (distortionTenths < 800 || distortionTenths >= MAX_DISTORTION) {
            darknessCycleTicks = 0;
            return false;
        }

        darknessCycleTicks++;

        if (darknessCycleTicks >= TICKS_PER_MINUTE) {
            darknessCycleTicks = 0;
            return true;
        }

        return false;
    }

    public boolean startDeathCountdownIfNeeded() {
        if (distortionTenths < MAX_DISTORTION) {
            deathCountdownTicks = -1;
            return false;
        }

        if (distortionDeathPending || deathCountdownTicks >= 0) {
            return false;
        }

        deathCountdownTicks = DEATH_COUNTDOWN_TICKS;

        return true;
    }

    public boolean hasDeathCountdown() {
        return deathCountdownTicks >= 0;
    }

    public int getDeathCountdownTicks() {
        return Math.max(0, deathCountdownTicks);
    }

    public boolean isDistortionDeathPending() {
        return distortionDeathPending;
    }

    public boolean tickDeathCountdown() {
        if (distortionTenths < MAX_DISTORTION) {
            deathCountdownTicks = -1;
            return false;
        }

        if (distortionDeathPending || deathCountdownTicks < 0) {
            return false;
        }

        deathCountdownTicks--;

        if (deathCountdownTicks <= 0) {
            deathCountdownTicks = -1;
            distortionDeathPending = true;
            return true;
        }

        return false;
    }

    public boolean consumeDeathPenaltyAfterRespawn() {
        if (!distortionDeathPending) {
            return false;
        }

        distortionDeathPending = false;
        deathCountdownTicks = -1;

        distortionTenths = Math.max(
                0,
                distortionTenths - 10
        );

        darknessCycleTicks = 0;

        return true;
    }

    public void captureFoodState(FoodData foodData) {
        foodExhaustionAtTickStart = foodData.getExhaustionLevel();
        foodSaturationAtTickStart = foodData.getSaturationLevel();
        foodLevelAtTickStart = foodData.getFoodLevel();

        foodSnapshotCaptured = true;
    }

    public float finishFoodTick(FoodData foodData) {
        if (!foodSnapshotCaptured) {
            return 0.0F;
        }

        foodSnapshotCaptured = false;

        float addedExhaustion =
                foodData.getExhaustionLevel()
                        - foodExhaustionAtTickStart;

        boolean exhaustionConsumed =
                foodData.getFoodLevel() < foodLevelAtTickStart
                        || foodData.getSaturationLevel()
                        < foodSaturationAtTickStart - 0.0001F;

        if (exhaustionConsumed) {
            addedExhaustion += 4.0F;
        }

        return Math.max(0.0F, addedExhaustion);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("DistortionTenths", distortionTenths);
        tag.putInt("OverworldTicks", overworldTicks);
        tag.putInt("PrisonTicks", prisonTicks);

        tag.putInt("DarknessCycleTicks", darknessCycleTicks);
        tag.putInt("DeathCountdownTicks", deathCountdownTicks);
        tag.putBoolean("DistortionDeathPending", distortionDeathPending);

        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag
    ) {
        distortionTenths = Math.max(
                0,
                Math.min(MAX_DISTORTION, tag.getInt("DistortionTenths"))
        );

        overworldTicks = Math.max(
                0,
                Math.min(TICKS_PER_MINUTE - 1, tag.getInt("OverworldTicks"))
        );

        prisonTicks = Math.max(
                0,
                Math.min(TICKS_PER_MINUTE - 1, tag.getInt("PrisonTicks"))
        );

        darknessCycleTicks = Math.max(
                0,
                Math.min(
                        TICKS_PER_MINUTE - 1,
                        tag.getInt("DarknessCycleTicks")
                )
        );

        deathCountdownTicks = tag.contains("DeathCountdownTicks")
                ? Math.max(
                -1,
                Math.min(
                        DEATH_COUNTDOWN_TICKS,
                        tag.getInt("DeathCountdownTicks")
                )
        )
                : -1;

        distortionDeathPending =
                tag.getBoolean("DistortionDeathPending");

        if (distortionDeathPending) {
            deathCountdownTicks = -1;
        }

        foodSnapshotCaptured = false;
    }
}
