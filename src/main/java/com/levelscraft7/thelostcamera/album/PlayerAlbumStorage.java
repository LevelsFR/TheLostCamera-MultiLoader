package com.levelscraft7.thelostcamera.album;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.data.PhotoData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative personal album library.
 * Data is stored by player UUID in the world save, independently from any album ItemStack.
 */
public final class PlayerAlbumStorage {
    private static final LevelResource DIRECTORY = new LevelResource("thelostcamera/albums");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlayerAlbumStorage() {
    }

    public static List<StoredPhoto> load(ServerPlayer player) {
        Path primary = file(player);
        List<StoredPhoto> loaded = read(primary, player.getUUID());
        if (loaded != null) {
            return loaded;
        }

        Path backup = backupFile(primary);
        loaded = read(backup, player.getUUID());
        if (loaded != null) {
            TheLostCamera.LOGGER.warn("Recovered personal photo album for {} from its backup", player.getUUID());
            return loaded;
        }
        return List.of();
    }

    public static boolean add(ServerPlayer player, PhotoData data) {
        if (data == null || data.imageId() == null) {
            return false;
        }
        List<StoredPhoto> photos = new ArrayList<>(load(player));
        if (photos.stream().anyMatch(entry -> entry.data().imageId().equals(data.imageId()))) {
            return false;
        }
        photos.add(new StoredPhoto(data, false, false));
        return save(player, photos);
    }

    /** Imports old ItemStack-backed album contents in one atomic save. A non-negative result means the migration is safe to clear. */
    public static int importLegacy(ServerPlayer player, List<PhotoData> legacyPhotos) {
        if (legacyPhotos.isEmpty()) {
            return 0;
        }

        Map<UUID, StoredPhoto> merged = new LinkedHashMap<>();
        for (StoredPhoto entry : load(player)) {
            merged.put(entry.data().imageId(), entry);
        }

        int added = 0;
        for (PhotoData data : legacyPhotos) {
            if (data == null || data.imageId() == null || merged.containsKey(data.imageId())) {
                continue;
            }
            merged.put(data.imageId(), new StoredPhoto(data, false, false));
            added++;
        }

        if (added == 0) {
            return 0;
        }
        return save(player, new ArrayList<>(merged.values())) ? added : -1;
    }

    public static boolean setArchived(ServerPlayer player, UUID imageId, boolean archived) {
        return replace(player, imageId, entry -> entry.withArchived(archived));
    }

    public static boolean setFavorite(ServerPlayer player, UUID imageId, boolean favorite) {
        return replace(player, imageId, entry -> entry.withFavorite(favorite));
    }

    public static int photoCount(ServerPlayer player) {
        return load(player).size();
    }

    public static int documentedRuinCount(ServerPlayer player) {
        return (int) load(player).stream()
                .map(StoredPhoto::data)
                .filter(PhotoData::hasRuin)
                .map(PhotoData::ruinId)
                .distinct()
                .count();
    }

    public static int clear(ServerPlayer player) {
        int previousCount = load(player).size();
        return save(player, List.of()) ? previousCount : -1;
    }

    private static boolean replace(ServerPlayer player, UUID imageId, EntryTransformer transformer) {
        List<StoredPhoto> photos = new ArrayList<>(load(player));
        for (int index = 0; index < photos.size(); index++) {
            StoredPhoto entry = photos.get(index);
            if (!entry.data().imageId().equals(imageId)) {
                continue;
            }
            photos.set(index, transformer.apply(entry));
            return save(player, photos);
        }
        return false;
    }

    private static List<StoredPhoto> read(Path file, UUID playerId) {
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            AlbumFile decoded = GSON.fromJson(reader, AlbumFile.class);
            if (decoded == null || decoded.photos == null) {
                return List.of();
            }
            List<StoredPhoto> result = new ArrayList<>();
            for (StoredPhoto entry : decoded.photos) {
                if (entry != null && entry.data() != null && entry.data().imageId() != null) {
                    result.add(entry);
                }
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException exception) {
            TheLostCamera.LOGGER.error("Failed to read personal photo album {} for {}", file, playerId, exception);
            return null;
        }
    }

    private static boolean save(ServerPlayer player, List<StoredPhoto> photos) {
        Path file = file(player);
        Path backup = backupFile(file);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(new AlbumFile(1, List.copyOf(photos)), writer);
            }

            if (Files.isRegularFile(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            TheLostCamera.LOGGER.error("Failed to save personal photo album for {}", player.getUUID(), exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Keep the original failure as the useful diagnostic.
            }
            return false;
        }
    }

    private static Path file(ServerPlayer player) {
        Path directory = player.level().getServer().getWorldPath(DIRECTORY);
        return directory.resolve(player.getUUID() + ".json");
    }

    private static Path backupFile(Path primary) {
        return primary.resolveSibling(primary.getFileName() + ".bak");
    }

    private record AlbumFile(int version, List<StoredPhoto> photos) {
    }

    @FunctionalInterface
    private interface EntryTransformer {
        StoredPhoto apply(StoredPhoto entry);
    }
}
