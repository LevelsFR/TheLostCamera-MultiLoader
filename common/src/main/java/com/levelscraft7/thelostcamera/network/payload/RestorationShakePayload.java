package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RestorationShakePayload(int durationTicks, float intensity) implements CustomPacketPayload {
    public static final Type<RestorationShakePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "restoration_shake")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RestorationShakePayload> STREAM_CODEC =
            StreamCodec.ofMember(RestorationShakePayload::write, RestorationShakePayload::new);

    private RestorationShakePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(durationTicks);
        buffer.writeFloat(intensity);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


