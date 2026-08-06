package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;

/** Identifies a Slimefun core family that an addon has been tested against. */
@SlimefunAPI
public enum SlimefunCoreVariant {
    ORIGINAL("original", "Original Slimefun"),
    GUGU("gugu", "Slimefun Gugu"),
    UNITED("united", "Slimefun United"),
    SLIMEFUN5("slimefun5", "Slimefun5"),
    SLIMEFUN_CORE("slimefun-core", "Slimefun Core"),
    LEGACY("legacy", "Slimefun Legacy"),
    UNKNOWN("unknown", "Unknown core");

    private final String id;
    private final String displayName;

    SlimefunCoreVariant(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public @Nonnull String getId() {
        return id;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    /**
     * Parses the stable manifest identifier or enum name for a core variant.
     *
     * @param value an identifier such as {@code legacy}, {@code gugu}, or {@code slimefun-core}
     * @return the matching variant, or an empty optional for an unknown value
     */
    public static @Nonnull Optional<SlimefunCoreVariant> fromId(@Nonnull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (SlimefunCoreVariant variant : values()) {
            if (variant.id.equals(normalized)
                    || variant.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }
}
