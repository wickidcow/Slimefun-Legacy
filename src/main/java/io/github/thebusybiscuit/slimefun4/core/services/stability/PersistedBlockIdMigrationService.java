package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockIdStorageMaintenance;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockIdStorageMaintenance.PersistedBlockIdentity;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockIdStorageMaintenance.RewriteRequest;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockIdStorageMaintenance.RewriteSummary;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Prepares and executes conservative persisted identity rewrites from declared legacy aliases. */
public final class PersistedBlockIdMigrationService {

    static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;

    private final AtomicLong generation = new AtomicLong();
    private volatile PersistedBlockIdMigrationPlan preparedPlan;

    /**
     * Performs the same storage-level classification used for planning without creating or replacing an execution
     * fingerprint. This is intended for aggregate Doctor/upgrade reporting.
     */
    public @Nonnull AuditResult audit() {
        ScanComputation computation = scanStorage();
        if (computation.busy()) {
            return AuditResult.busyResult();
        }
        return new AuditResult(
                false,
                computation.scannedRecords(),
                computation.canonicalRecords(),
                computation.entries().size(),
                computation.unknownRecords(),
                computation.missingTargetRecords(),
                computation.loadedCandidates());
    }

    /**
     * Reads BLOCK_RECORD and UNIVERSAL_RECORD identities directly from storage. No Bukkit block or chunk is resolved.
     */
    public @Nonnull ScanResult preparePlan() {
        preparedPlan = null;
        ScanComputation computation = scanStorage();
        if (computation.busy()) {
            return ScanResult.busyResult();
        }

        PersistedBlockIdMigrationPlan plan = new PersistedBlockIdMigrationPlan(
                generation.incrementAndGet(),
                System.currentTimeMillis(),
                PLAN_TTL_MILLIS,
                computation.entries(),
                computation.scannedRecords(),
                computation.canonicalRecords(),
                computation.unknownRecords(),
                computation.missingTargetRecords());
        preparedPlan = plan;
        return new ScanResult(false, plan, computation.loadedCandidates());
    }

    private @Nonnull ScanComputation scanStorage() {
        var snapshot = maintenance().snapshot();
        if (!snapshot.available()) {
            return ScanComputation.busyResult();
        }

        var registry = Slimefun.getRegistry();
        List<PersistedBlockIdMigrationPlan.Entry> entries = new ArrayList<>();
        long canonical = 0L;
        long unknown = 0L;
        long missingTarget = 0L;
        long loadedCandidates = 0L;

        for (PersistedBlockIdentity identity : snapshot.identities()) {
            String storedId = identity.slimefunId();
            Optional<String> target = registry.resolveLegacySlimefunItemId(storedId);
            if (target.isPresent()) {
                String canonicalId = target.get();
                if (!registry.getSlimefunItemIds().containsKey(canonicalId)) {
                    missingTarget++;
                    continue;
                }
                if (!storedId.equals(canonicalId)) {
                    entries.add(new PersistedBlockIdMigrationPlan.Entry(
                            identity.storageScope(), identity.recordKey(), storedId, canonicalId));
                    if (identity.loaded()) loadedCandidates++;
                    continue;
                }
            }

            if (registry.getSlimefunItemIds().containsKey(storedId)) {
                canonical++;
            } else {
                unknown++;
            }
        }

        return new ScanComputation(
                false,
                List.copyOf(entries),
                snapshot.identities().size(),
                canonical,
                unknown,
                missingTarget,
                loadedCandidates);
    }

    public @Nonnull Optional<PersistedBlockIdMigrationPlan> getPreparedPlan() {
        PersistedBlockIdMigrationPlan plan = preparedPlan;
        if (plan == null) return Optional.empty();
        if (plan.isExpired(System.currentTimeMillis())) {
            preparedPlan = null;
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    public long getPlanTtlMillis() {
        return PLAN_TTL_MILLIS;
    }

    /** The plan is single-use and is consumed before any storage mutation is attempted. */
    public @Nonnull ExecutionResult execute(@Nullable String fingerprint) {
        PersistedBlockIdMigrationPlan plan = getPreparedPlan().orElse(null);
        if (plan == null) {
            return new ExecutionResult(ExecutionStatus.NO_PLAN, null, null);
        }
        if (!plan.matchesFingerprint(fingerprint)) {
            return new ExecutionResult(ExecutionStatus.BAD_FINGERPRINT, plan, null);
        }
        preparedPlan = null;

        List<RewriteRequest> requests = plan.entries().stream()
                .map(entry -> new RewriteRequest(
                        entry.storageScope(), entry.recordKey(), entry.legacyId(), entry.canonicalId()))
                .toList();
        RewriteSummary summary = maintenance().rewrite(requests);
        if (summary.busy()) {
            return new ExecutionResult(ExecutionStatus.STORAGE_BUSY, plan, summary);
        }
        if (summary.failures() > 0 || !summary.rollbackComplete()) {
            return new ExecutionResult(ExecutionStatus.FAILED, plan, summary);
        }
        return new ExecutionResult(ExecutionStatus.COMPLETE, plan, summary);
    }

    private BlockIdStorageMaintenance maintenance() {
        return new BlockIdStorageMaintenance(Slimefun.getDatabaseManager().getBlockDataController());
    }

    public enum ExecutionStatus {
        COMPLETE,
        NO_PLAN,
        BAD_FINGERPRINT,
        STORAGE_BUSY,
        FAILED
    }

    public record AuditResult(
            boolean busy,
            long scannedRecords,
            long canonicalRecords,
            long rewriteCandidates,
            long unknownRecords,
            long missingTargetRecords,
            long loadedCandidates) {
        static AuditResult busyResult() {
            return new AuditResult(true, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        public long immediatelyRewritableCandidates() {
            return Math.max(0L, rewriteCandidates - loadedCandidates);
        }
    }

    public record ScanResult(boolean busy, @Nullable PersistedBlockIdMigrationPlan plan, long loadedCandidates) {
        static ScanResult busyResult() {
            return new ScanResult(true, null, 0L);
        }
    }

    public record ExecutionResult(
            ExecutionStatus status,
            @Nullable PersistedBlockIdMigrationPlan plan,
            @Nullable RewriteSummary summary) {}

    private record ScanComputation(
            boolean busy,
            List<PersistedBlockIdMigrationPlan.Entry> entries,
            long scannedRecords,
            long canonicalRecords,
            long unknownRecords,
            long missingTargetRecords,
            long loadedCandidates) {
        static ScanComputation busyResult() {
            return new ScanComputation(true, List.of(), 0L, 0L, 0L, 0L, 0L);
        }
    }
}
