package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.potion.PotionEffectType;

/**
 * Legacy support-mode field retained only so early Curios development data can be read safely.
 * New Beacon Plus configuration uses {@link BeaconPlusEffect} instead.
 */
public enum BeaconPlusSupportMode {
    OFF("Off", null),
    SPEED("Speed", PotionEffectType.SPEED),
    HASTE("Haste", PotionEffectType.HASTE),
    RESISTANCE("Resistance", PotionEffectType.RESISTANCE),
    REGENERATION("Regeneration", PotionEffectType.REGENERATION),
    NIGHT_VISION("Night Vision", PotionEffectType.NIGHT_VISION);

    private final String displayName;
    private final PotionEffectType effectType;

    BeaconPlusSupportMode(String displayName, PotionEffectType effectType) {
        this.displayName = displayName;
        this.effectType = effectType;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public PotionEffectType getEffectType() {
        return effectType;
    }

    public @Nonnull BeaconPlusSupportMode next() {
        BeaconPlusSupportMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static @Nonnull BeaconPlusSupportMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return OFF;
        }
    }
}
