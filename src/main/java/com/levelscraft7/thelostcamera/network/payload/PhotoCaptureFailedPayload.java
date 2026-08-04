package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PhotoCaptureFailedPayload(UUID imageId) implements CustomPacketPayload {
    public static final Type<PhotoCaptureFailedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "photo_capture_failed")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoCaptureFailedPayload> STREAM_CODEC =
            StreamCodec.ofMember(PhotoCaptureFailedPayload::write, PhotoCaptureFailedPayload::new);

    private PhotoCaptureFailedPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
