package com.levelscraft7.thelostcamera.block;

import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.platform.BlockCodecBridge;

import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenDarkroomPayload;
import net.minecraft.core.Direction;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public final class DarkroomTableBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DarkroomTableBlock> CODEC = BlockCodecBridge.simpleCodec(DarkroomTableBlock::new);

    public DarkroomTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<DarkroomTableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        open(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, BlockHitResult hit) {
        open(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private static void open(Level level, net.minecraft.core.BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PlatformServices.sendToPlayer(serverPlayer, new AlbumSnapshotPayload(PlayerAlbumStorage.load(serverPlayer)));
            PlatformServices.sendToPlayer(serverPlayer, new OpenDarkroomPayload(pos.immutable()));
        }
    }
}



