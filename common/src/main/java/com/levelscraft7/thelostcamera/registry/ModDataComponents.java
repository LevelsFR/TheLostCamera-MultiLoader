package com.levelscraft7.thelostcamera.registry;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import com.levelscraft7.thelostcamera.platform.RegistryObject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;

public final class ModDataComponents {
    public static RegistryObject<DataComponentType<Unit>> CAMERA_ACTIVE;
    public static RegistryObject<DataComponentType<PhotoData>> PHOTO_DATA;
    public static RegistryObject<DataComponentType<RuinPhotoData>> RUIN_PHOTO_DATA;
    public static RegistryObject<DataComponentType<String>> ALBUM_OWNER;
    public static RegistryObject<DataComponentType<String>> ALBUM_OWNER_NAME;
    public static RegistryObject<DataComponentType<Integer>> CAMERA_FOCAL_MM;

    private ModDataComponents() {
    }

    public static void register() {
        CAMERA_ACTIVE = PlatformServices.registry().registerDataComponent(TheLostCamera.MOD_ID + ":camera_active", () -> DataComponentType.<Unit>builder()
                .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).build());
        PHOTO_DATA = PlatformServices.registry().registerDataComponent(TheLostCamera.MOD_ID + ":photo_data", () -> DataComponentType.<PhotoData>builder()
                .persistent(PhotoData.CODEC).networkSynchronized(PhotoData.STREAM_CODEC).build());
        RUIN_PHOTO_DATA = PlatformServices.registry().registerDataComponent(TheLostCamera.MOD_ID + ":ruin_photo_data", () -> DataComponentType.<RuinPhotoData>builder()
                .persistent(RuinPhotoData.CODEC).networkSynchronized(RuinPhotoData.STREAM_CODEC).build());
        ALBUM_OWNER = stringComponent("album_owner");
        ALBUM_OWNER_NAME = stringComponent("album_owner_name");
        CAMERA_FOCAL_MM = PlatformServices.registry().registerDataComponent(TheLostCamera.MOD_ID + ":camera_focal_mm", () -> DataComponentType.<Integer>builder()
                .persistent(com.mojang.serialization.Codec.INT)
                .networkSynchronized(ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.INT)).build());
    }

    private static RegistryObject<DataComponentType<String>> stringComponent(String id) {
        return PlatformServices.registry().registerDataComponent(TheLostCamera.MOD_ID + ":" + id, () -> DataComponentType.<String>builder()
                .persistent(com.mojang.serialization.Codec.STRING)
                .networkSynchronized(ByteBufCodecs.fromCodec(com.mojang.serialization.Codec.STRING)).build());
    }
}


