package net.kaupenjoe.tutorialmod.distortion.compatibility;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class LegendarySurvivalOverhaulCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID =
            "legendarysurvivaloverhaul";

    private static final String API_CLASS =
            "sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil";

    private static Method addExhaustionMethod;
    private static boolean initializationAttempted;

    private LegendarySurvivalOverhaulCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static boolean initialize() {
        if (initializationAttempted) {
            return addExhaustionMethod != null;
        }

        initializationAttempted = true;

        if (!isLoaded()) {
            return false;
        }

        try {
            Class<?> thirstUtil = Class.forName(API_CLASS);

            addExhaustionMethod = thirstUtil.getMethod(
                    "addExhaustion",
                    Player.class,
                    float.class
            );

            return true;

        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn(
                    "Could not initialize Legendary Survival Overhaul compatibility.",
                    exception
            );

            return false;
        }
    }

    public static boolean addThirstExhaustion(
            Player player,
            float exhaustion
    ) {
        if (exhaustion <= 0.0F || !initialize()) {
            return false;
        }

        try {
            addExhaustionMethod.invoke(
                    null,
                    player,
                    exhaustion
            );

            return true;

        } catch (IllegalAccessException | InvocationTargetException exception) {
            LOGGER.warn(
                    "Could not add LSO thirst exhaustion.",
                    exception
            );

            return false;
        }
    }
}