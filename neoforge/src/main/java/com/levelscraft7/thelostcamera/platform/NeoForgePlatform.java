package com.levelscraft7.thelostcamera.platform;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.function.Supplier;

/** NeoForge registry and packet services for the shared core. */
public final class NeoForgePlatform {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TheLostCamera.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TheLostCamera.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TheLostCamera.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TheLostCamera.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TheLostCamera.MOD_ID);

    private static final RegistryBridge REGISTRY = new RegistryBridge() {
        @Override
        public RegistryObject<Block> registerBlock(String id, Supplier<? extends Block> factory) {
            DeferredHolder<Block, Block> entry = BLOCKS.register(path(id), factory);
            return RegistryObject.of(entry::get);
        }

        @Override
        public RegistryObject<Item> registerItem(String id, Supplier<? extends Item> factory) {
            DeferredHolder<Item, Item> entry = ITEMS.register(path(id), factory);
            return RegistryObject.of(entry::get);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends net.minecraft.world.level.block.entity.BlockEntity> RegistryObject<BlockEntityType<T>>
        registerBlockEntity(String id, java.util.function.BiFunction<net.minecraft.core.BlockPos,
                net.minecraft.world.level.block.state.BlockState, T> factory, Supplier<? extends Block>... validBlocks) {
            DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> entry =
                    (DeferredHolder<BlockEntityType<?>, BlockEntityType<T>>) (DeferredHolder<?, ?>)
                            BLOCK_ENTITIES.register(path(id), () -> new BlockEntityType<>(factory::apply,
                                    java.util.Set.copyOf(Arrays.stream(validBlocks).map(Supplier::get).toList())));
            return RegistryObject.of(entry::get);
        }

        @Override
        public RegistryObject<CreativeModeTab> registerCreativeTab(String id, Supplier<CreativeModeTab> factory) {
            DeferredHolder<CreativeModeTab, CreativeModeTab> entry = CREATIVE_TABS.register(path(id), factory);
            return RegistryObject.of(entry::get);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> RegistryObject<DataComponentType<T>> registerDataComponent(
                String id, Supplier<DataComponentType<T>> factory
        ) {
            DeferredHolder<DataComponentType<?>, DataComponentType<T>> entry =
                    (DeferredHolder<DataComponentType<?>, DataComponentType<T>>) (DeferredHolder<?, ?>)
                            DATA_COMPONENTS.register(path(id), factory);
            return RegistryObject.of(entry::get);
        }
    };

    private NeoForgePlatform() {
    }

    public static void install(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        CREATIVE_TABS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        PlatformServices.install(REGISTRY, PacketDistributor::sendToPlayer, ClientPacketDistributor::sendToServer);
    }

    private static String path(String id) {
        int separator = id.indexOf(':');
        return separator >= 0 ? id.substring(separator + 1) : id;
    }
}
