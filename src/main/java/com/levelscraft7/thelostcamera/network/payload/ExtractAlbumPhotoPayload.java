package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests that one stored album slot be returned to the player's inventory. */
public record ExtractAlbumPhotoPayload(boolean mainHand, int albumSlot) implements CustomPacketPayload {
    public static final Type<ExtractAlbumPhotoPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "extract_album_photo")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractAlbumPhotoPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExtractAlbumPhotoPayload::write, ExtractAlbumPhotoPayload::new);

    private ExtractAlbumPhotoPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(mainHand);
        buffer.writeVarInt(albumSlot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
