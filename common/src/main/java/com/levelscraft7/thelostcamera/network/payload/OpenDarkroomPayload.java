package com.levelscraft7.thelostcamera.network.payload;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenDarkroomPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenDarkroomPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "open_darkroom"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDarkroomPayload> STREAM_CODEC = StreamCodec.ofMember(OpenDarkroomPayload::write, OpenDarkroomPayload::new);
    private OpenDarkroomPayload(RegistryFriendlyByteBuf buffer) { this(buffer.readBlockPos()); }
    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeBlockPos(pos); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


