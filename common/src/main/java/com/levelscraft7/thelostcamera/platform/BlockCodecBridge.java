package com.levelscraft7.thelostcamera.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

/** Keeps the block codec seam source-compatible with the 26.3 block API change. */
public final class BlockCodecBridge {
    private BlockCodecBridge() {
    }

    @SuppressWarnings("unchecked")
    public static <B extends Block> MapCodec<B> simpleCodec(Function<BlockBehaviour.Properties, B> factory) {
        try {
            Method method = BlockBehaviour.class.getDeclaredMethod("simpleCodec", Function.class);
            method.setAccessible(true);
            return (MapCodec<B>) method.invoke(null, factory);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException ignored) {
            return MapCodec.unit(() -> factory.apply(BlockBehaviour.Properties.of()));
        }
    }
}
