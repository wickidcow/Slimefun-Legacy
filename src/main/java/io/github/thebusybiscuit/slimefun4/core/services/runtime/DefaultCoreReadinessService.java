package io.github.thebusybiscuit.slimefun4.core.services.runtime;

import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeHealthService;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleService;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleState;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessService;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessState;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SchedulerSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Internal aggregation service. It observes existing services and never changes gameplay state. */
@SlimefunInternal
public final class DefaultCoreReadinessService implements CoreReadinessService {

    private final CoreLifecycleService lifecycle;
    private final RegistryRuntimeService registry;
    private final SlimefunScheduler scheduler;
    private final StorageRuntimeService storage;
    private final MachineRuntimeService machines;
    private final AddonRuntimeHealthService addonHealth;

    public DefaultCoreReadinessService(
            @Nonnull CoreLifecycleService lifecycle,
            @Nonnull RegistryRuntimeService registry,
            @Nonnull SlimefunScheduler scheduler,
            @Nonnull StorageRuntimeService storage,
            @Nonnull MachineRuntimeService machines,
            @Nonnull AddonRuntimeHealthService addonHealth) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.machines = Objects.requireNonNull(machines, "machines");
        this.addonHealth = Objects.requireNonNull(addonHealth, "addonHealth");
    }

    @Override
    public @Nonnull CoreReadinessSnapshot getSnapshot() {
        CoreLifecycleState lifecycleState = lifecycle.getSnapshot().getState();
        RegistryRuntimeSnapshot registrySnapshot = registry.getSnapshot();
        SchedulerSnapshot schedulerSnapshot = scheduler.getSnapshot();
        StorageRuntimeSnapshot storageSnapshot = storage.getSnapshot();
        MachineRuntimeSnapshot machineSnapshot = machines.getSnapshot();
        List<String> reasons = new ArrayList<>();

        CoreReadinessState state;
        if (lifecycleState == CoreLifecycleState.FAILED) {
            state = CoreReadinessState.FAILED;
            reasons.add("Core lifecycle reported a startup/runtime failure");
        } else if (lifecycleState == CoreLifecycleState.STOPPING) {
            state = CoreReadinessState.STOPPING;
            reasons.add("Core shutdown is in progress");
        } else if (lifecycleState == CoreLifecycleState.STOPPED) {
            state = CoreReadinessState.STOPPED;
            reasons.add("Core is stopped");
        } else if (lifecycleState != CoreLifecycleState.RUNNING) {
            state = CoreReadinessState.STARTING;
            reasons.add("Core startup has not reached RUNNING");
        } else {
            if (!registrySnapshot.isInitialRegistrationFinalized()) {
                reasons.add("Initial item registry has not finalized");
            }
            if (!schedulerSnapshot.isAcceptingTasks()) {
                reasons.add("Scheduler is not accepting new tasks");
            }
            if (!storageSnapshot.isReady()) {
                reasons.add("Storage runtime is not ready");
            }
            if (machineSnapshot.isHalted()) {
                reasons.add("Machine runtime is halted");
            } else if (machineSnapshot.isPaused()) {
                reasons.add("Machine runtime is paused");
            }
            if (machineSnapshot.getActiveMachineFailures() > 0) {
                reasons.add(machineSnapshot.getActiveMachineFailures() + " machine failure record(s) are active");
            }
            state = reasons.isEmpty() ? CoreReadinessState.READY : CoreReadinessState.DEGRADED;
        }

        return new CoreReadinessSnapshot(
                state,
                lifecycleState,
                registrySnapshot.isInitialRegistrationFinalized(),
                schedulerSnapshot.isAcceptingTasks(),
                storageSnapshot.isReady(),
                !machineSnapshot.isHalted() && !machineSnapshot.isPaused(),
                machineSnapshot.getActiveMachineFailures(),
                addonHealth.getFailures().size(),
                reasons);
    }
}
