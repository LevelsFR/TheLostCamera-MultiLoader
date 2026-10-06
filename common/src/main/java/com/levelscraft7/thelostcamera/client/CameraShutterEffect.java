package com.levelscraft7.thelostcamera.client;

import net.minecraft.client.Minecraft;

/** Shared shutter recoil state; loader adapters apply the returned offsets to their camera event. */
public final class CameraShutterEffect {
    private static final long RECOIL_NANOS = 240_000_000L;
    private static long startedAtNanos;
    private static Object level;

    private CameraShutterEffect() {
    }

    public static void start() {
        startedAtNanos = System.nanoTime();
        level = Minecraft.getInstance().level;
    }

    public static Offsets offsets() {
        if (!active()) {
            return Offsets.ZERO;
        }
        double progress = (double) (System.nanoTime() - startedAtNanos) / RECOIL_NANOS;
        double pulse = Math.sin(Math.min(1.0D, progress) * Math.PI);
        float recoil = (float) (pulse * 0.95D);
        return new Offsets(-recoil, recoil * 0.18F);
    }

    private static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        if (startedAtNanos == 0L || minecraft.level == null || minecraft.level != level) {
            reset();
            return false;
        }
        if (System.nanoTime() - startedAtNanos >= RECOIL_NANOS) {
            reset();
            return false;
        }
        return true;
    }

    public static void reset() {
        startedAtNanos = 0L;
        level = null;
    }

    public record Offsets(float pitch, float roll) {
        public static final Offsets ZERO = new Offsets(0.0F, 0.0F);
    }
}
