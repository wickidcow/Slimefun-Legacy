package io.github.thebusybiscuit.slimefun4.api.lifecycle;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Read-only addon-facing view of Slimefun's core lifecycle. */
@SlimefunAPI
public interface CoreLifecycleService {

    @Nonnull
    CoreLifecycleSnapshot getSnapshot();

    default boolean isRunning() {
        return getSnapshot().getState() == CoreLifecycleState.RUNNING;
    }

    default boolean isStopping() {
        CoreLifecycleState state = getSnapshot().getState();
        return state == CoreLifecycleState.STOPPING || state == CoreLifecycleState.STOPPED;
    }
}
