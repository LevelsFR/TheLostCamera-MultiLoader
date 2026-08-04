package com.levelscraft7.thelostcamera.network;

import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.item.PhotoAlbumItem;
import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.ArchivePhotoPayload;
import com.levelscraft7.thelostcamera.network.payload.CameraFocalPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoCaptureFailedPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUploadChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.RequestAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoArchivedPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoFavoritePayload;
import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {
    }

    public static void handlePhotoUpload(PhotoUploadChunkPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PhotoCaptureManager.acceptChunk(player, payload.imageId(), payload.offset(), payload.totalLength(),
                    payload.imageWidth(), payload.imageHeight(), payload.bytes());
        }
    }

    public static void handleCaptureFailure(PhotoCaptureFailedPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PhotoCaptureManager.cancelCapture(player, payload.imageId());
        }
    }

    public static void handlePhotoRequest(PhotoImageRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PhotoCaptureManager.requestPhoto(player, payload.imageId(), payload.thumbnail());
        }
    }

    public static void handleAlbumRequest(RequestAlbumPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && validAlbum(player, payload.mainHand())) {
            sendSnapshot(player);
        }
    }

    public static void handleArchivePhoto(ArchivePhotoPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !validAlbum(player, payload.mainHand())) {
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
        PhotoData data = photograph.get(ModDataComponents.PHOTO_DATA);
        if (data == null || !PlayerAlbumStorage.add(player, data)) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.photo_already_archived"));
            return;
        }
        photograph.shrink(1);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.sendOverlayMessage(Component.translatable("message.thelostcamera.photo_archived"));
        sendSnapshot(player);
    }

    public static void handleSetArchived(SetPhotoArchivedPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && PhotoAlbumItem.hasOwnedAlbum(player)
                && PlayerAlbumStorage.setArchived(player, payload.imageId(), payload.archived())) {
            sendSnapshot(player);
        }
    }

    public static void handleSetFavorite(SetPhotoFavoritePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && PhotoAlbumItem.hasOwnedAlbum(player)
                && PlayerAlbumStorage.setFavorite(player, payload.imageId(), payload.favorite())) {
            sendSnapshot(player);
        }
    }

    public static void handleCameraFocal(CameraFocalPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int focal = Math.max(35, Math.min(135, payload.focalLengthMm()));
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack camera = player.getItemInHand(hand);
            if (camera.getItem() == ModItems.LOST_CAMERA.get()) {
                camera.set(ModDataComponents.CAMERA_FOCAL_MM, focal);
            }
        }
    }

    public static void handleLowerCamera(com.levelscraft7.thelostcamera.network.payload.LowerCameraPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        boolean lowered = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack camera = player.getItemInHand(hand);
            if (camera.getItem() == ModItems.LOST_CAMERA.get() && LostCameraItem.isActive(camera)) {
                LostCameraItem.setActive(camera, false);
                lowered = true;
            }
        }

        if (lowered) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.camera_closed"));
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new AlbumSnapshotPayload(PlayerAlbumStorage.load(player)));
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
