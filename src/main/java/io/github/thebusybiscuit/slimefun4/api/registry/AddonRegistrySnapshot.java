package io.github.thebusybiscuit.slimefun4.api.registry;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Immutable ownership summary for one plugin represented in Slimefun's registries. */
@SlimefunAPI
public final class AddonRegistrySnapshot {

    private final String pluginName;
    private final String pluginVersion;
    private final int totalItems;
    private final int enabledItems;
    private final int disabledItems;
    private final int itemGroups;
    private final int tickingItems;

    public AddonRegistrySnapshot(
            @Nonnull String pluginName,
            @Nonnull String pluginVersion,
            int totalItems,
            int enabledItems,
            int disabledItems,
            int itemGroups,
            int tickingItems) {
        this.pluginName = java.util.Objects.requireNonNull(pluginName, "pluginName");
        this.pluginVersion = java.util.Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.totalItems = totalItems;
        this.enabledItems = enabledItems;
        this.disabledItems = disabledItems;
        this.itemGroups = itemGroups;
        this.tickingItems = tickingItems;
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nonnull String getPluginVersion() {
        return pluginVersion;
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

    public int getTickingItems() {
        return tickingItems;
    }
}
