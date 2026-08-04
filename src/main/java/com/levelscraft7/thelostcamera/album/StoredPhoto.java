package com.levelscraft7.thelostcamera.album;

import com.levelscraft7.thelostcamera.data.PhotoData;

/** One immutable library entry synchronized between the server and its owner. */
public record StoredPhoto(PhotoData data, boolean archived, boolean favorite) {
    public StoredPhoto withArchived(boolean value) {
        return new StoredPhoto(data, value, favorite);
    }

    public StoredPhoto withFavorite(boolean value) {
        return new StoredPhoto(data, archived, value);
    }
}
