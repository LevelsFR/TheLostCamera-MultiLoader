package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.block.entity.RuinAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Server-side restoration runtime for ruins that no longer place a technical anchor block in the world. */
public final class VirtualRuinRestorationManager {
    private static final Map<Key, RuinAnchorBlockEntity> ACTIVE = new HashMap<>();
    private static MinecraftServer activeServer;

    private VirtualRuinRestorationManager() {
    }

    public static boolean begin(ServerLevel level, BlockPos anchorPos, String ruinId) {
        return begin(level, anchorPos, ruinId, null);
    }

    public static boolean begin(ServerLevel level, BlockPos anchorPos, String ruinId, RestorationRotation forcedRotation) {
        Key key = new Key(level.dimension(), anchorPos.immutable());
        if (ACTIVE.containsKey(key)) {
            return false;
        }

        RestoredRuinSavedData restoredRuins = RestoredRuinSavedData.get(level);
        if (restoredRuins.hasCompleted(level, anchorPos, ruinId)) {
            return false;
        }
        if (restoredRuins.hasStarted(level, anchorPos, ruinId)) {
            if (RuinAnchorBlockEntity.restoreSilently(level, anchorPos.immutable(), ruinId, forcedRotation)) {
                restoredRuins.markCompleted(level, anchorPos, ruinId);
            }
            return false;
        }

        RuinAnchorBlockEntity virtual = RuinAnchorBlockEntity.virtual(level, anchorPos.immutable(), ruinId);
        if (!virtual.beginRestoration(forcedRotation)) {
            return false;
        }

        restoredRuins.markStarted(level, anchorPos, ruinId,
                forcedRotation == null ? virtual.currentRotationForPersistence() : forcedRotation);
        ACTIVE.put(key, virtual);
        return true;
    }

    public static boolean isRestoring(ServerLevel level, BlockPos anchorPos) {
        return ACTIVE.containsKey(new Key(level.dimension(), anchorPos.immutable()));
    }

    public static void markCompleted(ServerLevel level, BlockPos anchorPos, String ruinId) {
        RestoredRuinSavedData.get(level).markCompleted(level, anchorPos, ruinId);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static void onServerTick(MinecraftServer server) {
        if (activeServer != server) {
            activeServer = server;
            ACTIVE.clear();
        }
        tick(server);
    }

    private static void tick(MinecraftServer server) {
        recoverInterruptedRestorations(server);
        Iterator<Map.Entry<Key, RuinAnchorBlockEntity>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, RuinAnchorBlockEntity> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getKey().dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }

            RuinAnchorBlockEntity virtual = entry.getValue();
            RuinAnchorBlockEntity.tickVirtual(level, virtual);
            if (virtual.isRestored()) {
                RestoredRuinSavedData.get(level).markCompleted(level, virtual.getBlockPos(), virtual.getRuinId());
                iterator.remove();
            } else if (!virtual.isRestoring()) {
                iterator.remove();
            }
        }
    }

    private static void recoverInterruptedRestorations(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount(), 20) != 0) {
            return;
        }
        RestoredRuinSavedData data = RestoredRuinSavedData.get(server.overworld());
        for (RestoredRuinSavedData.PendingRestoration pending : data.pendingRestorations()) {
            ServerLevel targetLevel = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().toString().equals(pending.dimension())) {
                    targetLevel = candidate;
                    break;
                }
            }
            if (targetLevel == null || !targetLevel.hasChunkAt(pending.anchorPos())) {
                continue;
            }
            Key key = new Key(targetLevel.dimension(), pending.anchorPos().immutable());
            if (ACTIVE.containsKey(key)) {
                continue;
            }
            if (RuinAnchorBlockEntity.restoreSilently(
                    targetLevel, pending.anchorPos(), pending.ruinId(), pending.rotation())) {
                data.markCompleted(targetLevel, pending.anchorPos(), pending.ruinId());
            }
        }
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }
}



