package com.levelscraft7.thelostcamera.client;

import com.levelscraft7.thelostcamera.network.payload.CameraFocalPayload;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinDetector;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * Fluid wheel zoom plus a small automatic photographic metering engine.
 *
 * <p>The meter is deliberately client-side because it evaluates the exact view the player is framing. It samples
 * scene light through the view cone, tracks camera translation/rotation, watches moving entities in-frame and derives
 * a realistic automatic combination of aperture, shutter speed and ISO. The selected values are snapshotted when a
 * photograph is requested and sent back with the captured frame so the saved metadata matches the viewfinder.</p>
 */
public final class CameraZoomManager {
    public static final int MIN_FOCAL_MM = 35;
    public static final int MAX_FOCAL_MM = 135;

    private static final int[] APERTURES_TENTHS = {
            18, 20, 22, 25, 28, 32, 35, 40, 45, 50, 56, 63, 71, 80, 90, 100, 110, 130, 160
    };
    private static final int[] SHUTTER_DENOMINATORS = {
            8, 10, 15, 20, 30, 40, 50, 60, 80, 100, 125, 160, 200, 250, 320, 400, 500,
            640, 800, 1000, 1250, 1600, 2000
    };
    private static final int[] ISO_VALUES = {
            100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000,
            2500, 3200, 4000, 5000, 6400
    };

    private static float focalLengthMm = MIN_FOCAL_MM;
    private static float targetFocalLengthMm = MIN_FOCAL_MM;
    private static int lastSyncedFocalMm = MIN_FOCAL_MM;
    private static boolean sessionInitialized;
    private static Double originalSensitivity;
    private static CameraType originalCameraType;
    private static boolean framedRuin;
    private static long lastRuinScanTick = Long.MIN_VALUE;
    private static long lastMeterTick = Long.MIN_VALUE;

    private static boolean motionInitialized;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static float lastYaw;
    private static float lastPitch;
    private static long lastMotionNanos;
    private static double translationSpeed;
    private static double angularSpeed;
    private static double yawSpeed;
    private static double pitchSpeed;
    private static double subjectMotionScore;
    private static double focusDistance = Double.POSITIVE_INFINITY;
    private static double sceneBrightness = 0.55D;
    private static Exposure currentExposure = new Exposure(28, 125, 100, 120, 0, 0, 0, 0);

    private CameraZoomManager() {
    }

    public static void update(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || minecraft.level == null) {
            restoreSensitivity(minecraft);
            restoreCameraType(minecraft);
            sessionInitialized = false;
            framedRuin = false;
            resetMotion();
            return;
        }

