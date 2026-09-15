package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance.PersistedItemRecord;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance.RewriteRequest;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance.RewriteSummary;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance.StoredItemValue;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Fingerprinted migration of legacy serialized item payloads in unloaded Slimefun machine storage.
 *
 * <p>The migration is intentionally format-only. It deserializes through Slimefun's retained legacy compatibility
 * codec, serializes through the current SF2/Paper codec, verifies the round-trip ItemStack is equal, and only then
 * authorizes a row for replacement. No chunks are loaded and no item IDs or metadata are intentionally changed.
 */
public final class PersistedItemFormatMigrationService {

    private static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;
    private static final int MAX_UNREADABLE_SAMPLES = 8;

    private final AtomicLong generation = new AtomicLong();
    private volatile PersistedItemFormatMigrationPlan preparedPlan;

    public @Nonnull ScanResult preparePlan() {
        PersistedItemStorageMaintenance maintenance = maintenance();
        var snapshot = maintenance.snapshot();
        if (!snapshot.available()) {
            preparedPlan = null;
            return new ScanResult(true, null);
        }

        long scanned = 0L;
        long current = 0L;
        long unreadable = 0L;
        List<String> unreadableSamples = new ArrayList<>();
        List<PersistedItemFormatMigrationPlan.Entry> entries = new ArrayList<>();

        for (PersistedItemRecord record : snapshot.records()) {
            scanned++;
            if (!record.usesLegacyStorageFormat()) {
                current++;
                continue;
            }

            if (!canRoundTrip(record)) {
                unreadable++;
                if (unreadableSamples.size() < MAX_UNREADABLE_SAMPLES) {
                    unreadableSamples.add(record.identity());
                }
                continue;
            }

            entries.add(new PersistedItemFormatMigrationPlan.Entry(
                    record.scope(),
                    record.ownerKey(),
                    record.slotKey(),
                    hash(record.storedValue())));
        }

        long now = System.currentTimeMillis();
        PersistedItemFormatMigrationPlan plan = new PersistedItemFormatMigrationPlan(
                generation.incrementAndGet(),
                now,
                PLAN_TTL_MILLIS,
                entries,
                scanned,
                current,
                unreadable,
                unreadableSamples);
        preparedPlan = plan;
        return new ScanResult(false, plan);
    }

    public @Nonnull Optional<PersistedItemFormatMigrationPlan> getPreparedPlan() {
        PersistedItemFormatMigrationPlan plan = preparedPlan;
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

    public @Nonnull ExecutionResult execute(String suppliedFingerprint) {
        PersistedItemFormatMigrationPlan plan = getPreparedPlan().orElse(null);
        if (plan == null) return new ExecutionResult(ExecutionStatus.NO_PLAN, null);
        if (!plan.matchesFingerprint(suppliedFingerprint)) {
            return new ExecutionResult(ExecutionStatus.BAD_FINGERPRINT, null);
        }

        // Execution fingerprints are deliberately single-use, even when storage becomes busy or stale afterwards.
        preparedPlan = null;
        if (plan.getUnreadableLegacyRecords() != 0L) {
            return new ExecutionResult(ExecutionStatus.BLOCKED_UNREADABLE, null);
        }
        if (plan.getRewriteCount() == 0) {
            return new ExecutionResult(ExecutionStatus.SUCCESS, new RewriteSummary(false, 0, 0, 0, 0, 0, true));
        }

        PersistedItemStorageMaintenance maintenance = maintenance();
        var snapshot = maintenance.snapshot();
        if (!snapshot.available()) {
            return new ExecutionResult(ExecutionStatus.STORAGE_BUSY, null);
        }

        Map<String, PersistedItemRecord> live = new HashMap<>();
        for (PersistedItemRecord record : snapshot.records()) {
            live.put(record.identity(), record);
        }

        List<RewriteRequest> requests = new ArrayList<>(plan.getRewriteCount());
        for (PersistedItemFormatMigrationPlan.Entry entry : plan.entries()) {
            PersistedItemRecord current = live.get(entry.identity());
            if (current == null
                    || !current.usesLegacyStorageFormat()
                    || !entry.expectedValueHash().equals(hash(current.storedValue()))) {
                return new ExecutionResult(ExecutionStatus.STALE, null);
            }

            StoredItemValue replacement = currentValueAfterVerifiedRoundTrip(current);
            if (replacement == null) {
                return new ExecutionResult(ExecutionStatus.STALE, null);
            }
            requests.add(new RewriteRequest(current, replacement));
        }

        RewriteSummary summary = maintenance.rewrite(requests);
        if (summary.busy()) {
            return new ExecutionResult(ExecutionStatus.STORAGE_BUSY, summary);
        }
        if (summary.failures() != 0 || !summary.rollbackComplete()) {
            return new ExecutionResult(ExecutionStatus.FAILED, summary);
        }
        if (summary.stale() != 0 || summary.loaded() != 0 || summary.missing() != 0) {
            return new ExecutionResult(ExecutionStatus.STALE, summary);
        }
        return new ExecutionResult(ExecutionStatus.SUCCESS, summary);
    }

    private boolean canRoundTrip(PersistedItemRecord record) {
        return currentValueAfterVerifiedRoundTrip(record) != null;
    }

    private StoredItemValue currentValueAfterVerifiedRoundTrip(PersistedItemRecord record) {
        try {
            ItemStack original = record.deserializeItem();
            if (original == null || original.getType() == Material.AIR || original.getAmount() <= 0) {
                return null;
            }

            StoredItemValue replacement = StoredItemValue.current(original);
            byte[] bytes = replacement.binaryCopy();
            if (bytes.length == 0 || replacement.isLegacyStorageFormat()) {
                return null;
            }

            ItemStack roundTrip = replacement.deserialize();
            if (roundTrip == null || !original.equals(roundTrip)) {
                return null;
            }
            return replacement;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private String hash(StoredItemValue value) {
        return PersistedItemFormatMigrationPlan.hashStoredValue(
                value.isBinary(), value.binaryCopy(), value.textValue());
    }

    private PersistedItemStorageMaintenance maintenance() {
        return new PersistedItemStorageMaintenance(Slimefun.getDatabaseManager().getBlockDataController());
    }

    public record ScanResult(boolean busy, PersistedItemFormatMigrationPlan plan) {}

    public record ExecutionResult(ExecutionStatus status, RewriteSummary summary) {}

    public enum ExecutionStatus {
        SUCCESS,
        NO_PLAN,
        BAD_FINGERPRINT,
        BLOCKED_UNREADABLE,
        STORAGE_BUSY,
        STALE,
        FAILED
    }
}
