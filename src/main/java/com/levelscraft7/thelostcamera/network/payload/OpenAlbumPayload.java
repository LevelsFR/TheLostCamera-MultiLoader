package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenAlbumPayload(boolean mainHand) implements CustomPacketPayload {
    public static final Type<OpenAlbumPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "open_album")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAlbumPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenAlbumPayload::write, OpenAlbumPayload::new);

    private OpenAlbumPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(mainHand);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
