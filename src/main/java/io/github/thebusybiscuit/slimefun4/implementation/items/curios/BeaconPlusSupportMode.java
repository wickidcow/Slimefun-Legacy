package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
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

    public String getDisplayName() {
        return displayName;
    }

    public PotionEffectType getEffectType() {
        return effectType;
    }

    public static BeaconPlusSupportMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OFF;
        }
    }
}
