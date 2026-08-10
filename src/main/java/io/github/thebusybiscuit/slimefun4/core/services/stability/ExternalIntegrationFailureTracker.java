package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationFailureSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nonnull;

/** Thread-safe live diagnostics for optional external integration provider failures. */
public final class ExternalIntegrationFailureTracker {

    private final Map<String, MutableFailure> active = new ConcurrentHashMap<>();
    private final LongAdder totalFailures = new LongAdder();
    private final LongAdder suppressedReports = new LongAdder();

    public void recordFailure(
            @Nonnull String key,
            @Nonnull String integrationId,
            @Nonnull String displayName,
            @Nonnull String pluginName,
            @Nonnull String operation,
            @Nonnull Throwable failure,
            int consecutiveFailures,
            long nowMillis,
            long pausedUntilMillis,
            boolean reportSuppressed) {
        totalFailures.increment();
        if (reportSuppressed) {
            suppressedReports.increment();
        }

        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = "<no message>";
        } else if (message.length() > 160) {
            message = message.substring(0, 157) + "...";
        }

        final String safeMessage = message;
        active.compute(
                key,
                (ignored, previous) -> new MutableFailure(
                        integrationId,
                        displayName,
                        pluginName,
                        operation,
                        failure.getClass().getName(),
                        safeMessage,
                        consecutiveFailures,
                        previous == null ? 1L : previous.failuresObserved + 1L,
                        previous == null
                                ? (reportSuppressed ? 1L : 0L)
                                : previous.suppressedReports + (reportSuppressed ? 1L : 0L),
                        previous == null ? nowMillis : previous.firstFailureMillis,
                        nowMillis,
                        pausedUntilMillis));
    }

    public void clear(@Nonnull String key) {
        active.remove(key);
    }

    public int clearIntegration(@Nonnull String integrationId) {
        int before = active.size();
        active.entrySet().removeIf(entry -> entry.getValue().integrationId.equalsIgnoreCase(integrationId));
        return before - active.size();
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

    public @Nonnull List<ExternalIntegrationFailureSnapshot> snapshot(int limit) {
        int safeLimit = Math.max(0, limit);
        List<ExternalIntegrationFailureSnapshot> snapshots = new ArrayList<>();
        for (MutableFailure value : active.values()) {
            snapshots.add(value.snapshot());
        }
        snapshots.sort(Comparator.comparingLong(ExternalIntegrationFailureSnapshot::getLastFailureMillis)
                .reversed());
        return safeLimit == 0 || snapshots.size() <= safeLimit
                ? List.copyOf(snapshots)
                : List.copyOf(snapshots.subList(0, safeLimit));
    }

    private record MutableFailure(
            String integrationId,
            String displayName,
            String pluginName,
            String operation,
            String failureType,
            String failureMessage,
            int consecutiveFailures,
            long failuresObserved,
            long suppressedReports,
            long firstFailureMillis,
            long lastFailureMillis,
            long pausedUntilMillis) {
        private ExternalIntegrationFailureSnapshot snapshot() {
            return new ExternalIntegrationFailureSnapshot(
                    integrationId,
                    displayName,
                    pluginName,
                    operation,
                    failureType,
                    failureMessage,
                    consecutiveFailures,
                    failuresObserved,
                    suppressedReports,
                    firstFailureMillis,
                    lastFailureMillis,
                    pausedUntilMillis);
        }
    }
}
