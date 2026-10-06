package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.block.entity.PhotoFrameBlockEntity;
import com.levelscraft7.thelostcamera.block.entity.RuinAnchorBlockEntity;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.platform.RegistryObject;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static RegistryObject<BlockEntityType<RuinAnchorBlockEntity>> RUIN_ANCHOR;
    public static RegistryObject<BlockEntityType<PhotoFrameBlockEntity>> PHOTO_FRAME;

    private ModBlockEntities() {
    }

    public static void register() {
        RUIN_ANCHOR = PlatformServices.registry().registerBlockEntity(
                TheLostCamera.MOD_ID + ":ruin_anchor",
                RuinAnchorBlockEntity::new,
                ModBlocks.RUIN_ANCHOR::get
        );
        PHOTO_FRAME = PlatformServices.registry().registerBlockEntity(
                TheLostCamera.MOD_ID + ":photo_frame",
                PhotoFrameBlockEntity::new,
                ModBlocks.PHOTO_FRAME::get
        );
    }
}


