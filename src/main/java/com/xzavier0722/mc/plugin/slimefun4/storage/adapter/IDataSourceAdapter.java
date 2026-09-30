package com.xzavier0722.mc.plugin.slimefun4.storage.adapter;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.util.List;

public interface IDataSourceAdapter<T> {
    /** Current database schema version. Increment this when persistent storage changes. */
    int DATABASE_VERSION = 3;

    void prepare(T config);

    void initStorage(DataType type);

    void shutdown();

    void setData(RecordKey key, RecordSet item);

    default List<RecordSet> getData(RecordKey key) {
        return getData(key, false);
    }

    List<RecordSet> getData(RecordKey key, boolean distinct);

    void deleteData(RecordKey key);

    /**
     * Atomically persists a preflighted universal destination and removes its exact source.
     * Existing third-party adapters remain binary-compatible and refuse this optional operation
     * unless they explicitly implement its transactional contract. No best-effort fallback is safe.
     */
    default void migrateBlockToUniversal(BlockStorageMigration migration) {
        throw new UnsupportedOperationException("This storage adapter does not support atomic universal migration");
    }

    void patch();
}
