package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Narrow administrative access to persisted Slimefun machine inventory items.
 *
 * <p>The maintenance view covers normal block inventories and universal inventories without resolving Bukkit
 * locations or loading chunks. Rows belonging to currently loaded data are excluded from snapshots and protected
 * again during rewrites so direct storage maintenance can never get ahead of an in-memory menu.
 *
 * <p>Snapshots retain the exact stored representation. This matters for migration from older Slimefun databases:
 * legacy inventory values may be ASCII Base64/Bukkit object streams while current values are versioned binary
 * payloads. A rewrite is accepted only when the exact persisted value still matches the value that was scanned.
 */
public final class PersistedItemStorageMaintenance {

    private final BlockDataController controller;

    public PersistedItemStorageMaintenance(@Nonnull BlockDataController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    /** Takes a read-only snapshot of inventory rows whose owning data is not currently loaded. */
    public @Nonnull SnapshotResult snapshot() {
        AtomicReference<List<PersistedItemRecord>> snapshot = new AtomicReference<>();
        AtomicBoolean readGateAcquired = new AtomicBoolean();

        boolean writeGateAcquired = controller.runIfAllWriteWorkIdle(() -> readGateAcquired.set(
                controller.runIfReadExecutorIdle(() -> snapshot.set(readAllUnloadedItems()))));

        if (!writeGateAcquired || !readGateAcquired.get()) {
            return new SnapshotResult(false, List.of());
        }
        List<PersistedItemRecord> records = snapshot.get();
        return new SnapshotResult(true, records == null ? List.of() : records);
    }

    /**
     * Rewrites a batch of previously snapshotted rows while normal controller reads and writes are gated.
     *
     * <p>Each row is rechecked for exact-value equality and loaded ownership immediately before mutation. Stale,
     * loaded and missing rows are skipped. If a storage write throws, already-applied rows are restored using their
     * exact original representation.
     */
    public @Nonnull RewriteSummary rewrite(@Nonnull List<RewriteRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        if (requests.isEmpty()) {
            return new RewriteSummary(false, 0, 0, 0, 0, 0, true);
        }

        AtomicReference<RewriteSummary> result = new AtomicReference<>();
        AtomicBoolean readGateAcquired = new AtomicBoolean();
        boolean writeGateAcquired = controller.runIfAllWriteWorkIdle(() -> readGateAcquired.set(
                controller.runIfReadExecutorIdle(() -> result.set(rewriteWhileGated(requests)))));

        if (!writeGateAcquired || !readGateAcquired.get()) {
            return RewriteSummary.busyResult();
        }
        RewriteSummary summary = result.get();
        return summary == null ? RewriteSummary.busyResult() : summary;
    }

    private List<PersistedItemRecord> readAllUnloadedItems() {
        Set<String> loadedBlocks = loadedBlockKeys();
        Set<String> loadedUniversal = loadedUniversalKeys();
        List<PersistedItemRecord> result = new ArrayList<>();
        readScope(DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, loadedBlocks, result);
        readScope(DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, loadedUniversal, result);
        result.sort((left, right) -> left.identity().compareTo(right.identity()));
        return List.copyOf(result);
    }

    private void readScope(
            DataScope scope,
            FieldKey ownerField,
            Set<String> loadedOwners,
            List<PersistedItemRecord> destination) {
        RecordKey key = new RecordKey(scope);
        key.addField(ownerField);
        key.addField(FieldKey.INVENTORY_SLOT);
        key.addField(FieldKey.INVENTORY_ITEM);

        for (RecordSet record : controller.getData(key)) {
            String owner = stringValue(record, ownerField);
            String slot = stringValue(record, FieldKey.INVENTORY_SLOT);
            Object rawValue = record.getValue(FieldKey.INVENTORY_ITEM);
            if (owner == null || owner.isBlank() || slot == null || slot.isBlank() || rawValue == null) {
                continue;
            }
            if (loadedOwners.contains(owner)) {
                continue;
            }

            StoredItemValue value = StoredItemValue.copyOf(rawValue);
            if (value != null) {
                destination.add(new PersistedItemRecord(scope, owner, slot, value));
            }
        }
    }

    private RewriteSummary rewriteWhileGated(List<RewriteRequest> requests) {
        Map<String, PersistedItemRecord> live = new HashMap<>();
        for (PersistedItemRecord record : readAllItemsIncludingLoaded()) {
            live.put(record.identity(), record);
        }

        Set<String> loadedBlocks = loadedBlockKeys();
        Set<String> loadedUniversal = loadedUniversalKeys();
        int stale = 0;
        int loaded = 0;
        int missing = 0;
        List<PersistedItemRecord> applied = new ArrayList<>();

        try {
            for (RewriteRequest request : requests) {
                PersistedItemRecord expected = request.expected();
                PersistedItemRecord current = live.get(expected.identity());
                if (current == null) {
                    missing++;
                    continue;
                }
                if (!current.storedValue().sameValue(expected.storedValue())) {
                    stale++;
                    continue;
                }
                if (isLoaded(current, loadedBlocks, loadedUniversal)) {
                    loaded++;
                    continue;
                }

                writeValue(current, request.replacement());
                applied.add(current);
            }
            return new RewriteSummary(false, applied.size(), stale, loaded, missing, 0, true);
        } catch (RuntimeException | LinkageError failure) {
            boolean rollbackComplete = true;
            for (int i = applied.size() - 1; i >= 0; i--) {
                PersistedItemRecord original = applied.get(i);
                try {
                    writeValue(original, original.storedValue());
                } catch (RuntimeException | LinkageError rollbackFailure) {
                    rollbackComplete = false;
                }
            }
            return new RewriteSummary(false, 0, stale, loaded, missing, 1, rollbackComplete);
        }
    }

    private List<PersistedItemRecord> readAllItemsIncludingLoaded() {
        List<PersistedItemRecord> result = new ArrayList<>();
        readScopeIncludingLoaded(DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, result);
        readScopeIncludingLoaded(DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, result);
        return result;
    }

    private void readScopeIncludingLoaded(
            DataScope scope, FieldKey ownerField, List<PersistedItemRecord> destination) {
        RecordKey key = new RecordKey(scope);
        key.addField(ownerField);
        key.addField(FieldKey.INVENTORY_SLOT);
        key.addField(FieldKey.INVENTORY_ITEM);
        for (RecordSet record : controller.getData(key)) {
            String owner = stringValue(record, ownerField);
            String slot = stringValue(record, FieldKey.INVENTORY_SLOT);
            Object rawValue = record.getValue(FieldKey.INVENTORY_ITEM);
            if (owner == null || owner.isBlank() || slot == null || slot.isBlank() || rawValue == null) {
                continue;
            }
            StoredItemValue value = StoredItemValue.copyOf(rawValue);
            if (value != null) {
                destination.add(new PersistedItemRecord(scope, owner, slot, value));
            }
        }
    }

    private boolean isLoaded(
            PersistedItemRecord record, Set<String> loadedBlocks, Set<String> loadedUniversal) {
        return switch (record.scope()) {
            case BLOCK_INVENTORY -> loadedBlocks.contains(record.ownerKey());
            case UNIVERSAL_INVENTORY -> loadedUniversal.contains(record.ownerKey());
            default -> true;
        };
    }

    private void writeValue(PersistedItemRecord record, StoredItemValue value) {
        RecordSet data = new RecordSet();
        if (record.scope() == DataScope.BLOCK_INVENTORY) {
            data.put(FieldKey.LOCATION, record.ownerKey());
        } else if (record.scope() == DataScope.UNIVERSAL_INVENTORY) {
            data.put(FieldKey.UNIVERSAL_UUID, record.ownerKey());
        } else {
            throw new IllegalArgumentException("Unsupported persisted item scope: " + record.scope());
        }
        data.put(FieldKey.INVENTORY_SLOT, record.slotKey());
        value.put(data, FieldKey.INVENTORY_ITEM);
        controller.setData(new RecordKey(record.scope()), data);
    }

    private Set<String> loadedBlockKeys() {
        Set<String> loaded = new HashSet<>();
        for (SlimefunChunkData chunkData : controller.getAllLoadedChunkData()) {
            for (SlimefunBlockData blockData : chunkData.getAllBlockData()) {
                loaded.add(blockData.getKey());
            }
        }
        return loaded;
    }

    private Set<String> loadedUniversalKeys() {
        Set<String> loaded = new HashSet<>();
        for (SlimefunUniversalData data : controller.getAllLoadedUniversalData()) {
            loaded.add(data.getKey());
        }
        return loaded;
    }

    @Nullable
    private static String stringValue(RecordSet record, FieldKey key) {
        Object value = record.getValue(key);
        return value == null ? null : String.valueOf(value);
    }

    public record PersistedItemRecord(
            DataScope scope, String ownerKey, String slotKey, StoredItemValue storedValue) {
        public PersistedItemRecord {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(ownerKey, "ownerKey");
            Objects.requireNonNull(slotKey, "slotKey");
            Objects.requireNonNull(storedValue, "storedValue");
            if (scope != DataScope.BLOCK_INVENTORY && scope != DataScope.UNIVERSAL_INVENTORY) {
                throw new IllegalArgumentException("Unsupported persisted item scope: " + scope);
            }
        }

        public String identity() {
            return scope.name() + ':' + ownerKey + ':' + slotKey;
        }

        /** Deserializes a defensive copy using Slimefun's current+legacy compatibility codec. */
        public @Nullable ItemStack deserializeItem() {
            return storedValue.deserialize();
        }

        public boolean usesLegacyStorageFormat() {
            return storedValue.isLegacyStorageFormat();
        }
    }

    public static final class StoredItemValue {
        private final byte[] binary;
        private final String text;

        private StoredItemValue(@Nullable byte[] binary, @Nullable String text) {
            this.binary = binary == null ? null : binary.clone();
            this.text = text;
        }

        static @Nullable StoredItemValue copyOf(Object rawValue) {
            if (rawValue instanceof byte[] bytes) {
                return new StoredItemValue(bytes, null);
            }
            if (rawValue instanceof String string) {
                return new StoredItemValue(null, string);
            }
            return null;
        }

        public static @Nonnull StoredItemValue current(@Nonnull ItemStack item) {
            Objects.requireNonNull(item, "item");
            return new StoredItemValue(DataUtils.serializeItemStackBytes(item), null);
        }

        public boolean isBinary() {
            return binary != null;
        }

        public byte[] binaryCopy() {
            return binary == null ? new byte[0] : binary.clone();
        }

        public @Nullable String textValue() {
            return text;
        }

        public boolean isLegacyStorageFormat() {
            return text != null || (binary != null && DataUtils.isLegacyItemStack(binary));
        }

        public @Nullable ItemStack deserialize() {
            return binary != null ? DataUtils.deserializeItemStack(binary) : DataUtils.deserializeItemStack(text);
        }

        boolean sameValue(StoredItemValue other) {
            if (other == null) {
                return false;
            }
            return Arrays.equals(binary, other.binary) && Objects.equals(text, other.text);
        }

        void put(RecordSet record, FieldKey field) {
            if (binary != null) {
                record.put(field, binary);
            } else if (text != null) {
                record.put(field, text);
            } else {
                throw new IllegalStateException("Stored item value has no representation");
            }
        }
    }

    public record RewriteRequest(PersistedItemRecord expected, StoredItemValue replacement) {
        public RewriteRequest {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    public record SnapshotResult(boolean available, List<PersistedItemRecord> records) {
        public SnapshotResult {
            records = List.copyOf(records);
        }
    }

    public record RewriteSummary(
            boolean busy,
            int rewritten,
            int stale,
            int loaded,
            int missing,
            int failures,
            boolean rollbackComplete) {
        static RewriteSummary busyResult() {
            return new RewriteSummary(true, 0, 0, 0, 0, 0, true);
        }
    }
}
