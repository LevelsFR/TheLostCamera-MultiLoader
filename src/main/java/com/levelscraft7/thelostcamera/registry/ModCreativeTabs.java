package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TheLostCamera.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
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
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
