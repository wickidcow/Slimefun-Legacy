package com.xzavier0722.mc.plugin.slimefun4.storage.adapter;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * A narrow compatibility wrapper for BlockStorage records which resolves explicitly registered legacy Slimefun ids.
 *
 * <p>The wrapper never creates a second live item registration and never deletes unknown data. An id is reconciled only
 * when the stored id is not currently registered, a legacy alias resolves to a final target, and that target is
 * currently registered. The returned block record is rewritten in-memory immediately and the persisted record is
 * updated using only its {@link FieldKey#SLIMEFUN_ID} column.
 *
 * <p>Block data, inventories and chunk data are delegated unchanged. This keeps reconciliation independent from Bukkit
 * block access and therefore safe for Paper and Folia storage threads.
 *
 * @param <T> adapter configuration type
 */
public final class LegacyIdReconciliationAdapter<T> implements IDataSourceAdapter<T> {
    private final IDataSourceAdapter<T> delegate;
    private final UnaryOperator<String> canonicalIdResolver;
    private final Logger logger;

    public LegacyIdReconciliationAdapter(@Nonnull IDataSourceAdapter<T> delegate) {
        this(delegate, LegacyIdReconciliationAdapter::resolveRegisteredCanonicalId, Slimefun.logger());
    }

    LegacyIdReconciliationAdapter(
            @Nonnull IDataSourceAdapter<T> delegate,
            @Nonnull UnaryOperator<String> canonicalIdResolver,
            @Nonnull Logger logger) {
        this.delegate = delegate;
        this.canonicalIdResolver = canonicalIdResolver;
        this.logger = logger;
    }

    @Override
    public void prepare(T config) {
        delegate.prepare(config);
    }

    @Override
    public void initStorage(DataType type) {
        delegate.initStorage(type);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public void setData(RecordKey key, RecordSet item) {
        delegate.setData(key, item);
    }

    @Override
    public List<RecordSet> getData(RecordKey key, boolean distinct) {
        List<RecordSet> records = delegate.getData(key, distinct);
        if (key.getScope() != DataScope.BLOCK_RECORD || records.isEmpty()) {
            return records;
        }

        ArrayList<RecordSet> reconciled = null;
        for (int i = 0; i < records.size(); i++) {
            RecordSet record = records.get(i);
            RecordSet resolved = reconcileBlockRecord(key, record);
            if (resolved != record) {
                if (reconciled == null) {
                    reconciled = new ArrayList<>(records);
                }
                reconciled.set(i, resolved);
            }
        }
        return reconciled == null ? records : reconciled;
    }

    @Override
    public void deleteData(RecordKey key) {
        delegate.deleteData(key);
    }

    @Override
    public void patch() {
        delegate.patch();
    }

    private RecordSet reconcileBlockRecord(RecordKey queryKey, RecordSet record) {
        String storedId = record.get(FieldKey.SLIMEFUN_ID);
        if (storedId == null || storedId.isBlank()) {
            return record;
        }

        String canonicalId = canonicalIdResolver.apply(storedId);
        if (canonicalId == null || canonicalId.equals(storedId)) {
            return record;
        }

        RecordSet result = copyRecord(record);
        result.put(FieldKey.SLIMEFUN_ID, canonicalId);

        String location = findLocation(queryKey, record);
        if (location != null) {
            persistCanonicalId(location, canonicalId);
        }

        return result;
    }

    private void persistCanonicalId(String location, String canonicalId) {
        var updateKey = new RecordKey(DataScope.BLOCK_RECORD);
        updateKey.addCondition(FieldKey.LOCATION, location);
        updateKey.addField(FieldKey.SLIMEFUN_ID);

        var update = new RecordSet();
        update.put(FieldKey.SLIMEFUN_ID, canonicalId);

        try {
            delegate.setData(updateKey, update);
        } catch (RuntimeException failure) {
            // The current load can still safely use the canonical id. A later read may retry persistence.
            logger.log(
                    Level.WARNING,
                    "Could not persist reconciled Slimefun id '" + canonicalId + "' at " + location
                            + "; stored machine data was left intact.",
                    failure);
        }
    }

    private static String findLocation(RecordKey queryKey, RecordSet record) {
        String location = record.get(FieldKey.LOCATION);
        if (location != null && !location.isBlank()) {
            return location;
        }

        for (var condition : queryKey.getConditions()) {
            if (condition.getFirstValue() == FieldKey.LOCATION) {
                String value = condition.getSecondValue();
                if (value != null && !value.isBlank() && !value.contains("%")) {
                    return value;
                }
            }
        }
        return null;
    }

    private static RecordSet copyRecord(RecordSet source) {
        var copy = new RecordSet();
        source.getAllValues().forEach((field, value) -> {
            if (value instanceof byte[] bytes) {
                copy.put(field, bytes);
            } else if (value != null) {
                copy.put(field, String.valueOf(value));
            }
        });
        return copy;
    }

    private static String resolveRegisteredCanonicalId(String storedId) {
        // Exact/current ids always win, even if an alias with the same key was declared earlier.
        if (SlimefunItem.getById(storedId) != null) {
            return storedId;
        }

        return Slimefun.getRegistry()
                .resolveLegacySlimefunItemId(storedId)
                .filter(targetId -> SlimefunItem.getById(targetId) != null)
                .orElse(storedId);
    }
}
