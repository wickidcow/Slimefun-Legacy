package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

/**
 * Low-level administrative access for persisted Slimefun block identities.
 *
 * <p>This helper never loads a Bukkit world or chunk. Rewrites are allowed only while the controller's tracked read
 * executor and all write queues/executors are idle, and only when the source ID is not live-registered while the target
 * ID is registered. This keeps an already-loaded source identity from being made stale behind an in-memory cache.
 */
public final class LegacyBlockIdStorageMigration {

    public static final String BLOCK_SCOPE = DataScope.BLOCK_RECORD.name();
    public static final String UNIVERSAL_SCOPE = DataScope.UNIVERSAL_RECORD.name();

    private LegacyBlockIdStorageMigration() {}

    /** Returns an exact snapshot of persisted block and universal identities without touching world state. */
    public static @Nonnull List<StoredIdentity> scan(@Nonnull BlockDataController controller) {
        Objects.requireNonNull(controller, "controller");
        List<StoredIdentity> identities = new ArrayList<>();

        RecordKey blocks = new RecordKey(DataScope.BLOCK_RECORD);
        blocks.addField(FieldKey.LOCATION);
        blocks.addField(FieldKey.SLIMEFUN_ID);
        for (RecordSet row : controller.getData(blocks)) {
            String key = row.get(FieldKey.LOCATION);
            String id = row.get(FieldKey.SLIMEFUN_ID);
            if (key != null && id != null && !key.isBlank() && !id.isBlank()) {
                identities.add(new StoredIdentity(BLOCK_SCOPE, key, id));
            }
        }

        RecordKey universals = new RecordKey(DataScope.UNIVERSAL_RECORD);
        universals.addField(FieldKey.UNIVERSAL_UUID);
        universals.addField(FieldKey.SLIMEFUN_ID);
        for (RecordSet row : controller.getData(universals)) {
            String key = row.get(FieldKey.UNIVERSAL_UUID);
            String id = row.get(FieldKey.SLIMEFUN_ID);
            if (key != null && id != null && !key.isBlank() && !id.isBlank()) {
                identities.add(new StoredIdentity(UNIVERSAL_SCOPE, key, id));
            }
        }

        identities.sort(Comparator.comparing(StoredIdentity::storageScope)
                .thenComparing(StoredIdentity::recordKey)
                .thenComparing(StoredIdentity::slimefunId));
        return List.copyOf(identities);
    }

    /**
     * Applies exact source-to-target rewrites while storage work is quiescent.
     *
     * <p>A rewrite is skipped if its row disappeared or changed after planning. A source ID that became live-registered,
     * or a target that is no longer registered, is also skipped. Successful rewrites are verified immediately.
     */
    public static @Nonnull ExecutionResult execute(
            @Nonnull BlockDataController controller, @Nonnull List<Rewrite> requestedRewrites) {
        Objects.requireNonNull(controller, "controller");
        List<Rewrite> rewrites = List.copyOf(Objects.requireNonNull(requestedRewrites, "requestedRewrites"));
        if (rewrites.isEmpty()) {
            return new ExecutionResult(false, false, 0, 0, 0);
        }

        AtomicReference<ExecutionResult> result = new AtomicReference<>();
        AtomicBoolean readsIdle = new AtomicBoolean();
        boolean writesIdle = controller.runIfAllWriteWorkIdle(() -> readsIdle.set(controller.runIfReadExecutorIdle(() ->
                result.set(executeNow(controller, rewrites)))));

        if (!writesIdle) {
            return new ExecutionResult(false, false, 0, rewrites.size(), 0);
        }
        if (!readsIdle.get()) {
            return new ExecutionResult(true, false, 0, rewrites.size(), 0);
        }

        ExecutionResult completed = result.get();
        return completed == null
                ? new ExecutionResult(true, true, 0, rewrites.size(), 1)
                : completed;
    }

