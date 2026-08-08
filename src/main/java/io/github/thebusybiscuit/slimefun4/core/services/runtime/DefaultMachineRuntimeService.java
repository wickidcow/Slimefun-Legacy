package io.github.thebusybiscuit.slimefun4.core.services.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/** Internal adapter that keeps callers away from {@link TickerTask} implementation details. */
@SlimefunInternal
public final class DefaultMachineRuntimeService implements MachineRuntimeService {

    private final TickerTask ticker;

    public DefaultMachineRuntimeService(@Nonnull TickerTask ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    @Override
    public @Nonnull MachineRuntimeSnapshot getSnapshot() {
        int tickingLocations = ticker.getTickLocations().values().stream()
                .mapToInt(java.util.Set::size)
                .sum();
        return new MachineRuntimeSnapshot(
                ticker.isPaused(),
                ticker.isHalted(),
                ticker.getTickRate(),
                ticker.getTickLocations().size(),
                tickingLocations,
                ticker.getPausedMachineCount(),
                ticker.getFailingMachineCount(),
                ticker.getObservedMachineFailureCount(),
                ticker.getSuppressedMachineFailureReportCount());
    }

    @Override
    public boolean retryMachine(@Nonnull Location location) {
        return ticker.retryMachine(Objects.requireNonNull(location, "location"));
    }

    @Override
    public int retryAllMachines() {
        return ticker.retryAllMachines();
    }

    @Override
    public void setPaused(boolean paused) {
        ticker.setPaused(paused);
    }
}
