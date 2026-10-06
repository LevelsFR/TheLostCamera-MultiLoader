package com.levelscraft7.thelostcamera.client.photo;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.client.CameraZoomManager;
import com.levelscraft7.thelostcamera.client.ClientPlatform;
import com.levelscraft7.thelostcamera.config.ModConfig;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.network.payload.CaptureReadyPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoCaptureFailedPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUploadChunkPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Captures the rendered scene as a physical exposure.
 *
 * <p>The viewfinder drops immediately when the shutter is pressed. A single clean rendered frame is acquired without
 * blocking the game thread, then all PNG processing and upload work continues in the background. High ISO adds
 * restrained sensor/film-like noise, while any unavoidable metering error affects the saved PNG instead of remaining
 * cosmetic metadata.</p>
 */
public final class ClientPhotoCapture {
    private static final int CHUNK_SIZE = 28_000;
    private static final long CLIENT_CAPTURE_TIMEOUT_MILLIS = 15_000L;
    private static final int MINIMUM_DIMENSION = 640;

    private static int screenshotDelay = -1;
    private static UUID pendingImageId;
    private static boolean previousHudHidden;
    private static long captureStartedAtMillis;
    private static Object lastLevel;
    private static UUID lastPlayerId;
    private static boolean frameInFlight;
    private static int requiredFrames;
    private static CameraZoomManager.CaptureProfile captureProfile;
    private static final List<BufferedImage> capturedFrames = new ArrayList<>();

    private ClientPhotoCapture() {
    }

    public static void request(UUID imageId) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshSession(minecraft);

        if (minecraft.level == null || minecraft.player == null) {
            failCapture(imageId);
            return;
        }

        if (isCapturing()) {
            if (!Objects.equals(pendingImageId, imageId)) {
                failCapture(imageId);
            }
            return;
        }

        previousHudHidden = ClientPlatform.isHudHidden(minecraft);
        if (!previousHudHidden) {
            ClientPlatform.setHudHidden(minecraft, true);
        }

        captureProfile = CameraZoomManager.captureProfile();
        requiredFrames = 1;
        capturedFrames.clear();
        pendingImageId = imageId;
        captureStartedAtMillis = System.currentTimeMillis();
        frameInFlight = false;

        // Drop the viewfinder immediately on the client instead of waiting for the held-item sync from the server.
        lowerLocalCamera(minecraft);

