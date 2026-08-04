package com.levelscraft7.thelostcamera.ruin;

/** Human-readable authored rarity. Actual worldgen frequency remains defined by structure_set JSON. */
public enum RuinRarity {
    COMMON("ruin.thelostcamera.rarity.common"),
    UNCOMMON("ruin.thelostcamera.rarity.uncommon"),
    RARE("ruin.thelostcamera.rarity.rare");

    private final String translationKey;

    RuinRarity(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
