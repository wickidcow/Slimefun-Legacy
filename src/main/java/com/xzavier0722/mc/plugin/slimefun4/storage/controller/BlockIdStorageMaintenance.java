package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Narrow administrative access to persisted normal-block identities.
 *
 * <p>This helper deliberately lives beside {@link ADataController} so it can use the controller's protected
 * read/write maintenance gates without widening the normal storage API. It never resolves Bukkit locations or
 * loads chunks. Loaded block records are not rewritten because {@link SlimefunBlockData} keeps its Slimefun id
 * immutable for the lifetime of the cached object.</p>
 */
public final class BlockIdStorageMaintenance {

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
        RecordKey key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.LOCATION);
        key.addField(FieldKey.CHUNK);
        key.addField(FieldKey.SLIMEFUN_ID);

        List<PersistedBlockIdentity> result = new ArrayList<>();
        for (RecordSet record : controller.getData(key)) {
            String location = stringValue(record, FieldKey.LOCATION);
            String chunk = stringValue(record, FieldKey.CHUNK);
            String slimefunId = stringValue(record, FieldKey.SLIMEFUN_ID);
            if (location != null && !location.isBlank() && slimefunId != null && !slimefunId.isBlank()) {
                result.add(new PersistedBlockIdentity(location, chunk, slimefunId, isLoaded(location)));
            }
        }
        result.sort((left, right) -> left.locationKey().compareTo(right.locationKey()));
        return List.copyOf(result);
    }

    private RewriteSummary rewriteWhileGated(List<RewriteRequest> requests) {
        Map<String, PersistedBlockIdentity> live = new HashMap<>();
        for (PersistedBlockIdentity identity : readAllIdentities()) {
            live.put(identity.locationKey(), identity);
        }

        int stale = 0;
        int loaded = 0;
        int missing = 0;
        List<PersistedBlockIdentity> applied = new ArrayList<>();

        try {
            for (RewriteRequest request : requests) {
                PersistedBlockIdentity current = live.get(request.locationKey());
                if (current == null) {
                    missing++;
                    continue;
                }
                if (!current.slimefunId().equals(request.expectedId())) {
                    stale++;
                    continue;
                }
                if (current.loaded() || isLoaded(current.locationKey())) {
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
        RecordKey key = new RecordKey(DataScope.BLOCK_RECORD);
        RecordSet data = new RecordSet();
        data.put(FieldKey.LOCATION, current.locationKey());
        if (current.chunkKey() != null) {
            data.put(FieldKey.CHUNK, current.chunkKey());
        }
        data.put(FieldKey.SLIMEFUN_ID, replacementId);
        controller.setData(key, data);
    }

    private boolean isLoaded(String locationKey) {
        for (SlimefunChunkData chunkData : controller.getAllLoadedChunkData()) {
            for (SlimefunBlockData blockData : chunkData.getAllBlockData()) {
                if (locationKey.equals(blockData.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static String stringValue(RecordSet record, FieldKey key) {
        Object value = record.getValue(key);
        return value == null ? null : String.valueOf(value);
    }

    public record PersistedBlockIdentity(String locationKey, @Nullable String chunkKey, String slimefunId, boolean loaded) {
        public PersistedBlockIdentity {
            Objects.requireNonNull(locationKey, "locationKey");
            Objects.requireNonNull(slimefunId, "slimefunId");
        }
    }

    public record RewriteRequest(String locationKey, String expectedId, String replacementId) {
        public RewriteRequest {
            Objects.requireNonNull(locationKey, "locationKey");
            Objects.requireNonNull(expectedId, "expectedId");
            Objects.requireNonNull(replacementId, "replacementId");
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
