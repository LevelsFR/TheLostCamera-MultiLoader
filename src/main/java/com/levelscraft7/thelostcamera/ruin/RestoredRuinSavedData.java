package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Persistent world state for started and completed ruin restorations. */
public final class RestoredRuinSavedData extends SavedData {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, "restored_ruins");

    private static final Codec<RestoredRuinSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("started", List.of()).forGetter(data -> List.copyOf(data.started)),
            Codec.STRING.listOf().optionalFieldOf("completed", List.of()).forGetter(data -> List.copyOf(data.completed)),
            Codec.STRING.listOf().optionalFieldOf("in_progress", List.of()).forGetter(RestoredRuinSavedData::serializedInProgress)
    ).apply(instance, RestoredRuinSavedData::new));

    private static final SavedDataType<RestoredRuinSavedData> TYPE = new SavedDataType<>(
            ID,
            (Supplier<RestoredRuinSavedData>) RestoredRuinSavedData::new,
            CODEC,
            null
    );

    private final Set<String> started;
    private final Set<String> completed;
    private final Map<String, Integer> inProgress;

    public RestoredRuinSavedData() {
        this.started = new HashSet<>();
        this.completed = new HashSet<>();
        this.inProgress = new HashMap<>();
    }

    private RestoredRuinSavedData(List<String> started, List<String> completed, List<String> inProgress) {
        this.started = new HashSet<>(started);
        this.completed = new HashSet<>(completed);
        this.inProgress = new HashMap<>();
        for (String serialized : inProgress) {
            int separator = serialized.lastIndexOf("|r=");
            if (separator <= 0) {
                continue;
            }
            try {
                this.inProgress.put(serialized.substring(0, separator),
                        Integer.parseInt(serialized.substring(separator + 3)));
            } catch (NumberFormatException ignored) {
            }
        }
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

    public void markStarted(ServerLevel level, BlockPos anchorPos, String ruinId, RestorationRotation rotation) {
        String key = key(level, anchorPos, ruinId);
        boolean changed = started.add(key);
        int ordinal = rotation == null ? RestorationRotation.NONE.ordinal() : rotation.ordinal();
        Integer previous = inProgress.put(key, ordinal);
        changed |= previous == null || previous != ordinal;
        if (changed) {
            setDirty();
        }
    }

    public void markCompleted(ServerLevel level, BlockPos anchorPos, String ruinId) {
        String key = key(level, anchorPos, ruinId);
        boolean changed = started.add(key);
        changed |= completed.add(key);
        changed |= inProgress.remove(key) != null;
        if (changed) {
            setDirty();
        }
    }

    public List<PendingRestoration> pendingRestorations() {
        List<PendingRestoration> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inProgress.entrySet()) {
            PendingRestoration parsed = parse(entry.getKey(), entry.getValue());
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return List.copyOf(result);
    }

    private List<String> serializedInProgress() {
        return inProgress.entrySet().stream()
                .map(entry -> entry.getKey() + "|r=" + entry.getValue())
                .sorted()
                .toList();
    }

    private static PendingRestoration parse(String key, int rotationOrdinal) {
        String[] parts = key.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }
        String[] coordinates = parts[2].split(",", 3);
        if (coordinates.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(coordinates[0]);
            int y = Integer.parseInt(coordinates[1]);
            int z = Integer.parseInt(coordinates[2]);
            RestorationRotation[] rotations = RestorationRotation.values();
            RestorationRotation rotation = rotations[Math.floorMod(rotationOrdinal, rotations.length)];
            return new PendingRestoration(parts[0], parts[1], new BlockPos(x, y, z), rotation);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String key(ServerLevel level, BlockPos anchorPos, String ruinId) {
        return level.dimension() + "|" + ruinId + "|"
                + anchorPos.getX() + "," + anchorPos.getY() + "," + anchorPos.getZ();
    }

    public record PendingRestoration(
            String dimension,
            String ruinId,
            BlockPos anchorPos,
            RestorationRotation rotation
    ) {
    }
}
