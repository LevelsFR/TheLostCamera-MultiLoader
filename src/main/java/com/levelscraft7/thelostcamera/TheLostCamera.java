package com.levelscraft7.thelostcamera;

import com.levelscraft7.thelostcamera.config.ModConfig;
import com.levelscraft7.thelostcamera.network.ModNetworking;
import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.photo.PhotoDevelopmentManager;
import com.levelscraft7.thelostcamera.ruin.RuinDebugCommands;
import com.levelscraft7.thelostcamera.ruin.RuinPresenceTracker;
import com.levelscraft7.thelostcamera.ruin.VirtualRuinRestorationManager;
import com.levelscraft7.thelostcamera.registry.ModBlockEntities;
import com.levelscraft7.thelostcamera.registry.ModBlocks;
import com.levelscraft7.thelostcamera.registry.ModCreativeTabs;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(TheLostCamera.MOD_ID)
public final class TheLostCamera {
    public static final String MOD_ID = "thelostcamera";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheLostCamera(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModConfig.register(modContainer);
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.addListener(VirtualRuinRestorationManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(PhotoDevelopmentManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(PhotoCaptureManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(RuinPresenceTracker::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(RuinPresenceTracker::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(RuinPresenceTracker::onServerTick);
        NeoForge.EVENT_BUS.addListener(RuinDebugCommands::register);

        LOGGER.info("The Lost Camera {} loaded", modContainer.getModInfo().getVersion());
    }
}
