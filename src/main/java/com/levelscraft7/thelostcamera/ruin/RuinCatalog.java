package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Central authored definition list. The catalog intentionally contains only
 * the three custom structures currently validated for the mod.
 */
public final class RuinCatalog {
    private static final List<Entry> ENTRIES = List.of(
            entry("solstice_shrine", RuinSizeClass.MEDIUM),
            entry("orma_homestead", RuinSizeClass.MEDIUM),
            entry("frontier_watchtower", RuinSizeClass.SMALL)
    );

    private RuinCatalog() {
    }

    private static Entry entry(String path, RuinSizeClass size) {
        String key = "ruin.thelostcamera." + path;
        return new Entry(
                Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, path),
                key + ".name",
                key + ".hint",
                key + ".biomes",
                key + ".description",
                key + ".echo",
                size,
                RuinRarity.COMMON
        );
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String id) {
        return ENTRIES.stream().filter(entry -> entry.id().toString().equals(id)).findFirst();
    }

    public static Entry fallback() {
        return ENTRIES.getFirst();
    }

    public record Entry(
            Identifier id,
            String nameKey,
            String hintKey,
            String biomeHintKey,
            String descriptionKey,
            String echoKey,
            RuinSizeClass size,
            RuinRarity rarity
    ) {
    }
}
