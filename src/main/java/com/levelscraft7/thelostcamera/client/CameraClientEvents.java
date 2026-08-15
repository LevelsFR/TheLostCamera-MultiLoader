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
    private static final int EXPOSURE_TEXT = 0xFFFFF2D0;
    private static final int EXPOSURE_PANEL = 0xB0000000;
    private static final int EXPOSURE_BORDER = 0xFFD2A65A;
    private static final int RUIN_LOCK_TEXT = 0xFFFFE3A0;

    @SubscribeEvent
    public void renderOverlay(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = isCameraActive();
        if (!active || ClientPhotoCapture.isCapturing()) {
            return;
        }

        event.setCanceled(true);
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
        CameraShutterEffect.apply(event);
    }

    @SubscribeEvent
    public void captureFrame(RenderFrameEvent.Pre event) {
        CameraZoomManager.update((isCameraActive() || ClientPhotoCapture.isCapturing())
                && Minecraft.getInstance().gui.screen() == null);
        ClientPhotoCapture.onRenderFrame(event);
    }

    @SubscribeEvent
    public void clearAlbumCaches(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAlbumState.clear();
        ClientPhotoCache.clear();
        CameraShutterEffect.reset();
    }

    /**
     * Consumes the first Escape press while the viewfinder is active.
     * Called from the client keyboard mixin before vanilla opens the pause menu.
     */
    public static boolean consumeEscape() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || (!isCameraActive() && !ClientPhotoCapture.isCapturing())) {
            return false;
        }
        ClientPhotoCapture.cancelPending();
        lowerLocalCamera();
        ClientPacketDistributor.sendToServer(new LowerCameraPayload());
        return true;
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

        int frameSize = Math.min((int) (screenWidth * 0.92F), (int) (screenHeight * 0.86F));
        int x = (screenWidth - frameSize) / 2;
        int y = (screenHeight - frameSize) / 2;

        drawPanel(graphics, 0, 0, x, screenHeight);
        drawPanel(graphics, x + frameSize, 0, screenWidth - x - frameSize, screenHeight);
        drawPanel(graphics, x, 0, frameSize, y);
        drawPanel(graphics, x, y + frameSize, frameSize, screenHeight - y - frameSize);

        graphics.blit(RenderPipelines.GUI_TEXTURED, VIEWFINDER, x, y, 0.0F, 0.0F,
                frameSize, frameSize, 512, 384, 512, 384);

        drawExposureHud(graphics, minecraft, x, y, frameSize);

        if (CameraZoomManager.hasFramedRuin()) {
            Component lock = Component.translatable("gui.thelostcamera.camera.ruin_lock");
            graphics.text(minecraft.font, lock.getVisualOrderText(),
                    x + (frameSize - minecraft.font.width(lock)) / 2,
                    y + 10, RUIN_LOCK_TEXT, true);
        }
    }

    private static void drawExposureHud(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int frameX,
            int frameY,
            int frameSize
    ) {
        CameraZoomManager.Exposure exposure = CameraZoomManager.exposure();
        Component settings = Component.literal(
                CameraZoomManager.focalLengthMm() + " mm   f/" + exposure.aperture()
                        + "   1/" + exposure.shutterDenominator() + " s   ISO " + exposure.iso()
        );

        int horizontalPadding = 10;
        int verticalPadding = 4;
        int settingsWidth = minecraft.font.width(settings);
        int panelWidth = Math.min(frameSize - 20, settingsWidth + horizontalPadding * 2);
        int panelHeight = minecraft.font.lineHeight + verticalPadding * 2;
        int panelX = frameX + (frameSize - panelWidth) / 2;
        int panelY = frameY + frameSize - panelHeight - 7;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, EXPOSURE_PANEL);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, EXPOSURE_BORDER);

        int settingsX = panelX + (panelWidth - settingsWidth) / 2;
        int settingsY = panelY + verticalPadding;
        graphics.text(minecraft.font, settings.getVisualOrderText(), settingsX, settingsY, EXPOSURE_TEXT, true);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, DARK_PANEL, x, y, 0.0F, 0.0F,
                width, height, 1, 1, 1, 1);
    }
}
