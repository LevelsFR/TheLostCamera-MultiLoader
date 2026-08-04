package com.levelscraft7.thelostcamera.client;

import com.levelscraft7.thelostcamera.network.payload.CameraFocalPayload;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Fluid wheel zoom, live exposure readout and lightweight ruin lock detection. */
public final class CameraZoomManager {
    public static final int MIN_FOCAL_MM = 35;
    public static final int MAX_FOCAL_MM = 135;
    private static float focalLengthMm = MIN_FOCAL_MM;
    private static float targetFocalLengthMm = MIN_FOCAL_MM;
    private static int lastSyncedFocalMm = MIN_FOCAL_MM;
    private static boolean sessionInitialized;
    private static Double originalSensitivity;
    private static boolean framedRuin;
    private static long lastRuinScanTick = Long.MIN_VALUE;

    private CameraZoomManager() {
    }

    public static void update(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || minecraft.level == null) {
            restoreSensitivity(minecraft);
            sessionInitialized = false;
            framedRuin = false;
            return;
        }

        ItemStack camera = activeCamera();
        if (!sessionInitialized) {
            Integer stored = camera.get(ModDataComponents.CAMERA_FOCAL_MM);
            focalLengthMm = stored == null ? MIN_FOCAL_MM : Mth.clamp(stored, MIN_FOCAL_MM, MAX_FOCAL_MM);
            targetFocalLengthMm = focalLengthMm;
            lastSyncedFocalMm = Math.round(focalLengthMm);
            sessionInitialized = true;
            syncFocal(lastSyncedFocalMm);
        }

        focalLengthMm = Mth.lerp(0.24F, focalLengthMm, targetFocalLengthMm);
        if (Math.abs(targetFocalLengthMm - focalLengthMm) < 0.025F) {
            focalLengthMm = targetFocalLengthMm;
        }
        int roundedFocal = Math.round(focalLengthMm);
        if (roundedFocal != lastSyncedFocalMm) {
            lastSyncedFocalMm = roundedFocal;
            syncFocal(roundedFocal);
        }

        applySensitivity(minecraft);
        long gameTime = minecraft.level.getGameTime();
        if (gameTime - lastRuinScanTick >= 8L) {
            lastRuinScanTick = gameTime;
            framedRuin = scanFramedRuin(minecraft);
        }
    }

    public static boolean scroll(double amount) {
        if (amount == 0.0D || !CameraClientEvents.isCameraActive()) {
            return false;
        }
        targetFocalLengthMm = Mth.clamp((float) (targetFocalLengthMm + amount * 7.5D),
                MIN_FOCAL_MM, MAX_FOCAL_MM);
        return true;
    }

    public static float fov() {
        return Mth.clamp(63.0F * MIN_FOCAL_MM / focalLengthMm, 16.0F, 63.0F);
    }

    public static int focalLengthMm() {
        return Math.round(focalLengthMm);
    }

    public static boolean hasFramedRuin() {
        return framedRuin;
    }

    public static Exposure exposure() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return new Exposure(28, 125, 100);
        }
        long time = Math.floorMod(minecraft.level.getDefaultClockTime(), 24_000L);
        boolean night = time >= 12_500L && time <= 23_000L;
        if (night || minecraft.level.isThundering()) {
            return new Exposure(18, 40, 800);
        }
        if (minecraft.level.isRaining()) {
            return new Exposure(22, 80, 400);
        }
        int shutter = focalLengthMm() >= 100 ? 250 : focalLengthMm() >= 70 ? 160 : 125;
        return new Exposure(28, shutter, 100);
    }

    private static void applySensitivity(Minecraft minecraft) {
        if (originalSensitivity == null) {
            originalSensitivity = minecraft.options.sensitivity().get();
        }
        double zoomProgress = (focalLengthMm - MIN_FOCAL_MM) / (MAX_FOCAL_MM - MIN_FOCAL_MM);
        double multiplier = 1.0D - zoomProgress * 0.78D;
        minecraft.options.sensitivity().set(originalSensitivity * multiplier);
    }

    private static void restoreSensitivity(Minecraft minecraft) {
        if (originalSensitivity != null) {
            minecraft.options.sensitivity().set(originalSensitivity);
            originalSensitivity = null;
        }
    }

    private static void syncFocal(int focalMm) {
        ClientPacketDistributor.sendToServer(new CameraFocalPayload(focalMm));
    }

    private static ItemStack activeCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = minecraft.player.getMainHandItem();
        if (main.getItem() == ModItems.LOST_CAMERA.get()) {
            return main;
        }
        return minecraft.player.getOffhandItem();
    }

    private static boolean scanFramedRuin(Minecraft minecraft) {
        return RuinDetector.hasFramedRuin(minecraft.player);
    }

    public record Exposure(int apertureTenths, int shutterDenominator, int iso) {
        public float aperture() {
            return apertureTenths / 10.0F;
        }
    }
}
