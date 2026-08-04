package com.levelscraft7.thelostcamera.ruin;

import net.minecraft.world.level.block.Rotation;

/** Quarter-turn rotation around the ruin anchor. */
public enum RestorationRotation {
    NONE(Rotation.NONE, 0.0F),
    CLOCKWISE_90(Rotation.CLOCKWISE_90, 90.0F),
    CLOCKWISE_180(Rotation.CLOCKWISE_180, 180.0F),
    COUNTERCLOCKWISE_90(Rotation.COUNTERCLOCKWISE_90, -90.0F);

    private final Rotation vanillaRotation;
    private final float yawOffset;

    RestorationRotation(Rotation vanillaRotation, float yawOffset) {
        this.vanillaRotation = vanillaRotation;
        this.yawOffset = yawOffset;
    }

    public Rotation vanillaRotation() {
        return vanillaRotation;
    }

    public float yawOffset() {
        return yawOffset;
    }

    public int rotateX(int x, int z) {
        return switch (this) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    public int rotateZ(int x, int z) {
        return switch (this) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    public double rotateX(double x, double z) {
        return switch (this) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    public double rotateZ(double x, double z) {
        return switch (this) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }
}
