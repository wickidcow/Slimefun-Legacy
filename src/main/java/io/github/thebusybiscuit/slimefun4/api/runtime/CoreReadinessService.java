package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Read-only combined readiness view of Slimefun's core runtime. */
@SlimefunAPI
public interface CoreReadinessService {

    @Nonnull
    CoreReadinessSnapshot getSnapshot();

    default boolean isReady() {
        return getSnapshot().getState() == CoreReadinessState.READY;
    }
}
