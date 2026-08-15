package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.item.PhotoAlbumItem;
import com.levelscraft7.thelostcamera.item.PhotographItem;
import com.levelscraft7.thelostcamera.item.PhotographicPlateItem;
import com.levelscraft7.thelostcamera.item.PhotographicPaperItem;
import com.levelscraft7.thelostcamera.item.PhotographicPrintItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheLostCamera.MOD_ID);

    public static final DeferredItem<LostCameraItem> LOST_CAMERA = ITEMS.registerItem(
            "lost_camera",
            LostCameraItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<PhotographItem> PHOTOGRAPH = ITEMS.registerItem(
            "photograph",
            PhotographItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<PhotographicPlateItem> PHOTOGRAPHIC_PLATE = ITEMS.registerItem(
            "photographic_plate",
            PhotographicPlateItem::new,
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<PhotoAlbumItem> PHOTO_ALBUM = ITEMS.registerItem(
            "photo_album",
            PhotoAlbumItem::new,
            properties -> properties.stacksTo(1)
    );


    public static final DeferredItem<PhotographicPaperItem> PHOTOGRAPHIC_PAPER = ITEMS.registerItem(
            "photographic_paper",
            PhotographicPaperItem::new,
            properties -> properties.stacksTo(32)
    );

    public static final DeferredItem<PhotographicPrintItem> PHOTOGRAPHIC_PRINT = ITEMS.registerItem(
            "photographic_print",
            PhotographicPrintItem::new,
            properties -> properties.stacksTo(1)
    );

    public static final DeferredItem<BlockItem> DARKROOM_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.DARKROOM_TABLE);
    public static final DeferredItem<BlockItem> PHOTO_FRAME = ITEMS.registerSimpleBlockItem(ModBlocks.PHOTO_FRAME);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
