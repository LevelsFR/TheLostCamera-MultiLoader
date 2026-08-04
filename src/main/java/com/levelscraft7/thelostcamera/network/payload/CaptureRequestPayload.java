package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record CaptureRequestPayload(UUID imageId) implements CustomPacketPayload {
    public static final Type<CaptureRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "capture_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(CaptureRequestPayload::write, CaptureRequestPayload::new);

    private CaptureRequestPayload(RegistryFriendlyByteBuf buffer) {
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
