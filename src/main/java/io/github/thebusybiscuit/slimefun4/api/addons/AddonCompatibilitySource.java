package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Describes where an addon's compatibility declaration came from. */
@SlimefunAPI
public enum AddonCompatibilitySource {
    EXPLICIT_REGISTRATION("Runtime registration"),
    PROVIDER_INTERFACE("Provider interface"),
    EMBEDDED_MANIFEST("Embedded manifest"),
    LEGACY_MAINTAINED_CATALOG("Slimefun Legacy maintained addon catalog"),
    NONE("No declaration");

    private final String displayName;

    AddonCompatibilitySource(String displayName) {
        this.displayName = displayName;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }
}
