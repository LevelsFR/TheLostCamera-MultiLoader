package com.levelscraft7.thelostcamera.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/** Parsed pair of ruined and restored vanilla structure templates. */
public record RestorationTemplate(
        int sizeX,
        int sizeY,
        int sizeZ,
        BlockPos ruinedAnchor,
        BlockPos restoredAnchor,
        Map<BlockPos, BlockState> ruinedBlocks,
        List<RestorationBlock> restoredTargets,
        List<RestorationEntity> restoredEntities
) {
}
