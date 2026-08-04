package com.levelscraft7.thelostcamera.network;

import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.CaptureRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUnavailablePayload;
import com.levelscraft7.thelostcamera.network.payload.RestorationShakePayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Common-side indirection which keeps client classes out of dedicated-server class loading. */
public final class ClientPayloadBridge {
    private static BiConsumer<CaptureRequestPayload, IPayloadContext> captureHandler = (payload, context) -> { };
    private static BiConsumer<PhotoImageChunkPayload, IPayloadContext> imageChunkHandler = (payload, context) -> { };
    private static BiConsumer<PhotoUnavailablePayload, IPayloadContext> unavailableHandler = (payload, context) -> { };
    private static BiConsumer<RestorationShakePayload, IPayloadContext> shakeHandler = (payload, context) -> { };
    private static BiConsumer<OpenAlbumPayload, IPayloadContext> openAlbumHandler = (payload, context) -> { };
    private static BiConsumer<AlbumSnapshotPayload, IPayloadContext> albumSnapshotHandler = (payload, context) -> { };

    private ClientPayloadBridge() {
    }

    public static void install(
            BiConsumer<CaptureRequestPayload, IPayloadContext> capture,
            BiConsumer<PhotoImageChunkPayload, IPayloadContext> imageChunk,
            BiConsumer<PhotoUnavailablePayload, IPayloadContext> unavailable,
            BiConsumer<RestorationShakePayload, IPayloadContext> shake,
            BiConsumer<OpenAlbumPayload, IPayloadContext> openAlbum,
            BiConsumer<AlbumSnapshotPayload, IPayloadContext> albumSnapshot
    ) {
        captureHandler = Objects.requireNonNull(capture);
        imageChunkHandler = Objects.requireNonNull(imageChunk);
        unavailableHandler = Objects.requireNonNull(unavailable);
        shakeHandler = Objects.requireNonNull(shake);
        openAlbumHandler = Objects.requireNonNull(openAlbum);
        albumSnapshotHandler = Objects.requireNonNull(albumSnapshot);
    }

    public static void handleCapture(CaptureRequestPayload payload, IPayloadContext context) { captureHandler.accept(payload, context); }
    public static void handleImageChunk(PhotoImageChunkPayload payload, IPayloadContext context) { imageChunkHandler.accept(payload, context); }
    public static void handleUnavailable(PhotoUnavailablePayload payload, IPayloadContext context) { unavailableHandler.accept(payload, context); }
    public static void handleShake(RestorationShakePayload payload, IPayloadContext context) { shakeHandler.accept(payload, context); }
    public static void handleOpenAlbum(OpenAlbumPayload payload, IPayloadContext context) { openAlbumHandler.accept(payload, context); }
    public static void handleAlbumSnapshot(AlbumSnapshotPayload payload, IPayloadContext context) { albumSnapshotHandler.accept(payload, context); }
}
