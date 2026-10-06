package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Confirms that the rendered exposure exists and carries the exact automatic settings shown in the viewfinder. */
public record CaptureReadyPayload(
        UUID imageId,
        int focalLengthMm,
        int apertureTenths,
        int shutterDenominator,
        int iso
) implements CustomPacketPayload {
    public static final Type<CaptureReadyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "capture_ready")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureReadyPayload> STREAM_CODEC =
            StreamCodec.ofMember(CaptureReadyPayload::write, CaptureReadyPayload::new);

    private CaptureReadyPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
        buffer.writeVarInt(focalLengthMm);
        buffer.writeVarInt(apertureTenths);
        buffer.writeVarInt(shutterDenominator);
        buffer.writeVarInt(iso);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


