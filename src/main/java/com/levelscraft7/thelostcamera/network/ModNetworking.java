package com.levelscraft7.thelostcamera.network;

import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.ArchivePhotoPayload;
import com.levelscraft7.thelostcamera.network.payload.CameraFocalPayload;
import com.levelscraft7.thelostcamera.network.payload.DevelopPrintPayload;
import com.levelscraft7.thelostcamera.network.payload.CaptureRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.CaptureReadyPayload;
import com.levelscraft7.thelostcamera.network.payload.LowerCameraPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenDarkroomPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoCaptureFailedPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUnavailablePayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUploadChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.RequestAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.RestorationShakePayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoArchivedPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoFavoritePayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String NETWORK_VERSION = "10";

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);

        registrar.playToClient(CaptureRequestPayload.TYPE, CaptureRequestPayload.STREAM_CODEC, ClientPayloadBridge::handleCapture);
        registrar.playToClient(PhotoImageChunkPayload.TYPE, PhotoImageChunkPayload.STREAM_CODEC, ClientPayloadBridge::handleImageChunk);
        registrar.playToClient(PhotoUnavailablePayload.TYPE, PhotoUnavailablePayload.STREAM_CODEC, ClientPayloadBridge::handleUnavailable);
        registrar.playToClient(RestorationShakePayload.TYPE, RestorationShakePayload.STREAM_CODEC, ClientPayloadBridge::handleShake);
        registrar.playToClient(OpenAlbumPayload.TYPE, OpenAlbumPayload.STREAM_CODEC, ClientPayloadBridge::handleOpenAlbum);
        registrar.playToClient(OpenDarkroomPayload.TYPE, OpenDarkroomPayload.STREAM_CODEC, ClientPayloadBridge::handleOpenDarkroom);
        registrar.playToClient(AlbumSnapshotPayload.TYPE, AlbumSnapshotPayload.STREAM_CODEC, ClientPayloadBridge::handleAlbumSnapshot);

        registrar.playToServer(CaptureReadyPayload.TYPE, CaptureReadyPayload.STREAM_CODEC, ServerPayloadHandlers::handleCaptureReady);
        registrar.playToServer(PhotoUploadChunkPayload.TYPE, PhotoUploadChunkPayload.STREAM_CODEC, ServerPayloadHandlers::handlePhotoUpload);
        registrar.playToServer(PhotoCaptureFailedPayload.TYPE, PhotoCaptureFailedPayload.STREAM_CODEC, ServerPayloadHandlers::handleCaptureFailure);
        registrar.playToServer(PhotoImageRequestPayload.TYPE, PhotoImageRequestPayload.STREAM_CODEC, ServerPayloadHandlers::handlePhotoRequest);
        registrar.playToServer(ArchivePhotoPayload.TYPE, ArchivePhotoPayload.STREAM_CODEC, ServerPayloadHandlers::handleArchivePhoto);
        registrar.playToServer(RequestAlbumPayload.TYPE, RequestAlbumPayload.STREAM_CODEC, ServerPayloadHandlers::handleAlbumRequest);
        registrar.playToServer(SetPhotoArchivedPayload.TYPE, SetPhotoArchivedPayload.STREAM_CODEC, ServerPayloadHandlers::handleSetArchived);
        registrar.playToServer(SetPhotoFavoritePayload.TYPE, SetPhotoFavoritePayload.STREAM_CODEC, ServerPayloadHandlers::handleSetFavorite);
        registrar.playToServer(CameraFocalPayload.TYPE, CameraFocalPayload.STREAM_CODEC, ServerPayloadHandlers::handleCameraFocal);
        registrar.playToServer(LowerCameraPayload.TYPE, LowerCameraPayload.STREAM_CODEC, ServerPayloadHandlers::handleLowerCamera);
        registrar.playToServer(DevelopPrintPayload.TYPE, DevelopPrintPayload.STREAM_CODEC, ServerPayloadHandlers::handleDevelopPrint);
    }
}
