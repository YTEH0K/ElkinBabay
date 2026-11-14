
package net.kaupenjoe.tutorialmod.rift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class RiftProtection {

    private static final String PROTECTED_UNTIL_TAG =
            "tutorialmod_rift_protected_until";

    private RiftProtection() {
    }

    public static void protect(ServerPlayer player, int durationTicks) {
        long expiresAt = player.serverLevel().getGameTime()
                + Math.max(1, durationTicks);

        player.getPersistentData().putLong(
                PROTECTED_UNTIL_TAG,
                expiresAt
        );
    }

    public static boolean isProtected(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();

        if (!data.contains(PROTECTED_UNTIL_TAG)) {
            return false;
        }

        if (player.serverLevel().getGameTime()
                < data.getLong(PROTECTED_UNTIL_TAG)) {
            return true;
        }

        data.remove(PROTECTED_UNTIL_TAG);
        return false;
    }
}
