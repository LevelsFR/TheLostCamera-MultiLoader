package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.block.DarkroomTableBlock;
import com.levelscraft7.thelostcamera.block.PhotoFrameBlock;
import com.levelscraft7.thelostcamera.block.RuinAnchorBlock;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.platform.RegistryObject;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public final class ModBlocks {
    public static RegistryObject<RuinAnchorBlock> RUIN_ANCHOR;
    public static RegistryObject<DarkroomTableBlock> DARKROOM_TABLE;
    public static RegistryObject<PhotoFrameBlock> PHOTO_FRAME;

    private ModBlocks() {
    }

    public static void register() {
        RUIN_ANCHOR = registerBlock("ruin_anchor", () -> new RuinAnchorBlock(PlatformServices.blockProperties("ruin_anchor")
                .mapColor(MapColor.STONE).noCollision().noOcclusion()
                .strength(-1.0F, 3_600_000.0F).noLootTable()));
        DARKROOM_TABLE = registerBlock("darkroom_table", () -> new DarkroomTableBlock(PlatformServices.blockProperties("darkroom_table")
                .mapColor(MapColor.WOOD).strength(2.5F).noOcclusion()));
        PHOTO_FRAME = registerBlock("photo_frame", () -> new PhotoFrameBlock(PlatformServices.blockProperties("photo_frame")
                .mapColor(MapColor.WOOD).strength(0.8F).noOcclusion().noCollision()));
    }

    private static <T extends net.minecraft.world.level.block.Block> RegistryObject<T> registerBlock(
            String id, Supplier<T> factory
    ) {
        return cast(PlatformServices.registry().registerBlock(TheLostCamera.MOD_ID + ":" + id, factory));
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.level.block.Block> RegistryObject<T> cast(
            RegistryObject<? extends net.minecraft.world.level.block.Block> object
    ) {
        return (RegistryObject<T>) object;
    }
}


