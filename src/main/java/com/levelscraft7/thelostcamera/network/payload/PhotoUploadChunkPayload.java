package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PhotoUploadChunkPayload(
        UUID imageId,
        int offset,
        int totalLength,
        int imageWidth,
        int imageHeight,
        byte[] bytes
) implements CustomPacketPayload {
    public static final Type<PhotoUploadChunkPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "photo_upload_chunk")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoUploadChunkPayload> STREAM_CODEC =
            StreamCodec.ofMember(PhotoUploadChunkPayload::write, PhotoUploadChunkPayload::new);

    private PhotoUploadChunkPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readByteArray()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
        buffer.writeInt(offset);
        buffer.writeInt(totalLength);
        buffer.writeInt(imageWidth);
        buffer.writeInt(imageHeight);
        buffer.writeByteArray(bytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
