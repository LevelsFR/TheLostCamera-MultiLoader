package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.item.PhotoAlbumItem;
import com.levelscraft7.thelostcamera.item.PhotographItem;
import com.levelscraft7.thelostcamera.item.PhotographicPaperItem;
import com.levelscraft7.thelostcamera.item.PhotographicPlateItem;
import com.levelscraft7.thelostcamera.item.PhotographicPrintItem;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.platform.RegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static RegistryObject<LostCameraItem> LOST_CAMERA;
    public static RegistryObject<PhotographItem> PHOTOGRAPH;
    public static RegistryObject<PhotographicPlateItem> PHOTOGRAPHIC_PLATE;
    public static RegistryObject<PhotoAlbumItem> PHOTO_ALBUM;
    public static RegistryObject<PhotographicPaperItem> PHOTOGRAPHIC_PAPER;
    public static RegistryObject<PhotographicPrintItem> PHOTOGRAPHIC_PRINT;
    public static RegistryObject<BlockItem> DARKROOM_TABLE;
    public static RegistryObject<BlockItem> PHOTO_FRAME;

    private ModItems() {
    }

    public static void register() {
        LOST_CAMERA = registerItem("lost_camera", () -> new LostCameraItem(PlatformServices.itemProperties("lost_camera").stacksTo(1)));
        PHOTOGRAPH = registerItem("photograph", () -> new PhotographItem(PlatformServices.itemProperties("photograph").stacksTo(1)));
        PHOTOGRAPHIC_PLATE = registerItem("photographic_plate", () -> new PhotographicPlateItem(PlatformServices.itemProperties("photographic_plate").stacksTo(16)));
        PHOTO_ALBUM = registerItem("photo_album", () -> new PhotoAlbumItem(PlatformServices.itemProperties("photo_album").stacksTo(1)));
        PHOTOGRAPHIC_PAPER = registerItem("photographic_paper", () -> new PhotographicPaperItem(PlatformServices.itemProperties("photographic_paper").stacksTo(32)));
        PHOTOGRAPHIC_PRINT = registerItem("photographic_print", () -> new PhotographicPrintItem(PlatformServices.itemProperties("photographic_print").stacksTo(1)));
        DARKROOM_TABLE = registerItem("darkroom_table", () -> new BlockItem(ModBlocks.DARKROOM_TABLE.get(), PlatformServices.itemProperties("darkroom_table")));
        PHOTO_FRAME = registerItem("photo_frame", () -> new BlockItem(ModBlocks.PHOTO_FRAME.get(), PlatformServices.itemProperties("photo_frame")));
    }

    private static <T extends Item> RegistryObject<T> registerItem(String id, java.util.function.Supplier<T> factory) {
        return cast(PlatformServices.registry().registerItem(TheLostCamera.MOD_ID + ":" + id, factory));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Item> RegistryObject<T> cast(RegistryObject<? extends Item> object) {
        return (RegistryObject<T>) object;
    }
}


