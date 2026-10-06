package com.levelscraft7.thelostcamera;

import com.levelscraft7.thelostcamera.network.ModNetworking;
import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.photo.PhotoDevelopmentManager;
import com.levelscraft7.thelostcamera.platform.NeoForgePlatform;
import com.levelscraft7.thelostcamera.ruin.RuinDebugCommands;
import com.levelscraft7.thelostcamera.ruin.RuinPresenceTracker;
import com.levelscraft7.thelostcamera.ruin.VirtualRuinRestorationManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(TheLostCamera.MOD_ID)
public final class TheLostCameraNeoForge {
    public TheLostCameraNeoForge(IEventBus modBus) {
        NeoForgePlatform.install(modBus);
        TheLostCamera.initialize();
        modBus.addListener(ModNetworking::register);

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            VirtualRuinRestorationManager.onServerTick(event.getServer());
            PhotoDevelopmentManager.onServerTick(event.getServer());
            PhotoCaptureManager.onServerTick(event.getServer());
            RuinPresenceTracker.onServerTick(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                RuinPresenceTracker.onChunkLoad(level, event.getChunk());
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                RuinPresenceTracker.onChunkUnload(level, event.getChunk());
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                RuinDebugCommands.register(event.getDispatcher()));
    }
}
