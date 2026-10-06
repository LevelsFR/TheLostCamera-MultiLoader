package com.levelscraft7.thelostcamera.ruin;

/** Coarse authored size class used by restoration timing and the album filters. */
public enum RuinSizeClass {
    SMALL("ruin.thelostcamera.size.small", 120),
    MEDIUM("ruin.thelostcamera.size.medium", 220),
    LARGE("ruin.thelostcamera.size.large", 340);

    private final String translationKey;
    private final int targetDurationTicks;

    RuinSizeClass(String translationKey, int targetDurationTicks) {
        this.translationKey = translationKey;
        this.targetDurationTicks = targetDurationTicks;
    }

    public String translationKey() {
        return translationKey;
    }

    public int targetDurationTicks() {
        return targetDurationTicks;
    }
}


