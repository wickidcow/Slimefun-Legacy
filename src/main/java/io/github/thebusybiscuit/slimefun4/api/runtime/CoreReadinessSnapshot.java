package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleState;
import java.util.List;
import javax.annotation.Nonnull;

/** Immutable combined readiness snapshot for diagnostics and addon capability checks. */
@SlimefunAPI
public final class CoreReadinessSnapshot {

    private final CoreReadinessState state;
    private final CoreLifecycleState lifecycleState;
    private final boolean registryFinalized;
    private final boolean schedulerAcceptingTasks;
    private final boolean storageReady;
    private final boolean machineRuntimeOperational;
    private final int activeMachineFailures;
    private final int addonFailureRecords;
    private final List<String> reasons;

    public CoreReadinessSnapshot(
            @Nonnull CoreReadinessState state,
            @Nonnull CoreLifecycleState lifecycleState,
            boolean registryFinalized,
            boolean schedulerAcceptingTasks,
            boolean storageReady,
            boolean machineRuntimeOperational,
            int activeMachineFailures,
            int addonFailureRecords,
            @Nonnull List<String> reasons) {
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.lifecycleState = java.util.Objects.requireNonNull(lifecycleState, "lifecycleState");
        this.registryFinalized = registryFinalized;
        this.schedulerAcceptingTasks = schedulerAcceptingTasks;
        this.storageReady = storageReady;
        this.machineRuntimeOperational = machineRuntimeOperational;
        this.activeMachineFailures = activeMachineFailures;
        this.addonFailureRecords = addonFailureRecords;
        this.reasons = List.copyOf(java.util.Objects.requireNonNull(reasons, "reasons"));
    }

    public @Nonnull CoreReadinessState getState() {
        return state;
    }

    public @Nonnull CoreLifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public boolean isRegistryFinalized() {
        return registryFinalized;
    }

    public boolean isSchedulerAcceptingTasks() {
        return schedulerAcceptingTasks;
    }

    public boolean isStorageReady() {
        return storageReady;
    }

    public boolean isMachineRuntimeOperational() {
        return machineRuntimeOperational;
    }

    public int getActiveMachineFailures() {
        return activeMachineFailures;
    }

    public int getAddonFailureRecords() {
        return addonFailureRecords;
    }

    public @Nonnull List<String> getReasons() {
        return reasons;
    }
}
