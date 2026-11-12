package net.kaupenjoe.tutorialmod.distortion;

import net.kaupenjoe.tutorialmod.distortion.compatibility.LegendarySurvivalOverhaulCompat;
import net.minecraft.server.level.ServerPlayer;

public final class DistortionCompatibility {

    private DistortionCompatibility() {
    }

    public static void applyThirstPressure(
            ServerPlayer player,
            int distortionTenths
    ) {
        if (distortionTenths < 500) {
            return;
        }

        float exhaustionPerSecond =
                0.01F + (distortionTenths - 500) * 0.0001F;

        LegendarySurvivalOverhaulCompat.addThirstExhaustion(
                player,
                exhaustionPerSecond
        );
    }
}