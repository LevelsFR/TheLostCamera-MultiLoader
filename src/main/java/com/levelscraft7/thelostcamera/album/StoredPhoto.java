package com.levelscraft7.thelostcamera.album;

import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;

/** One immutable library entry synchronized between the server and its owner. */
public record StoredPhoto(PhotoData data, boolean archived, boolean favorite, RuinPhotoData ruinPhoto) {
    public StoredPhoto(PhotoData data, boolean archived, boolean favorite) {
        this(data, archived, favorite, null);
    }

    public StoredPhoto withArchived(boolean value) {
        return new StoredPhoto(data, value, favorite, ruinPhoto);
    }

    public StoredPhoto withFavorite(boolean value) {
        return new StoredPhoto(data, archived, value, ruinPhoto);
    }
}
