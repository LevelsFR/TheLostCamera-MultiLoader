package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.platform.RegistryObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public final class ModCreativeTabs {
    public static RegistryObject<CreativeModeTab> MAIN;

    private ModCreativeTabs() {
    }

    public static void register() {
        MAIN = PlatformServices.registry().registerCreativeTab(TheLostCamera.MOD_ID + ":main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.thelostcamera"))
                .icon(() -> ModItems.LOST_CAMERA.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.LOST_CAMERA.get());
                    output.accept(ModItems.PHOTOGRAPHIC_PLATE.get());
                    output.accept(ModItems.PHOTO_ALBUM.get());
                    output.accept(ModItems.PHOTOGRAPH.get());
                    output.accept(ModItems.PHOTOGRAPHIC_PAPER.get());
                    output.accept(ModItems.PHOTOGRAPHIC_PRINT.get());
                    output.accept(ModItems.DARKROOM_TABLE.get());
                    output.accept(ModItems.PHOTO_FRAME.get());
                })
                .build());
    }
}


