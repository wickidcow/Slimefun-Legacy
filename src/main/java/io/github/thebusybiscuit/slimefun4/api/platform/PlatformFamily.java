package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Identifies the server platform family detected by Slimefun Legacy. */
@SlimefunAPI
public enum PlatformFamily {
    PAPER("Paper"),
    PURPUR("Purpur"),
    FOLIA("Folia"),
    PAPER_DERIVATIVE("Paper derivative"),
    UNKNOWN("Unknown");

    private final String displayName;

    PlatformFamily(String displayName) {
        this.displayName = displayName;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }
}
