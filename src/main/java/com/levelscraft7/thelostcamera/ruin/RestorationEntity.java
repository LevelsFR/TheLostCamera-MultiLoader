package com.levelscraft7.thelostcamera.ruin;

import net.minecraft.nbt.CompoundTag;

/** One entity stored by a vanilla structure NBT, relative to the ruin anchor. */
public record RestorationEntity(
        int index,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        CompoundTag tag,
        boolean living
) {
    public int waveY() {
        return (int) Math.floor(y);
    }
}
