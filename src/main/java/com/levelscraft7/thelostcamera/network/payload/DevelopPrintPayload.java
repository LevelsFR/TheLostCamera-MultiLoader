package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record DevelopPrintPayload(UUID imageId, BlockPos darkroomPos) implements CustomPacketPayload {
    public static final Type<DevelopPrintPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "develop_print"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DevelopPrintPayload> STREAM_CODEC = StreamCodec.ofMember(DevelopPrintPayload::write, DevelopPrintPayload::new);
    private DevelopPrintPayload(RegistryFriendlyByteBuf buffer) { this(buffer.readUUID(), buffer.readBlockPos()); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeUUID(imageId); buffer.writeBlockPos(darkroomPos); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
