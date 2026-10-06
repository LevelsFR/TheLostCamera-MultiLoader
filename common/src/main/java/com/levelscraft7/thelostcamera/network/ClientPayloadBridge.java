package com.levelscraft7.thelostcamera.network;

import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.CaptureRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenDarkroomPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUnavailablePayload;
import com.levelscraft7.thelostcamera.network.payload.RestorationShakePayload;

import java.util.Objects;
import java.util.function.Consumer;

/** Keeps client-only handlers out of dedicated-server class loading. */
public final class ClientPayloadBridge {
    private static Consumer<CaptureRequestPayload> captureHandler = payload -> { };
    private static Consumer<PhotoImageChunkPayload> imageChunkHandler = payload -> { };
    private static Consumer<PhotoUnavailablePayload> unavailableHandler = payload -> { };
    private static Consumer<RestorationShakePayload> shakeHandler = payload -> { };
    private static Consumer<OpenAlbumPayload> openAlbumHandler = payload -> { };
    private static Consumer<OpenDarkroomPayload> openDarkroomHandler = payload -> { };
    private static Consumer<AlbumSnapshotPayload> albumSnapshotHandler = payload -> { };

    private ClientPayloadBridge() {
    }

    public static void install(
            Consumer<CaptureRequestPayload> capture,
            Consumer<PhotoImageChunkPayload> imageChunk,
            Consumer<PhotoUnavailablePayload> unavailable,
            Consumer<RestorationShakePayload> shake,
            Consumer<OpenAlbumPayload> openAlbum,
            Consumer<OpenDarkroomPayload> openDarkroom,
            Consumer<AlbumSnapshotPayload> albumSnapshot
    ) {
        captureHandler = Objects.requireNonNull(capture);
        imageChunkHandler = Objects.requireNonNull(imageChunk);
        unavailableHandler = Objects.requireNonNull(unavailable);
        shakeHandler = Objects.requireNonNull(shake);
        openAlbumHandler = Objects.requireNonNull(openAlbum);
        openDarkroomHandler = Objects.requireNonNull(openDarkroom);
        albumSnapshotHandler = Objects.requireNonNull(albumSnapshot);
    }

    public static void handleCapture(CaptureRequestPayload payload) { captureHandler.accept(payload); }
    public static void handleImageChunk(PhotoImageChunkPayload payload) { imageChunkHandler.accept(payload); }
    public static void handleUnavailable(PhotoUnavailablePayload payload) { unavailableHandler.accept(payload); }
    public static void handleShake(RestorationShakePayload payload) { shakeHandler.accept(payload); }
    public static void handleOpenAlbum(OpenAlbumPayload payload) { openAlbumHandler.accept(payload); }
    public static void handleOpenDarkroom(OpenDarkroomPayload payload) { openDarkroomHandler.accept(payload); }
    public static void handleAlbumSnapshot(AlbumSnapshotPayload payload) { albumSnapshotHandler.accept(payload); }
}

