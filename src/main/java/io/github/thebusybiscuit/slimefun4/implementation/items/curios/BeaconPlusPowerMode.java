package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import org.bukkit.Material;

/** Selects the source used to power Beacon Plus field effects. */
enum BeaconPlusPowerMode {
    SLIMEFUN_ENERGY("Slimefun Electricity", Material.REDSTONE_BLOCK),
    BEACON_BLOCKS("Beacon Blocks", Material.IRON_BLOCK);

    private final String displayName;
    private final Material icon;

    BeaconPlusPowerMode(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    String getDisplayName() {
        return displayName;
    }

    Material getIcon() {
        return icon;
    }

    BeaconPlusPowerMode next() {
        return this == SLIMEFUN_ENERGY ? BEACON_BLOCKS : SLIMEFUN_ENERGY;
    }

    static BeaconPlusPowerMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            // Existing Beacon Plus blocks predate this setting and were energy machines.
            return SLIMEFUN_ENERGY;
        }

        String normalized =
                value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "BEACON_BLOCKS", "BLOCKS", "VANILLA", "VANILLA_BLOCKS", "PYRAMID" -> BEACON_BLOCKS;
            case "SLIMEFUN_ENERGY", "SLIMEFUN", "ENERGY", "ELECTRICITY", "ELECTRIC" -> SLIMEFUN_ENERGY;
            default -> SLIMEFUN_ENERGY;
        };
    }
}
