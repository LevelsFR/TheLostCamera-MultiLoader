package com.levelscraft7.thelostcamera.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** NeoForge event adapter for the loader-neutral camera behavior. */
public final class NeoForgeClientEvents {
    @SubscribeEvent
    public void renderOverlay(RenderGuiLayerEvent.Pre event) {
        if (CameraClientEvents.renderOverlay(event.getGuiGraphics())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (CameraClientEvents.onMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void hideHand(RenderHandEvent event) {
        if (CameraClientEvents.shouldHideHand()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void applyCameraFov(ViewportEvent.ComputeFov event) {
        if (CameraClientEvents.shouldHideHand()) {
            event.setFOV(CameraClientEvents.fov());
        }
    }

    @SubscribeEvent
    public void applyCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CameraClientEvents.CameraAngles angles = CameraClientEvents.cameraAngles();
        event.setYaw(event.getYaw() + angles.yaw());
        event.setPitch(event.getPitch() + angles.pitch());
        event.setRoll(event.getRoll() + angles.roll());
    }

    @SubscribeEvent
    public void captureFrame(RenderFrameEvent.Pre event) {
        CameraClientEvents.onClientTick();
    }

    @SubscribeEvent
    public void clearAlbumCaches(ClientPlayerNetworkEvent.LoggingOut event) {
        CameraClientEvents.onLogout();
    }
}
