package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent world state for ruins whose restoration animation has already been consumed. */
public final class RestoredRuinSavedData extends SavedData {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "restored_ruins");

    private static final Codec<RestoredRuinSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("started", List.of()).forGetter(data -> List.copyOf(data.started)),
            Codec.STRING.listOf().optionalFieldOf("completed", List.of()).forGetter(data -> List.copyOf(data.completed))
    ).apply(instance, RestoredRuinSavedData::new));

    private static final SavedDataType<RestoredRuinSavedData> TYPE = new SavedDataType<>(
            ID,
            RestoredRuinSavedData::new,
            CODEC
    );

    private final Set<String> started;
    private final Set<String> completed;

    public RestoredRuinSavedData() {
        this.started = new HashSet<>();
        this.completed = new HashSet<>();
    }

    private RestoredRuinSavedData(List<String> started, List<String> completed) {
        this.started = new HashSet<>(started);
        this.completed = new HashSet<>(completed);
    }

    public static RestoredRuinSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean hasStarted(ServerLevel level, BlockPos anchorPos, String ruinId) {
        return started.contains(key(level, anchorPos, ruinId));
    }

    public boolean hasCompleted(ServerLevel level, BlockPos anchorPos, String ruinId) {
        return completed.contains(key(level, anchorPos, ruinId));
    }

    public void markStarted(ServerLevel level, BlockPos anchorPos, String ruinId) {
        if (started.add(key(level, anchorPos, ruinId))) {
            setDirty();
        }
    }

    public void markCompleted(ServerLevel level, BlockPos anchorPos, String ruinId) {
        String key = key(level, anchorPos, ruinId);
        boolean changed = started.add(key);
        changed |= completed.add(key);
        if (changed) {
            setDirty();
        }
    }

    private static String key(ServerLevel level, BlockPos anchorPos, String ruinId) {
        return level.dimension() + "|" + ruinId + "|"
                + anchorPos.getX() + "," + anchorPos.getY() + "," + anchorPos.getZ();
    }
}
