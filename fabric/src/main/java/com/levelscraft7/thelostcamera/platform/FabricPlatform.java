package com.levelscraft7.thelostcamera.platform;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;

import java.util.Arrays;
import java.util.function.Supplier;

/** Fabric registry services for the shared core. */
public final class FabricPlatform {
    private static final RegistryBridge REGISTRY = new RegistryBridge() {
        @Override
        public RegistryObject<Block> registerBlock(String id, Supplier<? extends Block> factory) {
            Block block = Registry.register(BuiltInRegistries.BLOCK, identifier(id), factory.get());
            return RegistryObject.of(() -> block);
        }

        @Override
        public RegistryObject<Item> registerItem(String id, Supplier<? extends Item> factory) {
            Item item = Registry.register(BuiltInRegistries.ITEM, identifier(id), factory.get());
            return RegistryObject.of(() -> item);
        }

        @Override
        public <T extends net.minecraft.world.level.block.entity.BlockEntity> RegistryObject<BlockEntityType<T>>
        registerBlockEntity(String id, java.util.function.BiFunction<net.minecraft.core.BlockPos,
                net.minecraft.world.level.block.state.BlockState, T> factory, Supplier<? extends Block>... validBlocks) {
            Block[] resolvedBlocks = Arrays.stream(validBlocks).map(Supplier::get).toArray(Block[]::new);
            BlockEntityType<T> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, identifier(id),
                    FabricBlockEntityTypeBuilder.create(factory::apply, resolvedBlocks).build());
            return RegistryObject.of(() -> type);
        }

        @Override
        public RegistryObject<CreativeModeTab> registerCreativeTab(String id, Supplier<CreativeModeTab> factory) {
            CreativeModeTab tab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, identifier(id), factory.get());
            return RegistryObject.of(() -> tab);
        }

        @Override
        public <T> RegistryObject<DataComponentType<T>> registerDataComponent(String id,
                                                                                Supplier<DataComponentType<T>> factory) {
            DataComponentType<T> type = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, identifier(id), factory.get());
            return RegistryObject.of(() -> type);
        }
    };

    private FabricPlatform() {
    }

    public static void install() {
        PlatformServices.install(REGISTRY, (player, payload) -> {
            throw new IllegalStateException("Fabric server packet sender is installed by the networking entrypoint");
        }, payload -> {
            throw new IllegalStateException("Fabric client packet sender is installed by the client entrypoint");
        });
    }

    public static void installNetworking(java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload> serverSender,
                                         java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> clientSender) {
        PlatformServices.install(REGISTRY, serverSender, clientSender);
    }

    private static Identifier identifier(String id) {
        int separator = id.indexOf(':');
        return Identifier.fromNamespaceAndPath(separator < 0 ? TheLostCamera.MOD_ID : id.substring(0, separator),
                separator < 0 ? id : id.substring(separator + 1));
    }
}
