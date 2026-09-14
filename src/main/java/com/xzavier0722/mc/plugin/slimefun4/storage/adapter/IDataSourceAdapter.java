package com.xzavier0722.mc.plugin.slimefun4.storage.adapter;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
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

    /**
     * Performs an atomic conditional update of one String-valued field and returns the affected-row count.
     *
     * <p>This maintenance primitive is deliberately separate from {@link #setData(RecordKey, RecordSet)} because some
     * adapters implement normal writes as an UPSERT whose conflict branch cannot honor arbitrary conditions. The default
     * implementation fails closed so third-party adapters cannot silently participate in compare-and-set migrations
     * until they explicitly support this operation.
     *
     * @param key storage scope plus the conditions that must still match
     * @param field String-valued field to update
     * @param value replacement value
     * @return number of rows updated
     */
    default int conditionalSetString(RecordKey key, FieldKey field, String value) {
        throw new UnsupportedOperationException("Conditional String updates are not supported by this data source adapter.");
    }

    default List<RecordSet> getData(RecordKey key) {
        return getData(key, false);
    }

    List<RecordSet> getData(RecordKey key, boolean distinct);

    void deleteData(RecordKey key);

    void patch();
}
