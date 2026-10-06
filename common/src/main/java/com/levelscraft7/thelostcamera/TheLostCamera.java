package com.levelscraft7.thelostcamera;

import com.levelscraft7.thelostcamera.registry.ModBlockEntities;
import com.levelscraft7.thelostcamera.registry.ModBlocks;
import com.levelscraft7.thelostcamera.registry.ModCreativeTabs;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Shared mod bootstrap. Loader entrypoints install platform services before calling this method. */
public final class TheLostCamera {
    public static final String MOD_ID = "thelostcamera";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized;

    private TheLostCamera() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModBlocks.register();
        ModBlockEntities.register();
        ModDataComponents.register();
        ModItems.register();
        ModCreativeTabs.register();
        LOGGER.info("The Lost Camera shared core initialized");
    }
}


