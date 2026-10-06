package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record SetPhotoArchivedPayload(UUID imageId, boolean archived) implements CustomPacketPayload {
    public static final Type<SetPhotoArchivedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "set_photo_archived")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetPhotoArchivedPayload> STREAM_CODEC =
            StreamCodec.ofMember(SetPhotoArchivedPayload::write, SetPhotoArchivedPayload::new);

    private SetPhotoArchivedPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(imageId);
        buffer.writeBoolean(archived);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


