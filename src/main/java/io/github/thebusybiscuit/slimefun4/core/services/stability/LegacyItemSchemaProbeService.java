package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidation;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Creates per-run snapshots of addon-provided read-only legacy item schema probes and validators. */
public final class LegacyItemSchemaProbeService {

    private final JavaPlugin plugin;

    public LegacyItemSchemaProbeService(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Snapshots enabled probe and validator registrations once for a migration-aware Doctor scan. */
    @Nonnull
    Session createSession(@Nonnull ItemDoctorReport report) {
        Map<String, List<ProbeRegistration>> byItemId = new HashMap<>();
        Map<ValidatorKey, ValidatorRegistration> validators = new HashMap<>();
        int providers = 0;

        for (RegisteredServiceProvider<LegacyItemSchemaProbe> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaProbe.class)) {
            if (registration.getPlugin() == null || !registration.getPlugin().isEnabled()) {
                continue;
            }
            String providerId = registration.getPlugin().getName();
            LegacyItemSchemaProbe provider = registration.getProvider();
            try {
                String migrationName = provider.getMigrationName();
                if (migrationName == null || migrationName.isBlank()) {
                    migrationName = providerId;
                } else {
                    migrationName = migrationName.trim();
                }
                Set<String> supportedIds = provider.getSupportedItemIds();
                if (supportedIds == null) {
                    throw new IllegalStateException("Schema probe returned null supported item IDs");
                }
                ProbeRegistration probe = new ProbeRegistration(providerId, migrationName, provider);
                boolean registeredAny = false;
                for (String supportedId : supportedIds) {
                    if (supportedId == null || supportedId.isBlank()) continue;
                    byItemId.computeIfAbsent(supportedId.trim(), ignored -> new ArrayList<>()).add(probe);
                    registeredAny = true;
                }
                if (registeredAny) providers++;
            } catch (Throwable throwable) {
                report.failure();
                plugin.getLogger().log(Level.WARNING,
                        "Could not initialize legacy item schema probe from " + providerId + '.', throwable);
            }
        }

        for (RegisteredServiceProvider<LegacyItemSchemaValidator> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaValidator.class)) {
            if (registration.getPlugin() == null || !registration.getPlugin().isEnabled()) continue;
            String providerId = registration.getPlugin().getName();
            LegacyItemSchemaValidator provider = registration.getProvider();
            try {
                Set<String> candidateTypes = provider.getSupportedCandidateTypes();
                if (candidateTypes == null) {
                    throw new IllegalStateException("Schema validator returned null supported candidate types");
                }
                for (String candidateType : candidateTypes) {
                    if (candidateType == null || candidateType.isBlank()) continue;
                    ValidatorKey key = new ValidatorKey(providerId, candidateType.trim());
                    ValidatorRegistration previous = validators.putIfAbsent(key, new ValidatorRegistration(provider));
                    if (previous != null) {
                        validators.remove(key);
                        report.failure();
                        plugin.getLogger().warning("Ignoring ambiguous duplicate legacy schema validators from "
                                + providerId + " for candidate type " + candidateType.trim() + '.');
                    }
                }
            } catch (Throwable throwable) {
                report.failure();
                plugin.getLogger().log(Level.WARNING,
                        "Could not initialize legacy item schema validator from " + providerId + '.', throwable);
            }
        }

        Map<String, List<ProbeRegistration>> frozen = new HashMap<>();
        for (Map.Entry<String, List<ProbeRegistration>> entry : byItemId.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new Session(plugin, Collections.unmodifiableMap(frozen), Map.copyOf(validators), providers);
    }

    static final class Session {
        private final JavaPlugin plugin;
        private final Map<String, List<ProbeRegistration>> byItemId;
        private final Map<ValidatorKey, ValidatorRegistration> validators;
        private final Map<ValidationRequestKey, AtomicLong> validationRequests = new ConcurrentHashMap<>();
        private final int providerCount;

        private Session(
                JavaPlugin plugin,
                Map<String, List<ProbeRegistration>> byItemId,
                Map<ValidatorKey, ValidatorRegistration> validators,
                int providerCount) {
            this.plugin = plugin;
            this.byItemId = byItemId;
            this.validators = validators;
            this.providerCount = providerCount;
        }

        int getProviderCount() { return providerCount; }

