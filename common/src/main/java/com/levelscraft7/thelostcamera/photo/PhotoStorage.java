package com.levelscraft7.thelostcamera.photo;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUnavailablePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

public final class PhotoStorage {
    private static final LevelResource PHOTO_DIRECTORY = new LevelResource("thelostcamera/photos");
    private static final int NETWORK_CHUNK_SIZE = 28_000;
    private static final int THUMBNAIL_SIZE = 640;

    private PhotoStorage() {
    }

    public static void save(ServerPlayer player, UUID imageId, byte[] bytes) throws IOException {
        Path directory = player.level().getServer().getWorldPath(PHOTO_DIRECTORY);
        Files.createDirectories(directory);
        Files.write(pngFile(directory, imageId), bytes);
        try {
            Files.write(thumbnailFile(directory, imageId), createThumbnail(bytes));
        } catch (IOException thumbnailFailure) {
            TheLostCamera.LOGGER.warn("Photograph {} was saved, but its album thumbnail could not be generated", imageId,
                    thumbnailFailure);
        }
    }

    public static byte[] load(ServerPlayer player, UUID imageId) throws IOException {
        Path directory = player.level().getServer().getWorldPath(PHOTO_DIRECTORY);
        Path png = pngFile(directory, imageId);
        if (Files.isRegularFile(png)) {
            return Files.readAllBytes(png);
        }

        // Supports photographs created before lossless PNG storage was introduced.
        Path legacyJpeg = legacyJpegFile(directory, imageId);
        if (Files.isRegularFile(legacyJpeg)) {
            return Files.readAllBytes(legacyJpeg);
        }
        return null;
    }

    public static byte[] loadThumbnail(ServerPlayer player, UUID imageId) throws IOException {
        Path directory = player.level().getServer().getWorldPath(PHOTO_DIRECTORY);
        Path thumbnail = thumbnailFile(directory, imageId);
        if (Files.isRegularFile(thumbnail)) {
            return Files.readAllBytes(thumbnail);
        }

        byte[] original = load(player, imageId);
        if (original == null) {
            return null;
        }
        byte[] generated = createThumbnail(original);
        Files.createDirectories(directory);
        Files.write(thumbnail, generated);
        return generated;
    }

    public static boolean validatePng(byte[] bytes, int expectedWidth, int expectedHeight) {
        if (bytes.length < 33 || expectedWidth <= 0 || expectedHeight <= 0) {
            return false;
        }

        // Validate the IHDR dimensions before ImageIO allocates the decoded image.
        ByteBuffer header = ByteBuffer.wrap(bytes, 16, 8).order(ByteOrder.BIG_ENDIAN);
        int declaredWidth = header.getInt();
        int declaredHeight = header.getInt();
        long declaredPixels = (long) declaredWidth * declaredHeight;
        if (declaredWidth != expectedWidth
                || declaredHeight != expectedHeight
                || declaredWidth > 8_192
                || declaredHeight > 8_192
                || declaredPixels <= 0L
                || declaredPixels > 67_108_864L
                || Math.abs((double) declaredWidth / declaredHeight - 1.0D) >= 0.02D) {
            return false;
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            return image != null
                    && image.getWidth() == declaredWidth
                    && image.getHeight() == declaredHeight;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public static void sendToClient(ServerPlayer player, UUID imageId, boolean thumbnail) {
        try {
            byte[] image = thumbnail ? loadThumbnail(player, imageId) : load(player, imageId);
            if (image == null) {
                PlatformServices.sendToPlayer(player, new PhotoUnavailablePayload(imageId));
                return;
            }

            for (int offset = 0; offset < image.length; offset += NETWORK_CHUNK_SIZE) {
                int end = Math.min(offset + NETWORK_CHUNK_SIZE, image.length);
                PlatformServices.sendToPlayer(
                        player, new PhotoImageChunkPayload(
                                imageId,
                                offset,
                                image.length,
                                Arrays.copyOfRange(image, offset, end)
                        )
                );
            }
        } catch (IOException exception) {
            TheLostCamera.LOGGER.error("Failed to load photograph {}", imageId, exception);
            PlatformServices.sendToPlayer(player, new PhotoUnavailablePayload(imageId));
        }
    }

    private static byte[] createThumbnail(byte[] original) throws IOException {
        BufferedImage source;
        try (ByteArrayInputStream input = new ByteArrayInputStream(original)) {
            source = ImageIO.read(input);
        }
        if (source == null) {
            throw new IOException("Unsupported photograph image");
        }

        int cropSize = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - cropSize) / 2;
        int cropY = (source.getHeight() - cropSize) / 2;
        BufferedImage thumbnail = new BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    cropX, cropY, cropX + cropSize, cropY + cropSize, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(thumbnail, "png", output)) {
                throw new IOException("No PNG writer available for photograph thumbnail");
            }
            return output.toByteArray();
        }
    }

    private static Path pngFile(Path directory, UUID imageId) {
        return directory.resolve(imageId + ".png");
    }

    private static Path legacyJpegFile(Path directory, UUID imageId) {
        return directory.resolve(imageId + ".jpg");
    }

    private static Path thumbnailFile(Path directory, UUID imageId) {
        return directory.resolve(imageId + ".thumb.png");
    }
}



