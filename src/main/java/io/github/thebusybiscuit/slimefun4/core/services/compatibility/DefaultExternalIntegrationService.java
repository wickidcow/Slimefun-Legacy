package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalBlockIntegration;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationCapability;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationFailureSnapshot;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationProvider;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationService;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationStatus;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ExternalIntegrationFailureTracker;
import io.github.thebusybiscuit.slimefun4.core.services.stability.MachineCircuitBreaker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Default guarded registry for external machine/storage/cargo integration providers. */
public final class DefaultExternalIntegrationService implements ExternalIntegrationService {

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final long DEFAULT_FAILURE_COOLDOWN_SECONDS = 120L;

    private static final KnownSystem REBAR = new KnownSystem(
            "rebar",
            "Rebar",
            "Rebar",
            "Optional Rebar integration. Phase 1E loads its block capability adapter only when the runtime API can be probed safely.");
    private static final KnownSystem PYLON = new KnownSystem(
            "pylon",
            "Pylon",
            "Pylon",
            "Optional Pylon integration. Pylon blocks are inspected through Rebar without assuming Slimefun cargo or energy semantics.");

    private final Plugin owner;
    private final Map<String, ExternalIntegrationProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, Integer> providerFailures = new ConcurrentHashMap<>();
    private final MachineCircuitBreaker<String> providerCircuitBreaker = new MachineCircuitBreaker<>();
    private final ExternalIntegrationFailureTracker failureTracker = new ExternalIntegrationFailureTracker();
    private volatile List<ExternalIntegrationStatus> statuses = List.of();
    private volatile List<ProviderRegistration> activeProviders = List.of();

