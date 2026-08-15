package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.album.StoredPhoto;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record AlbumSnapshotPayload(List<StoredPhoto> photos) implements CustomPacketPayload {
    public static final Type<AlbumSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "album_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AlbumSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(AlbumSnapshotPayload::write, AlbumSnapshotPayload::new);

    private AlbumSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(readPhotos(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(photos.size());
        for (StoredPhoto entry : photos) {
            PhotoData.STREAM_CODEC.encode(buffer, entry.data());
            buffer.writeBoolean(entry.archived());
            buffer.writeBoolean(entry.favorite());
            buffer.writeBoolean(entry.ruinPhoto() != null);
            if (entry.ruinPhoto() != null) {
                RuinPhotoData.STREAM_CODEC.encode(buffer, entry.ruinPhoto());
            }
        }
    }

    private static List<StoredPhoto> readPhotos(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), 4096);
        List<StoredPhoto> photos = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            PhotoData data = PhotoData.STREAM_CODEC.decode(buffer);
            boolean archived = buffer.readBoolean();
            boolean favorite = buffer.readBoolean();
            RuinPhotoData ruinPhoto = buffer.readBoolean() ? RuinPhotoData.STREAM_CODEC.decode(buffer) : null;
            photos.add(new StoredPhoto(data, archived, favorite, ruinPhoto));
        }
        return List.copyOf(photos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
