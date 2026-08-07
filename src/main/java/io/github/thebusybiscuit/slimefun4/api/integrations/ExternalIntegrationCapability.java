package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Capabilities an optional external-system bridge can expose to Slimefun Legacy. */
@SlimefunAPI
public enum ExternalIntegrationCapability {
    INVENTORY("Inventory access"),
    STORAGE("Storage access"),
    CARGO("Cargo routing"),
    MACHINE("Machine access"),
    ENERGY("Energy exchange"),
    FLUID("Fluid exchange");

    private final String displayName;

    ExternalIntegrationCapability(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
