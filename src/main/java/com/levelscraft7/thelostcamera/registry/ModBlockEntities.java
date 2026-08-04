package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.block.entity.RuinAnchorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TheLostCamera.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RuinAnchorBlockEntity>> RUIN_ANCHOR =
            BLOCK_ENTITY_TYPES.register(
                    "ruin_anchor",
                    () -> new BlockEntityType<>(RuinAnchorBlockEntity::new, false, ModBlocks.RUIN_ANCHOR.get())
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
