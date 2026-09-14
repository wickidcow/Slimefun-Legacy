package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LegacyBlockIdStorageMigration;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LegacyBlockIdStorageMigration.ExecutionResult;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LegacyBlockIdStorageMigration.StoredIdentity;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plans and executes conservative rewrites of persisted Slimefun block identities.
 *
 * <p>Only mappings declared by the live Slimefun registry are authoritative. Historical Doctor catalogs are never
 * consulted for mutation. Live-resolvable source aliases are deliberately left untouched because an in-memory block
 * cache may already hold that identity; only non-live legacy IDs with a registered canonical target are executable.
 */
public final class LegacyBlockIdMigrationService {

    private static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;

    private final AtomicLong generation = new AtomicLong();
    private volatile LegacyBlockIdMigrationPlan preparedPlan;

    public @Nonnull ScanResult scan() {
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        List<StoredIdentity> identities = LegacyBlockIdStorageMigration.scan(controller);
        List<LegacyBlockIdMigrationPlan.Candidate> candidates = new ArrayList<>();
        long liveAliases = 0L;
        long missingTargets = 0L;
        long unknown = 0L;

        for (StoredIdentity identity : identities) {
            String sourceId = identity.slimefunId();
            Optional<String> resolved = Slimefun.getRegistry().resolveLegacySlimefunItemId(sourceId);
            if (resolved.isEmpty()) {
                if (SlimefunItem.getById(sourceId) == null) {
                    unknown++;
                }
                continue;
            }

            String targetId = resolved.get();
            if (sourceId.equals(targetId)) {
                continue;
            }

            // A live source could already be represented by an in-memory cache. Keep those aliases read-only.
            if (SlimefunItem.getById(sourceId) != null) {
                liveAliases++;
                continue;
            }

            if (SlimefunItem.getById(targetId) == null) {
                missingTargets++;
                continue;
            }

            candidates.add(new LegacyBlockIdMigrationPlan.Candidate(
                    identity.storageScope(), identity.recordKey(), sourceId, targetId));
        }

        return new ScanResult(identities.size(), candidates, liveAliases, missingTargets, unknown);
    }

    public synchronized @Nonnull Optional<LegacyBlockIdMigrationPlan> preparePlan() {
        invalidatePreparedPlan();
        ScanResult scan = scan();
        if (scan.candidates().isEmpty()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        LegacyBlockIdMigrationPlan plan = new LegacyBlockIdMigrationPlan(
                generation.incrementAndGet(), now, PLAN_TTL_MILLIS, scan.candidates());
        preparedPlan = plan;
        return Optional.of(plan);
    }

    public synchronized @Nonnull Optional<LegacyBlockIdMigrationPlan> getPreparedPlan() {
        LegacyBlockIdMigrationPlan plan = preparedPlan;
        if (plan != null && plan.isExpired(System.currentTimeMillis())) {
            preparedPlan = null;
            return Optional.empty();
        }
        return Optional.ofNullable(plan);
    }

    public synchronized void invalidatePreparedPlan() {
        preparedPlan = null;
    }

    public long getPlanTtlMillis() {
        return PLAN_TTL_MILLIS;
    }

    /**
     * Consumes and executes a prepared plan after an exact storage and registry re-scan.
     *
     * <p>The plan is single-use regardless of outcome. If the candidate set drifted, no mutation is attempted.
     */
    public synchronized @Nonnull ExecutionOutcome execute(
            @Nonnull LegacyBlockIdMigrationPlan plan, @Nullable String suppliedFingerprint) {
        Objects.requireNonNull(plan, "plan");
        LegacyBlockIdMigrationPlan active = getPreparedPlan().orElse(null);
        if (active != plan || !plan.matchesFingerprint(suppliedFingerprint)) {
            return ExecutionOutcome.rejected("prepared plan or fingerprint does not match");
        }

        preparedPlan = null;
        if (plan.isExpired(System.currentTimeMillis())) {
            return ExecutionOutcome.rejected("plan expired");
        }

        ScanResult fresh = scan();
        if (!plan.matchesCandidates(fresh.candidates())) {
            return ExecutionOutcome.rejected("persisted identities or registry mappings changed after scan");
        }

        List<LegacyBlockIdStorageMigration.Rewrite> rewrites = plan.getCandidates().stream()
                .map(candidate -> new LegacyBlockIdStorageMigration.Rewrite(
                        candidate.storageScope(),
                        candidate.recordKey(),
                        candidate.legacyId(),
                        candidate.canonicalId()))
                .toList();
        ExecutionResult result = LegacyBlockIdStorageMigration.execute(
                Slimefun.getDatabaseManager().getBlockDataController(), rewrites);

        if (!result.writesIdle() || !result.readsIdle()) {
            return new ExecutionOutcome(false, "storage was not quiescent", result);
        }
        return new ExecutionOutcome(true, "execution completed", result);
    }

    public record ScanResult(
            long persistedIdentities,
            List<LegacyBlockIdMigrationPlan.Candidate> candidates,
            long liveAliases,
            long missingTargets,
            long unknownIds) {
        public ScanResult {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }

        public long ready() {
            return candidates.size();
        }
    }

    public record ExecutionOutcome(boolean attempted, String detail, @Nullable ExecutionResult result) {
        public ExecutionOutcome {
            detail = Objects.requireNonNull(detail, "detail");
        }

        static ExecutionOutcome rejected(String detail) {
            return new ExecutionOutcome(false, detail, null);
        }
    }
}
