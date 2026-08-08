package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/** Stable facade for machine-runtime diagnostics and recovery controls. */
@SlimefunAPI
public interface MachineRuntimeService {

    @Nonnull
    MachineRuntimeSnapshot getSnapshot();

    boolean retryMachine(@Nonnull Location location);

    int retryAllMachines();

    void setPaused(boolean paused);
}
