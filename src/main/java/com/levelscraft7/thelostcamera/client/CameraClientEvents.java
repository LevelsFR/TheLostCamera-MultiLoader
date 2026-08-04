package com.levelscraft7.thelostcamera.client;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.client.album.ClientAlbumState;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCapture;
import com.levelscraft7.thelostcamera.item.LostCameraItem;
import com.levelscraft7.thelostcamera.network.payload.LowerCameraPayload;
import com.levelscraft7.thelostcamera.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only viewfinder, fluid zoom and screenshot hooks. */
public final class CameraClientEvents {
    private static final Identifier VIEWFINDER = Identifier.fromNamespaceAndPath(
            TheLostCamera.MOD_ID,
            "textures/gui/viewfinder.png"
    );
    private static final Identifier DARK_PANEL = Identifier.fromNamespaceAndPath(
            TheLostCamera.MOD_ID,
            "textures/gui/dark_panel.png"
    );

    @SubscribeEvent
    public void renderOverlay(RenderGuiLayerEvent.Pre event) {
        boolean active = isCameraActive();
        if (!active || ClientPhotoCapture.isCapturing()) {
            return;
        }

        event.setCanceled(true);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        drawViewfinder(event.getGuiGraphics(), minecraft);
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (CameraZoomManager.scroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        if (event.getKey() != GLFW.GLFW_KEY_ESCAPE || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || (!isCameraActive() && !ClientPhotoCapture.isCapturing())) {
            return;
        }

        ClientPhotoCapture.cancelPending();
        lowerLocalCamera();
        ClientPacketDistributor.sendToServer(new LowerCameraPayload());
    }

    @SubscribeEvent
    public void hideHand(RenderHandEvent event) {
        if (isCameraActive() || ClientPhotoCapture.isCapturing()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void applyCameraFov(ViewportEvent.ComputeFov event) {
        if (isCameraActive() || ClientPhotoCapture.isCapturing()) {
            event.setFOV(CameraZoomManager.fov());
        }
    }

    @SubscribeEvent
    public void applyRestorationShake(ViewportEvent.ComputeCameraAngles event) {
        CameraShakeManager.apply(event);
    }

    @SubscribeEvent
    public void captureFrame(RenderFrameEvent.Pre event) {
        CameraZoomManager.update(isCameraActive() && Minecraft.getInstance().gui.screen() == null);
        ClientPhotoCapture.onRenderFrame(event);
    }

    @SubscribeEvent
    public void clearAlbumCaches(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAlbumState.clear();
        ClientPhotoCache.clear();
    }

    public static boolean isCameraActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return isActiveCameraStack(minecraft.player.getMainHandItem())
                || isActiveCameraStack(minecraft.player.getOffhandItem());
    }

    private static boolean isActiveCameraStack(ItemStack stack) {
        return stack.getItem() == ModItems.LOST_CAMERA.get() && LostCameraItem.isActive(stack);
    }

    private static void lowerLocalCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (isActiveCameraStack(mainHand)) {
            LostCameraItem.setActive(mainHand, false);
        }
        ItemStack offHand = minecraft.player.getOffhandItem();
        if (isActiveCameraStack(offHand)) {
            LostCameraItem.setActive(offHand, false);
        }
    }

    private static void drawViewfinder(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int frameWidth = Math.min((int) (screenWidth * 0.92F), Math.round(screenHeight * 0.86F * 4.0F / 3.0F));
        int frameHeight = Math.round(frameWidth * 3.0F / 4.0F);
        int x = (screenWidth - frameWidth) / 2;
        int y = (screenHeight - frameHeight) / 2;

        drawPanel(graphics, 0, 0, x, screenHeight);
        drawPanel(graphics, x + frameWidth, 0, screenWidth - x - frameWidth, screenHeight);
        drawPanel(graphics, x, 0, frameWidth, y);
        drawPanel(graphics, x, y + frameHeight, frameWidth, screenHeight - y - frameHeight);

        graphics.blit(RenderPipelines.GUI_TEXTURED, VIEWFINDER, x, y, 0.0F, 0.0F,
                frameWidth, frameHeight, 512, 384, 512, 384);

        CameraZoomManager.Exposure exposure = CameraZoomManager.exposure();
        Component settings = Component.literal(
                CameraZoomManager.focalLengthMm() + " mm   f/" + exposure.aperture()
                        + "   1/" + exposure.shutterDenominator() + " s   ISO " + exposure.iso()
        );
        int settingsX = x + (frameWidth - minecraft.font.width(settings)) / 2;
        graphics.text(minecraft.font, settings.getVisualOrderText(), settingsX,
                y + frameHeight - minecraft.font.lineHeight - 8, 0xFFF2D0, true);

        if (CameraZoomManager.hasFramedRuin()) {
            Component lock = Component.translatable("gui.thelostcamera.camera.ruin_lock");
            graphics.text(minecraft.font, lock.getVisualOrderText(),
                    x + (frameWidth - minecraft.font.width(lock)) / 2,
                    y + 10, 0xFFE3A0, true);
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, DARK_PANEL, x, y, 0.0F, 0.0F,
                width, height, 1, 1, 1, 1);
    }
}
