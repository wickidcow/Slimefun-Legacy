package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Capabilities an optional external-system bridge can expose to Slimefun Legacy. */
@SlimefunAPI
public enum ExternalIntegrationCapability {
    INVENTORY("Inventory discovery"),
    STORAGE("Storage discovery"),
    CARGO("Cargo endpoint mapping"),
    MACHINE("Machine discovery"),
    ENERGY("Energy exchange"),
    FLUID("Fluid endpoint mapping");

    private final String displayName;

    ExternalIntegrationCapability(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
