package com.levelscraft7.thelostcamera.client.photo;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.album.StoredPhoto;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageRequestPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client photo texture cache.
 * Album grids request compact square thumbnails, while the full PNG is only decoded when a photograph is opened.
 */
public final class ClientPhotoCache {
    private static final int THUMBNAIL_SIZE = 320;

    private static final Map<UUID, DynamicTexture> FULL_TEXTURES = new HashMap<>();
    private static final Map<UUID, Identifier> FULL_IDENTIFIERS = new HashMap<>();
    private static final Map<UUID, DynamicTexture> THUMBNAIL_TEXTURES = new HashMap<>();
    private static final Map<UUID, Identifier> THUMBNAIL_IDENTIFIERS = new HashMap<>();
    private static final Map<UUID, Download> DOWNLOADS = new HashMap<>();
    private static final Map<UUID, Long> REQUESTED_AT = new HashMap<>();
    private static final Map<UUID, RequestMode> REQUEST_MODES = new HashMap<>();
    private static final Set<UUID> UNAVAILABLE = new HashSet<>();

    private ClientPhotoCache() {
    }

    public static void initialize() {
        // Forces safe client-only class loading during client setup.
    }

    public static Identifier getTexture(UUID imageId) {
        return getOrRequest(imageId, RequestMode.FULL);
    }

    public static Identifier getThumbnailTexture(UUID imageId) {
        return getOrRequest(imageId, RequestMode.THUMBNAIL);
    }

    public static NativeImage getNativeImage(UUID imageId) {
        DynamicTexture texture = FULL_TEXTURES.get(imageId);
        return texture == null ? null : texture.getPixels();
    }

    public static NativeImage getThumbnailNativeImage(UUID imageId) {
        DynamicTexture texture = THUMBNAIL_TEXTURES.get(imageId);
        return texture == null ? null : texture.getPixels();
    }

    /** Starts thumbnail downloads as soon as album metadata arrives, before the first album frame is rendered. */
    public static void preloadThumbnails(List<StoredPhoto> photos) {
        for (StoredPhoto photo : photos) {
            getOrRequest(photo.data().imageId(), RequestMode.THUMBNAIL);
        }
    }

    /** Reuses the client-side capture so a newly taken photograph is already present when the album opens. */
    public static void cacheCapturedPhoto(UUID imageId, BufferedImage image) {
        registerThumbnailImageIfMissing(imageId, image);
        UNAVAILABLE.remove(imageId);
    }

    private static Identifier getOrRequest(UUID imageId, RequestMode requestedMode) {
        Identifier present = requestedMode == RequestMode.FULL
                ? FULL_IDENTIFIERS.get(imageId)
                : THUMBNAIL_IDENTIFIERS.get(imageId);
        if (present != null || UNAVAILABLE.contains(imageId)) {
            return present;
        }

        RequestMode currentMode = REQUEST_MODES.get(imageId);
        if (currentMode == null) {
            REQUEST_MODES.put(imageId, requestedMode);
        }

        long now = System.currentTimeMillis();
        long lastRequest = REQUESTED_AT.getOrDefault(imageId, 0L);
        if (now - lastRequest > 10_000L) {
            REQUESTED_AT.put(imageId, now);
            PlatformServices.sendToServer(new PhotoImageRequestPayload(imageId, requestedMode == RequestMode.THUMBNAIL));
        }
        return null;
    }

    public static void acceptChunk(UUID imageId, int offset, int totalLength, byte[] bytes) {
        if (totalLength <= 0 || totalLength > 32_000_000 || bytes.length == 0) {
            return;
        }

        Download download = DOWNLOADS.computeIfAbsent(imageId, ignored -> new Download(totalLength));
        if (!download.accept(offset, totalLength, bytes)) {
            DOWNLOADS.remove(imageId);
            REQUESTED_AT.remove(imageId);
            REQUEST_MODES.remove(imageId);
            return;
        }

        if (!download.complete()) {
            return;
        }

        DOWNLOADS.remove(imageId);
        REQUESTED_AT.remove(imageId);
        RequestMode mode = REQUEST_MODES.remove(imageId);
        decodeAndRegister(imageId, download.bytes, mode == null ? RequestMode.THUMBNAIL : mode);
    }

