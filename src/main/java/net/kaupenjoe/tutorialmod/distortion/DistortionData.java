package net.kaupenjoe.tutorialmod.distortion;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class DistortionData implements INBTSerializable<CompoundTag> {

    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int MAX_DISTORTION = 1000;

    private int distortionTenths;
    private int overworldTicks;
    private int prisonTicks;

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

        int previous = distortionTenths;

        distortionTenths = Math.max(
                0,
                distortionTenths - 10
        );

        return previous != distortionTenths;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("DistortionTenths", distortionTenths);
        tag.putInt("OverworldTicks", overworldTicks);
        tag.putInt("PrisonTicks", prisonTicks);

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
    }
}