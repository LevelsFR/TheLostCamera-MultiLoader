package com.levelscraft7.thelostcamera.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Identifies the exact ruin instance and whether the photograph shows it before or after restoration. */
public record RuinPhotoData(
        String dimension,
        int anchorX,
        int anchorY,
        int anchorZ,
        String stage
) {
    public static final Codec<RuinPhotoData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(RuinPhotoData::dimension),
            Codec.INT.fieldOf("anchor_x").forGetter(RuinPhotoData::anchorX),
            Codec.INT.fieldOf("anchor_y").forGetter(RuinPhotoData::anchorY),
            Codec.INT.fieldOf("anchor_z").forGetter(RuinPhotoData::anchorZ),
            Codec.STRING.optionalFieldOf("stage", "before").forGetter(RuinPhotoData::stage)
    ).apply(instance, RuinPhotoData::new));

    public static final StreamCodec<ByteBuf, RuinPhotoData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public boolean isBefore() {
        return "before".equals(stage);
    }

    public boolean isRestored() {
        return "restored".equals(stage);
    }

    public boolean sameInstance(RuinPhotoData other) {
        return other != null
                && dimension.equals(other.dimension)
                && anchorX == other.anchorX
                && anchorY == other.anchorY
                && anchorZ == other.anchorZ;
    }
}


