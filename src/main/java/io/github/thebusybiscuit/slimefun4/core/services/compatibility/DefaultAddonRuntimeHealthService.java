package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeFailureSnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeHealthService;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/** Thread-safe in-memory callback failure registry. No plugin is disabled or altered by this service. */
@SlimefunInternal
public final class DefaultAddonRuntimeHealthService implements AddonRuntimeHealthService {

    private static final int MAX_MESSAGE_LENGTH = 200;

    private final Map<String, FailureState> failures = new ConcurrentHashMap<>();
    private final AtomicLong observedFailures = new AtomicLong();

    @Override
    public void recordFailure(@Nonnull Plugin plugin, @Nonnull String operation, @Nonnull Throwable failure) {
        Objects.requireNonNull(plugin, "plugin");
        recordFailure(plugin.getName(), plugin.getDescription().getVersion(), operation, failure);
    }

    void recordFailure(
            @Nonnull String pluginName,
            @Nonnull String pluginVersion,
            @Nonnull String operation,
            @Nonnull Throwable failure) {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(failure, "failure");
        long now = System.currentTimeMillis();
        observedFailures.incrementAndGet();
        failures.compute(key(pluginName), (ignored, previous) -> {
            if (previous == null) {
                return new FailureState(pluginName, pluginVersion, now, operation, failure);
            }
            previous.update(pluginVersion, now, operation, failure);
            return previous;
        });
    }

    @Override
    public @Nonnull List<AddonRuntimeFailureSnapshot> getFailures() {
        return failures.values().stream()
                .map(FailureState::snapshot)
                .sorted(Comparator.comparingLong(AddonRuntimeFailureSnapshot::getLastFailureMillis)
                        .reversed()
                        .thenComparing(AddonRuntimeFailureSnapshot::getPluginName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public long getObservedFailureCount() {
        return observedFailures.get();
    }

    @Override
    public boolean clear(@Nonnull String pluginName) {
        return failures.remove(key(Objects.requireNonNull(pluginName, "pluginName"))) != null;
    }

    @Override
    public int clearAll() {
        int count = failures.size();
        failures.clear();
        return count;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private static final class FailureState {
        private final String pluginName;
        private final long firstFailureMillis;
        private final AtomicLong count = new AtomicLong(1L);
        private volatile String pluginVersion;
        private volatile long lastFailureMillis;
        private volatile String operation;
        private volatile String exceptionClass;
        private volatile String message;

        private FailureState(String pluginName, String pluginVersion, long now, String operation, Throwable failure) {
            this.pluginName = pluginName;
            this.pluginVersion = pluginVersion;
            this.firstFailureMillis = now;
            this.lastFailureMillis = now;
            this.operation = operation;
            this.exceptionClass = failure.getClass().getName();
            this.message = DefaultAddonRuntimeHealthService.message(failure);
        }

        private void update(String pluginVersion, long now, String operation, Throwable failure) {
            count.incrementAndGet();
            this.pluginVersion = pluginVersion;
            this.lastFailureMillis = now;
            this.operation = operation;
            this.exceptionClass = failure.getClass().getName();
            this.message = DefaultAddonRuntimeHealthService.message(failure);
        }

        private AddonRuntimeFailureSnapshot snapshot() {
            return new AddonRuntimeFailureSnapshot(
                    pluginName,
                    pluginVersion,
                    operation,
                    exceptionClass,
                    message,
                    count.get(),
                    firstFailureMillis,
                    lastFailureMillis);
        }
    }
}
