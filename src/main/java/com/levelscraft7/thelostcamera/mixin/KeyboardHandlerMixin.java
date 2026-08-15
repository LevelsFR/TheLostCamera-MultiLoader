package com.levelscraft7.thelostcamera.mixin;

import com.levelscraft7.thelostcamera.client.CameraClientEvents;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents the first Escape press from opening the pause menu while using the camera. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void thelostcamera$consumeCameraEscape(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (action == GLFW.GLFW_PRESS
                && event.key() == GLFW.GLFW_KEY_ESCAPE
                && CameraClientEvents.consumeEscape()) {
            callback.cancel();
        }
    }
}
