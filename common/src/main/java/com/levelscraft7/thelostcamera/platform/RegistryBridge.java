package com.levelscraft7.thelostcamera.platform;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Registration seam implemented by Fabric and NeoForge. */
public interface RegistryBridge {
    RegistryObject<Block> registerBlock(String id, Supplier<? extends Block> factory);

    RegistryObject<Item> registerItem(String id, Supplier<? extends Item> factory);

    <T extends net.minecraft.world.level.block.entity.BlockEntity> RegistryObject<BlockEntityType<T>> registerBlockEntity(
            String id, BiFunction<BlockPos, BlockState, T> factory, Supplier<? extends Block>... validBlocks
    );

    RegistryObject<CreativeModeTab> registerCreativeTab(String id, Supplier<CreativeModeTab> factory);

    <T> RegistryObject<DataComponentType<T>> registerDataComponent(String id, Supplier<DataComponentType<T>> factory);
}


