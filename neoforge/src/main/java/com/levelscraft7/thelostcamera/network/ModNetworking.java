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

        registrar.playToClient(CaptureRequestPayload.TYPE, CaptureRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleCapture(payload)));
        registrar.playToClient(PhotoImageChunkPayload.TYPE, PhotoImageChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleImageChunk(payload)));
        registrar.playToClient(PhotoUnavailablePayload.TYPE, PhotoUnavailablePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleUnavailable(payload)));
        registrar.playToClient(RestorationShakePayload.TYPE, RestorationShakePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleShake(payload)));
        registrar.playToClient(OpenAlbumPayload.TYPE, OpenAlbumPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleOpenAlbum(payload)));
        registrar.playToClient(OpenDarkroomPayload.TYPE, OpenDarkroomPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleOpenDarkroom(payload)));
        registrar.playToClient(AlbumSnapshotPayload.TYPE, AlbumSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleAlbumSnapshot(payload)));

        registrar.playToServer(CaptureReadyPayload.TYPE, CaptureReadyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleCaptureReady(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(PhotoUploadChunkPayload.TYPE, PhotoUploadChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handlePhotoUpload(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(PhotoCaptureFailedPayload.TYPE, PhotoCaptureFailedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleCaptureFailure(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(PhotoImageRequestPayload.TYPE, PhotoImageRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handlePhotoRequest(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(ArchivePhotoPayload.TYPE, ArchivePhotoPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleArchivePhoto(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(RequestAlbumPayload.TYPE, RequestAlbumPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleAlbumRequest(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(SetPhotoArchivedPayload.TYPE, SetPhotoArchivedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleSetArchived(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(SetPhotoFavoritePayload.TYPE, SetPhotoFavoritePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleSetFavorite(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(CameraFocalPayload.TYPE, CameraFocalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleCameraFocal(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(LowerCameraPayload.TYPE, LowerCameraPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleLowerCamera(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToServer(DevelopPrintPayload.TYPE, DevelopPrintPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ServerPayloadHandlers.handleDevelopPrint(payload, (net.minecraft.server.level.ServerPlayer) context.player())));
    }
}

