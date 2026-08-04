package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LowerCameraPayload() implements CustomPacketPayload {
    public static final Type<LowerCameraPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "lower_camera")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, LowerCameraPayload> STREAM_CODEC =
            StreamCodec.unit(new LowerCameraPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
