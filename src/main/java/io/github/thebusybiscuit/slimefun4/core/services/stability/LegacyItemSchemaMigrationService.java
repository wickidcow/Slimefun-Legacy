package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidation;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Prepares and validates short-lived execution plans for same-ID addon item-schema migration. */
public final class LegacyItemSchemaMigrationService {

    static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;

    private final JavaPlugin plugin;
    private final AtomicLong generation = new AtomicLong();
    private final Map<String, LegacyItemSchemaMigrationPlan> preparedPlans = new ConcurrentHashMap<>();

    public LegacyItemSchemaMigrationService(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Replaces all previously prepared schema plans with plans derived from this completed migration-aware scan.
     * A scan containing any Doctor failure never creates executable schema plans.
     */
    public @Nonnull List<LegacyItemSchemaMigrationPlan> preparePlans(@Nonnull ItemDoctorReport report) {
        preparedPlans.clear();
        LegacyItemSchemaProbeService.Session session = report.getSchemaProbeSession();
        if (!report.isComplete() || report.getFailures() != 0L || session == null) {
            return List.of();
        }

        List<LegacyItemSchemaProbeService.VerifiedAuthorization> verified = session.getVerifiedAuthorizations();
        if (verified.isEmpty()) return List.of();

        Map<MigratorKey, RegisteredServiceProvider<LegacyItemSchemaMigrator>> migrators = snapshotMigrators();
        Set<MigratorKey> ambiguous = findAmbiguousMigrators();
        Map<String, List<LegacyItemSchemaMigrationPlan.Authorization>> byProvider = new LinkedHashMap<>();
        Map<String, String> migrationNames = new HashMap<>();
        Map<String, String> versions = new HashMap<>();

        for (LegacyItemSchemaProbeService.VerifiedAuthorization authorization : verified) {
            MigratorKey key = new MigratorKey(authorization.providerId(), authorization.candidateType());
            RegisteredServiceProvider<LegacyItemSchemaMigrator> registration = migrators.get(key);
            if (registration == null || ambiguous.contains(key)) continue;

            Plugin owner = registration.getPlugin();
            if (owner == null || !owner.isEnabled() || !owner.getName().equals(authorization.providerId())) continue;

            String providerKey = normalizeProviderId(authorization.providerId());
            String version = owner.getDescription().getVersion();
            String previousVersion = versions.putIfAbsent(providerKey, version);
            if (previousVersion != null && !previousVersion.equals(version)) continue;
            migrationNames.putIfAbsent(providerKey, authorization.migrationName());
            byProvider.computeIfAbsent(providerKey, ignored -> new ArrayList<>())
                    .add(new LegacyItemSchemaMigrationPlan.Authorization(
                            authorization.slimefunId(),
                            authorization.candidateType(),
                            authorization.validationClaim(),
                            authorization.migrationPayload(),
                            authorization.candidateCount()));
        }

        List<LegacyItemSchemaMigrationPlan> plans = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<LegacyItemSchemaMigrationPlan.Authorization>> entry : byProvider.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String providerId = findCanonicalProviderId(verified, entry.getKey());
            String migrationName = migrationNames.get(entry.getKey());
            String version = versions.get(entry.getKey());
            if (providerId == null || migrationName == null || version == null) continue;

            LegacyItemSchemaMigrationPlan plan = new LegacyItemSchemaMigrationPlan(
                    providerId,
                    migrationName,
                    version,
                    generation.incrementAndGet(),
                    now,
                    PLAN_TTL_MILLIS,
                    entry.getValue());
            preparedPlans.put(entry.getKey(), plan);
            plans.add(plan);
        }
        return List.copyOf(plans);
    }

    public @Nonnull List<LegacyItemSchemaMigrationPlan> getPreparedPlans() {
        long now = System.currentTimeMillis();
        List<LegacyItemSchemaMigrationPlan> plans = new ArrayList<>();
        for (Map.Entry<String, LegacyItemSchemaMigrationPlan> entry : preparedPlans.entrySet()) {
            LegacyItemSchemaMigrationPlan plan = entry.getValue();
            if (plan.isExpired(now)) {
                preparedPlans.remove(entry.getKey(), plan);
            } else {
                plans.add(plan);
            }
        }
        plans.sort((left, right) -> left.getProviderId().compareToIgnoreCase(right.getProviderId()));
        return List.copyOf(plans);
    }

