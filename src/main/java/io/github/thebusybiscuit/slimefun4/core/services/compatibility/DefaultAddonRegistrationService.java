package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationDisposition;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationService;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeHealthService;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunItemRegistryFinalizedEvent;
import io.github.thebusybiscuit.slimefun4.api.registry.AddonRegistrySnapshot;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Internal implementation of the additive addon-registration compatibility layer. */
@SlimefunInternal
public final class DefaultAddonRegistrationService implements AddonRegistrationService, Listener {

    private final RegistryRuntimeService registryRuntime;
    private final AddonRuntimeHealthService runtimeHealth;
    private final Object pendingLock = new Object();
    private final List<PendingCallback> pendingCallbacks = new ArrayList<>();
    private final Map<String, CallbackStats> callbackStats = new ConcurrentHashMap<>();
    private final AtomicLong executedCallbacks = new AtomicLong();
    private final AtomicLong failedCallbacks = new AtomicLong();
    private final AtomicLong skippedDisabledCallbacks = new AtomicLong();

    public DefaultAddonRegistrationService(
            @Nonnull RegistryRuntimeService registryRuntime, @Nonnull AddonRuntimeHealthService runtimeHealth) {
        this.registryRuntime = Objects.requireNonNull(registryRuntime, "registryRuntime");
        this.runtimeHealth = Objects.requireNonNull(runtimeHealth, "runtimeHealth");
    }

    @Override
    public @Nonnull AddonRegistrationRuntimeSnapshot getSnapshot() {
        RegistryRuntimeSnapshot registry = registryRuntime.getSnapshot();
        int pending;
        synchronized (pendingLock) {
            pending = pendingCallbacks.size();
        }
        return new AddonRegistrationRuntimeSnapshot(
                registry.isInitialRegistrationFinalized(),
                registry.getFinalizedAtMillis(),
                pending,
                executedCallbacks.get(),
                failedCallbacks.get(),
                skippedDisabledCallbacks.get(),
                registry.getRuntimeRegisteredItems());
    }

    @Override
    public @Nonnull List<AddonRegistrationSnapshot> getAddonSnapshots() {
        Map<String, AddonRegistrySnapshot> registryByName = new LinkedHashMap<>();
        for (AddonRegistrySnapshot snapshot : registryRuntime.getAddonSnapshots()) {
            registryByName.put(key(snapshot.getPluginName()), snapshot);
        }

        Map<String, Integer> pendingByName = new LinkedHashMap<>();
        Map<String, Plugin> pluginByName = new LinkedHashMap<>();
        synchronized (pendingLock) {
            for (PendingCallback pending : pendingCallbacks) {
                pendingByName.merge(key(pending.plugin().getName()), 1, Integer::sum);
                pluginByName.putIfAbsent(key(pending.plugin().getName()), pending.plugin());
            }
        }

        for (CallbackStats stats : callbackStats.values()) {
            pluginByName.putIfAbsent(key(stats.plugin.getName()), stats.plugin);
        }

        var names = new java.util.HashSet<String>();
        names.addAll(registryByName.keySet());
        names.addAll(pluginByName.keySet());
        names.addAll(pendingByName.keySet());
        names.addAll(callbackStats.keySet());

        List<AddonRegistrationSnapshot> snapshots = new ArrayList<>();
        for (String name : names) {
            AddonRegistrySnapshot registry = registryByName.get(name);
            CallbackStats stats = callbackStats.get(name);
            Plugin plugin = stats != null ? stats.plugin : pluginByName.get(name);
            String resolvedName = registry != null ? registry.getPluginName() : plugin != null ? plugin.getName() : name;
            if (plugin == null) {
                plugin = Bukkit.getPluginManager().getPlugin(resolvedName);
            }

            String pluginName = registry != null
                    ? registry.getPluginName()
                    : plugin != null ? plugin.getName() : name;
            String pluginVersion = registry != null
                    ? registry.getPluginVersion()
                    : plugin != null ? plugin.getDescription().getVersion() : "unknown";
            boolean enabled = plugin == null || plugin.isEnabled();
            snapshots.add(new AddonRegistrationSnapshot(
                    pluginName,
                    pluginVersion,
                    enabled,
                    registry == null ? 0 : registry.getTotalItems(),
                    registry == null ? 0 : registry.getItemGroups(),
                    registry == null ? 0 : registry.getTickingItems(),
                    pendingByName.getOrDefault(name, 0),
                    stats == null ? 0 : stats.executed.get(),
                    stats == null ? 0 : stats.failed.get(),
                    stats == null ? 0 : stats.skipped.get()));
        }

        snapshots.sort(Comparator.comparing(AddonRegistrationSnapshot::getPluginName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(snapshots);
    }

    @Override
    public @Nonnull AddonRegistrationDisposition runAfterInitialRegistration(
            @Nonnull Plugin plugin, @Nonnull String operation, @Nonnull Runnable callback) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callback, "callback");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation cannot be blank");
        }

        if (!plugin.isEnabled()) {
            recordSkipped(plugin);
            return AddonRegistrationDisposition.SKIPPED_DISABLED;
        }

        synchronized (pendingLock) {
            if (!registryRuntime.getSnapshot().isInitialRegistrationFinalized()) {
                pendingCallbacks.add(new PendingCallback(plugin, operation, callback));
                return AddonRegistrationDisposition.QUEUED;
            }
        }

        return execute(plugin, operation, callback);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInitialRegistrationFinalized(SlimefunItemRegistryFinalizedEvent event) {
        drainPendingCallbacks();
    }

    /** Drains queued callbacks after the registry-finalized event. Safe to call more than once. */
    public void drainPendingCallbacks() {
        List<PendingCallback> callbacks;
        synchronized (pendingLock) {
            if (!registryRuntime.getSnapshot().isInitialRegistrationFinalized() || pendingCallbacks.isEmpty()) {
                return;
            }
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        for (PendingCallback pending : callbacks) {
            if (!pending.plugin().isEnabled()) {
                recordSkipped(pending.plugin());
                continue;
            }
            execute(pending.plugin(), pending.operation(), pending.callback());
        }
    }

    private AddonRegistrationDisposition execute(Plugin plugin, String operation, Runnable callback) {
        CallbackStats stats = callbackStats.computeIfAbsent(key(plugin.getName()), ignored -> new CallbackStats(plugin));
        boolean success = runtimeHealth.runGuarded(plugin, "post-registration:" + operation, callback);
        if (success) {
            stats.executed.incrementAndGet();
            executedCallbacks.incrementAndGet();
            return AddonRegistrationDisposition.EXECUTED;
        }
        stats.failed.incrementAndGet();
        failedCallbacks.incrementAndGet();
        return AddonRegistrationDisposition.FAILED;
    }

    private void recordSkipped(Plugin plugin) {
        callbackStats
                .computeIfAbsent(key(plugin.getName()), ignored -> new CallbackStats(plugin))
                .skipped
                .incrementAndGet();
        skippedDisabledCallbacks.incrementAndGet();
    }

    private static String key(String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT);
    }

    private record PendingCallback(Plugin plugin, String operation, Runnable callback) {}

    private static final class CallbackStats {
        private final Plugin plugin;
        private final AtomicLong executed = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong skipped = new AtomicLong();

        private CallbackStats(Plugin plugin) {
            this.plugin = plugin;
        }
    }
}
