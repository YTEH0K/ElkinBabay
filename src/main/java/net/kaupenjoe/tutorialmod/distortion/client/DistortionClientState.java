package net.kaupenjoe.tutorialmod.distortion.client;

public final class DistortionClientState {

    private static volatile int distortionTenths;

    private DistortionClientState() {
    }

    public static int getDistortionTenths() {
        return distortionTenths;
    }

    public static void setDistortionTenths(int value) {
        distortionTenths = Math.max(
                0,
                Math.min(1000, value)
        );
    }
}