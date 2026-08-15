package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.TheLostCamera;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Central authored definition list for every ruin/restored template pair.
 */
public final class RuinCatalog {
    private static final List<Entry> ENTRIES = List.of(
            entry("solstice_shrine", RuinSizeClass.MEDIUM, RuinRarity.COMMON),
            entry("orma_homestead", RuinSizeClass.MEDIUM, RuinRarity.COMMON),
            entry("frontier_watchtower", RuinSizeClass.SMALL, RuinRarity.COMMON),
            entry("sunscar_pyramid", RuinSizeClass.MEDIUM, RuinRarity.UNCOMMON),
            entry("buried_waystone", RuinSizeClass.SMALL, RuinRarity.COMMON),
            entry("moonwell", RuinSizeClass.SMALL, RuinRarity.COMMON)
    );

    private RuinCatalog() {
    }

    private static Entry entry(String path, RuinSizeClass size, RuinRarity rarity) {
        String key = "ruin.thelostcamera." + path;
        return new Entry(
                Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, path),
                key + ".name",
                key + ".hint",
                key + ".biomes",
                key + ".description",
                key + ".echo",
                size,
                rarity
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
