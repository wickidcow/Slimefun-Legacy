package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedBackpackItemStorageMaintenance;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedBackpackItemStorageMaintenance.BackpackItemRecord;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedBackpackItemStorageMaintenance.RewriteRequest;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedBackpackItemStorageMaintenance.RewriteSummary;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance.StoredItemValue;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Fingerprinted migration of declared legacy Slimefun Item IDs inside persisted backpack inventories. */
public final class PersistedBackpackItemIdMigrationService {

    private static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;

    private final AtomicLong generation = new AtomicLong();
    private volatile PersistedBackpackItemIdMigrationPlan preparedPlan;

    /** Read-only classification that never creates or consumes an execution fingerprint. */
    public @Nonnull AuditResult audit() {
        ScanComputation computation = scanStorage();
        if (computation.busy()) return AuditResult.busyResult();
        return new AuditResult(
                false,
                computation.scannedRecords(),
                computation.cachedRecords(),
                computation.canonicalRecords(),
                computation.nonSlimefunRecords(),
                computation.entries().size(),
                computation.unknownIdRecords(),
                computation.missingTargetRecords(),
                computation.unreadableRecords());
    }

    public @Nonnull ScanResult preparePlan() {
        preparedPlan = null;
        ScanComputation computation = scanStorage();
        if (computation.busy()) return new ScanResult(true, null);

        long now = System.currentTimeMillis();
        PersistedBackpackItemIdMigrationPlan plan = new PersistedBackpackItemIdMigrationPlan(
                generation.incrementAndGet(),
                now,
                PLAN_TTL_MILLIS,
                computation.entries(),
                computation.scannedRecords(),
                computation.cachedRecords(),
                computation.canonicalRecords(),
                computation.nonSlimefunRecords(),
                computation.unknownIdRecords(),
                computation.missingTargetRecords(),
                computation.unreadableRecords());
        preparedPlan = plan;
        return new ScanResult(false, plan);
    }

    private @Nonnull ScanComputation scanStorage() {
        var snapshot = maintenance().snapshot();
        if (!snapshot.available()) return ScanComputation.busyResult();

        var registry = Slimefun.getRegistry();
        List<PersistedBackpackItemIdMigrationPlan.Entry> entries = new ArrayList<>();
        long canonical = 0L;
        long nonSlimefun = 0L;
        long unknown = 0L;
        long missingTarget = 0L;
        long unreadable = 0L;

        for (BackpackItemRecord record : snapshot.records()) {
            ItemStack item = deserializeUsable(record);
            if (item == null) {
                unreadable++;
                continue;
            }

            Optional<String> storedIdValue = Slimefun.getItemDataService().getItemData(item);
            if (storedIdValue.isEmpty()) {
                nonSlimefun++;
                continue;
            }

            String storedId = storedIdValue.get();
            Optional<String> target = registry.resolveLegacySlimefunItemId(storedId);
            if (target.isEmpty()) {
                if (SlimefunItem.getById(storedId) != null) canonical++;
                else unknown++;
                continue;
            }

            String canonicalId = target.get();
            if (SlimefunItem.getById(canonicalId) == null) {
                missingTarget++;
                continue;
            }

            StoredItemValue replacement = verifiedReplacement(item, storedId, canonicalId);
            if (replacement == null) {
                unreadable++;
                continue;
            }

            entries.add(new PersistedBackpackItemIdMigrationPlan.Entry(
                    record.backpackId(),
                    record.slotKey(),
                    storedId,
                    canonicalId,
                    hash(record.storedValue())));
        }

        return new ScanComputation(
                false,
                List.copyOf(entries),
                snapshot.records().size() + snapshot.cachedRecords(),
                snapshot.cachedRecords(),
                canonical,
                nonSlimefun,
                unknown,
                missingTarget,
                unreadable);
    }

