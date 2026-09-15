package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationResult;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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

/** Discovers, fingerprints, revalidates and executes exact addon-owned placed-block migrations. */
public final class LegacyBlockMigrationService {

    private static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;
    private static final int MAX_DETAIL_LINES = 20;

    private final JavaPlugin plugin;
    private final Map<String, LegacyBlockMigrationPlan> preparedPlans = new ConcurrentHashMap<>();

    public LegacyBlockMigrationService(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public @Nonnull List<RegisteredServiceProvider<LegacyBlockMigrationProvider>> getProviders() {
        List<RegisteredServiceProvider<LegacyBlockMigrationProvider>> providers =
                new ArrayList<>(Bukkit.getServicesManager().getRegistrations(LegacyBlockMigrationProvider.class));
        providers.removeIf(provider -> provider.getPlugin() == null || !provider.getPlugin().isEnabled());
        providers.sort(Comparator.comparing(this::getProviderId, String.CASE_INSENSITIVE_ORDER));
        return providers;
    }

    public @Nonnull Optional<RegisteredServiceProvider<LegacyBlockMigrationProvider>> findProvider(
            @Nonnull String providerId) {
        return getProviders().stream()
                .filter(provider -> getProviderId(provider).equalsIgnoreCase(providerId))
                .findFirst();
    }

    public @Nonnull String getProviderId(@Nonnull RegisteredServiceProvider<LegacyBlockMigrationProvider> registration) {
        return registration.getPlugin() == null ? "unknown" : registration.getPlugin().getName();
    }

    public @Nonnull String getProviderVersion(
            @Nonnull RegisteredServiceProvider<LegacyBlockMigrationProvider> registration) {
        return registration.getPlugin() == null
                ? "unknown"
                : registration.getPlugin().getPluginMeta().getVersion();
    }

    public @Nonnull String getProviderName(
            @Nonnull RegisteredServiceProvider<LegacyBlockMigrationProvider> registration) {
        String fallback = getProviderId(registration);
        try {
            String name = registration.getProvider().getMigrationName();
            return name == null || name.isBlank() ? fallback : name.trim();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Block migration provider name lookup failed for " + fallback, throwable);
            return fallback;
        }
    }

    public @Nonnull Map<String, String> getMappings(
            @Nonnull RegisteredServiceProvider<LegacyBlockMigrationProvider> registration) {
        String providerId = getProviderId(registration);
        try {
            Map<String, String> mappings = registration.getProvider().getLegacyBlockMappings();
            if (mappings == null) {
                throw new IllegalStateException("Block migration provider returned null mappings");
            }

            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (entry.getKey() == null
                        || entry.getValue() == null
                        || entry.getKey().isBlank()
                        || entry.getValue().isBlank()) {
                    throw new IllegalStateException("Block migration provider returned a blank/null mapping");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            return Collections.unmodifiableMap(copy);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Unable to read block migration mappings from " + providerId, throwable);
            return Collections.emptyMap();
        }
    }

    /**
     * Performs one read-only provider scan and creates a short-lived plan only when every candidate is valid.
     * Provider/mapping problems fail closed and no partial plan is retained.
     */
    public @Nonnull Preparation preparePlan(
            @Nonnull RegisteredServiceProvider<LegacyBlockMigrationProvider> registration) {
        String providerId = getProviderId(registration);
        invalidatePreparedPlan(providerId);

        Map<String, String> mappings = getMappings(registration);
        List<String> problems = validateMappings(mappings);
        List<LegacyBlockMigrationCandidate> candidates = new ArrayList<>();

        if (problems.isEmpty()) {
            try {
                Collection<LegacyBlockMigrationCandidate> scanned = registration.getProvider().scanLoadedCandidates();
                if (scanned == null) {
                    problems.add("Provider returned null candidate collection.");
                } else {
                    candidates.addAll(scanned);
                    validateCandidates(mappings, candidates, problems);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Legacy block migration scan failed for " + providerId, throwable);
                problems.add("Provider scan threw " + throwable.getClass().getSimpleName() + ".");
            }
        }

        if (!problems.isEmpty() || candidates.isEmpty()) {
            return new Preparation(null, candidates.size(), List.copyOf(problems));
        }

        long now = System.currentTimeMillis();
        LegacyBlockMigrationPlan plan = new LegacyBlockMigrationPlan(
                providerId,
                getProviderVersion(registration),
                now,
                now + PLAN_TTL_MILLIS,
                mappings,
                candidates);
        preparedPlans.put(normalize(providerId), plan);
        return new Preparation(plan, candidates.size(), List.of());
    }

    public @Nonnull Optional<LegacyBlockMigrationPlan> getPreparedPlan(@Nonnull String providerId) {
        String key = normalize(providerId);
        LegacyBlockMigrationPlan plan = preparedPlans.get(key);
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
    public @Nonnull LegacyBlockMigrationExecutionReport execute(
            @Nonnull RegisteredServiceProvider<LegacyBlockMigrationProvider> registration,
            @Nonnull LegacyBlockMigrationPlan plan) {
        String providerId = getProviderId(registration);
        invalidatePreparedPlan(providerId);

        Map<String, String> currentMappings = getMappings(registration);
        String currentVersion = getProviderVersion(registration);
        if (!providerId.equalsIgnoreCase(plan.getProviderId())
                || !plan.matchesProviderSnapshot(currentVersion, currentMappings)) {
            return new LegacyBlockMigrationExecutionReport(
                    plan.getCandidates().size(), 0L, 0L, 0L, 1L, 0L,
                    List.of("Provider version or mapping snapshot changed after planning; execution was blocked."));
        }

        long revalidated = 0L;
        long migrated = 0L;
        long skippedChanged = 0L;
        long blocked = 0L;
        long failures = 0L;
        List<String> details = new ArrayList<>();

        for (LegacyBlockMigrationCandidate candidate : plan.getCandidates()) {
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
                plugin.getLogger().log(Level.WARNING,
                        "Legacy block migration revalidation failed for " + providerId + " at " + candidate.locationKey(),
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
                LegacyBlockMigrationResult result = registration.getProvider().migrate(candidate);
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
                plugin.getLogger().log(Level.WARNING,
                        "Legacy block migration failed for " + providerId + " at " + candidate.locationKey(),
                        throwable);
            }
        }

        return new LegacyBlockMigrationExecutionReport(
                plan.getCandidates().size(), revalidated, migrated, skippedChanged, blocked, failures, details);
    }

    private @Nonnull List<String> validateMappings(@Nonnull Map<String, String> mappings) {
        List<String> problems = new ArrayList<>();
        if (mappings.isEmpty()) {
            problems.add("Provider declared no legacy block mappings.");
            return problems;
        }

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String registered = Slimefun.getRegistry().getLegacySlimefunItemIdTarget(entry.getKey()).orElse(null);
            if (!entry.getValue().equals(registered)) {
                problems.add("Mapping disagrees with Slimefun registry: " + entry.getKey() + " -> " + entry.getValue());
                continue;
            }
            if (SlimefunItem.getById(entry.getValue()) == null) {
                problems.add("Target is not registered: " + entry.getValue());
            }
        }
        return problems;
    }

    private void validateCandidates(
            Map<String, String> mappings,
            List<LegacyBlockMigrationCandidate> candidates,
            List<String> problems) {
        Set<String> locations = new LinkedHashSet<>();
        for (LegacyBlockMigrationCandidate candidate : candidates) {
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

    private boolean isStillLoaded(LegacyBlockMigrationCandidate candidate) {
        World world = Bukkit.getWorld(candidate.worldId());
        return world != null && world.isChunkLoaded(candidate.x() >> 4, candidate.z() >> 4);
    }

    private void addDetail(List<String> details, String detail) {
        if (details.size() < MAX_DETAIL_LINES) {
            details.add(detail);
        }
    }

    private String normalize(String providerId) {
        return providerId.toLowerCase(Locale.ROOT);
    }

    public record Preparation(LegacyBlockMigrationPlan plan, int candidateCount, @Nonnull List<String> problems) {
        public Preparation {
            problems = List.copyOf(problems);
        }

        public boolean isReady() {
            return plan != null && problems.isEmpty();
        }
    }
}
