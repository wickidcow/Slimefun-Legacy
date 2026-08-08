package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/**
 * Read-only diagnostics that correlate machine ticker registrations with observed chunk lifecycle state.
 *
 * <p>This service does not pause, remove, re-register, or otherwise change machine tickers.
 */
@SlimefunAPI
public interface MachineChunkCoordinationService {

    @Nonnull
    MachineChunkCoordinationSnapshot getSnapshot();
}
