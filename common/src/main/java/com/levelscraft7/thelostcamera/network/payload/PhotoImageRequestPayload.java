package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Requests either the lossless photograph or its compact album thumbnail. */
public record PhotoImageRequestPayload(UUID imageId, boolean thumbnail) implements CustomPacketPayload {
    public static final Type<PhotoImageRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "photo_image_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoImageRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(PhotoImageRequestPayload::write, PhotoImageRequestPayload::new);

    private PhotoImageRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
        buffer.writeBoolean(thumbnail);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


