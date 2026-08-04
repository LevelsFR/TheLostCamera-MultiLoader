package com.levelscraft7.thelostcamera.client.album;

import com.levelscraft7.thelostcamera.album.StoredPhoto;

import java.util.List;

/** Client cache of the server-authoritative personal album snapshot. */
public final class ClientAlbumState {
    private static List<StoredPhoto> photos = List.of();

    private ClientAlbumState() {
    }

    public static void replace(List<StoredPhoto> snapshot) {
        photos = List.copyOf(snapshot);
    }

    public static List<StoredPhoto> photos() {
        return photos;
    }

    public static void clear() {
        photos = List.of();
    }
}
