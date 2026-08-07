package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/** Thread-safe live diagnostics for machine ticker failures. */
public final class MachineFailureTracker<K> {

    private final Map<K, MutableFailure> active = new ConcurrentHashMap<>();
    private final LongAdder totalFailures = new LongAdder();
    private final LongAdder suppressedReports = new LongAdder();

    public void recordFailure(
            @Nonnull K position,
            @Nonnull Location location,
            @Nonnull SlimefunItem item,
            @Nonnull Throwable failure,
            int consecutiveFailures,
            long nowMillis,
            long pausedUntilMillis,
            boolean reportSuppressed) {
        totalFailures.increment();
        if (reportSuppressed) {
            suppressedReports.increment();
        }
        String addonName;
        try {
            addonName = item.getAddon().getName();
        } catch (RuntimeException | LinkageError ignored) {
            addonName = "Unknown addon";
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = "<no message>";
        } else if (message.length() > 160) {
            message = message.substring(0, 157) + "...";
        }
        final String safeAddonName = addonName;
        final String safeMessage = message;
        active.compute(position, (ignored, previous) -> new MutableFailure(
                location.getWorld() == null ? "<unknown>" : location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), item.getId(), safeAddonName,
                failure.getClass().getName(), safeMessage, consecutiveFailures,
                previous == null ? 1L : previous.failuresObserved + 1L,
                previous == null ? (reportSuppressed ? 1L : 0L) : previous.suppressedReports + (reportSuppressed ? 1L : 0L),
                previous == null ? nowMillis : previous.firstFailureMillis,
                nowMillis,
                pausedUntilMillis));
    }

    public void markPaused(@Nonnull K position, long pausedUntilMillis) {
        active.computeIfPresent(position, (ignored, previous) -> previous.withPausedUntil(pausedUntilMillis));
    }

    public void clear(@Nonnull K position) {
        active.remove(position);
    }

    public void clearAll() {
        active.clear();
    }

    public int getActiveFailureCount() {
        return active.size();
    }

    public long getTotalFailureCount() {
        return totalFailures.sum();
    }

    public long getSuppressedReportCount() {
        return suppressedReports.sum();
    }

    public @Nonnull List<MachineFailureSnapshot> snapshot(int limit) {
        int safeLimit = Math.max(0, limit);
        List<MachineFailureSnapshot> snapshots = new ArrayList<>();
        for (MutableFailure value : active.values()) {
            snapshots.add(value.snapshot());
        }
        snapshots.sort(Comparator.comparingLong(MachineFailureSnapshot::getLastFailureMillis).reversed());
        return safeLimit == 0 || snapshots.size() <= safeLimit
                ? List.copyOf(snapshots)
                : List.copyOf(snapshots.subList(0, safeLimit));
    }

    private record MutableFailure(
            String worldName, int x, int y, int z, String itemId, String addonName,
            String failureType, String failureMessage, int consecutiveFailures,
            long failuresObserved, long suppressedReports, long firstFailureMillis,
            long lastFailureMillis, long pausedUntilMillis) {
        private MutableFailure withPausedUntil(long value) {
            return new MutableFailure(worldName, x, y, z, itemId, addonName, failureType, failureMessage,
                    consecutiveFailures, failuresObserved, suppressedReports, firstFailureMillis, lastFailureMillis, value);
        }
        private MachineFailureSnapshot snapshot() {
            return new MachineFailureSnapshot(worldName, x, y, z, itemId, addonName, failureType, failureMessage,
                    consecutiveFailures, failuresObserved, suppressedReports, firstFailureMillis, lastFailureMillis,
                    pausedUntilMillis);
        }
    }
}
