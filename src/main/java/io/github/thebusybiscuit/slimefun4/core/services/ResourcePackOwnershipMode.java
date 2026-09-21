package io.github.thebusybiscuit.slimefun4.core.services;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Declares which system owns Slimefun resource-pack delivery semantics.
 *
 * <p>This is intentionally separate from item-model mappings. A server may use EXTERNAL delivery while keeping
 * Slimefun's matching item-model mappings enabled inside a combined pack.</p>
 */
public enum ResourcePackOwnershipMode {
    /** Preserve historical behavior and infer intent from the Legacy sender toggle. */
    AUTO,
    /** Slimefun Legacy owns delivery of the configured pack URL. */
    LEGACY,
    /** Another plugin, proxy or server-level pack system owns delivery. */
    EXTERNAL,
    /** No Slimefun-textured resource pack is intentionally in use. */
    NONE;

    public static @Nonnull ResourcePackOwnershipMode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }

    public @Nonnull String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
