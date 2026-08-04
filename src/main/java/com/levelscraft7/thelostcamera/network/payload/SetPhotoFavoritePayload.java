package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record SetPhotoFavoritePayload(UUID imageId, boolean favorite) implements CustomPacketPayload {
    public static final Type<SetPhotoFavoritePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "set_photo_favorite")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetPhotoFavoritePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetPhotoFavoritePayload::write, SetPhotoFavoritePayload::new);

    private SetPhotoFavoritePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
        buffer.writeBoolean(favorite);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
