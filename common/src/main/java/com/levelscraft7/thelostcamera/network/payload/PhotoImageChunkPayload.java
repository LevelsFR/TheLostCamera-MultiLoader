package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PhotoImageChunkPayload(UUID imageId, int offset, int totalLength, byte[] bytes)
        implements CustomPacketPayload {
    public static final Type<PhotoImageChunkPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "photo_image_chunk")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoImageChunkPayload> STREAM_CODEC =
            StreamCodec.ofMember(PhotoImageChunkPayload::write, PhotoImageChunkPayload::new);

    private PhotoImageChunkPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readInt(), buffer.readInt(), buffer.readByteArray());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
        buffer.writeInt(offset);
        buffer.writeInt(totalLength);
        buffer.writeByteArray(bytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