    public static void markUnavailable(UUID imageId) {
        DOWNLOADS.remove(imageId);
        REQUESTED_AT.remove(imageId);
        REQUEST_MODES.remove(imageId);
        UNAVAILABLE.add(imageId);
    }

    public static void clearThumbnails() {
        closeAll(THUMBNAIL_TEXTURES);
        THUMBNAIL_IDENTIFIERS.clear();
    }

    public static void clearFullResolutionTextures() {
        closeAll(FULL_TEXTURES);
        FULL_IDENTIFIERS.clear();
        for (Map.Entry<UUID, RequestMode> entry : List.copyOf(REQUEST_MODES.entrySet())) {
            if (entry.getValue() == RequestMode.FULL) {
                UUID imageId = entry.getKey();
                REQUEST_MODES.remove(imageId);
                REQUESTED_AT.remove(imageId);
                DOWNLOADS.remove(imageId);
            }
        }
    }

    public static void clear() {
        clearFullResolutionTextures();
        clearThumbnails();
        DOWNLOADS.clear();
        REQUESTED_AT.clear();
        REQUEST_MODES.clear();
        UNAVAILABLE.clear();
    }

    private static void decodeAndRegister(UUID imageId, byte[] bytes, RequestMode mode) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage buffered = ImageIO.read(input);
            if (buffered == null) {
                throw new IOException("Unsupported photograph image");
            }

            if (mode == RequestMode.THUMBNAIL) {
                registerThumbnailImageIfMissing(imageId, buffered);
            } else {
                registerThumbnailIfMissing(imageId, buffered);
                registerFullIfMissing(imageId, buffered);
            }
            UNAVAILABLE.remove(imageId);
        } catch (IOException exception) {
            TheLostCamera.LOGGER.error("Failed to decode photograph {}", imageId, exception);
            UNAVAILABLE.add(imageId);
        }
    }

    private static void registerFullIfMissing(UUID imageId, BufferedImage buffered) {
        if (FULL_IDENTIFIERS.containsKey(imageId)) {
            return;
        }
        NativeImage nativeImage = toNativeImage(buffered);
        Identifier identifier = Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "dynamic_photo/" + imageId);
        DynamicTexture texture = new DynamicTexture(identifier::toString, nativeImage);
        Minecraft.getInstance().getEntityRenderDispatcher().textureManager.register(identifier, texture);
        FULL_TEXTURES.put(imageId, texture);
        FULL_IDENTIFIERS.put(imageId, identifier);
    }

    private static void registerThumbnailImageIfMissing(UUID imageId, BufferedImage source) {
        if (THUMBNAIL_IDENTIFIERS.containsKey(imageId)) {
            return;
        }
        BufferedImage thumbnail = createSquareThumbnail(source);
        NativeImage nativeImage = toNativeImage(thumbnail);
        Identifier identifier = Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "dynamic_photo_thumbnail/" + imageId);
        DynamicTexture texture = new DynamicTexture(identifier::toString, nativeImage);
        Minecraft.getInstance().getEntityRenderDispatcher().textureManager.register(identifier, texture);
        THUMBNAIL_TEXTURES.put(imageId, texture);
        THUMBNAIL_IDENTIFIERS.put(imageId, identifier);
    }

    private static void registerThumbnailIfMissing(UUID imageId, BufferedImage source) {
        if (THUMBNAIL_IDENTIFIERS.containsKey(imageId)) {
            return;
        }

        registerThumbnailImageIfMissing(imageId, source);
    }

    private static BufferedImage createSquareThumbnail(BufferedImage source) {
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
        return thumbnail;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage result = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                result.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return result;
    }

    private static void closeAll(Map<UUID, DynamicTexture> textures) {
        for (DynamicTexture texture : textures.values()) {
            texture.close();
        }
        textures.clear();
    }

    private enum RequestMode {
        THUMBNAIL,
        FULL
    }

    private static final class Download {
        private final byte[] bytes;
        private int nextOffset;

        private Download(int totalLength) {
            this.bytes = new byte[totalLength];
        }

        private boolean accept(int offset, int totalLength, byte[] chunk) {
            if (bytes.length != totalLength || offset != nextOffset || offset + chunk.length > bytes.length) {
                return false;
            }
            System.arraycopy(chunk, 0, bytes, offset, chunk.length);
            nextOffset += chunk.length;
            return true;
        }

        private boolean complete() {
            return nextOffset == bytes.length;
        }
    }
}