    public @Nonnull Optional<PersistedBackpackItemIdMigrationPlan> getPreparedPlan() {
        PersistedBackpackItemIdMigrationPlan plan = preparedPlan;
        if (plan == null) return Optional.empty();
        if (plan.isExpired(System.currentTimeMillis())) {
            preparedPlan = null;
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    public void invalidatePreparedPlan() {
        preparedPlan = null;
    }

    public long getPlanTtlMillis() {
        return PLAN_TTL_MILLIS;
    }

    /** The plan is single-use and is consumed before storage is revalidated or mutated. */
    public @Nonnull ExecutionResult execute(@Nullable String suppliedFingerprint) {
        PersistedBackpackItemIdMigrationPlan plan = getPreparedPlan().orElse(null);
        if (plan == null) return new ExecutionResult(ExecutionStatus.NO_PLAN, null);
        if (!plan.matchesFingerprint(suppliedFingerprint)) {
            return new ExecutionResult(ExecutionStatus.BAD_FINGERPRINT, null);
        }
        preparedPlan = null;

        if (plan.getRewriteCount() == 0) {
            return new ExecutionResult(ExecutionStatus.SUCCESS, new RewriteSummary(false, 0, 0, 0, 0, 0, true));
        }

        PersistedBackpackItemStorageMaintenance maintenance = maintenance();
        var snapshot = maintenance.snapshot();
        if (!snapshot.available()) return new ExecutionResult(ExecutionStatus.STORAGE_BUSY, null);

        Map<String, BackpackItemRecord> live = new HashMap<>();
        for (BackpackItemRecord record : snapshot.records()) live.put(record.identity(), record);

        List<RewriteRequest> requests = new ArrayList<>(plan.getRewriteCount());
        for (PersistedBackpackItemIdMigrationPlan.Entry entry : plan.entries()) {
            BackpackItemRecord current = live.get(entry.identity());
            if (current == null || !entry.expectedValueHash().equals(hash(current.storedValue()))) {
                return new ExecutionResult(ExecutionStatus.STALE, null);
            }

            ItemStack item = deserializeUsable(current);
            if (item == null) return new ExecutionResult(ExecutionStatus.STALE, null);
            String currentId = Slimefun.getItemDataService().getItemData(item).orElse(null);
            if (!entry.legacyId().equals(currentId)) return new ExecutionResult(ExecutionStatus.STALE, null);

            Optional<String> liveTarget = Slimefun.getRegistry().resolveLegacySlimefunItemId(currentId);
            if (liveTarget.isEmpty()
                    || !entry.canonicalId().equals(liveTarget.get())
                    || SlimefunItem.getById(entry.canonicalId()) == null) {
                return new ExecutionResult(ExecutionStatus.STALE, null);
            }

            StoredItemValue replacement = verifiedReplacement(item, entry.legacyId(), entry.canonicalId());
            if (replacement == null) return new ExecutionResult(ExecutionStatus.STALE, null);
            requests.add(new RewriteRequest(current, replacement));
        }

        RewriteSummary summary = maintenance.rewrite(requests);
        if (summary.busy()) return new ExecutionResult(ExecutionStatus.STORAGE_BUSY, summary);
        if (summary.failures() != 0 || !summary.rollbackComplete()) {
            return new ExecutionResult(ExecutionStatus.FAILED, summary);
        }
        if (summary.stale() != 0 || summary.cached() != 0 || summary.missing() != 0) {
            return new ExecutionResult(ExecutionStatus.STALE, summary);
        }
        return new ExecutionResult(ExecutionStatus.SUCCESS, summary);
    }

    private @Nullable ItemStack deserializeUsable(BackpackItemRecord record) {
        try {
            ItemStack item = record.deserializeItem();
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return null;
            return item;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    /** Builds a current-format replacement and proves the Slimefun ID is the only semantic ItemStack change. */
    private @Nullable StoredItemValue verifiedReplacement(ItemStack original, String legacyId, String canonicalId) {
        try {
            ItemStack replacement = original.clone();
            Slimefun.getItemDataService().setItemData(replacement, canonicalId);
            if (!Slimefun.getItemDataService().getItemData(replacement).filter(canonicalId::equals).isPresent()) {
                return null;
            }

            StoredItemValue stored = StoredItemValue.current(replacement);
            ItemStack roundTrip = stored.deserialize();
            if (roundTrip == null
                    || !Slimefun.getItemDataService().getItemData(roundTrip).filter(canonicalId::equals).isPresent()) {
                return null;
            }

            ItemStack comparison = roundTrip.clone();
            Slimefun.getItemDataService().setItemData(comparison, legacyId);
            return original.equals(comparison) ? stored : null;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private String hash(StoredItemValue value) {
        return PersistedItemFormatMigrationPlan.hashStoredValue(
                value.isBinary(), value.binaryCopy(), value.textValue());
    }

    private PersistedBackpackItemStorageMaintenance maintenance() {
        return new PersistedBackpackItemStorageMaintenance(
                Slimefun.getDatabaseManager().getProfileDataController());
    }

    public record AuditResult(
            boolean busy,
            long scannedRecords,
            long cachedRecords,
            long canonicalRecords,
            long nonSlimefunRecords,
            long rewriteCandidates,
            long unknownIdRecords,
            long missingTargetRecords,
            long unreadableRecords) {
        static AuditResult busyResult() {
            return new AuditResult(true, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public record ScanResult(boolean busy, @Nullable PersistedBackpackItemIdMigrationPlan plan) {}

    public record ExecutionResult(ExecutionStatus status, @Nullable RewriteSummary summary) {}

    public enum ExecutionStatus {
        SUCCESS,
        NO_PLAN,
        BAD_FINGERPRINT,
        STORAGE_BUSY,
        STALE,
        FAILED
    }

    private record ScanComputation(
            boolean busy,
            List<PersistedBackpackItemIdMigrationPlan.Entry> entries,
            long scannedRecords,
            long cachedRecords,
            long canonicalRecords,
            long nonSlimefunRecords,
            long unknownIdRecords,
            long missingTargetRecords,
            long unreadableRecords) {
        static ScanComputation busyResult() {
            return new ScanComputation(true, List.of(), 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }
}