        ItemStack camera = activeCamera();
        if (!sessionInitialized) {
            originalCameraType = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            Integer stored = camera.get(ModDataComponents.CAMERA_FOCAL_MM);
            focalLengthMm = stored == null ? MIN_FOCAL_MM : Mth.clamp(stored, MIN_FOCAL_MM, MAX_FOCAL_MM);
            targetFocalLengthMm = focalLengthMm;
            lastSyncedFocalMm = Math.round(focalLengthMm);
            sessionInitialized = true;
            syncFocal(lastSyncedFocalMm);
            initializeMotion(minecraft);
            meterScene(minecraft, true);
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

        updateMotion(minecraft);
        applySensitivity(minecraft);

        long gameTime = minecraft.level.getGameTime();
        if (gameTime - lastRuinScanTick >= 8L) {
            lastRuinScanTick = gameTime;
            framedRuin = scanFramedRuin(minecraft);
        }
        if (gameTime - lastMeterTick >= 3L) {
            lastMeterTick = gameTime;
            meterScene(minecraft, false);
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

    /** The currently metered automatic exposure. */
    public static Exposure exposure() {
        return currentExposure;
    }

    /** Freezes all values that must remain stable for the duration of one physical exposure. */
    public static CaptureProfile captureProfile() {
        Exposure exposure = currentExposure;
        return new CaptureProfile(
                focalLengthMm(),
                exposure,
                translationSpeed,
                angularSpeed,
                yawSpeed,
                pitchSpeed,
                subjectMotionScore
        );
    }

    private static void meterScene(Minecraft minecraft, boolean immediate) {
        double measuredBrightness = measureSceneBrightness(minecraft);
        if (immediate) {
            sceneBrightness = measuredBrightness;
        } else {
            sceneBrightness = Mth.lerp(0.28D, sceneBrightness, measuredBrightness);
        }

        SubjectReading subject = measureSubjects(minecraft);
        subjectMotionScore = immediate
                ? subject.motionScore
                : Mth.lerp(0.32D, subjectMotionScore, subject.motionScore);
        focusDistance = subject.focusDistance;
        currentExposure = calculateExposure(sceneBrightness, focalLengthMm(), translationSpeed, angularSpeed, subjectMotionScore, focusDistance);
    }

    /**
     * Centre-weighted 3x3 metering. Minecraft light levels are transformed non-linearly so caves, torch-lit rooms,
     * dusk and open daylight separate into useful photographic exposure values instead of all looking equally bright.
     */
    private static double measureSceneBrightness(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return 0.5D;
        }

        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 screenUp = right.cross(look).normalize();

        double spread = Math.tan(Math.toRadians(fov() * 0.5D)) * 0.34D;
        double total = 0.0D;
        double totalWeight = 0.0D;
        double[] distances = {3.0D, 7.0D, 14.0D, 28.0D, 44.0D};

        for (int gridY = -1; gridY <= 1; gridY++) {
            for (int gridX = -1; gridX <= 1; gridX++) {
                double centreWeight = gridX == 0 && gridY == 0 ? 3.2D : (gridX == 0 || gridY == 0 ? 1.45D : 0.8D);
                Vec3 ray = look
                        .add(right.scale(gridX * spread))
                        .add(screenUp.scale(gridY * spread))
                        .normalize();

                for (int index = 0; index < distances.length; index++) {
                    Vec3 sample = eye.add(ray.scale(distances[index]));
                    double depthWeight = 1.0D + index * 0.12D;
                    double weight = centreWeight * depthWeight;
                    total += lightAt(minecraft.level, sample) * weight;
                    totalWeight += weight;
                }
            }
        }

        // The immediate focus point gets extra weight, just like a centre-weighted / partial meter.
        HitResult hit = minecraft.hitResult;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            total += lightAt(minecraft.level, hit.getLocation()) * 5.0D;
            totalWeight += 5.0D;
        }

        double average = totalWeight <= 0.0D ? 0.5D : total / totalWeight;
        if (minecraft.level.dimension() == Level.NETHER) {
            average = Math.max(average, 0.055D);
        } else if (minecraft.level.dimension() == Level.END) {
            average = Math.max(average, 0.085D);
        }
        return Mth.clamp(average, 0.005D, 1.0D);
    }

    private static double lightAt(Level level, Vec3 location) {
        BlockPos pos = BlockPos.containing(location.x, location.y, location.z);
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);

