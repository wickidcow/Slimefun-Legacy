package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Immutable summary of Slimefun's addon-registration compatibility layer. */
@SlimefunAPI
public final class AddonRegistrationRuntimeSnapshot {

    private final boolean initialRegistrationFinalized;
    private final long finalizedAtMillis;
    private final int pendingCallbacks;
    private final long executedCallbacks;
    private final long failedCallbacks;
    private final long skippedDisabledCallbacks;
    private final int runtimeRegisteredItems;

    public AddonRegistrationRuntimeSnapshot(
            boolean initialRegistrationFinalized,
            long finalizedAtMillis,
            int pendingCallbacks,
            long executedCallbacks,
            long failedCallbacks,
            long skippedDisabledCallbacks,
            int runtimeRegisteredItems) {
        this.initialRegistrationFinalized = initialRegistrationFinalized;
        this.finalizedAtMillis = finalizedAtMillis;
        this.pendingCallbacks = pendingCallbacks;
        this.executedCallbacks = executedCallbacks;
        this.failedCallbacks = failedCallbacks;
        this.skippedDisabledCallbacks = skippedDisabledCallbacks;
        this.runtimeRegisteredItems = runtimeRegisteredItems;
    }

    public boolean isInitialRegistrationFinalized() {
        return initialRegistrationFinalized;
    }

    public long getFinalizedAtMillis() {
        return finalizedAtMillis;
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

    public int getRuntimeRegisteredItems() {
        return runtimeRegisteredItems;
    }
}
