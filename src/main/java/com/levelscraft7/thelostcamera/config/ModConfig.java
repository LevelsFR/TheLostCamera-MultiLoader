package com.levelscraft7.thelostcamera.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RUIN_DETECTION_RANGE = BUILDER
            .comment("Maximum distance in blocks at which the camera can recognize one of The Lost Camera's ruins.")
            .defineInRange("ruinDetectionRange", 50, 1, 128);

    public static final ModConfigSpec.IntValue RUIN_TARGET_SEARCH_RADIUS = BUILDER
            .comment("Radius searched around the centre of the camera view for a generated ruin anchor.")
            .defineInRange("ruinTargetSearchRadius", 16, 4, 32);

    public static final ModConfigSpec.IntValue PHOTO_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown between two photographs, in ticks.")
            .defineInRange("photoCooldownTicks", 40, 0, 1200);

    public static final ModConfigSpec.IntValue PHOTO_MAX_DIMENSION = BUILDER
            .comment("Maximum width or height after the native frame has been cropped to the camera's square sensor.")
            .defineInRange("photoNativeMaxDimension", 5120, 640, 8192);

    public static final ModConfigSpec.IntValue MAX_PHOTO_BYTES = BUILDER
            .comment("Maximum accepted lossless PNG photograph size in bytes.")
            .defineInRange("maxPhotoBytes", 20_000_000, 256_000, 32_000_000);

    public static final ModConfigSpec.IntValue RESTORATION_MIN_DURATION_TICKS = BUILDER
            .comment("Minimum target restoration duration for a small ruin, in ticks.")
            .defineInRange("restorationMinDurationTicks", 120, 40, 600);

    public static final ModConfigSpec.IntValue RESTORATION_MAX_DURATION_TICKS = BUILDER
            .comment("Maximum target restoration duration even for a very large ruin, in ticks.")
            .defineInRange("restorationMaxDurationTicks", 440, 80, 1200);

    public static final ModConfigSpec.IntValue RESTORATION_MAX_BLOCKS_PER_TICK = BUILDER
            .comment("Safety cap for blocks restored during one server tick.")
            .defineInRange("restorationMaxBlocksPerTick", 64, 1, 256);

    public static final ModConfigSpec.IntValue RESTORATION_WAVE_PAUSE_TICKS = BUILDER
            .comment("Brief pause between Rising Core Bloom waves.")
            .defineInRange("restorationWavePauseTicks", 2, 0, 20);

    public static final ModConfigSpec.DoubleValue RESTORATION_INITIAL_SHAKE_INTENSITY = BUILDER
            .comment("Short impact shake immediately after a ruin photograph.")
            .defineInRange("restorationInitialShakeIntensity", 4.2D, 0.0D, 5.0D);

    public static final ModConfigSpec.DoubleValue RESTORATION_AMBIENT_SHAKE_INTENSITY = BUILDER
            .comment("Subtle long shake while the structure rises.")
            .defineInRange("restorationAmbientShakeIntensity", 0.11D, 0.0D, 0.5D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ModConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(Type.COMMON, SPEC);
    }
}
