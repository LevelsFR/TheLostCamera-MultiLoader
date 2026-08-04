package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.block.RuinAnchorBlock;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TheLostCamera.MOD_ID);

    /**
     * Hidden technical anchor embedded in generated ruins. It behaves like bedrock and has no item drop.
     */
    public static final DeferredBlock<RuinAnchorBlock> RUIN_ANCHOR = BLOCKS.registerBlock(
            "ruin_anchor",
            RuinAnchorBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .noCollision()
                    .noOcclusion()
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
