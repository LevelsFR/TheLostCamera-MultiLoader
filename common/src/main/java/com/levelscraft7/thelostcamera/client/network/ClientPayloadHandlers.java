package com.levelscraft7.thelostcamera.client.network;

import com.levelscraft7.thelostcamera.client.CameraShakeManager;
import com.levelscraft7.thelostcamera.client.album.ClientAlbumState;
import com.levelscraft7.thelostcamera.client.gui.AlbumScreen;
import com.levelscraft7.thelostcamera.client.gui.DarkroomScreen;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCapture;
import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.CaptureRequestPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenDarkroomPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoImageChunkPayload;
import com.levelscraft7.thelostcamera.network.payload.PhotoUnavailablePayload;
import com.levelscraft7.thelostcamera.network.payload.RestorationShakePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleCaptureRequest(CaptureRequestPayload payload) {
        ClientPhotoCapture.request(payload.imageId());
    }

    public static void handlePhotoChunk(PhotoImageChunkPayload payload) {
        ClientPhotoCache.acceptChunk(payload.imageId(), payload.offset(), payload.totalLength(), payload.bytes());
    }

    public static void handleUnavailablePhoto(PhotoUnavailablePayload payload) {
        ClientPhotoCache.markUnavailable(payload.imageId());
    }

    public static void handleRestorationShake(RestorationShakePayload payload) {
        CameraShakeManager.start(payload.durationTicks(), payload.intensity());
    }

    public static void handleOpenAlbum(OpenAlbumPayload payload) {
        InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        Minecraft.getInstance().setScreenAndShow(new AlbumScreen(hand));
    }

    public static void handleOpenDarkroom(OpenDarkroomPayload payload) {
        Minecraft.getInstance().setScreenAndShow(new DarkroomScreen(payload.pos()));
    }

    public static void handleAlbumSnapshot(AlbumSnapshotPayload payload) {
        ClientAlbumState.replace(payload.photos());
        ClientPhotoCache.preloadThumbnails(payload.photos());
    }
}

