package com.levelscraft7.thelostcamera.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Immutable metadata stored on a physical photograph item.
 * The image bytes are kept in the world save and referenced by {@link #imageId()}.
 */
public record PhotoData(
        UUID imageId,
        String photographer,
        long capturedAt,
        long worldTime,
        String dimension,
        int x,
        int y,
        int z,
        float yaw,
        float pitch,
        CameraSettings cameraSettings,
        String weather,
        int imageWidth,
        int imageHeight,
        String ruinId,
        boolean restorationTriggered
) {
    public static final Codec<PhotoData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.AUTHLIB_CODEC.fieldOf("image_id").forGetter(PhotoData::imageId),
            Codec.STRING.fieldOf("photographer").forGetter(PhotoData::photographer),
            Codec.LONG.fieldOf("captured_at").forGetter(PhotoData::capturedAt),
            Codec.LONG.optionalFieldOf("world_time", 0L).forGetter(PhotoData::worldTime),
            Codec.STRING.fieldOf("dimension").forGetter(PhotoData::dimension),
            Codec.INT.fieldOf("x").forGetter(PhotoData::x),
            Codec.INT.fieldOf("y").forGetter(PhotoData::y),
            Codec.INT.fieldOf("z").forGetter(PhotoData::z),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(PhotoData::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(PhotoData::pitch),
            CameraSettings.CODEC.optionalFieldOf("camera_settings", CameraSettings.DEFAULT).forGetter(PhotoData::cameraSettings),
            Codec.STRING.optionalFieldOf("weather", "clear").forGetter(PhotoData::weather),
            Codec.INT.optionalFieldOf("image_width", 0).forGetter(PhotoData::imageWidth),
            Codec.INT.optionalFieldOf("image_height", 0).forGetter(PhotoData::imageHeight),
            Codec.STRING.optionalFieldOf("ruin_id", "").forGetter(PhotoData::ruinId),
            Codec.BOOL.fieldOf("restoration_triggered").forGetter(PhotoData::restorationTriggered)
    ).apply(instance, PhotoData::new));

    public static final StreamCodec<ByteBuf, PhotoData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public boolean hasRuin() {
        return !ruinId.isBlank();
    }

    public boolean hasResolution() {
        return imageWidth > 0 && imageHeight > 0;
    }

    public PhotoData withResolution(int width, int height) {
        return new PhotoData(
                imageId,
                photographer,
                capturedAt,
                worldTime,
                dimension,
                x,
                y,
                z,
                yaw,
                pitch,
                cameraSettings,
                weather,
                width,
                height,
                ruinId,
                restorationTriggered
        );
    }

    public record CameraSettings(
            int focalLengthMm,
            int apertureTenths,
            int shutterDenominator,
            int iso
    ) {
        public static final CameraSettings DEFAULT = new CameraSettings(50, 28, 125, 100);

        public static final Codec<CameraSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("focal_length_mm", 50).forGetter(CameraSettings::focalLengthMm),
                Codec.INT.optionalFieldOf("aperture_tenths", 28).forGetter(CameraSettings::apertureTenths),
                Codec.INT.optionalFieldOf("shutter_denominator", 125).forGetter(CameraSettings::shutterDenominator),
                Codec.INT.optionalFieldOf("iso", 100).forGetter(CameraSettings::iso)
        ).apply(instance, CameraSettings::new));

        public float aperture() {
            return apertureTenths / 10.0F;
        }
    }
}
