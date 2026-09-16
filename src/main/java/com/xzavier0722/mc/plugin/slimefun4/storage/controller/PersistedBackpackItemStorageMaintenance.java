package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance.StoredItemValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

/**
 * Narrow administrative access to persisted backpack inventory items.
 *
 * <p>Normal backpack saves are snapshot based and asynchronous, which is intentionally not used here. Doctor needs
 * an exact persisted-value gate: a candidate is rewritten only when its stored bytes still match the scan and every
 * owning backpack remains absent from the gameplay/maintenance cache. Profile read/write submissions are gated for
 * the entire critical section, and the backpack cache monitor is held across the complete mutation/rollback batch.</p>
 */
public final class PersistedBackpackItemStorageMaintenance {

    private final ProfileDataController controller;

    public PersistedBackpackItemStorageMaintenance(@Nonnull ProfileDataController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    /** Takes a read-only snapshot of persisted backpack rows whose backpack UUID is not currently cached. */
    public @Nonnull SnapshotResult snapshot() {
        if (!BackpackCache.hasActiveControllerCache()) {
            return SnapshotResult.busyResult();
        }

        AtomicReference<SnapshotResult> snapshot = new AtomicReference<>();
        AtomicBoolean readGateAcquired = new AtomicBoolean();
        boolean writeGateAcquired = controller.runIfAllWriteWorkIdle(() -> readGateAcquired.set(
                controller.runIfReadExecutorIdle(() -> {
                    if (BackpackCache.hasActiveControllerCache()) {
                        snapshot.set(readUncachedItems());
                    }
                })));
        if (!writeGateAcquired || !readGateAcquired.get()) {
            return SnapshotResult.busyResult();
        }
        SnapshotResult result = snapshot.get();
        return result == null ? SnapshotResult.busyResult() : result;
    }

    /**
     * Rewrites exact previously-snapshotted rows while profile reads/writes and backpack cache ownership are gated.
     * All stale/missing checks complete before mutation. The active cache then guards the entire approved batch, so
     * no backpack may become gameplay-owned between the first write and the last write or rollback.
     */
    public @Nonnull RewriteSummary rewrite(@Nonnull List<RewriteRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        if (requests.isEmpty()) {
            return new RewriteSummary(false, 0, 0, 0, 0, 0, true);
        }
        if (!BackpackCache.hasActiveControllerCache()) {
            return RewriteSummary.busyResult();
        }

        AtomicReference<RewriteSummary> result = new AtomicReference<>();
        AtomicBoolean readGateAcquired = new AtomicBoolean();
        boolean writeGateAcquired = controller.runIfAllWriteWorkIdle(() -> readGateAcquired.set(
                controller.runIfReadExecutorIdle(() -> {
                    if (BackpackCache.hasActiveControllerCache()) {
                        result.set(rewriteWhileGated(requests));
                    }
                })));
        if (!writeGateAcquired || !readGateAcquired.get()) {
            return RewriteSummary.busyResult();
        }
        RewriteSummary summary = result.get();
        return summary == null ? RewriteSummary.busyResult() : summary;
    }

    private SnapshotResult readUncachedItems() {
        ReadResult read = readAllItems();
        List<BackpackItemRecord> records = new ArrayList<>();
        int cached = 0;
        for (BackpackItemRecord record : read.records()) {
            if (BackpackCache.isCachedInActiveController(record.backpackId())) {
                cached++;
            } else {
                records.add(record);
            }
        }
        records.sort((left, right) -> left.identity().compareTo(right.identity()));
        return new SnapshotResult(true, List.copyOf(records), cached, read.unreadableRecords());
    }

    private ReadResult readAllItems() {
        RecordKey key = new RecordKey(DataScope.BACKPACK_INVENTORY);
        key.addField(FieldKey.BACKPACK_ID);
        key.addField(FieldKey.INVENTORY_SLOT);
        key.addField(FieldKey.INVENTORY_ITEM);

        List<BackpackItemRecord> records = new ArrayList<>();
        int unreadable = 0;
        for (RecordSet record : controller.getData(key)) {
            String backpackId = stringValue(record, FieldKey.BACKPACK_ID);
            String slot = stringValue(record, FieldKey.INVENTORY_SLOT);
            Object rawValue = record.getValue(FieldKey.INVENTORY_ITEM);
            if (backpackId == null || backpackId.isBlank() || slot == null || slot.isBlank() || rawValue == null) {
                unreadable++;
                continue;
            }
            StoredItemValue value = StoredItemValue.copyOf(rawValue);
            if (value == null) {
                unreadable++;
                continue;
            }
            records.add(new BackpackItemRecord(backpackId, slot, value));
        }
        return new ReadResult(List.copyOf(records), unreadable);
    }

    private RewriteSummary rewriteWhileGated(List<RewriteRequest> requests) {
        Map<String, BackpackItemRecord> live = new HashMap<>();
        for (BackpackItemRecord record : readAllItems().records()) {
            live.put(record.identity(), record);
        }

        int stale = 0;
        int missing = 0;
        Set<String> backpackIds = new HashSet<>();
        for (RewriteRequest request : requests) {
            BackpackItemRecord expected = request.expected();
            BackpackItemRecord current = live.get(expected.identity());
            if (current == null) {
                missing++;
                continue;
            }
            if (!current.storedValue().sameValue(expected.storedValue())) {
                stale++;
                continue;
            }
            backpackIds.add(current.backpackId());
        }

        if (stale != 0 || missing != 0) {
            return new RewriteSummary(false, 0, stale, 0, missing, 0, true);
        }

        AtomicReference<RewriteSummary> batch = new AtomicReference<>();
        boolean ran = BackpackCache.runIfAllUncachedInActiveController(
                backpackIds, () -> batch.set(applyBatch(requests, live)));
        if (!ran) {
            return new RewriteSummary(false, 0, 0, 1, 0, 0, true);
        }
        RewriteSummary summary = batch.get();
        return summary == null ? new RewriteSummary(false, 0, 0, 0, 0, 1, false) : summary;
    }

    private RewriteSummary applyBatch(List<RewriteRequest> requests, Map<String, BackpackItemRecord> live) {
        List<BackpackItemRecord> applied = new ArrayList<>();
        try {
            for (RewriteRequest request : requests) {
                BackpackItemRecord current = live.get(request.expected().identity());
                writeValue(current, request.replacement());
                applied.add(current);
            }
            return new RewriteSummary(false, applied.size(), 0, 0, 0, 0, true);
        } catch (RuntimeException | LinkageError failure) {
            boolean rollbackComplete = true;
            for (int i = applied.size() - 1; i >= 0; i--) {
                BackpackItemRecord original = applied.get(i);
                try {
                    writeValue(original, original.storedValue());
                } catch (RuntimeException | LinkageError rollbackFailure) {
                    rollbackComplete = false;
                }
            }
            return new RewriteSummary(false, 0, 0, 0, 0, 1, rollbackComplete);
        }
    }

    private void writeValue(BackpackItemRecord record, StoredItemValue value) {
        RecordKey key = new RecordKey(DataScope.BACKPACK_INVENTORY);
        key.addCondition(FieldKey.BACKPACK_ID, record.backpackId());
        key.addCondition(FieldKey.INVENTORY_SLOT, record.slotKey());
        key.addField(FieldKey.INVENTORY_ITEM);

        RecordSet data = new RecordSet();
        data.put(FieldKey.BACKPACK_ID, record.backpackId());
        data.put(FieldKey.INVENTORY_SLOT, record.slotKey());
        value.put(data, FieldKey.INVENTORY_ITEM);
        controller.setData(key, data);
    }

    @Nullable
    private static String stringValue(RecordSet record, FieldKey key) {
        Object value = record.getValue(key);
        return value == null ? null : String.valueOf(value);
    }

    private record ReadResult(List<BackpackItemRecord> records, int unreadableRecords) {}

    public record BackpackItemRecord(String backpackId, String slotKey, StoredItemValue storedValue) {
        public BackpackItemRecord {
            backpackId = requireText(backpackId, "backpackId");
            slotKey = requireText(slotKey, "slotKey");
            Objects.requireNonNull(storedValue, "storedValue");
        }

        public String identity() {
            return backpackId + ':' + slotKey;
        }

        public @Nullable ItemStack deserializeItem() {
            return storedValue.deserialize();
        }
    }

    public record RewriteRequest(BackpackItemRecord expected, StoredItemValue replacement) {
        public RewriteRequest {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    public record SnapshotResult(
            boolean available,
            List<BackpackItemRecord> records,
            int cachedRecords,
            int unreadableRecords) {
        public SnapshotResult {
            records = List.copyOf(records);
        }

        static SnapshotResult busyResult() {
            return new SnapshotResult(false, List.of(), 0, 0);
        }
    }

    public record RewriteSummary(
            boolean busy,
            int rewritten,
            int stale,
            int cached,
            int missing,
            int failures,
            boolean rollbackComplete) {
        static RewriteSummary busyResult() {
            return new RewriteSummary(true, 0, 0, 0, 0, 0, true);
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return text;
    }
}
