package com.levelscraft7.thelostcamera.ruin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** One authoritative target position from the restored structure template. */
public record RestorationBlock(
        int x,
        int y,
        int z,
        BlockState state,
        BlockState sourceState,
        CompoundTag blockEntityTag
) {
    public boolean hasBlockEntityData() {
        return blockEntityTag != null && !blockEntityTag.isEmpty();
    }

    public boolean requiresSourceMatch() {
        return sourceState != null;
    }
}


