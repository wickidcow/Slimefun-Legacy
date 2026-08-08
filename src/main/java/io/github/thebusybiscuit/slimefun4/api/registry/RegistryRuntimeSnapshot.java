package io.github.thebusybiscuit.slimefun4.api.registry;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Immutable read-only snapshot of Slimefun's live registries. */
@SlimefunAPI
public final class RegistryRuntimeSnapshot {

    private final boolean initialRegistrationFinalized;
    private final long finalizedAtMillis;
    private final int finalizedItemCount;
    private final int runtimeRegisteredItems;
    private final int totalItems;
    private final int enabledItems;
    private final int disabledItems;
    private final int itemGroups;
    private final int researches;
    private final int tickerBlocks;
    private final int representedPlugins;

    public RegistryRuntimeSnapshot(
            boolean initialRegistrationFinalized,
            long finalizedAtMillis,
            int finalizedItemCount,
            int runtimeRegisteredItems,
            int totalItems,
            int enabledItems,
            int disabledItems,
            int itemGroups,
            int researches,
            int tickerBlocks,
            int representedPlugins) {
        this.initialRegistrationFinalized = initialRegistrationFinalized;
        this.finalizedAtMillis = finalizedAtMillis;
        this.finalizedItemCount = finalizedItemCount;
        this.runtimeRegisteredItems = runtimeRegisteredItems;
        this.totalItems = totalItems;
        this.enabledItems = enabledItems;
        this.disabledItems = disabledItems;
        this.itemGroups = itemGroups;
        this.researches = researches;
        this.tickerBlocks = tickerBlocks;
        this.representedPlugins = representedPlugins;
    }

    public boolean isInitialRegistrationFinalized() {
        return initialRegistrationFinalized;
    }

    public long getFinalizedAtMillis() {
        return finalizedAtMillis;
    }

    public int getFinalizedItemCount() {
        return finalizedItemCount;
    }

    public int getRuntimeRegisteredItems() {
        return runtimeRegisteredItems;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getEnabledItems() {
        return enabledItems;
    }

    public int getDisabledItems() {
        return disabledItems;
    }

    public int getItemGroups() {
        return itemGroups;
    }

    public int getResearches() {
        return researches;
    }

    public int getTickerBlocks() {
        return tickerBlocks;
    }

    public int getRepresentedPlugins() {
        return representedPlugins;
    }
}
