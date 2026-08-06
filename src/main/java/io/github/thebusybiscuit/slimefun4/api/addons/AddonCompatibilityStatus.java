package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Runtime compatibility state reported for an installed Slimefun addon. */
@SlimefunAPI
public enum AddonCompatibilityStatus {
    COMPATIBLE("Compatible", 0),
    WARNING("Warning", 1),
    UNDECLARED("Undeclared", 2),
    DISABLED("Disabled", 3),
    INCOMPATIBLE("Incompatible", 4);

    private final String displayName;
    private final int severity;

    AddonCompatibilityStatus(String displayName, int severity) {
        this.displayName = displayName;
        this.severity = severity;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public int getSeverity() {
        return severity;
    }
}
