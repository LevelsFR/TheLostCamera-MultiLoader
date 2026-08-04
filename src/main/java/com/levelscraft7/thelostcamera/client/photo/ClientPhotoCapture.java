package com.levelscraft7.thelostcamera.client.photo;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.config.ModConfig;
import com.levelscraft7.thelostcamera.network.payload.PhotoCaptureFailedPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUploadChunkPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Captures the exact rendered frame, without a colour filter or post-processing effect. */
public final class ClientPhotoCapture {
    private static final int CHUNK_SIZE = 28_000;
    private static final long CLIENT_CAPTURE_TIMEOUT_MILLIS = 15_000L;
    private static final int MINIMUM_WIDTH = 640;
    private static final int MINIMUM_HEIGHT = 480;

    private static int screenshotDelay = -1;
    private static UUID pendingImageId;
    private static boolean previousHudHidden;
    private static long captureStartedAtMillis;
    private static Object lastLevel;
    private static UUID lastPlayerId;

    private ClientPhotoCapture() {
    }

    public static void request(UUID imageId) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshSession(minecraft);

        if (minecraft.level == null || minecraft.player == null) {
            failCapture(imageId);
            return;
        }

        if (screenshotDelay >= 0) {
            if (!Objects.equals(pendingImageId, imageId)) {
                failCapture(imageId);
            }
            return;
        }

        previousHudHidden = minecraft.gui.hud.isHidden();
        if (!previousHudHidden) {
            minecraft.gui.hud.toggle();
        }

        pendingImageId = imageId;
        captureStartedAtMillis = System.currentTimeMillis();
        screenshotDelay = 2;
        minecraft.setScreenAndShow(null);
    }

    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshSession(minecraft);

        if (screenshotDelay < 0) {
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

        if (screenshotDelay > 0) {
            screenshotDelay--;
            return;
        }

        UUID imageId = pendingImageId;
        screenshotDelay = -1;
        pendingImageId = null;
        captureStartedAtMillis = 0L;

        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            restoreHud(minecraft);
            Thread processor = new Thread(() -> processAndSend(imageId, image), "TheLostCamera-PhotoProcessor");
            processor.setDaemon(true);
            processor.start();
        });
    }

    public static boolean isCapturing() {
        return screenshotDelay >= 0;
    }

    public static void cancelPending() {
        if (screenshotDelay < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UUID canceledImage = pendingImageId;
        reset(minecraft);
        failCapture(canceledImage);
    }

    private static void refreshSession(Minecraft minecraft) {
        Object currentLevel = minecraft.level;
        UUID currentPlayerId = minecraft.player == null ? null : minecraft.player.getUUID();
        if (currentLevel == lastLevel && Objects.equals(currentPlayerId, lastPlayerId)) {
            return;
        }

        if (screenshotDelay >= 0) {
            UUID abandonedImage = pendingImageId;
            reset(minecraft);
            failCapture(abandonedImage);
        }
        lastLevel = currentLevel;
        lastPlayerId = currentPlayerId;
    }

    private static void reset(Minecraft minecraft) {
        restoreHud(minecraft);
        screenshotDelay = -1;
        pendingImageId = null;
        captureStartedAtMillis = 0L;
    }

    private static void restoreHud(Minecraft minecraft) {
        if (minecraft.gui != null && minecraft.gui.hud.isHidden() != previousHudHidden) {
            minecraft.gui.hud.toggle();
        }
    }

    private static void failCapture(UUID imageId) {
        if (imageId != null) {
            ClientPacketDistributor.sendToServer(new PhotoCaptureFailedPayload(imageId));
        }
    }

    private static void processAndSend(UUID imageId, NativeImage nativeImage) {
        try (nativeImage) {
            BufferedImage source = fromNativeImage(nativeImage);
            BufferedImage cropped = cropToFourThirds(source);
            BufferedImage prepared = clampLongestEdge(cropped, ModConfig.PHOTO_MAX_DIMENSION.get());
            EncodedPhoto encodedPhoto = encodePngWithinLimit(prepared, ModConfig.MAX_PHOTO_BYTES.get());
            byte[] encoded = encodedPhoto.bytes();

            for (int offset = 0; offset < encoded.length; offset += CHUNK_SIZE) {
                int end = Math.min(offset + CHUNK_SIZE, encoded.length);
                ClientPacketDistributor.sendToServer(new PhotoUploadChunkPayload(
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


    /** Crops the rendered frame around its centre so the saved photograph exactly matches a 4:3 sensor. */
    private static BufferedImage cropToFourThirds(BufferedImage source) {
        final double targetRatio = 4.0D / 3.0D;
        double sourceRatio = (double) source.getWidth() / source.getHeight();

        if (Math.abs(sourceRatio - targetRatio) < 0.0001D) {
            return source;
        }

        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if (sourceRatio > targetRatio) {
            cropWidth = Math.max(1, (int) Math.round(source.getHeight() * targetRatio));
        } else {
            cropHeight = Math.max(1, (int) Math.round(source.getWidth() / targetRatio));
        }

        int x = Math.max(0, (source.getWidth() - cropWidth) / 2);
        int y = Math.max(0, (source.getHeight() - cropHeight) / 2);
        BufferedImage cropped = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        graphics.drawImage(source, 0, 0, cropWidth, cropHeight, x, y, x + cropWidth, y + cropHeight, null);
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

            int reducedWidth = Math.max(MINIMUM_WIDTH, (int) Math.round(currentImage.getWidth() * 0.90D));
            int reducedHeight = Math.max(MINIMUM_HEIGHT, (int) Math.round(currentImage.getHeight() * 0.90D));
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

    private record EncodedPhoto(byte[] bytes, int width, int height) {
    }
}