        void inspect(@Nonnull ItemStack item, @Nonnull String slimefunId, @Nonnull ItemDoctorReport report) {
            List<ProbeRegistration> probes = byItemId.get(slimefunId);
            if (probes == null || probes.isEmpty()) return;

            for (ProbeRegistration registration : probes) {
                try {
                    LegacyItemSchemaCandidate candidate = registration.provider().probeItem(item.clone(), slimefunId);
                    if (candidate == null) continue;
                    report.schemaMigrationCandidateFound(
                            registration.providerId(), registration.migrationName(), slimefunId, candidate);
                    if (candidate.getReadiness() == LegacyItemSchemaCandidate.Readiness.VALIDATION_REQUIRED) {
                        String claim = candidate.getValidationClaim();
                        if (claim == null) {
                            report.failure();
                            continue;
                        }
                        ValidationRequestKey key = new ValidationRequestKey(
                                registration.providerId(),
                                registration.migrationName(),
                                slimefunId,
                                candidate.getCandidateType(),
                                claim);
                        validationRequests.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
                    }
                } catch (Throwable throwable) {
                    report.failure();
                    plugin.getLogger().log(Level.WARNING,
                            "Legacy item schema probe failed for " + registration.providerId()
                                    + " on Slimefun item " + slimefunId + '.', throwable);
                }
            }
        }

        /** Runs deduplicated read-only persistent-state validation after item discovery has finished. */
        @Nonnull
        CompletionStage<Void> validatePending(@Nonnull ItemDoctorReport report) {
            if (validationRequests.isEmpty()) return CompletableFuture.completedFuture(null);
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            for (Map.Entry<ValidationRequestKey, AtomicLong> entry : validationRequests.entrySet()) {
                ValidationRequestKey request = entry.getKey();
                long count = entry.getValue().get();
                ValidatorRegistration registration = validators.get(new ValidatorKey(request.providerId(), request.candidateType()));
                if (registration == null) {
                    report.schemaValidationFound(
                            request.providerId(), request.migrationName(), request.slimefunId(), request.candidateType(),
                            new LegacyItemSchemaValidation(
                                    LegacyItemSchemaValidation.Status.MANUAL_ONLY,
                                    "The addon did not register a persistent-state validator for this legacy format."),
                            count);
                    continue;
                }

                try {
                    CompletionStage<LegacyItemSchemaValidation> stage = registration.provider().validateCandidate(
                            request.candidateType(), request.validationClaim());
                    if (stage == null) throw new IllegalStateException("Schema validator returned null CompletionStage");
                    CompletableFuture<Void> future = stage.handle((validation, error) -> {
                        if (error != null || validation == null) {
                            report.failure();
                            report.schemaValidationFound(
                                    request.providerId(), request.migrationName(), request.slimefunId(), request.candidateType(),
                                    new LegacyItemSchemaValidation(
                                            LegacyItemSchemaValidation.Status.MANUAL_ONLY,
                                            "Addon validation failed safely; no migration was authorized."),
                                    count);
                        } else {
                            report.schemaValidationFound(
                                    request.providerId(), request.migrationName(), request.slimefunId(), request.candidateType(),
                                    validation, count);
                        }
                        return (Void) null;
                    }).toCompletableFuture();
                    pending.add(future);
                } catch (Throwable throwable) {
                    report.failure();
                    plugin.getLogger().log(Level.WARNING,
                            "Legacy item schema validation failed to start for " + request.providerId()
                                    + " candidate " + request.candidateType() + ". Claim contents were not logged.",
                            throwable);
                    report.schemaValidationFound(
                            request.providerId(), request.migrationName(), request.slimefunId(), request.candidateType(),
                            new LegacyItemSchemaValidation(
                                    LegacyItemSchemaValidation.Status.MANUAL_ONLY,
                                    "Addon validation failed safely; no migration was authorized."),
                            count);
                }
            }
            return pending.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        }
    }

    private record ProbeRegistration(String providerId, String migrationName, LegacyItemSchemaProbe provider) {}
    private record ValidatorKey(String providerId, String candidateType) {}
    private record ValidatorRegistration(LegacyItemSchemaValidator provider) {}
    private record ValidationRequestKey(
            String providerId,
            String migrationName,
            String slimefunId,
            String candidateType,
            String validationClaim) {}
}
