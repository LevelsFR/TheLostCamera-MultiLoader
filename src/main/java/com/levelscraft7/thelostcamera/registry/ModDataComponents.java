package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TheLostCamera.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> CAMERA_ACTIVE =
            COMPONENTS.register("camera_active", () -> DataComponentType.<Unit>builder()
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PhotoData>> PHOTO_DATA =
            COMPONENTS.register("photo_data", () -> DataComponentType.<PhotoData>builder()
                    .persistent(PhotoData.CODEC)
                    .networkSynchronized(PhotoData.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RuinPhotoData>> RUIN_PHOTO_DATA =
            COMPONENTS.register("ruin_photo_data", () -> DataComponentType.<RuinPhotoData>builder()
                    .persistent(RuinPhotoData.CODEC)
                    .networkSynchronized(RuinPhotoData.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ALBUM_OWNER =
            COMPONENTS.register("album_owner", () -> DataComponentType.<String>builder()
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.STRING))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ALBUM_OWNER_NAME =
            COMPONENTS.register("album_owner_name", () -> DataComponentType.<String>builder()
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.STRING))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CAMERA_FOCAL_MM =
            COMPONENTS.register("camera_focal_mm", () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.INT))
                    .build());

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