        // Let one normal world frame render after the viewfinder is lowered, then capture it.
        // This avoids sampling the previous viewfinder framebuffer while keeping the exit visually immediate.
        screenshotDelay = 1;
    }

    public static void onRenderFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        refreshSession(minecraft);

        if (!isCapturing()) {
            return;
        }

        if (System.currentTimeMillis() - captureStartedAtMillis > CLIENT_CAPTURE_TIMEOUT_MILLIS) {
            UUID timedOutImage = pendingImageId;
            reset(minecraft);
            failCapture(timedOutImage);
            return;
        }

        if (minecraft.level == null || minecraft.player == null) {
            UUID abandonedImage = pendingImageId;
            reset(minecraft);
            failCapture(abandonedImage);
            return;
        }

        if (frameInFlight) {
            return;
        }
        if (screenshotDelay > 0) {
            screenshotDelay--;
            return;
        }

        UUID imageId = pendingImageId;
        if (imageId == null || captureProfile == null) {
            reset(minecraft);
            failCapture(imageId);
            return;
        }

        frameInFlight = true;
        Screenshot.takeScreenshot(ClientPlatform.mainRenderTarget(minecraft), nativeImage ->
                acceptExposureFrame(minecraft, imageId, nativeImage));
    }

    public static boolean isCapturing() {
        return pendingImageId != null || screenshotDelay >= 0 || frameInFlight;
    }

    public static void cancelPending() {
        if (!isCapturing()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UUID canceledImage = pendingImageId;
        reset(minecraft);
        failCapture(canceledImage);
    }

    private static void acceptExposureFrame(Minecraft minecraft, UUID imageId, NativeImage nativeImage) {
        try (nativeImage) {
            if (!Objects.equals(pendingImageId, imageId) || captureProfile == null) {
                return;
            }

            BufferedImage source = fromNativeImage(nativeImage);
            BufferedImage cropped = cropToSquare(source);
            BufferedImage prepared = clampLongestEdge(cropped, ModConfig.PHOTO_MAX_DIMENSION.get());
            capturedFrames.add(prepared);
        } catch (Exception exception) {
            TheLostCamera.LOGGER.error("Failed to acquire exposure frame for photograph {}", imageId, exception);
            UUID failedImage = pendingImageId;
            reset(minecraft);
            failCapture(failedImage);
            return;
        }

        frameInFlight = false;
        if (capturedFrames.size() < requiredFrames) {
            screenshotDelay = 0;
            return;
        }

        CameraZoomManager.CaptureProfile profile = captureProfile;
        List<BufferedImage> exposureFrames = List.copyOf(capturedFrames);
        finishCaptureState(minecraft);

        CameraZoomManager.Exposure exposure = profile.exposure();
        PlatformServices.sendToServer(new CaptureReadyPayload(
                imageId,
                profile.focalLengthMm(),
                exposure.apertureTenths(),
                exposure.shutterDenominator(),
                exposure.iso()
        ));
        Thread processor = new Thread(
                () -> processAndSend(imageId, exposureFrames, profile),
                "TheLostCamera-PhotoProcessor"
        );
        processor.setDaemon(true);
        processor.start();
    }

    private static void lowerLocalCamera(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() == ModItems.LOST_CAMERA.get() && LostCameraItem.isActive(stack)) {
                LostCameraItem.setActive(stack, false);
            }
        }
    }

    private static void refreshSession(Minecraft minecraft) {
        Object currentLevel = minecraft.level;
        UUID currentPlayerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (currentLevel == lastLevel && Objects.equals(currentPlayerId, lastPlayerId)) {
            return;
        }

        if (isCapturing()) {
            UUID abandonedImage = pendingImageId;
            reset(minecraft);
            failCapture(abandonedImage);
        }
        lastLevel = currentLevel;
        lastPlayerId = currentPlayerId;
    }

    private static void finishCaptureState(Minecraft minecraft) {
        restoreHud(minecraft);
        screenshotDelay = -1;
        pendingImageId = null;
        captureStartedAtMillis = 0L;
        frameInFlight = false;
        requiredFrames = 0;
        captureProfile = null;
        capturedFrames.clear();
    }

    private static void reset(Minecraft minecraft) {
        finishCaptureState(minecraft);
    }

    private static void restoreHud(Minecraft minecraft) {
        if (ClientPlatform.isHudHidden(minecraft) != previousHudHidden) {
            ClientPlatform.setHudHidden(minecraft, previousHudHidden);
        }
    }

    private static void failCapture(UUID imageId) {
        if (imageId != null) {
            PlatformServices.sendToServer(new PhotoCaptureFailedPayload(imageId));
        }
    }

    private static void processAndSend(
            UUID imageId,
            List<BufferedImage> exposureFrames,
            CameraZoomManager.CaptureProfile profile
    ) {
        try {
            BufferedImage exposed = accumulateExposure(exposureFrames);
            exposed = applyExposureError(exposed, profile.exposure().exposureErrorEv());
            exposed = applyIsoNoise(exposed, profile.exposure().iso(), imageId);

            EncodedPhoto encodedPhoto = encodePngWithinLimit(exposed, ModConfig.MAX_PHOTO_BYTES.get());
            byte[] encoded = encodedPhoto.bytes();
            BufferedImage cachedPhoto = exposed;
            Minecraft.getInstance().execute(() -> ClientPhotoCache.cacheCapturedPhoto(imageId, cachedPhoto));

            for (int offset = 0; offset < encoded.length; offset += CHUNK_SIZE) {
                int end = Math.min(offset + CHUNK_SIZE, encoded.length);
                PlatformServices.sendToServer(new PhotoUploadChunkPayload(
                        imageId,
                        offset,
                        encoded.length,
                        encodedPhoto.width(),
                        encodedPhoto.height(),
                        Arrays.copyOfRange(encoded, offset, end)
                ));
            }
        } catch (Exception exception) {
            TheLostCamera.LOGGER.error("Failed to process captured photograph {}", imageId, exception);
            failCapture(imageId);
        }
    }

    /**
     * Sequential SRC_OVER weights of 1/(n+1) form an exact running average. This gives a true frame accumulation
     * without an expensive full-resolution manual pixel loop.
     */
    private static BufferedImage accumulateExposure(List<BufferedImage> frames) throws IOException {
        if (frames.isEmpty()) {
            throw new IOException("No exposure frames were captured");
        }
        if (frames.size() == 1) {
            return frames.getFirst();
        }

        BufferedImage first = frames.getFirst();
        BufferedImage result = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(first, 0, 0, null);

        for (int index = 1; index < frames.size(); index++) {
            BufferedImage frame = frames.get(index);
            if (frame.getWidth() != result.getWidth() || frame.getHeight() != result.getHeight()) {
                frame = resize(frame, result.getWidth(), result.getHeight());
            }
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0F / (index + 1.0F)));
            graphics.drawImage(frame, 0, 0, null);
        }
        graphics.dispose();
        return result;
    }

    private static BufferedImage applyExposureError(BufferedImage source, float errorEv) {
        if (Math.abs(errorEv) < 0.18F) {
            return source;
        }
        float scale = (float) Math.pow(2.0D, MthClamp(errorEv, -1.25F, 0.85F));
        RescaleOp operation = new RescaleOp(
                new float[]{scale, scale, scale, 1.0F},
                new float[]{0.0F, 0.0F, 0.0F, 0.0F},
                null
        );
        BufferedImage adjusted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        operation.filter(source, adjusted);
        return adjusted;
    }

    /** Adds subtle luminance/chroma variation only once ISO is high enough for it to matter. */
    private static BufferedImage applyIsoNoise(BufferedImage source, int iso, UUID imageId) {
        if (iso <= 640) {
            return source;
        }

        double stops = Math.log(iso / 640.0D) / Math.log(2.0D);
        int alpha = Math.min(24, 4 + (int) Math.round(stops * 5.0D));
        int tileSize = 256;
        BufferedImage noise = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(imageId.getMostSignificantBits() ^ imageId.getLeastSignificantBits());
        int[] pixels = new int[tileSize * tileSize];
        for (int index = 0; index < pixels.length; index++) {
            int base = random.nextInt(256);
            int chroma = alpha >= 14 ? random.nextInt(19) - 9 : 0;
            int red = clampChannel(base + chroma);
            int green = clampChannel(base);
            int blue = clampChannel(base - chroma);
            pixels[index] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        noise.setRGB(0, 0, tileSize, tileSize, pixels, 0, tileSize);

        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        for (int y = 0; y < source.getHeight(); y += tileSize) {
            for (int x = 0; x < source.getWidth(); x += tileSize) {
                graphics.drawImage(noise, x, y, null);
            }
        }
        graphics.dispose();
        return result;
    }


    /** Uses the same direct pixel conversion pattern as the reference 26.2 camera implementation. */
    private static BufferedImage fromNativeImage(NativeImage image) {
        int[] pixels = image.getPixels();
        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        result.setRGB(
                0,
                0,
                image.getWidth(),
                image.getHeight(),
                pixels,
                0,
                image.getWidth()
        );
        return result;
    }

    /** Crops the rendered frame around its centre so the saved photograph exactly matches a square sensor. */
    private static BufferedImage cropToSquare(BufferedImage source) {
        if (source.getWidth() == source.getHeight()) {
            return source;
        }

        int cropSize = Math.min(source.getWidth(), source.getHeight());
        int x = Math.max(0, (source.getWidth() - cropSize) / 2);
        int y = Math.max(0, (source.getHeight() - cropSize) / 2);
        BufferedImage cropped = new BufferedImage(cropSize, cropSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        graphics.drawImage(source, 0, 0, cropSize, cropSize, x, y, x + cropSize, y + cropSize, null);
        graphics.dispose();
        return cropped;
    }

    private static BufferedImage clampLongestEdge(BufferedImage source, int maximumDimension) {
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        if (longestEdge <= maximumDimension) {
            return source;
        }

        double scale = (double) maximumDimension / longestEdge;
        return resize(
                source,
                Math.max(1, (int) Math.round(source.getWidth() * scale)),
                Math.max(1, (int) Math.round(source.getHeight() * scale))
        );
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static EncodedPhoto encodePngWithinLimit(BufferedImage image, int maximumBytes) throws IOException {
        BufferedImage currentImage = image;

        while (true) {
            byte[] encoded = encodePng(currentImage);
            if (encoded.length <= maximumBytes) {
                TheLostCamera.LOGGER.debug(
                        "Photograph encoded losslessly at {}x{}, {} bytes",
                        currentImage.getWidth(),
                        currentImage.getHeight(),
                        encoded.length
                );
                return new EncodedPhoto(encoded, currentImage.getWidth(), currentImage.getHeight());
            }

            int reducedWidth = Math.max(MINIMUM_DIMENSION, (int) Math.round(currentImage.getWidth() * 0.95D));
            int reducedHeight = Math.max(MINIMUM_DIMENSION, (int) Math.round(currentImage.getHeight() * 0.95D));
            if (reducedWidth >= currentImage.getWidth() || reducedHeight >= currentImage.getHeight()) {
                throw new IOException("Lossless photograph exceeds configured size limit");
            }
            currentImage = resize(currentImage, reducedWidth, reducedHeight);
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ImageIO.setUseCache(false);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("No PNG writer available");
            }
            return output.toByteArray();
        }
    }

    private static float MthClamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record EncodedPhoto(byte[] bytes, int width, int height) {
    }
}



