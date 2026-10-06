package com.levelscraft7.thelostcamera.platform;

import java.util.Objects;
import java.util.function.Supplier;

/** Small loader-neutral view of a registered object. */
public final class RegistryObject<T> implements Supplier<T> {
    private final Supplier<T> supplier;

    private RegistryObject(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    public static <T> RegistryObject<T> of(Supplier<T> supplier) {
        return new RegistryObject<>(supplier);
    }

    @Override
    public T get() {
        return Objects.requireNonNull(supplier.get(), "Registered object is not available yet");
    }
}


