package com.xzavier0722.mc.plugin.slimefun4.storage.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class LegacyIdReconciliationAdapterTest {

    @Test
    void reconcilesOnlyTheStoredBlockIdentity() {
        var delegate = new RecordingAdapter();
        var blockRecord = record(
                FieldKey.LOCATION, "world;12:64:24",
                FieldKey.CHUNK, "world;0:1",
                FieldKey.SLIMEFUN_ID, "OLD_MACHINE");
        var blockData = record(FieldKey.DATA_KEY, "energy", FieldKey.DATA_VALUE, "712");
        var inventory = record(FieldKey.INVENTORY_SLOT, "4", FieldKey.INVENTORY_ITEM, "untouched-item-data");
        delegate.records.put(DataScope.BLOCK_RECORD, List.of(blockRecord));
        delegate.records.put(DataScope.BLOCK_DATA, List.of(blockData));
        delegate.records.put(DataScope.BLOCK_INVENTORY, List.of(inventory));

        var adapter = new LegacyIdReconciliationAdapter<Void>(
                delegate,
                id -> id.equals("OLD_MACHINE") ? "CURRENT_MACHINE" : id,
                Logger.getAnonymousLogger());

        var blockKey = new RecordKey(DataScope.BLOCK_RECORD);
        blockKey.addField(FieldKey.LOCATION);
        blockKey.addField(FieldKey.CHUNK);
        blockKey.addField(FieldKey.SLIMEFUN_ID);
        blockKey.addCondition(FieldKey.CHUNK, "world;0:1");

        RecordSet resolved = adapter.getData(blockKey).get(0);
        assertEquals("CURRENT_MACHINE", resolved.get(FieldKey.SLIMEFUN_ID));
        assertEquals("world;12:64:24", resolved.get(FieldKey.LOCATION));
        assertEquals("world;0:1", resolved.get(FieldKey.CHUNK));

        assertEquals(1, delegate.writes.size());
        Write write = delegate.writes.get(0);
        assertEquals(DataScope.BLOCK_RECORD, write.key().getScope());
        assertEquals(Set.of(FieldKey.SLIMEFUN_ID), write.key().getFields());
        assertEquals(Map.of(FieldKey.SLIMEFUN_ID, "CURRENT_MACHINE"), write.data().getAllValues());
        assertEquals(1, write.key().getConditions().size());
        assertEquals(FieldKey.LOCATION, write.key().getConditions().get(0).getFirstValue());
        assertEquals("world;12:64:24", write.key().getConditions().get(0).getSecondValue());
        assertEquals(0, delegate.deletes);

        var dataKey = new RecordKey(DataScope.BLOCK_DATA);
        var inventoryKey = new RecordKey(DataScope.BLOCK_INVENTORY);
        assertSame(blockData, adapter.getData(dataKey).get(0));
        assertSame(inventory, adapter.getData(inventoryKey).get(0));
        assertEquals("712", blockData.get(FieldKey.DATA_VALUE));
        assertEquals("untouched-item-data", inventory.get(FieldKey.INVENTORY_ITEM));
        assertEquals(1, delegate.writes.size(), "KV and inventory reads must not trigger reconciliation writes");
    }

    @Test
    void preservesUnknownIdsWithoutAnyWrite() {
        var delegate = new RecordingAdapter();
        var blockRecord = record(
                FieldKey.LOCATION, "world;1:64:1",
                FieldKey.SLIMEFUN_ID, "MISSING_ADDON_MACHINE");
        delegate.records.put(DataScope.BLOCK_RECORD, List.of(blockRecord));

        var adapter = new LegacyIdReconciliationAdapter<Void>(
                delegate, id -> id, Logger.getAnonymousLogger());
        var key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.LOCATION);
        key.addField(FieldKey.SLIMEFUN_ID);

        RecordSet result = adapter.getData(key).get(0);
        assertEquals("MISSING_ADDON_MACHINE", result.get(FieldKey.SLIMEFUN_ID));
        assertTrue(delegate.writes.isEmpty());
        assertEquals(0, delegate.deletes);
    }

    @Test
    void usesExactLocationConditionWhenProjectionDoesNotContainLocation() {
        var delegate = new RecordingAdapter();
        delegate.records.put(
                DataScope.BLOCK_RECORD,
                List.of(record(FieldKey.SLIMEFUN_ID, "OLD_MACHINE")));

        var adapter = new LegacyIdReconciliationAdapter<Void>(
                delegate,
                id -> id.equals("OLD_MACHINE") ? "CURRENT_MACHINE" : id,
                Logger.getAnonymousLogger());
        var key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.SLIMEFUN_ID);
        key.addCondition(FieldKey.LOCATION, "world;5:70:9");

        assertEquals("CURRENT_MACHINE", adapter.getData(key).get(0).get(FieldKey.SLIMEFUN_ID));
        assertEquals(1, delegate.writes.size());
        assertEquals(
                "world;5:70:9",
                delegate.writes.get(0).key().getConditions().get(0).getSecondValue());
    }

    @Test
    void doesNotPersistWhenARecordCannotBePinnedToOneLocation() {
        var delegate = new RecordingAdapter();
        delegate.records.put(
                DataScope.BLOCK_RECORD,
                List.of(record(FieldKey.SLIMEFUN_ID, "OLD_MACHINE")));

        var adapter = new LegacyIdReconciliationAdapter<Void>(
                delegate,
                id -> id.equals("OLD_MACHINE") ? "CURRENT_MACHINE" : id,
                Logger.getAnonymousLogger());
        var key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.SLIMEFUN_ID);
        key.addCondition(FieldKey.LOCATION, "world;%");

        assertEquals("CURRENT_MACHINE", adapter.getData(key).get(0).get(FieldKey.SLIMEFUN_ID));
        assertTrue(delegate.writes.isEmpty());
    }

    private static RecordSet record(Object... values) {
        var record = new RecordSet();
        for (int i = 0; i < values.length; i += 2) {
            record.put((FieldKey) values[i], (String) values[i + 1]);
        }
        return record;
    }

    private record Write(RecordKey key, RecordSet data) {}

    private static final class RecordingAdapter implements IDataSourceAdapter<Void> {
        private final Map<DataScope, List<RecordSet>> records = new EnumMap<>(DataScope.class);
        private final List<Write> writes = new ArrayList<>();
        private int deletes;

        @Override
        public void prepare(Void config) {}

        @Override
        public void initStorage(DataType type) {}

        @Override
        public void shutdown() {}

        @Override
        public void setData(RecordKey key, RecordSet item) {
            writes.add(new Write(key, item));
        }

        @Override
        public List<RecordSet> getData(RecordKey key, boolean distinct) {
            return records.getOrDefault(key.getScope(), List.of());
        }

        @Override
        public void deleteData(RecordKey key) {
            deletes++;
        }

        @Override
        public void patch() {}
    }
}