        double block = Math.pow(blockLight / 15.0D, 2.05D);
        double sky = Math.pow(skyLight / 15.0D, 1.55D) * daylightFactor(level);
        return Math.max(block, sky);
    }

    private static double daylightFactor(Level level) {
        if (level.dimension() == Level.NETHER) {
            return 0.0D;
        }
        if (level.dimension() == Level.END) {
            return 0.11D;
        }

        long time = Math.floorMod(level.getDefaultClockTime(), 24_000L);
        double angle = (time - 6_000.0D) / 24_000.0D * Math.PI * 2.0D;
        double sun = Math.max(0.0D, Math.cos(angle));
        double factor = 0.075D + 0.925D * sun;
        if (level.isThundering()) {
            factor *= 0.42D;
        } else if (level.isRaining()) {
            factor *= 0.68D;
        }
        return factor;
    }

    private static SubjectReading measureSubjects(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return new SubjectReading(0.0D, Double.POSITIVE_INFINITY);
        }

        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        AABB scanBox = minecraft.player.getBoundingBox().inflate(52.0D);
        List<Entity> entities = minecraft.level.getEntities(
                minecraft.player,
                scanBox,
                entity -> entity.isAlive() && !entity.isInvisible()
        );

        double halfAngle = Math.toRadians(Math.max(8.0D, fov() * 0.58D));
        double visibleThreshold = Math.cos(halfAngle);
        double focusThreshold = Math.cos(Math.toRadians(Math.max(1.8D, fov() * 0.075D)));
        double strongestMotion = 0.0D;
        double nearestFocus = focusDistanceFromHit(minecraft, eye);

        for (Entity entity : entities) {
            Vec3 centre = entity.getBoundingBox().getCenter();
            Vec3 offset = centre.subtract(eye);
            double distance = offset.length();
            if (distance < 0.25D || distance > 52.0D) {
                continue;
            }

            double alignment = look.dot(offset.scale(1.0D / distance));
            if (alignment < visibleThreshold || !minecraft.player.hasLineOfSight(entity)) {
                continue;
            }

            double speedBlocksPerSecond = entity.getDeltaMovement().length() * 20.0D;
            double angularMotion = speedBlocksPerSecond / Math.max(1.0D, distance);
            double focalMagnification = focalLengthMm() / (double) MIN_FOCAL_MM;
            double score = angularMotion * focalMagnification * 1.8D;
            strongestMotion = Math.max(strongestMotion, Mth.clamp(score, 0.0D, 1.0D));

            if (alignment >= focusThreshold && distance < nearestFocus) {
                nearestFocus = distance;
            }
        }

        return new SubjectReading(strongestMotion, nearestFocus);
    }

    private static double focusDistanceFromHit(Minecraft minecraft, Vec3 eye) {
        HitResult hit = minecraft.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return Double.POSITIVE_INFINITY;
        }
        double distance = hit.getLocation().distanceTo(eye);
        return distance > 0.1D ? distance : Double.POSITIVE_INFINITY;
    }

    private static Exposure calculateExposure(
            double brightness,
            int focal,
            double movementBlocksPerSecond,
            double rotationDegreesPerSecond,
            double subjectMotion,
            double focusDistance
    ) {
        double sceneEv = 2.0D + 13.0D * Math.pow(Mth.clamp(brightness, 0.0D, 1.0D), 0.70D);

        int widestAperture = widestApertureForFocal(focal);
        int preferredAperture = preferredAperture(sceneEv, widestAperture);
        double minimumShutter = minimumSafeShutter(focal, movementBlocksPerSecond, rotationDegreesPerSecond, subjectMotion);

        int aperture = preferredAperture;
        int shutter = nearestShutter(Math.max(minimumShutter, idealShutterDenominator(sceneEv, aperture, 100)));
        int iso = nearestIso(requiredIso(sceneEv, aperture, shutter));

        // If the selected combination demands too much ISO, progressively open the lens first.
        while (iso >= ISO_VALUES[ISO_VALUES.length - 1] && aperture > widestAperture) {
            aperture = previousAperture(aperture, widestAperture);
            iso = nearestIso(requiredIso(sceneEv, aperture, shutter));
        }

        // In very low light even ISO 6400 may not sustain a safe handheld shutter. Let the shutter slow down instead.
        // This is intentional: the resulting multi-frame exposure can then genuinely blur moving subjects/camera motion.
        double maxIsoIdealShutter = idealShutterDenominator(sceneEv, aperture, ISO_VALUES[ISO_VALUES.length - 1]);
        if (requiredIso(sceneEv, aperture, shutter) > ISO_VALUES[ISO_VALUES.length - 1]
                && maxIsoIdealShutter < minimumShutter) {
            shutter = nearestShutter(Math.max(SHUTTER_DENOMINATORS[0], maxIsoIdealShutter));
            iso = ISO_VALUES[ISO_VALUES.length - 1];
        } else {
            iso = nearestIso(requiredIso(sceneEv, aperture, shutter));
        }

        // Bright scenes at ISO 100 are allowed to close the diaphragm a little instead of exceeding 1/2000 s.
        if (iso <= 100 && shutter >= SHUTTER_DENOMINATORS[SHUTTER_DENOMINATORS.length - 1]) {
            while (aperture < 160 && exposureErrorEv(sceneEv, aperture, shutter, 100) > 0.35D) {
                aperture = nextAperture(aperture);
            }
            iso = 100;
        }

        iso = Mth.clamp(iso, 100, 6400);
        shutter = Mth.clamp(shutter, SHUTTER_DENOMINATORS[0], SHUTTER_DENOMINATORS[SHUTTER_DENOMINATORS.length - 1]);
        double errorEv = exposureErrorEv(sceneEv, aperture, shutter, iso);
        double risk = Mth.clamp((minimumShutter / Math.max(1.0D, shutter)) - 1.0D, 0.0D, 1.0D);
        if (subjectMotion > 0.35D && shutter < 250) {
            risk = Math.max(risk, Math.min(1.0D, subjectMotion * (250.0D / Math.max(30.0D, shutter))));
        }

        int focusTenths = Double.isFinite(focusDistance)
                ? Mth.clamp((int) Math.round(focusDistance * 10.0D), 1, 9_999)
                : 0;

        return new Exposure(
                aperture,
                shutter,
                iso,
                (int) Math.round(sceneEv * 10.0D),
                Mth.clamp((int) Math.round(errorEv * 10.0D), -30, 30),
                focusTenths,
                Mth.clamp((int) Math.round(risk * 100.0D), 0, 100),
                Mth.clamp((int) Math.round(subjectMotion * 100.0D), 0, 100)
        );
    }

    private static int widestApertureForFocal(int focal) {
        double zoom = (focal - MIN_FOCAL_MM) / (double) (MAX_FOCAL_MM - MIN_FOCAL_MM);
        int target = (int) Math.round(18.0D + 10.0D * Mth.clamp(zoom, 0.0D, 1.0D));
        for (int aperture : APERTURES_TENTHS) {
            if (aperture >= target) {
                return aperture;
            }
        }
        return 28;
    }

    private static int preferredAperture(double sceneEv, int widest) {
        int preferred;
        if (sceneEv >= 14.0D) {
            preferred = 80;
        } else if (sceneEv >= 12.2D) {
            preferred = 56;
        } else if (sceneEv >= 10.2D) {
            preferred = 40;
        } else if (sceneEv >= 8.0D) {
            preferred = 28;
        } else if (sceneEv >= 6.0D) {
            preferred = 22;
        } else {
            preferred = widest;
        }
        return Math.max(preferred, widest);
    }

    private static double minimumSafeShutter(
            int focal,
            double movementBlocksPerSecond,
            double rotationDegreesPerSecond,
            double subjectMotion
    ) {
        double denominator = focal * 1.12D;
        denominator *= 1.0D + Math.min(2.8D, movementBlocksPerSecond * 0.34D);
        denominator *= 1.0D + Math.min(4.0D, rotationDegreesPerSecond / 72.0D);
        denominator *= 1.0D + subjectMotion * 4.2D;
        return Mth.clamp(denominator, 30.0D, 2_000.0D);
    }

    private static double idealShutterDenominator(double sceneEv, int apertureTenths, int iso) {
        double aperture = apertureTenths / 10.0D;
        double targetCameraEv = sceneEv + log2(iso / 100.0D);
        return Math.pow(2.0D, targetCameraEv) / (aperture * aperture);
    }

    private static double requiredIso(double sceneEv, int apertureTenths, int shutterDenominator) {
        double aperture = apertureTenths / 10.0D;
        double cameraEvAtIso100 = log2(aperture * aperture * shutterDenominator);
        return 100.0D * Math.pow(2.0D, cameraEvAtIso100 - sceneEv);
    }

    private static double exposureErrorEv(double sceneEv, int apertureTenths, int shutterDenominator, int iso) {
        double aperture = apertureTenths / 10.0D;
        double cameraEv = log2(aperture * aperture * shutterDenominator) - log2(iso / 100.0D);
        return sceneEv - cameraEv;
    }

    private static double log2(double value) {
        return Math.log(Math.max(1.0E-6D, value)) / Math.log(2.0D);
    }

    private static int nearestShutter(double target) {
        int best = SHUTTER_DENOMINATORS[0];
        double bestDistance = Double.MAX_VALUE;
        for (int value : SHUTTER_DENOMINATORS) {
            double distance = Math.abs(Math.log(value / Math.max(1.0D, target)));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = value;
            }
        }
        return best;
    }

    private static int nearestIso(double target) {
        int best = ISO_VALUES[0];
        double bestDistance = Double.MAX_VALUE;
        for (int value : ISO_VALUES) {
            double distance = Math.abs(Math.log(value / Math.max(1.0D, target)));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = value;
            }
        }
        return best;
    }

    private static int previousAperture(int current, int minimum) {
        int previous = minimum;
        for (int aperture : APERTURES_TENTHS) {
            if (aperture >= current) {
                return Math.max(minimum, previous);
            }
            if (aperture >= minimum) {
                previous = aperture;
            }
        }
        return previous;
    }

    private static int nextAperture(int current) {
        for (int aperture : APERTURES_TENTHS) {
            if (aperture > current) {
                return aperture;
            }
        }
        return current;
    }

    private static void updateMotion(Minecraft minecraft) {
        if (minecraft.player == null) {
            resetMotion();
            return;
        }
        if (!motionInitialized) {
            initializeMotion(minecraft);
            return;
        }

        long now = System.nanoTime();
        double elapsed = (now - lastMotionNanos) / 1_000_000_000.0D;
        if (elapsed <= 0.0D || elapsed > 0.25D) {
            initializeMotion(minecraft);
            return;
        }

        elapsed = Mth.clamp(elapsed, 1.0D / 240.0D, 0.10D);
        double dx = minecraft.player.getX() - lastX;
        double dy = minecraft.player.getY() - lastY;
        double dz = minecraft.player.getZ() - lastZ;
        double rawTranslation = Math.sqrt(dx * dx + dy * dy + dz * dz) / elapsed;
        double yawDelta = Mth.wrapDegrees(minecraft.player.getYRot() - lastYaw);
        double pitchDelta = minecraft.player.getXRot() - lastPitch;
        double rawYaw = yawDelta / elapsed;
        double rawPitch = pitchDelta / elapsed;
        double rawAngular = Math.hypot(rawYaw, rawPitch);

        translationSpeed = Mth.lerp(0.28D, translationSpeed, Math.min(20.0D, rawTranslation));
        yawSpeed = Mth.lerp(0.34D, yawSpeed, rawYaw);
        pitchSpeed = Mth.lerp(0.34D, pitchSpeed, rawPitch);
        angularSpeed = Mth.lerp(0.34D, angularSpeed, Math.min(720.0D, rawAngular));

        lastX = minecraft.player.getX();
        lastY = minecraft.player.getY();
        lastZ = minecraft.player.getZ();
        lastYaw = minecraft.player.getYRot();
        lastPitch = minecraft.player.getXRot();
        lastMotionNanos = now;
    }

    private static void initializeMotion(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        lastX = minecraft.player.getX();
        lastY = minecraft.player.getY();
        lastZ = minecraft.player.getZ();
        lastYaw = minecraft.player.getYRot();
        lastPitch = minecraft.player.getXRot();
        lastMotionNanos = System.nanoTime();
        translationSpeed = 0.0D;
        angularSpeed = 0.0D;
        yawSpeed = 0.0D;
        pitchSpeed = 0.0D;
        motionInitialized = true;
    }

    private static void resetMotion() {
        motionInitialized = false;
        translationSpeed = 0.0D;
        angularSpeed = 0.0D;
        yawSpeed = 0.0D;
        pitchSpeed = 0.0D;
        subjectMotionScore = 0.0D;
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

    private static void restoreCameraType(Minecraft minecraft) {
        if (originalCameraType != null) {
            minecraft.options.setCameraType(originalCameraType);
            originalCameraType = null;
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

    public record Exposure(
            int apertureTenths,
            int shutterDenominator,
            int iso,
            int sceneEvTenths,
            int exposureErrorTenths,
            int focusDistanceTenths,
            int shakeRiskPercent,
            int subjectMotionPercent
    ) {
        public float aperture() {
            return apertureTenths / 10.0F;
        }

        public float sceneEv() {
            return sceneEvTenths / 10.0F;
        }

        public float exposureErrorEv() {
            return exposureErrorTenths / 10.0F;
        }

        public boolean hasFocusDistance() {
            return focusDistanceTenths > 0;
        }

        public float focusDistance() {
            return focusDistanceTenths / 10.0F;
        }
    }

    public record CaptureProfile(
            int focalLengthMm,
            Exposure exposure,
            double translationSpeed,
            double angularSpeed,
            double yawSpeed,
            double pitchSpeed,
            double subjectMotionScore
    ) {
    }

    private record SubjectReading(double motionScore, double focusDistance) {
    }
}
