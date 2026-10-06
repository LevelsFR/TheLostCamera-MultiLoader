package com.levelscraft7.thelostcamera.network;

import com.levelscraft7.thelostcamera.client.network.ClientPayloadHandlers;
import com.levelscraft7.thelostcamera.network.payload.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Fabric payload registration and receiver adapter. */
public final class FabricNetworking {
    private FabricNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry<net.minecraft.network.RegistryFriendlyByteBuf> clientbound = PayloadTypeRegistry.clientboundPlay();
        PayloadTypeRegistry<net.minecraft.network.RegistryFriendlyByteBuf> serverbound = PayloadTypeRegistry.serverboundPlay();
        clientbound.register(CaptureRequestPayload.TYPE, CaptureRequestPayload.STREAM_CODEC);
        clientbound.register(PhotoImageChunkPayload.TYPE, PhotoImageChunkPayload.STREAM_CODEC);
        clientbound.register(PhotoUnavailablePayload.TYPE, PhotoUnavailablePayload.STREAM_CODEC);
        clientbound.register(RestorationShakePayload.TYPE, RestorationShakePayload.STREAM_CODEC);
        clientbound.register(OpenAlbumPayload.TYPE, OpenAlbumPayload.STREAM_CODEC);
        clientbound.register(OpenDarkroomPayload.TYPE, OpenDarkroomPayload.STREAM_CODEC);
        clientbound.register(AlbumSnapshotPayload.TYPE, AlbumSnapshotPayload.STREAM_CODEC);
        serverbound.register(CaptureReadyPayload.TYPE, CaptureReadyPayload.STREAM_CODEC);
        serverbound.register(PhotoUploadChunkPayload.TYPE, PhotoUploadChunkPayload.STREAM_CODEC);
        serverbound.register(PhotoCaptureFailedPayload.TYPE, PhotoCaptureFailedPayload.STREAM_CODEC);
        serverbound.register(PhotoImageRequestPayload.TYPE, PhotoImageRequestPayload.STREAM_CODEC);
        serverbound.register(ArchivePhotoPayload.TYPE, ArchivePhotoPayload.STREAM_CODEC);
        serverbound.register(RequestAlbumPayload.TYPE, RequestAlbumPayload.STREAM_CODEC);
        serverbound.register(SetPhotoArchivedPayload.TYPE, SetPhotoArchivedPayload.STREAM_CODEC);
        serverbound.register(SetPhotoFavoritePayload.TYPE, SetPhotoFavoritePayload.STREAM_CODEC);
        serverbound.register(CameraFocalPayload.TYPE, CameraFocalPayload.STREAM_CODEC);
        serverbound.register(LowerCameraPayload.TYPE, LowerCameraPayload.STREAM_CODEC);
        serverbound.register(DevelopPrintPayload.TYPE, DevelopPrintPayload.STREAM_CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CaptureReadyPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleCaptureReady(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PhotoUploadChunkPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handlePhotoUpload(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PhotoCaptureFailedPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleCaptureFailure(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PhotoImageRequestPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handlePhotoRequest(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ArchivePhotoPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleArchivePhoto(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(RequestAlbumPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleAlbumRequest(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(SetPhotoArchivedPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleSetArchived(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(SetPhotoFavoritePayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleSetFavorite(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(CameraFocalPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleCameraFocal(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(LowerCameraPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleLowerCamera(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(DevelopPrintPayload.TYPE,
                (payload, context) -> context.server().execute(() -> ServerPayloadHandlers.handleDevelopPrint(payload, context.player())));
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(CaptureRequestPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleCapture(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoImageChunkPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleImageChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoUnavailablePayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleUnavailable(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RestorationShakePayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleShake(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OpenAlbumPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleOpenAlbum(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OpenDarkroomPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleOpenDarkroom(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AlbumSnapshotPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPayloadBridge.handleAlbumSnapshot(payload)));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
