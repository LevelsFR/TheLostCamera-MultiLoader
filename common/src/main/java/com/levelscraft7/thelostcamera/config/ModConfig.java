package com.levelscraft7.thelostcamera.config;

/** Loader-neutral configuration defaults. Loader adapters may persist these values in their config directory. */
public final class ModConfig {
    public static final Value<Integer> RUIN_DETECTION_RANGE = new Value<>(50);
    public static final Value<Integer> RUIN_TARGET_SEARCH_RADIUS = new Value<>(16);
    public static final Value<Integer> PHOTO_COOLDOWN_TICKS = new Value<>(40);
    public static final Value<Integer> PHOTO_MAX_DIMENSION = new Value<>(5120);
    public static final Value<Integer> MAX_PHOTO_BYTES = new Value<>(20_000_000);
    public static final Value<Integer> RESTORATION_MIN_DURATION_TICKS = new Value<>(120);
    public static final Value<Integer> RESTORATION_MAX_DURATION_TICKS = new Value<>(440);
    public static final Value<Integer> RESTORATION_MAX_BLOCKS_PER_TICK = new Value<>(64);
    public static final Value<Integer> RESTORATION_WAVE_PAUSE_TICKS = new Value<>(2);
    public static final Value<Double> RESTORATION_INITIAL_SHAKE_INTENSITY = new Value<>(4.2D);
    public static final Value<Double> RESTORATION_AMBIENT_SHAKE_INTENSITY = new Value<>(0.11D);

    private ModConfig() {
    }

    public static final class Value<T> {
        private T value;

        private Value(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }
    }
}


