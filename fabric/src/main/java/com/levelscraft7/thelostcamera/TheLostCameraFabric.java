package com.levelscraft7.thelostcamera;

import com.levelscraft7.thelostcamera.network.FabricNetworking;
import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.photo.PhotoDevelopmentManager;
import com.levelscraft7.thelostcamera.platform.FabricPlatform;
import com.levelscraft7.thelostcamera.ruin.RuinDebugCommands;
import com.levelscraft7.thelostcamera.ruin.RuinPresenceTracker;
import com.levelscraft7.thelostcamera.ruin.VirtualRuinRestorationManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class TheLostCameraFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform.installNetworking(FabricNetworking::sendToPlayer, payload -> {
            throw new IllegalStateException("Client networking is installed by the Fabric client entrypoint");
        });
        FabricNetworking.registerPayloadTypes();
        FabricNetworking.registerServerReceivers();
        TheLostCamera.initialize();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            VirtualRuinRestorationManager.onServerTick(server);
            PhotoDevelopmentManager.onServerTick(server);
            PhotoCaptureManager.onServerTick(server);
            RuinPresenceTracker.onServerTick(server);
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, justGenerated) ->
                RuinPresenceTracker.onChunkLoad(level, chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register(RuinPresenceTracker::onChunkUnload);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RuinDebugCommands.register(dispatcher));
    }
}
