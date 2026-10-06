package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests that one photograph from the player's inventory be moved into the held album. */
public record ArchivePhotoPayload(boolean mainHand, int inventorySlot) implements CustomPacketPayload {
    public static final Type<ArchivePhotoPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "archive_photo")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ArchivePhotoPayload> STREAM_CODEC =
            StreamCodec.ofMember(ArchivePhotoPayload::write, ArchivePhotoPayload::new);

    private ArchivePhotoPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(mainHand);
        buffer.writeVarInt(inventorySlot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