    private static ExecutionResult executeNow(BlockDataController controller, List<Rewrite> rewrites) {
        int rewritten = 0;
        int skipped = 0;
        int failures = 0;

        for (Rewrite rewrite : rewrites) {
            try {
                // A live source may already exist in a loaded cache. Never rewrite its backing identity in-place.
                if (SlimefunItem.getById(rewrite.legacyId()) != null
                        || SlimefunItem.getById(rewrite.canonicalId()) == null) {
                    skipped++;
                    continue;
                }

                StoredIdentity current = readIdentity(controller, rewrite.storageScope(), rewrite.recordKey());
                if (current == null || !current.slimefunId().equals(rewrite.legacyId())) {
                    skipped++;
                    continue;
                }

                RecordKey update = updateKey(rewrite.storageScope(), rewrite.recordKey(), rewrite.legacyId());
                RecordSet data = new RecordSet();
                data.put(FieldKey.SLIMEFUN_ID, rewrite.canonicalId());
                controller.setData(update, data);

                StoredIdentity verified = readIdentity(controller, rewrite.storageScope(), rewrite.recordKey());
                if (verified == null || !verified.slimefunId().equals(rewrite.canonicalId())) {
                    failures++;
                    continue;
                }
                rewritten++;
            } catch (RuntimeException | LinkageError failure) {
                failures++;
            }
        }

        return new ExecutionResult(true, true, rewritten, skipped, failures);
    }

    private static StoredIdentity readIdentity(BlockDataController controller, String scopeName, String recordKey) {
        DataScope scope = parseScope(scopeName);
        FieldKey primaryKey = primaryKey(scope);
        RecordKey query = new RecordKey(scope);
        query.addField(primaryKey);
        query.addField(FieldKey.SLIMEFUN_ID);
        query.addCondition(primaryKey, recordKey);
        List<RecordSet> rows = controller.getData(query);
        if (rows.size() != 1) {
            return null;
        }
        String id = rows.getFirst().get(FieldKey.SLIMEFUN_ID);
        return id == null ? null : new StoredIdentity(scope.name(), recordKey, id);
    }

    private static RecordKey updateKey(String scopeName, String recordKey, String expectedSourceId) {
        DataScope scope = parseScope(scopeName);
        FieldKey primaryKey = primaryKey(scope);
        RecordKey update = new RecordKey(scope);
        update.addField(FieldKey.SLIMEFUN_ID);
        update.addCondition(primaryKey, recordKey);
        update.addCondition(FieldKey.SLIMEFUN_ID, expectedSourceId);
        return update;
    }

    private static DataScope parseScope(String scopeName) {
        if (BLOCK_SCOPE.equals(scopeName)) {
            return DataScope.BLOCK_RECORD;
        }
        if (UNIVERSAL_SCOPE.equals(scopeName)) {
            return DataScope.UNIVERSAL_RECORD;
        }
        throw new IllegalArgumentException("Unsupported persisted block identity scope: " + scopeName);
    }

    private static FieldKey primaryKey(DataScope scope) {
        return scope == DataScope.BLOCK_RECORD ? FieldKey.LOCATION : FieldKey.UNIVERSAL_UUID;
    }

    public record StoredIdentity(String storageScope, String recordKey, String slimefunId) {
        public StoredIdentity {
            storageScope = requireText(storageScope, "storageScope");
            recordKey = requireText(recordKey, "recordKey");
            slimefunId = requireText(slimefunId, "slimefunId");
            parseScope(storageScope);
        }
    }

    public record Rewrite(String storageScope, String recordKey, String legacyId, String canonicalId) {
        public Rewrite {
            storageScope = requireText(storageScope, "storageScope");
            recordKey = requireText(recordKey, "recordKey");
            legacyId = requireText(legacyId, "legacyId");
            canonicalId = requireText(canonicalId, "canonicalId");
            parseScope(storageScope);
            if (legacyId.equals(canonicalId)) {
                throw new IllegalArgumentException("legacyId and canonicalId must differ");
            }
        }
    }

    public record ExecutionResult(boolean writesIdle, boolean readsIdle, int rewritten, int skipped, int failures) {}

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return text;
    }
}
