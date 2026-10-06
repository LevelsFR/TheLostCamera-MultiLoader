package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks only already-loaded chunks containing one of this mod's structure starts.
 * No block or structure search is performed around players when no relevant start is loaded.
 */
public final class RuinPresenceTracker {
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final double HINT_RANGE = 50.0D;
    private static final double HINT_RANGE_SQUARED = HINT_RANGE * HINT_RANGE;

    private static final Map<ResourceKey<Level>, Map<Long, List<LoadedRuin>>> LOADED = new HashMap<>();
    private static final Map<UUID, Set<String>> HINTED = new HashMap<>();
    private static long lastCheckTick = Long.MIN_VALUE;
    private static MinecraftServer activeServer;

    private RuinPresenceTracker() {
    }

    public static void onChunkLoad(ServerLevel level, ChunkAccess chunk) {
        prepareServer(level.getServer());
        cacheChunk(level, chunk);
    }

    public static void onChunkUnload(ServerLevel level, ChunkAccess chunk) {
        prepareServer(level.getServer());
        ChunkPos pos = chunk.getPos();
        Map<Long, List<LoadedRuin>> byChunk = LOADED.get(level.dimension());
        if (byChunk == null) {
            return;
        }
        byChunk.remove(pos.pack());
        if (byChunk.isEmpty()) {
            LOADED.remove(level.dimension());
        }
    }

    public static void onServerTick(MinecraftServer server) {
        prepareServer(server);
        long tick = server.getTickCount();
        if (tick == lastCheckTick || Math.floorMod(tick, CHECK_INTERVAL_TICKS) != 0L || LOADED.isEmpty()) {
            return;
        }
        lastCheckTick = tick;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            Map<Long, List<LoadedRuin>> byChunk = LOADED.get(level.dimension());
            if (byChunk == null || byChunk.isEmpty()) {
                continue;
            }
            checkPlayer(player, byChunk);
        }
    }

    private static void prepareServer(MinecraftServer server) {
        if (activeServer == server) {
            return;
        }
        activeServer = server;
        LOADED.clear();
        HINTED.clear();
        lastCheckTick = Long.MIN_VALUE;
    }

    private static void cacheChunk(ServerLevel level, ChunkAccess chunk) {
        List<LoadedRuin> found = new ArrayList<>();
        var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) {
                continue;
            }
            Identifier id = structureRegistry.getKey(entry.getKey());
            if (id == null || !TheLostCamera.MOD_ID.equals(id.getNamespace()) || RuinCatalog.find(id.toString()).isEmpty()) {
                continue;
            }
            found.add(new LoadedRuin(id.toString(), start.getBoundingBox()));
        }

        long chunkKey = chunk.getPos().pack();
        Map<Long, List<LoadedRuin>> byChunk = LOADED.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        if (found.isEmpty()) {
            byChunk.remove(chunkKey);
            if (byChunk.isEmpty()) {
                LOADED.remove(level.dimension());
            }
        } else {
            byChunk.put(chunkKey, List.copyOf(found));
        }
    }

    private static void checkPlayer(ServerPlayer player, Map<Long, List<LoadedRuin>> byChunk) {
        ChunkPos playerChunk = player.chunkPosition();
        int chunkRadius = (int) Math.ceil(HINT_RANGE / 16.0D) + 1;
        for (int chunkX = playerChunk.x() - chunkRadius; chunkX <= playerChunk.x() + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunk.z() - chunkRadius; chunkZ <= playerChunk.z() + chunkRadius; chunkZ++) {
                List<LoadedRuin> group = byChunk.get(ChunkPos.pack(chunkX, chunkZ));
                if (group == null) {
                    continue;
                }
                for (LoadedRuin ruin : group) {
                    if (tryHintPlayer(player, ruin)) {
                        return;
                    }
                }
            }
        }
    }

    private static boolean tryHintPlayer(ServerPlayer player, LoadedRuin ruin) {
        double centreX = (ruin.bounds.minX() + ruin.bounds.maxX() + 1) * 0.5D;
        double centreY = (ruin.bounds.minY() + ruin.bounds.maxY() + 1) * 0.5D;
        double centreZ = (ruin.bounds.minZ() + ruin.bounds.maxZ() + 1) * 0.5D;
        double dx = player.getX() - centreX;
        double dy = player.getY() - centreY;
        double dz = player.getZ() - centreZ;
        if (dx * dx + dy * dy + dz * dz > HINT_RANGE_SQUARED) {
            return false;
        }

        String key = player.level().dimension() + "|" + ruin.ruinId + "|"
                + ruin.bounds.minX() + "," + ruin.bounds.minY() + "," + ruin.bounds.minZ();
        if (hasDiscoveryTool(player)) {
            return false;
        }

        Set<String> playerHints = HINTED.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
        if (!playerHints.add(key)) {
            return false;
        }

        player.sendSystemMessage(Component.translatable("message.thelostcamera.ruin_proximity_hint"));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.AMBIENT, 0.65F, 1.28F);
        return true;
    }

    private static boolean hasDiscoveryTool(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == ModItems.LOST_CAMERA.get() || stack.getItem() == ModItems.PHOTO_ALBUM.get()) {
                return true;
            }
        }
        return false;
    }


    private record LoadedRuin(String ruinId, BoundingBox bounds) {
    }
}



