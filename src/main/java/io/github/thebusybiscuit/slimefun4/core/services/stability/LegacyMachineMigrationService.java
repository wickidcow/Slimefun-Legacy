package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyMachineMigrationCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyMachineMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyMachineMigrationResult;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Discovers, fingerprints, revalidates and executes exact addon-owned live-machine migrations. */
public final class LegacyMachineMigrationService {

    private static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;
    private static final int MAX_DETAIL_LINES = 20;

    private final JavaPlugin plugin;
    private final Map<String, LegacyMachineMigrationPlan> preparedPlans = new ConcurrentHashMap<>();

    public LegacyMachineMigrationService(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public @Nonnull List<RegisteredServiceProvider<LegacyMachineMigrationProvider>> getProviders() {
        List<RegisteredServiceProvider<LegacyMachineMigrationProvider>> providers =
                new ArrayList<>(Bukkit.getServicesManager().getRegistrations(LegacyMachineMigrationProvider.class));
        providers.removeIf(provider -> provider.getPlugin() == null || !provider.getPlugin().isEnabled());
        providers.sort(Comparator.comparing(this::getProviderId, String.CASE_INSENSITIVE_ORDER));
        return providers;
    }

    public @Nonnull Optional<RegisteredServiceProvider<LegacyMachineMigrationProvider>> findProvider(
            @Nonnull String providerId) {
        List<RegisteredServiceProvider<LegacyMachineMigrationProvider>> matches = getProviders().stream()
                .filter(provider -> getProviderId(provider).equalsIgnoreCase(providerId))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public @Nonnull String getProviderId(@Nonnull RegisteredServiceProvider<LegacyMachineMigrationProvider> registration) {
        return registration.getPlugin() == null ? "unknown" : registration.getPlugin().getName();
    }

    public @Nonnull String getProviderVersion(
            @Nonnull RegisteredServiceProvider<LegacyMachineMigrationProvider> registration) {
        return registration.getPlugin() == null
                ? "unknown"
                : registration.getPlugin().getPluginMeta().getVersion();
    }

    public @Nonnull String getProviderName(
            @Nonnull RegisteredServiceProvider<LegacyMachineMigrationProvider> registration) {
        String fallback = getProviderId(registration);
        try {
            String name = registration.getProvider().getMigrationName();
            return name == null || name.isBlank() ? fallback : name.trim();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Machine migration provider name lookup failed for " + fallback, throwable);
            return fallback;
        }
    }

    public @Nonnull Map<String, String> getMappings(
            @Nonnull RegisteredServiceProvider<LegacyMachineMigrationProvider> registration) {
        String providerId = getProviderId(registration);
        try {
            Map<String, String> mappings = registration.getProvider().getLegacyMachineMappings();
            if (mappings == null) {
                throw new IllegalStateException("Machine migration provider returned null mappings");
            }

            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (entry.getKey() == null
                        || entry.getValue() == null
                        || entry.getKey().isBlank()
                        || entry.getValue().isBlank()) {
                    throw new IllegalStateException("Machine migration provider returned a blank/null mapping");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            return Collections.unmodifiableMap(copy);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Unable to read machine migration mappings from " + providerId, throwable);
            return Collections.emptyMap();
        }
    }

    /**
     * Performs one read-only provider scan and creates a short-lived plan only when every candidate is valid.
     * Provider, registry or candidate problems fail closed and no partial plan is retained.
     */
    public @Nonnull Preparation preparePlan(
            @Nonnull RegisteredServiceProvider<LegacyMachineMigrationProvider> registration) {
        String providerId = getProviderId(registration);
        invalidatePreparedPlan(providerId);

        Map<String, String> mappings = getMappings(registration);
        List<String> problems = validateMappings(mappings);
        List<LegacyMachineMigrationCandidate> candidates = new ArrayList<>();

        if (problems.isEmpty()) {
            try {
                Collection<LegacyMachineMigrationCandidate> scanned = registration.getProvider().scanLoadedCandidates();
                if (scanned == null) {
                    problems.add("Provider returned null candidate collection.");
                } else {
                    candidates.addAll(scanned);
                    validateCandidates(mappings, candidates, problems);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Legacy machine migration scan failed for " + providerId, throwable);
                problems.add("Provider scan threw " + throwable.getClass().getSimpleName() + ".");
            }
        }

        if (!problems.isEmpty() || candidates.isEmpty()) {
            return new Preparation(null, candidates.size(), List.copyOf(problems));
        }

        long now = System.currentTimeMillis();
        LegacyMachineMigrationPlan plan = new LegacyMachineMigrationPlan(
                providerId,
                getProviderVersion(registration),
                now,
                now + PLAN_TTL_MILLIS,
                mappings,
                candidates);
        preparedPlans.put(normalize(providerId), plan);
        return new Preparation(plan, candidates.size(), List.of());
    }

    public @Nonnull Optional<LegacyMachineMigrationPlan> getPreparedPlan(@Nonnull String providerId) {
        String key = normalize(providerId);
        LegacyMachineMigrationPlan plan = preparedPlans.get(key);
        if (plan == null) {
            return Optional.empty();
        }
        if (plan.isExpired(System.currentTimeMillis())) {
            preparedPlans.remove(key, plan);
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    public void invalidatePreparedPlan(@Nonnull String providerId) {
        preparedPlans.remove(normalize(providerId));
    }

    public long getPlanTtlMillis() {
        return PLAN_TTL_MILLIS;
    }

    /**
     * Consumes and executes one exact candidate plan. Every candidate is checked for loaded scope and
     * provider-owned state equality immediately before that location is handed back to the addon.
     */
    public @Nonnull LegacyMachineMigrationExecutionReport execute(
            @Nonnull RegisteredServiceProvider<LegacyMachineMigrationProvider> registration,
            @Nonnull LegacyMachineMigrationPlan plan) {
        String providerId = getProviderId(registration);
        invalidatePreparedPlan(providerId);

        if (plan.isExpired(System.currentTimeMillis())) {
            return blockedPlan(plan, "Execution plan expired before migration began.");
        }

        Map<String, String> currentMappings = getMappings(registration);
        String currentVersion = getProviderVersion(registration);
        List<String> mappingProblems = validateMappings(currentMappings);
        if (!providerId.equalsIgnoreCase(plan.getProviderId())
                || !plan.matchesProviderSnapshot(currentVersion, currentMappings)
                || !mappingProblems.isEmpty()) {
            String detail = mappingProblems.isEmpty()
                    ? "Provider version or mapping snapshot changed after planning; execution was blocked."
                    : "Slimefun legacy-ID registry validation changed after planning; execution was blocked.";
            return blockedPlan(plan, detail);
        }

        long revalidated = 0L;
        long migrated = 0L;
        long skippedChanged = 0L;
        long blocked = 0L;
        long failures = 0L;
        List<String> details = new ArrayList<>();

        for (LegacyMachineMigrationCandidate candidate : plan.getCandidates()) {
            if (!isStillLoaded(candidate)) {
                skippedChanged++;
                addDetail(details, "Skipped " + candidate.locationKey() + ": chunk/world is no longer loaded.");
                continue;
            }

            boolean valid;
            try {
                valid = registration.getProvider().isCandidateStillValid(candidate);
            } catch (Throwable throwable) {
                failures++;
                addDetail(details, "Revalidation failed at " + candidate.locationKey() + ": "
                        + throwable.getClass().getSimpleName());
                plugin.getLogger().log(
                        Level.WARNING,
                        "Legacy machine migration revalidation failed for " + providerId + " at "
                                + candidate.locationKey(),
                        throwable);
                continue;
            }

            if (!valid) {
                skippedChanged++;
                addDetail(details, "Skipped " + candidate.locationKey() + ": addon state claim no longer matches.");
                continue;
            }
            revalidated++;

            try {
                LegacyMachineMigrationResult result = registration.getProvider().migrate(candidate);
                if (result == null) {
                    failures++;
                    addDetail(details, "Provider returned null result at " + candidate.locationKey() + '.');
                    continue;
                }
                switch (result.status()) {
                    case MIGRATED -> migrated++;
                    case SKIPPED_CHANGED -> skippedChanged++;
                    case BLOCKED -> blocked++;
                    case FAILED -> failures++;
                }
                if (!result.detail().isBlank()) {
                    addDetail(details, candidate.locationKey() + ": " + result.detail());
                }
            } catch (Throwable throwable) {
                failures++;
                addDetail(details, "Migration threw at " + candidate.locationKey() + ": "
                        + throwable.getClass().getSimpleName());
                plugin.getLogger().log(
                        Level.WARNING,
                        "Legacy machine migration failed for " + providerId + " at " + candidate.locationKey(),
                        throwable);
            }
        }

        return new LegacyMachineMigrationExecutionReport(
                plan.getCandidates().size(), revalidated, migrated, skippedChanged, blocked, failures, details);
    }

    public @Nonnull List<String> validateMappings(@Nonnull Map<String, String> mappings) {
        List<String> problems = new ArrayList<>();
        if (mappings.isEmpty()) {
            problems.add("Provider declared no legacy machine mappings.");
            return problems;
        }

        var registry = Slimefun.getRegistry();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String sourceId = entry.getKey();
            String targetId = entry.getValue();
            if (sourceId.equals(targetId)) {
                problems.add("Source and target are identical: " + sourceId + '.');
                continue;
            }

            Optional<String> resolved = registry.resolveLegacySlimefunItemId(sourceId);
            if (resolved.isEmpty()) {
                problems.add("Source is not a resolvable Slimefun legacy ID: " + sourceId + '.');
                continue;
            }
            if (!targetId.equals(resolved.get())) {
                problems.add("Mapping disagrees with canonical Slimefun registry target: "
                        + sourceId + " -> " + targetId + " (registry resolves to " + resolved.get() + ").");
                continue;
            }
            if (!registry.getSlimefunItemIds().containsKey(targetId)) {
                problems.add("Target is not currently registered: " + targetId + '.');
            }
        }
        return problems;
    }

    private void validateCandidates(
            Map<String, String> mappings,
            List<LegacyMachineMigrationCandidate> candidates,
            List<String> problems) {
        Set<String> locations = new LinkedHashSet<>();
        for (LegacyMachineMigrationCandidate candidate : candidates) {
            if (candidate == null) {
                problems.add("Provider returned a null candidate.");
                continue;
            }
            String expectedTarget = mappings.get(candidate.sourceId());
            if (!candidate.targetId().equals(expectedTarget)) {
                problems.add("Candidate mapping is undeclared or changed at " + candidate.locationKey() + '.');
            }
            if (!locations.add(candidate.locationKey())) {
                problems.add("Provider returned duplicate candidate location " + candidate.locationKey() + '.');
            }
            if (!isStillLoaded(candidate)) {
                problems.add("Candidate is outside loaded scope: " + candidate.locationKey() + '.');
            }
        }
    }

    private boolean isStillLoaded(LegacyMachineMigrationCandidate candidate) {
        World world = Bukkit.getWorld(candidate.worldId());
        return world != null && world.isChunkLoaded(candidate.x() >> 4, candidate.z() >> 4);
    }

    private LegacyMachineMigrationExecutionReport blockedPlan(LegacyMachineMigrationPlan plan, String detail) {
        return new LegacyMachineMigrationExecutionReport(
                plan.getCandidates().size(), 0L, 0L, 0L, 1L, 0L, List.of(detail));
    }

    private void addDetail(List<String> details, String detail) {
        if (details.size() < MAX_DETAIL_LINES) {
            details.add(detail);
        }
    }

    private String normalize(String providerId) {
        return providerId.toLowerCase(Locale.ROOT);
    }

    public record Preparation(LegacyMachineMigrationPlan plan, int candidateCount, @Nonnull List<String> problems) {
        public Preparation {
            problems = List.copyOf(problems);
        }

        public boolean isReady() {
            return plan != null && problems.isEmpty();
        }
    }
}
