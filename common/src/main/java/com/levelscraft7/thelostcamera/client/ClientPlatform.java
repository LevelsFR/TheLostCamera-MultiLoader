package com.levelscraft7.thelostcamera.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small reflective seam for client names that changed between 26.1.2 and 26.2+. */
public final class ClientPlatform {
    private ClientPlatform() {
    }

    public static boolean hasScreen(Minecraft minecraft) {
        try {
            Method method = minecraft.gui.getClass().getMethod("screen");
            return method.invoke(minecraft.gui) != null;
        } catch (ReflectiveOperationException ignored) {
            try {
                return minecraft.getClass().getField("screen").get(minecraft) != null;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to inspect the active client screen", exception);
            }
        }
    }

    public static boolean isHudHidden(Minecraft minecraft) {
        try {
            Field optionsField = minecraft.options.getClass().getField("hideGui");
            return optionsField.getBoolean(minecraft.options);
        } catch (ReflectiveOperationException ignored) {
            Object hud = field(minecraft.gui, "hud");
            return invokeBoolean(hud, "isHidden");
        }
    }

    public static void setHudHidden(Minecraft minecraft, boolean hidden) {
        try {
            Field optionsField = minecraft.options.getClass().getField("hideGui");
            optionsField.setBoolean(minecraft.options, hidden);
            return;
        } catch (ReflectiveOperationException ignored) {
            Object hud = field(minecraft.gui, "hud");
            if (isHudHidden(minecraft) != hidden) {
                invoke(hud, "toggle");
            }
        }
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        try {
            return (RenderTarget) invoke(minecraft.gameRenderer, "getMainRenderTarget");
        } catch (IllegalStateException ignored) {
            return (RenderTarget) invoke(minecraft.gameRenderer, "mainRenderTarget");
        }
    }

    private static Object field(Object owner, String name) {
        try {
            return owner.getClass().getField(name).get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access client field " + name, exception);
        }
    }

    private static boolean invokeBoolean(Object owner, String name) {
        return (Boolean) invoke(owner, name);
    }

    private static Object invoke(Object owner, String name) {
        try {
            return owner.getClass().getMethod(name).invoke(owner);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to invoke client method " + name, exception);
        }
    }
}
