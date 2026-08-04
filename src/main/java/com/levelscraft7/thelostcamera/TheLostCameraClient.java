package com.levelscraft7.thelostcamera;

import com.levelscraft7.thelostcamera.client.CameraClientEvents;
import com.levelscraft7.thelostcamera.client.gui.AlbumScreen;
import com.levelscraft7.thelostcamera.client.gui.PhotoScreen;
import com.levelscraft7.thelostcamera.client.network.ClientPayloadHandlers;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.network.ClientPayloadBridge;
import com.levelscraft7.thelostcamera.network.ClientUiBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TheLostCamera.MOD_ID, dist = Dist.CLIENT)
public final class TheLostCameraClient {
    public TheLostCameraClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(TheLostCameraClient::clientSetup);
        ClientUiBridge.installPhotoScreenOpener(TheLostCameraClient::openPhotoScreen);
        ClientUiBridge.installAlbumScreenOpener(TheLostCameraClient::openAlbumScreen);
        ClientPayloadBridge.install(
                ClientPayloadHandlers::handleCaptureRequest,
                ClientPayloadHandlers::handlePhotoChunk,
                ClientPayloadHandlers::handleUnavailablePhoto,
                ClientPayloadHandlers::handleRestorationShake,
                ClientPayloadHandlers::handleOpenAlbum,
                ClientPayloadHandlers::handleAlbumSnapshot
        );
    }

    public static void openPhotoScreen(ItemStack stack) {
        Minecraft.getInstance().setScreenAndShow(new PhotoScreen(stack));
    }

    public static void openAlbumScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreenAndShow(new AlbumScreen(hand));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(new CameraClientEvents());
        ClientPhotoCache.initialize();
    }
}
