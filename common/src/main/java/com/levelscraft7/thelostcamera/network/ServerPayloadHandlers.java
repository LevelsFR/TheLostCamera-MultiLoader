package com.levelscraft7.thelostcamera.network;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.item.PhotoAlbumItem;
import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.ArchivePhotoPayload;
import com.levelscraft7.thelostcamera.network.payload.CameraFocalPayload;
import com.levelscraft7.thelostcamera.network.payload.CaptureReadyPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoCaptureFailedPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUploadChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.RequestAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoArchivedPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoFavoritePayload;
import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.photo.PhotoDevelopmentManager;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {
    }

    public static void handleDevelopPrint(com.levelscraft7.thelostcamera.network.payload.DevelopPrintPayload payload, ServerPlayer player) {
        if (player != null) {
            PhotoDevelopmentManager.request(player, payload.imageId(), payload.darkroomPos());
        }
    }

    public static void handleCaptureReady(CaptureReadyPayload payload, ServerPlayer player) {
        if (player != null) {
            PhotoCaptureManager.confirmCapturedFrame(
                    player,
                    payload.imageId(),
                    new PhotoData.CameraSettings(
                            payload.focalLengthMm(),
                            payload.apertureTenths(),
                            payload.shutterDenominator(),
                            payload.iso()
                    )
            );
        }
    }

    public static void handlePhotoUpload(PhotoUploadChunkPayload payload, ServerPlayer player) {
        if (player != null) {
            PhotoCaptureManager.acceptChunk(player, payload.imageId(), payload.offset(), payload.totalLength(),
                    payload.imageWidth(), payload.imageHeight(), payload.bytes());
        }
    }

    public static void handleCaptureFailure(PhotoCaptureFailedPayload payload, ServerPlayer player) {
        if (player != null) {
            PhotoCaptureManager.cancelCapture(player, payload.imageId());
        }
    }

    public static void handlePhotoRequest(PhotoImageRequestPayload payload, ServerPlayer player) {
        if (player != null) {
            PhotoCaptureManager.requestPhoto(player, payload.imageId(), payload.thumbnail());
        }
    }

    public static void handleAlbumRequest(RequestAlbumPayload payload, ServerPlayer player) {
        if (validAlbum(player, payload.mainHand())) {
            sendSnapshot(player);
        }
    }

    public static void handleArchivePhoto(ArchivePhotoPayload payload, ServerPlayer player) {
        if (player == null || !PhotoAlbumItem.hasOwnedAlbum(player)) {
            return;
        }
        int slot = payload.inventorySlot();
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return;
        }
        ItemStack photograph = player.getInventory().getItem(slot);
        if (photograph.getItem() != ModItems.PHOTOGRAPH.get()) {
            return;
        }
        PhotoData data = photograph.get(ModDataComponents.PHOTO_DATA.get());
        com.levelscraft7.thelostcamera.data.RuinPhotoData ruinPhoto = photograph.get(ModDataComponents.RUIN_PHOTO_DATA.get());
        if (data == null || !PlayerAlbumStorage.add(player, data, ruinPhoto)) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.photo_already_archived"));
            return;
        }
        photograph.shrink(1);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.sendOverlayMessage(Component.translatable("message.thelostcamera.photo_archived"));
        sendSnapshot(player);
    }

    public static void handleSetArchived(SetPhotoArchivedPayload payload, ServerPlayer player) {
        if (PhotoAlbumItem.hasOwnedAlbum(player)
                && PlayerAlbumStorage.setArchived(player, payload.imageId(), payload.archived())) {
            sendSnapshot(player);
        }
    }

    public static void handleSetFavorite(SetPhotoFavoritePayload payload, ServerPlayer player) {
        if (PhotoAlbumItem.hasOwnedAlbum(player)
                && PlayerAlbumStorage.setFavorite(player, payload.imageId(), payload.favorite())) {
            sendSnapshot(player);
        }
    }

    public static void handleCameraFocal(CameraFocalPayload payload, ServerPlayer player) {
        int focal = Math.max(35, Math.min(135, payload.focalLengthMm()));
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack camera = player.getItemInHand(hand);
            if (camera.getItem() == ModItems.LOST_CAMERA.get()) {
                camera.set(ModDataComponents.CAMERA_FOCAL_MM.get(), focal);
            }
        }
    }

    public static void handleLowerCamera(com.levelscraft7.thelostcamera.network.payload.LowerCameraPayload payload, ServerPlayer player) {
        boolean lowered = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack camera = player.getItemInHand(hand);
            if (camera.getItem() == ModItems.LOST_CAMERA.get() && LostCameraItem.isActive(camera)) {
                LostCameraItem.setActive(camera, false);
                lowered = true;
            }
        }

        if (lowered) {
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SPYGLASS_STOP_USING,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.65F, 0.82F);
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.camera_closed"));
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        PlatformServices.sendToPlayer(player, new AlbumSnapshotPayload(PlayerAlbumStorage.load(player)));
    }

    private static boolean validAlbum(ServerPlayer player, boolean mainHand) {
        InteractionHand hand = mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack album = player.getItemInHand(hand);
        if (album.getItem() != ModItems.PHOTO_ALBUM.get()) {
            return false;
        }
        PhotoAlbumItem.bindIfNeeded(album, player);
        if (!PhotoAlbumItem.isOwnedBy(album, player)) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.album_not_yours"));
            return false;
        }
        return true;
    }
}




