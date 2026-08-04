package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.item.PhotoAlbumItem;
import com.levelscraft7.thelostcamera.item.PhotographItem;
import net.minecraft.world.item.Item;
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

    public static final DeferredItem<Item> PHOTOGRAPHIC_PLATE = ITEMS.registerSimpleItem(
            "photographic_plate",
            properties -> properties.stacksTo(16)
    );

    public static final DeferredItem<PhotoAlbumItem> PHOTO_ALBUM = ITEMS.registerItem(
            "photo_album",
            PhotoAlbumItem::new,
            properties -> properties.stacksTo(1)
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
