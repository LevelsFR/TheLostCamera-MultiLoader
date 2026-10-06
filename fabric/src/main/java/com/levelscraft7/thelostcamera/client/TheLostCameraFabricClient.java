package com.levelscraft7.thelostcamera.client;

import com.levelscraft7.thelostcamera.client.gui.AlbumScreen;
import com.levelscraft7.thelostcamera.client.gui.PhotoScreen;
import com.levelscraft7.thelostcamera.client.network.ClientPayloadHandlers;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.client.render.PhotoFrameRenderer;
import com.levelscraft7.thelostcamera.network.ClientPayloadBridge;
import com.levelscraft7.thelostcamera.network.ClientUiBridge;
import com.levelscraft7.thelostcamera.network.FabricNetworking;
import com.levelscraft7.thelostcamera.platform.FabricPlatform;
import com.levelscraft7.thelostcamera.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class TheLostCameraFabricClient implements ClientModInitializer {
    private static final Identifier HUD_ELEMENT = Identifier.fromNamespaceAndPath("thelostcamera", "viewfinder");

    @Override
    public void onInitializeClient() {
        FabricPlatform.installNetworking(FabricNetworking::sendToPlayer, FabricNetworking::sendToServer);
        FabricNetworking.registerClientReceivers();
        ClientUiBridge.installPhotoScreenOpener(TheLostCameraFabricClient::openPhotoScreen);
        ClientUiBridge.installAlbumScreenOpener(TheLostCameraFabricClient::openAlbumScreen);
        ClientPayloadBridge.install(
                ClientPayloadHandlers::handleCaptureRequest,
                ClientPayloadHandlers::handlePhotoChunk,
                ClientPayloadHandlers::handleUnavailablePhoto,
                ClientPayloadHandlers::handleRestorationShake,
                ClientPayloadHandlers::handleOpenAlbum,
                ClientPayloadHandlers::handleOpenDarkroom,
                ClientPayloadHandlers::handleAlbumSnapshot
        );
        ClientPhotoCache.initialize();
        BlockEntityRendererRegistry.register(ModBlockEntities.PHOTO_FRAME.get(), PhotoFrameRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> CameraClientEvents.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CameraClientEvents.onLogout());
        HudElementRegistry.addLast(HUD_ELEMENT, (graphics, delta) -> CameraClientEvents.renderOverlay(graphics));
    }

    private static void openPhotoScreen(ItemStack stack) {
        Minecraft.getInstance().setScreenAndShow(new PhotoScreen(stack));
    }

    private static void openAlbumScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreenAndShow(new AlbumScreen(hand));
    }
}