    public @Nonnull Optional<LegacyItemSchemaMigrationPlan> getPreparedPlan(@Nonnull String providerId) {
        String key = normalizeProviderId(providerId);
        LegacyItemSchemaMigrationPlan plan = preparedPlans.get(key);
        if (plan == null) return Optional.empty();
        if (plan.isExpired(System.currentTimeMillis())) {
            preparedPlans.remove(key, plan);
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    public void invalidatePreparedPlan(@Nonnull String providerId) {
        preparedPlans.remove(normalizeProviderId(providerId));
    }

    public void invalidateAllPreparedPlans() {
        preparedPlans.clear();
    }

    public long getPlanTtlMillis() { return PLAN_TTL_MILLIS; }

    /**
     * Re-runs addon-owned backing-state validation after a plan has been consumed and before live mutation begins.
     * Every authorization must still be VERIFIED and reproduce the same private migration payload.
     */
    public @Nonnull CompletionStage<Boolean> revalidatePlan(@Nonnull LegacyItemSchemaMigrationPlan plan) {
        if (plan.isExpired(System.currentTimeMillis())) {
            return CompletableFuture.completedFuture(false);
        }

        Plugin owner = Bukkit.getPluginManager().getPlugin(plan.getProviderId());
        if (owner == null || !owner.isEnabled() || !plan.matchesProviderVersion(owner.getDescription().getVersion())) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, LegacyItemSchemaValidator> validators = new HashMap<>();
        Set<String> ambiguousTypes = new HashSet<>();
        for (RegisteredServiceProvider<LegacyItemSchemaValidator> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaValidator.class)) {
            if (registration.getPlugin() != owner) continue;
            try {
                Set<String> types = registration.getProvider().getSupportedCandidateTypes();
                if (types == null) continue;
                for (String type : types) {
                    if (type == null || type.isBlank()) continue;
                    String normalized = type.trim();
                    if (validators.putIfAbsent(normalized, registration.getProvider()) != null) {
                        ambiguousTypes.add(normalized);
                    }
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not inspect schema validator registration for " + plan.getProviderId() + '.', throwable);
                return CompletableFuture.completedFuture(false);
            }
        }
        for (String type : ambiguousTypes) validators.remove(type);

        List<CompletableFuture<Boolean>> checks = new ArrayList<>();
        for (LegacyItemSchemaMigrationPlan.Authorization authorization : plan.authorizations()) {
            LegacyItemSchemaValidator validator = validators.get(authorization.candidateType());
            if (validator == null) {
                return CompletableFuture.completedFuture(false);
            }

            try {
                CompletionStage<LegacyItemSchemaValidation> stage = validator.validateCandidate(
                        authorization.candidateType(), authorization.validationClaim());
                if (stage == null) {
                    return CompletableFuture.completedFuture(false);
                }
                CompletableFuture<Boolean> check = stage.handle((result, error) -> error == null
                                && result != null
                                && result.getStatus() == LegacyItemSchemaValidation.Status.VERIFIED
                                && Objects.equals(result.getMigrationPayload(), authorization.migrationPayload()))
                        .toCompletableFuture();
                checks.add(check);
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not revalidate schema backing state for " + plan.getProviderId() + '.', throwable);
                return CompletableFuture.completedFuture(false);
            }
        }

        CompletableFuture<?>[] all = checks.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all)
                .thenApply(ignored -> checks.stream().allMatch(check -> Boolean.TRUE.equals(check.getNow(false))));
    }

    /** Builds an executor only if the same addon version still owns unambiguous probe and migrator registrations. */
    public @Nonnull Optional<LegacyItemSchemaMigrationExecutor> createExecutor(
            @Nonnull LegacyItemSchemaMigrationPlan plan) {
        if (plan.isExpired(System.currentTimeMillis())) return Optional.empty();

        Plugin owner = Bukkit.getPluginManager().getPlugin(plan.getProviderId());
        if (owner == null || !owner.isEnabled() || !plan.matchesProviderVersion(owner.getDescription().getVersion())) {
            return Optional.empty();
        }

        Map<String, LegacyItemSchemaProbe> probes = new HashMap<>();
        Set<String> ambiguousProbeIds = new HashSet<>();
        for (RegisteredServiceProvider<LegacyItemSchemaProbe> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaProbe.class)) {
            if (registration.getPlugin() != owner) continue;
            try {
                Set<String> ids = registration.getProvider().getSupportedItemIds();
                if (ids == null) continue;
                for (String id : ids) {
                    if (id == null || id.isBlank()) continue;
                    String normalized = id.trim();
                    if (probes.putIfAbsent(normalized, registration.getProvider()) != null) {
                        ambiguousProbeIds.add(normalized);
                    }
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not inspect schema probe registration for " + plan.getProviderId() + '.', throwable);
                return Optional.empty();
            }
        }
        for (String id : ambiguousProbeIds) probes.remove(id);

        Map<String, LegacyItemSchemaMigrator> migrators = new HashMap<>();
        Set<String> ambiguousTypes = new HashSet<>();
        for (RegisteredServiceProvider<LegacyItemSchemaMigrator> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaMigrator.class)) {
            if (registration.getPlugin() != owner) continue;
            try {
                Set<String> types = registration.getProvider().getSupportedCandidateTypes();
                if (types == null) continue;
                for (String type : types) {
                    if (type == null || type.isBlank()) continue;
                    String normalized = type.trim();
                    if (migrators.putIfAbsent(normalized, registration.getProvider()) != null) {
                        ambiguousTypes.add(normalized);
                    }
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not inspect schema migrator registration for " + plan.getProviderId() + '.', throwable);
                return Optional.empty();
            }
        }
        for (String type : ambiguousTypes) migrators.remove(type);

        if (probes.isEmpty() || migrators.isEmpty()) return Optional.empty();
        return Optional.of(new LegacyItemSchemaMigrationExecutor(plan, probes, migrators));
    }

    private Map<MigratorKey, RegisteredServiceProvider<LegacyItemSchemaMigrator>> snapshotMigrators() {
        Map<MigratorKey, RegisteredServiceProvider<LegacyItemSchemaMigrator>> result = new HashMap<>();
        for (RegisteredServiceProvider<LegacyItemSchemaMigrator> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaMigrator.class)) {
            if (registration.getPlugin() == null || !registration.getPlugin().isEnabled()) continue;
            String providerId = registration.getPlugin().getName();
            try {
                Set<String> types = registration.getProvider().getSupportedCandidateTypes();
                if (types == null) continue;
                for (String type : types) {
                    if (type == null || type.isBlank()) continue;
                    result.putIfAbsent(new MigratorKey(providerId, type.trim()), registration);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not initialize legacy schema migrator from " + providerId + '.', throwable);
            }
        }
        return result;
    }

    private Set<MigratorKey> findAmbiguousMigrators() {
        Map<MigratorKey, Integer> counts = new HashMap<>();
        for (RegisteredServiceProvider<LegacyItemSchemaMigrator> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaMigrator.class)) {
            if (registration.getPlugin() == null || !registration.getPlugin().isEnabled()) continue;
            try {
                Set<String> types = registration.getProvider().getSupportedCandidateTypes();
                if (types == null) continue;
                for (String type : types) {
                    if (type == null || type.isBlank()) continue;
                    counts.merge(new MigratorKey(registration.getPlugin().getName(), type.trim()), 1, Integer::sum);
                }
            } catch (Throwable ignored) {
                // snapshotMigrators logs provider initialization problems and those registrations are not executable.
            }
        }
        Set<MigratorKey> ambiguous = new HashSet<>();
        for (Map.Entry<MigratorKey, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) ambiguous.add(entry.getKey());
        }
        return ambiguous;
    }

    private static String findCanonicalProviderId(
            List<LegacyItemSchemaProbeService.VerifiedAuthorization> authorizations, String normalizedProviderId) {
        for (LegacyItemSchemaProbeService.VerifiedAuthorization authorization : authorizations) {
            if (normalizeProviderId(authorization.providerId()).equals(normalizedProviderId)) {
                return authorization.providerId();
            }
        }
        return null;
    }

    private static String normalizeProviderId(String providerId) {
        return providerId.trim().toLowerCase(Locale.ROOT);
    }

    private record MigratorKey(String providerId, String candidateType) {}
}
