package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Narrow administrative access to persisted Slimefun identities.
 *
 * <p>This helper deliberately lives beside {@link ADataController} so it can use the controller's protected
 * read/write maintenance gates without widening the normal storage API. It never resolves Bukkit locations or
 * loads chunks. Loaded records are not rewritten because cached data containers keep their Slimefun id immutable
 * for their lifetime.</p>
 */
public final class BlockIdStorageMaintenance {

    public static final String BLOCK_SCOPE = DataScope.BLOCK_RECORD.name();
    public static final String UNIVERSAL_SCOPE = DataScope.UNIVERSAL_RECORD.name();

    private final BlockDataController controller;

    public BlockIdStorageMaintenance(@Nonnull BlockDataController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    /**
     * Takes a storage-level identity snapshot while controller reads and writes are idle.
     *
     * @return a snapshot, or an unavailable result when the controller is busy
     */
    public @Nonnull SnapshotResult snapshot() {
        AtomicReference<List<PersistedBlockIdentity>> snapshot = new AtomicReference<>();
        AtomicBoolean readGateAcquired = new AtomicBoolean();

        boolean writeGateAcquired = controller.runIfAllWriteWorkIdle(() -> readGateAcquired.set(
                controller.runIfReadExecutorIdle(() -> snapshot.set(readAllIdentities()))));

        if (!writeGateAcquired || !readGateAcquired.get()) {
            return new SnapshotResult(false, List.of());
        }
        List<PersistedBlockIdentity> identities = snapshot.get();
        return new SnapshotResult(true, identities == null ? List.of() : identities);
    }

    /**
     * Rewrites a fingerprint-authorized batch while all normal controller reads and writes are gated.
     *
     * <p>Every request is rechecked against the current persisted id. Loaded records are skipped rather than
     * mutating an immutable cache identity. If a storage write throws, already-applied records are restored to
     * their original ids before the method returns.</p>
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

    private List<PersistedBlockIdentity> readAllIdentities() {
        List<PersistedBlockIdentity> result = new ArrayList<>();
        readBlockIdentities(result);
        readUniversalIdentities(result);
        result.sort((left, right) -> {
            int scope = left.storageScope().compareTo(right.storageScope());
            return scope != 0 ? scope : left.recordKey().compareTo(right.recordKey());
        });
        return List.copyOf(result);
    }

    private void readBlockIdentities(List<PersistedBlockIdentity> result) {
        RecordKey key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.LOCATION);
        key.addField(FieldKey.CHUNK);
        key.addField(FieldKey.SLIMEFUN_ID);

        Set<String> loadedLocations = loadedLocationKeys();
        for (RecordSet record : controller.getData(key)) {
            String location = stringValue(record, FieldKey.LOCATION);
            String chunk = stringValue(record, FieldKey.CHUNK);
            String slimefunId = stringValue(record, FieldKey.SLIMEFUN_ID);
            if (location != null && !location.isBlank() && slimefunId != null && !slimefunId.isBlank()) {
                result.add(new PersistedBlockIdentity(
                        BLOCK_SCOPE, location, chunk, slimefunId, loadedLocations.contains(location)));
            }
        }
    }

    private void readUniversalIdentities(List<PersistedBlockIdentity> result) {
        RecordKey key = new RecordKey(DataScope.UNIVERSAL_RECORD);
        key.addField(FieldKey.UNIVERSAL_UUID);
        key.addField(FieldKey.SLIMEFUN_ID);

        for (RecordSet record : controller.getData(key)) {
            String uuid = stringValue(record, FieldKey.UNIVERSAL_UUID);
            String slimefunId = stringValue(record, FieldKey.SLIMEFUN_ID);
            if (uuid == null || uuid.isBlank() || slimefunId == null || slimefunId.isBlank()) {
                continue;
            }

            UUID parsedUuid;
            try {
                parsedUuid = UUID.fromString(uuid);
            } catch (IllegalArgumentException malformedKey) {
                // A malformed primary key is unrelated storage corruption. Preserve it untouched rather than
                // authorizing an ID rewrite against a record Slimefun cannot safely address at runtime.
                continue;
            }

            result.add(new PersistedBlockIdentity(
                    UNIVERSAL_SCOPE,
                    uuid,
                    null,
                    slimefunId,
                    controller.getUniversalDataFromCache(parsedUuid) != null));
        }
    }

    private RewriteSummary rewriteWhileGated(List<RewriteRequest> requests) {
        Map<String, PersistedBlockIdentity> live = new HashMap<>();
        for (PersistedBlockIdentity identity : readAllIdentities()) {
            live.put(identityKey(identity.storageScope(), identity.recordKey()), identity);
        }

        int stale = 0;
        int loaded = 0;
        int missing = 0;
        List<PersistedBlockIdentity> applied = new ArrayList<>();

        try {
            for (RewriteRequest request : requests) {
                PersistedBlockIdentity current = live.get(identityKey(request.storageScope(), request.recordKey()));
                if (current == null) {
                    missing++;
                    continue;
                }
                if (!current.slimefunId().equals(request.expectedId())) {
                    stale++;
                    continue;
                }
                if (current.loaded()) {
                    loaded++;
                    continue;
                }

                writeIdentity(current, request.replacementId());
                applied.add(current);
            }
            return new RewriteSummary(false, applied.size(), stale, loaded, missing, 0, true);
        } catch (RuntimeException | LinkageError failure) {
            boolean rollbackComplete = true;
            for (int i = applied.size() - 1; i >= 0; i--) {
                PersistedBlockIdentity original = applied.get(i);
                try {
                    writeIdentity(original, original.slimefunId());
                } catch (RuntimeException | LinkageError rollbackFailure) {
                    rollbackComplete = false;
                }
            }
            return new RewriteSummary(false, 0, stale, loaded, missing, 1, rollbackComplete);
        }
    }

    private void writeIdentity(PersistedBlockIdentity current, String replacementId) {
        DataScope scope = parseScope(current.storageScope());
        RecordKey key = new RecordKey(scope);
        RecordSet data = new RecordSet();
        if (scope == DataScope.BLOCK_RECORD) {
            data.put(FieldKey.LOCATION, current.recordKey());
            if (current.chunkKey() != null) {
                data.put(FieldKey.CHUNK, current.chunkKey());
            }
        } else {
            data.put(FieldKey.UNIVERSAL_UUID, current.recordKey());
        }
        data.put(FieldKey.SLIMEFUN_ID, replacementId);
        controller.setData(key, data);
    }

    private Set<String> loadedLocationKeys() {
        Set<String> loaded = new HashSet<>();
        for (SlimefunChunkData chunkData : controller.getAllLoadedChunkData()) {
            for (SlimefunBlockData blockData : chunkData.getAllBlockData()) {
                loaded.add(blockData.getKey());
            }
        }
        return loaded;
    }

    private static DataScope parseScope(String storageScope) {
        if (BLOCK_SCOPE.equals(storageScope)) return DataScope.BLOCK_RECORD;
        if (UNIVERSAL_SCOPE.equals(storageScope)) return DataScope.UNIVERSAL_RECORD;
        throw new IllegalArgumentException("Unsupported persisted identity scope: " + storageScope);
    }

    private static String identityKey(String storageScope, String recordKey) {
        return storageScope + '\u0000' + recordKey;
    }

    @Nullable
    private static String stringValue(RecordSet record, FieldKey key) {
        Object value = record.getValue(key);
        return value == null ? null : String.valueOf(value);
    }

    public record PersistedBlockIdentity(
            String storageScope,
            String recordKey,
            @Nullable String chunkKey,
            String slimefunId,
            boolean loaded) {
        public PersistedBlockIdentity {
            storageScope = Objects.requireNonNull(storageScope, "storageScope");
            recordKey = Objects.requireNonNull(recordKey, "recordKey");
            slimefunId = Objects.requireNonNull(slimefunId, "slimefunId");
            parseScope(storageScope);
        }

        public PersistedBlockIdentity(
                String locationKey, @Nullable String chunkKey, String slimefunId, boolean loaded) {
            this(BLOCK_SCOPE, locationKey, chunkKey, slimefunId, loaded);
        }

        /** Backwards-compatible name for normal BLOCK_RECORD callers. */
        public String locationKey() {
            return recordKey;
        }
    }

    public record RewriteRequest(String storageScope, String recordKey, String expectedId, String replacementId) {
        public RewriteRequest {
            storageScope = Objects.requireNonNull(storageScope, "storageScope");
            recordKey = Objects.requireNonNull(recordKey, "recordKey");
            expectedId = Objects.requireNonNull(expectedId, "expectedId");
            replacementId = Objects.requireNonNull(replacementId, "replacementId");
            parseScope(storageScope);
        }

        public RewriteRequest(String locationKey, String expectedId, String replacementId) {
            this(BLOCK_SCOPE, locationKey, expectedId, replacementId);
        }

        /** Backwards-compatible name for normal BLOCK_RECORD callers. */
        public String locationKey() {
            return recordKey;
        }
    }

    public record SnapshotResult(boolean available, List<PersistedBlockIdentity> identities) {
        public SnapshotResult {
            identities = List.copyOf(identities);
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
