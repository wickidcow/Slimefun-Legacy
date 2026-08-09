package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable registration and registry-ownership summary for one addon plugin. */
@SlimefunAPI
public final class AddonRegistrationSnapshot {

    private final String pluginName;
    private final String pluginVersion;
    private final boolean enabled;
    private final int registeredItems;
    private final int itemGroups;
    private final int tickingItems;
    private final int pendingCallbacks;
    private final long executedCallbacks;
    private final long failedCallbacks;
    private final long skippedDisabledCallbacks;

    public AddonRegistrationSnapshot(
            @Nonnull String pluginName,
            @Nonnull String pluginVersion,
            boolean enabled,
            int registeredItems,
            int itemGroups,
            int tickingItems,
            int pendingCallbacks,
            long executedCallbacks,
            long failedCallbacks,
            long skippedDisabledCallbacks) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.enabled = enabled;
        this.registeredItems = registeredItems;
        this.itemGroups = itemGroups;
        this.tickingItems = tickingItems;
        this.pendingCallbacks = pendingCallbacks;
        this.executedCallbacks = executedCallbacks;
        this.failedCallbacks = failedCallbacks;
        this.skippedDisabledCallbacks = skippedDisabledCallbacks;
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nonnull String getPluginVersion() {
        return pluginVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRegisteredItems() {
        return registeredItems;
    }

    public int getItemGroups() {
        return itemGroups;
    }

    public int getTickingItems() {
        return tickingItems;
    }

    public int getPendingCallbacks() {
        return pendingCallbacks;
    }

    public long getExecutedCallbacks() {
        return executedCallbacks;
    }

    public long getFailedCallbacks() {
        return failedCallbacks;
    }

    public long getSkippedDisabledCallbacks() {
        return skippedDisabledCallbacks;
    }
}
