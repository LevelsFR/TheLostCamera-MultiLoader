package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CameraFocalPayload(int focalLengthMm) implements CustomPacketPayload {
    public static final Type<CameraFocalPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "camera_focal")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraFocalPayload> STREAM_CODEC =
            StreamCodec.ofMember(CameraFocalPayload::write, CameraFocalPayload::new);

    private CameraFocalPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(focalLengthMm);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


