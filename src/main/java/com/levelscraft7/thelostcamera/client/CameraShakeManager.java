package com.levelscraft7.thelostcamera.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Combines a long low-amplitude rumble with short impact impulses. */
public final class CameraShakeManager {
    private static long ambientStartedAtNanos;
    private static long ambientEndsAtNanos;
    private static float ambientIntensity;

    private static long impulseStartedAtNanos;
    private static long impulseEndsAtNanos;
    private static float impulseIntensity;

    private static Object lastLevel;

    private CameraShakeManager() {
    }

    public static void start(int durationTicks, float intensity) {
        if (durationTicks <= 0 || intensity <= 0.0F) {
            return;
        }

        long now = System.nanoTime();
        lastLevel = Minecraft.getInstance().level;
        if (durationTicks <= 30) {
            impulseStartedAtNanos = now;
            impulseEndsAtNanos = now + durationTicks * 50_000_000L;
            impulseIntensity = intensity;
        } else {
            ambientStartedAtNanos = now;
            ambientEndsAtNanos = now + durationTicks * 50_000_000L;
            ambientIntensity = intensity;
        }
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != lastLevel) {
            reset();
            return;
        }

        long now = System.nanoTime();
        float ambient = ambientStrength(now);
        float impulse = impulseStrength(now);
        float strength = ambient + impulse;
        if (strength <= 0.0F) {
            if (now >= ambientEndsAtNanos && now >= impulseEndsAtNanos) {
                reset();
            }
            return;
        }

        double seconds = now / 1_000_000_000.0D;
        float yawOffset = (float) ((Math.sin(seconds * 24.0D) + Math.sin(seconds * 53.0D) * 0.38D) * strength);
        float pitchOffset = (float) ((Math.sin(seconds * 33.0D + 1.4D) + Math.sin(seconds * 67.0D) * 0.30D) * strength * 0.78D);
        float rollOffset = (float) ((Math.sin(seconds * 19.0D + 0.7D) + Math.sin(seconds * 41.0D) * 0.24D) * strength * 0.92D);

        event.setYaw(event.getYaw() + yawOffset);
        event.setPitch(event.getPitch() + pitchOffset);
        event.setRoll(event.getRoll() + rollOffset);
    }

    private static float ambientStrength(long now) {
        if (ambientEndsAtNanos <= ambientStartedAtNanos || now >= ambientEndsAtNanos) {
            return 0.0F;
        }
        double progress = (double) (now - ambientStartedAtNanos) / (double) (ambientEndsAtNanos - ambientStartedAtNanos);
        double envelope = Math.sin(Math.PI * Math.max(0.0D, Math.min(1.0D, progress)));
        return (float) (ambientIntensity * envelope);
    }

    private static float impulseStrength(long now) {
        if (impulseEndsAtNanos <= impulseStartedAtNanos || now >= impulseEndsAtNanos) {
            return 0.0F;
        }
        double progress = (double) (now - impulseStartedAtNanos) / (double) (impulseEndsAtNanos - impulseStartedAtNanos);
        double attack = Math.pow(1.0D - Math.max(0.0D, Math.min(1.0D, progress)), 1.65D);
        return (float) (impulseIntensity * attack);
    }

    public static void reset() {
        ambientStartedAtNanos = 0L;
        ambientEndsAtNanos = 0L;
        ambientIntensity = 0.0F;
        impulseStartedAtNanos = 0L;
        impulseEndsAtNanos = 0L;
        impulseIntensity = 0.0F;
        lastLevel = null;
    }
}
