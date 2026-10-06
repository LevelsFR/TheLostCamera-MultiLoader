package com.levelscraft7.thelostcamera.mixin;

import com.levelscraft7.thelostcamera.client.CameraClientEvents;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents the first Escape press from opening the pause menu while using the camera. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    private static final int GLFW_PRESS = 1;
    private static final int GLFW_KEY_ESCAPE = 256;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void thelostcamera$consumeCameraEscape(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (action == GLFW_PRESS
                && event.key() == GLFW_KEY_ESCAPE
                && CameraClientEvents.consumeEscape()) {
            callback.cancel();
        }
    }
}


