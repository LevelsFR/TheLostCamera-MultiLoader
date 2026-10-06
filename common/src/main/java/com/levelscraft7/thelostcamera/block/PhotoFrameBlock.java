package com.levelscraft7.thelostcamera.block;

import com.levelscraft7.thelostcamera.block.entity.PhotoFrameBlockEntity;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.platform.BlockCodecBridge;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Wall-facing frame that stores and displays one developed photographic print. */
public final class PhotoFrameBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<PhotoFrameBlock> CODEC = BlockCodecBridge.simpleCodec(PhotoFrameBlock::new);
    // FACING is the visible side. The frame itself sits on the opposite edge, against its support.
    private static final VoxelShape NORTH_SHAPE = Block.box(1.0D, 1.0D, 14.5D, 15.0D, 15.0D, 16.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 1.5D);
    private static final VoxelShape WEST_SHAPE = Block.box(14.5D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0D, 1.0D, 1.0D, 1.5D, 15.0D, 15.0D);

    public PhotoFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<PhotoFrameBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            return null;
        }
        return defaultBlockState().setValue(FACING, clickedFace);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotoFrameBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity frame)) {
            return InteractionResult.PASS;
        }

        if (stack.getItem() == ModItems.PHOTOGRAPHIC_PRINT.get() && !frame.hasPhoto()) {
            PhotoData data = stack.get(ModDataComponents.PHOTO_DATA.get());
            if (data == null) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendOverlayMessage(Component.translatable("message.thelostcamera.print.empty"));
                }
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                frame.setPhoto(data, stack.get(ModDataComponents.RUIN_PHOTO_DATA.get()));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.isEmpty() && frame.hasPhoto()) {
            if (!level.isClientSide()) {
                ItemStack print = new ItemStack(ModItems.PHOTOGRAPHIC_PRINT.get());
                print.set(ModDataComponents.PHOTO_DATA.get(), frame.photo());
                if (frame.ruinPhoto() != null) {
                    print.set(ModDataComponents.RUIN_PHOTO_DATA.get(), frame.ruinPhoto());
                }
                if (!player.addItem(print)) {
                    PlatformServices.dropItem(player, print, false);
                }
                frame.clearPhoto();
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PhotoFrameBlockEntity frame && frame.hasPhoto()) {
            ItemStack print = new ItemStack(ModItems.PHOTOGRAPHIC_PRINT.get());
            print.set(ModDataComponents.PHOTO_DATA.get(), frame.photo());
            if (frame.ruinPhoto() != null) {
                print.set(ModDataComponents.RUIN_PHOTO_DATA.get(), frame.ruinPhoto());
            }
            popResource((ServerLevel) level, pos, print);
            frame.clearPhoto();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}


