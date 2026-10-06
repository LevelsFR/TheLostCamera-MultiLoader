package com.levelscraft7.thelostcamera.ruin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Variations of the same Rising Core Bloom restoration language. */
public enum RestorationPattern {
    RISING_CORE_BLOOM,
    RISING_CORE_BLOOM_CLOCKWISE,
    RISING_CORE_BLOOM_COUNTERCLOCKWISE;

    public List<RestorationBlock> order(List<RestorationBlock> source, long seed) {
        List<RestorationBlock> ordered = new ArrayList<>(source);
        ordered.sort(Comparator
                .comparingInt(this::wave)
                .thenComparingInt(RestorationBlock::y)
                .thenComparingInt(this::directionalKey)
                .thenComparingInt(block -> randomKey(block, seed)));
        return List.copyOf(ordered);
    }

    /**
     * The centre rises first, while lower blocks in progressively wider rings begin to bloom around it.
     * This interleaves vertical growth and radial expansion instead of completing either axis alone.
     */
    public int wave(RestorationBlock block) {
        int ring = (int) Math.floor(Math.sqrt((double) block.x() * block.x() + (double) block.z() * block.z()));
        int risingPhase = Math.max(0, block.y() - 1) / 2;
        return ring * 2 + risingPhase;
    }

    private int directionalKey(RestorationBlock block) {
        return switch (this) {
            case RISING_CORE_BLOOM -> Math.abs(block.x()) + Math.abs(block.z());
            case RISING_CORE_BLOOM_CLOCKWISE -> block.x() * 31 + block.z();
            case RISING_CORE_BLOOM_COUNTERCLOCKWISE -> -(block.x() * 31 + block.z());
        };
    }

    private int randomKey(RestorationBlock block, long seed) {
        long value = seed;
        value ^= (long) block.x() * 0x9E3779B97F4A7C15L;
        value ^= (long) block.y() * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) block.z() * 0x165667B19E3779F9L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) value;
    }
}