    public DefaultExternalIntegrationService(@Nonnull Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    @Override
    public void register(@Nonnull ExternalIntegrationProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = normalize(provider.getIntegrationId());
        if (id.isEmpty()) {
            throw new IllegalArgumentException("External integration id cannot be blank");
        }
        if (!provider.getPlugin().isEnabled()) {
            throw new IllegalStateException("External integration provider plugin must be enabled");
        }
        providers.put(id, provider);
        refresh();
    }

    @Override
    public void unregister(@Nonnull Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        providers.entrySet().removeIf(entry -> entry.getValue().getPlugin().equals(plugin));
        retryAllForPlugin(plugin);
        refresh();
    }

    @Override
    public void refresh() {
        Map<String, ExternalIntegrationStatus> result = new LinkedHashMap<>();
        addKnown(result, REBAR);
        addKnown(result, PYLON);

        Map<String, ExternalIntegrationProvider> effectiveProviders = new LinkedHashMap<>();
        Plugin rebarPlugin = findPlugin(REBAR.pluginName);
        Plugin pylonPlugin = findPlugin(PYLON.pluginName);
        if (rebarPlugin != null && rebarPlugin.isEnabled()) {
            try {
                ReflectiveRebarAccess access = ReflectiveRebarAccess.create(rebarPlugin);
                effectiveProviders.put(
                        REBAR.id,
                        new ReflectiveRebarIntegrationProvider(REBAR.id, REBAR.displayName, rebarPlugin, access, false));
                if (pylonPlugin != null && pylonPlugin.isEnabled()) {
                    effectiveProviders.put(
                            PYLON.id,
                            new ReflectiveRebarIntegrationProvider(PYLON.id, PYLON.displayName, pylonPlugin, access, true));
                }
            } catch (ClassNotFoundException | RuntimeException | LinkageError failure) {
                replaceKnownDetail(
                        result,
                        REBAR,
                        rebarPlugin,
                        "Rebar detected, but the reflection-only adapter could not load: "
                                + failure.getClass().getSimpleName());
                if (pylonPlugin != null && pylonPlugin.isEnabled()) {
                    replaceKnownDetail(
                            result,
                            PYLON,
                            pylonPlugin,
                            "Pylon detected, but its Rebar-backed adapter is unavailable because the Rebar API probe failed.");
                }
            }
        }

        // Explicit addon registrations take precedence over Legacy's conservative built-in reflective adapters.
        effectiveProviders.putAll(providers);

        List<ProviderRegistration> active = new ArrayList<>();
        for (Map.Entry<String, ExternalIntegrationProvider> entry : effectiveProviders.entrySet()) {
            String id = entry.getKey();
            ExternalIntegrationProvider provider = entry.getValue();
            Plugin plugin = provider.getPlugin();
            String fallbackDisplayName = plugin.getName();
            Set<ExternalIntegrationCapability> capabilities = Set.of();
            String displayName = fallbackDisplayName;
            String detail;
            String key = operationKey(id, "status");

            if (!providerCircuitBreaker.canAttempt(key, System.currentTimeMillis())) {
                detail = "Adapter status probe is temporarily isolated after repeated failures. "
                        + "Use /sf doctor integrations retry " + id + " to retry immediately.";
            } else {
                try {
                    capabilities = Set.copyOf(provider.getCapabilities());
                    displayName = provider.getDisplayName();
                    detail = provider.getStatusDescription();
                    markProviderSuccess(key);
                } catch (RuntimeException | LinkageError failure) {
                    recordProviderFailure(id, fallbackDisplayName, plugin, "status", failure);
                    detail = "Bridge provider failed its status probe: " + failure.getClass().getSimpleName();
                }
            }

            result.put(id, new ExternalIntegrationStatus(
                    id,
                    displayName,
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    true,
                    plugin.isEnabled(),
                    true,
                    capabilities,
                    detail));
            active.add(new ProviderRegistration(id, displayName, provider));
        }

        List<ExternalIntegrationStatus> ordered = new ArrayList<>(result.values());
        ordered.sort(Comparator.comparing(ExternalIntegrationStatus::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        statuses = List.copyOf(ordered);
        activeProviders = List.copyOf(active);
    }

    @Override
    public @Nonnull List<ExternalIntegrationStatus> getStatuses() {
        return statuses;
    }

    @Override
    public @Nonnull List<ExternalBlockIntegration> inspectBlock(@Nonnull Block block) {
        Objects.requireNonNull(block, "block");
        List<ExternalBlockIntegration> result = new ArrayList<>();
        for (ProviderRegistration registration : activeProviders) {
            ExternalIntegrationProvider provider = registration.provider;
            Plugin plugin = provider.getPlugin();
            if (!plugin.isEnabled()) {
                continue;
            }

            String key = operationKey(registration.integrationId, "block-inspection");
            if (!providerCircuitBreaker.canAttempt(key, System.currentTimeMillis())) {
                continue;
            }

            try {
                provider.inspectBlock(block).ifPresent(result::add);
                markProviderSuccess(key);
            } catch (RuntimeException | LinkageError failure) {
                recordProviderFailure(
                        registration.integrationId,
                        registration.displayName,
                        plugin,
                        "block-inspection",
                        failure);
            }
        }
        result.sort(Comparator.comparing(ExternalBlockIntegration::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    @Override
    public @Nonnull List<ExternalIntegrationFailureSnapshot> getFailureSnapshots(int limit) {
        return failureTracker.snapshot(limit);
    }

    @Override
    public int getActiveFailureCount() {
        return failureTracker.getActiveFailureCount();
    }

    @Override
    public long getObservedFailureCount() {
        return failureTracker.getTotalFailureCount();
    }

    @Override
    public long getSuppressedFailureReportCount() {
        return failureTracker.getSuppressedReportCount();
    }

    @Override
    public boolean retry(@Nonnull String integrationId) {
        String normalized = normalize(Objects.requireNonNull(integrationId, "integrationId"));
        if (normalized.isEmpty()) {
            return false;
        }

        int cleared = 0;
        String prefix = normalized + '|';
        for (String key : List.copyOf(providerFailures.keySet())) {
            if (key.startsWith(prefix)) {
                providerFailures.remove(key);
                if (providerCircuitBreaker.clear(key)) {
                    cleared++;
                }
                failureTracker.clear(key);
                cleared++;
            }
        }
        cleared += failureTracker.clearIntegration(normalized);
        return cleared > 0;
    }

    @Override
    public int retryAll() {
        int tracked = failureTracker.getActiveFailureCount();
        int circuits = providerCircuitBreaker.clearAll();
        providerFailures.clear();
        failureTracker.clearAll();
        return Math.max(tracked, circuits);
    }

    private void recordProviderFailure(
            String integrationId, String displayName, Plugin plugin, String operation, Throwable failure) {
        String key = operationKey(integrationId, operation);
        long now = System.currentTimeMillis();
        int errors = providerFailures.merge(key, 1, Integer::sum);
        int threshold = getFailureThreshold();
        boolean suppressFullReport = errors > 1;
        long pausedUntil = 0L;

        if (providerCircuitBreaker.isOpen(key)) {
            long cooldownSeconds = getFailureCooldownSeconds();
            pausedUntil = now + cooldownSeconds * 1000L;
            providerCircuitBreaker.open(key, pausedUntil);
            providerFailures.put(key, threshold);
            failureTracker.recordFailure(
                    key,
                    integrationId,
                    displayName,
                    plugin.getName(),
                    operation,
                    failure,
                    threshold,
                    now,
                    pausedUntil,
                    true);
            owner.getLogger().log(
                    Level.WARNING,
                    "External integration " + integrationId + " failed its retry for " + operation
                            + "; the operation remains isolated for " + cooldownSeconds + " seconds.");
            return;
        }

        if (errors == 1) {
            owner.getLogger().log(
                    Level.WARNING,
                    "External integration " + integrationId + " failed during " + operation
                            + ". Slimefun isolated the exception from normal core processing.",
                    failure);
        }

        if (errors >= threshold) {
            long cooldownSeconds = getFailureCooldownSeconds();
            pausedUntil = now + cooldownSeconds * 1000L;
            providerCircuitBreaker.open(key, pausedUntil);
            providerFailures.put(key, threshold);
            owner.getLogger().log(
                    Level.WARNING,
                    "External integration " + integrationId + " failed " + threshold + " consecutive " + operation
                            + " calls and is isolated for " + cooldownSeconds + " seconds.");
        }

        failureTracker.recordFailure(
                key,
                integrationId,
                displayName,
                plugin.getName(),
                operation,
                failure,
                errors,
                now,
                pausedUntil,
                suppressFullReport);
    }

    private void markProviderSuccess(String key) {
        providerFailures.remove(key);
        providerCircuitBreaker.clear(key);
        failureTracker.clear(key);
    }

    private int getFailureThreshold() {
        if (owner instanceof JavaPlugin javaPlugin) {
            int configured = javaPlugin.getConfig().getInt("stability.external-integration-failure-threshold");
            if (configured >= 2) {
                return Math.min(50, configured);
            }
        }
        return DEFAULT_FAILURE_THRESHOLD;
    }

    private long getFailureCooldownSeconds() {
        if (owner instanceof JavaPlugin javaPlugin) {
            int configured = javaPlugin.getConfig().getInt("stability.external-integration-cooldown-seconds");
            if (configured > 0) {
                return Math.max(30L, configured);
            }
        }
        return DEFAULT_FAILURE_COOLDOWN_SECONDS;
    }

    private void retryAllForPlugin(Plugin plugin) {
        for (ExternalIntegrationStatus status : statuses) {
            if (status.getPluginName().equalsIgnoreCase(plugin.getName())) {
                retry(status.getIntegrationId());
            }
        }
    }

    private void addKnown(Map<String, ExternalIntegrationStatus> result, KnownSystem known) {
        Plugin plugin = findPlugin(known.pluginName);
        boolean detected = plugin != null;
        boolean enabled = detected && plugin.isEnabled();
        result.put(known.id, new ExternalIntegrationStatus(
                known.id,
                known.displayName,
                known.pluginName,
                detected ? plugin.getDescription().getVersion() : null,
                detected,
                enabled,
                false,
                Set.of(),
                known.detail));
    }

    private void replaceKnownDetail(
            Map<String, ExternalIntegrationStatus> result, KnownSystem known, Plugin plugin, String detail) {
        result.put(known.id, new ExternalIntegrationStatus(
                known.id,
                known.displayName,
                plugin.getName(),
                plugin.getDescription().getVersion(),
                true,
                plugin.isEnabled(),
                false,
                Set.of(),
                detail));
    }

    private Plugin findPlugin(String name) {
        for (Plugin plugin : owner.getServer().getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }

    private static String operationKey(String integrationId, String operation) {
        return normalize(integrationId) + '|' + operation.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private record KnownSystem(String id, String displayName, String pluginName, String detail) {}

    private record ProviderRegistration(
            String integrationId, String displayName, ExternalIntegrationProvider provider) {}
}
