package com.levelscraft7.thelostcamera.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Short mechanical shutter recoil, started only after the saved frame was captured. */
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

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (!activeFor(RECOIL_NANOS)) {
            return;
        }
        double progress = (double) (System.nanoTime() - startedAtNanos) / RECOIL_NANOS;
        double clamped = Math.min(1.0D, progress);
        double pulse = Math.sin(clamped * Math.PI);
        float recoil = (float) (pulse * 0.95D);
        event.setPitch(event.getPitch() - recoil);
        event.setRoll(event.getRoll() + recoil * 0.18F);
    }

    private static boolean activeFor(long duration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (startedAtNanos == 0L || minecraft.level == null || minecraft.level != level) {
            reset();
            return false;
        }
        if (System.nanoTime() - startedAtNanos >= duration) {
            return false;
        }
        return true;
    }

    public static void reset() {
        startedAtNanos = 0L;
        level = null;
    }
}
