package net.kaupenjoe.tutorialmod.rift.client;

public final class RiftCompassClientState {

    private static volatile boolean hasTarget;

    private static volatile int x;
    private static volatile int y;
    private static volatile int z;

    private static volatile String dimension = "";

    private RiftCompassClientState() {
    }

    public static void setTarget(
            boolean found,
            int targetX,
            int targetY,
            int targetZ,
            String targetDimension
    ) {
        hasTarget = found;

        if (found) {
            x = targetX;
            y = targetY;
            z = targetZ;
            dimension = targetDimension;
        } else {
            dimension = "";
        }
    }

    public static boolean hasTarget() {
        return hasTarget;
    }

    public static int getX() {
        return x;
    }

    public static int getY() {
        return y;
    }

    public static int getZ() {
        return z;
    }

    public static String getDimension() {
        return dimension;
    }
}