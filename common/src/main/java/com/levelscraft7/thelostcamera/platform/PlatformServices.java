package com.levelscraft7.thelostcamera.platform;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Runtime services supplied by the active loader. */
public final class PlatformServices {
    private static RegistryBridge registry;
    private static BiConsumer<ServerPlayer, CustomPacketPayload> serverSender;
    private static Consumer<CustomPacketPayload> clientSender;

    private PlatformServices() {
    }

    public static void install(
            RegistryBridge registryBridge,
            BiConsumer<ServerPlayer, CustomPacketPayload> serverPayloadSender,
            Consumer<CustomPacketPayload> clientPayloadSender
    ) {
        registry = Objects.requireNonNull(registryBridge);
        serverSender = Objects.requireNonNull(serverPayloadSender);
        clientSender = Objects.requireNonNull(clientPayloadSender);
    }

    public static RegistryBridge registry() {
        if (registry == null) {
            throw new IllegalStateException(TheLostCamera.MOD_ID + " platform services were not installed");
        }
        return registry;
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (serverSender == null) {
            throw new IllegalStateException(TheLostCamera.MOD_ID + " server networking was not installed");
        }
        serverSender.accept(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (clientSender == null) {
            throw new IllegalStateException(TheLostCamera.MOD_ID + " client networking was not installed");
        }
        clientSender.accept(payload);
    }

    public static BlockBehaviour.Properties blockProperties(String id) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(
                Registries.BLOCK, Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, id)));
    }

    public static Item.Properties itemProperties(String id) {
        return new Item.Properties().setId(ResourceKey.create(
                Registries.ITEM, Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, id)));
    }

    /** Bridges the two-argument drop method and the 26.3 prediction-aware overload. */
    public static void dropItem(Player player, ItemStack stack, boolean randomly) {
        try {
            try {
                player.getClass().getMethod("drop", ItemStack.class, boolean.class)
                        .invoke(player, stack, randomly);
                return;
            } catch (NoSuchMethodException ignored) {
                // 26.3 adds a prediction argument to this method.
            }

            for (Method method : player.getClass().getMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!method.getName().equals("drop") || parameterTypes.length != 3
                        || parameterTypes[0] != ItemStack.class || parameterTypes[1] != boolean.class) {
                    continue;
                }
                Object prediction = null;
                if (parameterTypes[2].isEnum()) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Object serverOnly = Enum.valueOf((Class) parameterTypes[2], "SERVER_ONLY");
                    prediction = serverOnly;
                }
                method.invoke(player, stack, randomly, prediction);
                return;
            }
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to drop an item on this Minecraft version", exception);
        }
        throw new IllegalStateException("No compatible item drop method found on this Minecraft version");
    }
}


