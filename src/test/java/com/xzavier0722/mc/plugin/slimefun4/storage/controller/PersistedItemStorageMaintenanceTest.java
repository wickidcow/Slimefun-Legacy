package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistedItemStorageMaintenanceTest {
    private static final String OWNER = "11111111-2222-3333-4444-555555555555";

    @Test
    void updatesExistingRowsRatherThanIssuingAnInsertOnlyWrite() {
        var fixture = new Fixture();
        fixture.values.put("0", "original");
        var result = fixture.maintenance.rewrite(fixture.requests());
        assertEquals(1, result.rewritten());
        assertEquals(0, result.failures());
        assertArrayEquals(new byte[] {9, 8, 7}, (byte[]) fixture.values.get("0"));
    }

    @Test
    void restoresTheAttemptedRowWhenAnAdapterCommitsThenThrows() {
        var fixture = new Fixture();
        byte[] originalBinary = {0, -1, 42};
        fixture.values.put("0", originalBinary);
        fixture.values.put("1", "exact legacy text envelope");
        fixture.failAfterWrite = 2;
        var result = fixture.maintenance.rewrite(fixture.requests());
        assertEquals(1, result.failures());
        assertEquals(0, result.rewritten());
        assertTrue(result.rollbackComplete());
        assertArrayEquals(originalBinary, (byte[]) fixture.values.get("0"));
        assertEquals("exact legacy text envelope", fixture.values.get("1"));
        assertEquals(4, fixture.writes);
    }

    @Test
    void preservesRowsChangedOrPromotedToLoadedOwnershipAfterScanning() {
        var fixture = new Fixture();
        fixture.values.put("0", "scanned value");
        var requests = fixture.requests();
        fixture.values.put("0", "new gameplay value");
        var stale = fixture.maintenance.rewrite(requests);
        assertEquals(1, stale.stale());
        assertEquals("new gameplay value", fixture.values.get("0"));
        requests = fixture.requests();
        fixture.loaded = Set.of(new SlimefunUniversalData(UUID.fromString(OWNER), "fixture"));
        var owned = fixture.maintenance.rewrite(requests);
        assertEquals(1, owned.loaded());
        assertEquals(0, fixture.writes);
        assertEquals("new gameplay value", fixture.values.get("0"));
    }

    @Test
    void refusesBusyStorageWithoutReadingOrWritingRows() {
        var fixture = new Fixture();
        fixture.values.put("0", "original");
        var requests = fixture.requests();
        fixture.idle = false;
        int reads = fixture.reads;
        assertFalse(fixture.maintenance.snapshot().available());
        assertTrue(fixture.maintenance.rewrite(requests).busy());
        assertEquals(reads, fixture.reads);
        assertEquals(0, fixture.writes);
        assertEquals("original", fixture.values.get("0"));
    }

    /** Records the existing adapter contract: update fields/conditions select UPDATE; otherwise INSERT only. */
    private static final class Fixture extends BlockDataController {
        final PersistedItemStorageMaintenance maintenance = new PersistedItemStorageMaintenance(this);
        final Map<String, Object> values = new LinkedHashMap<>();
        Set<SlimefunUniversalData> loaded = Set.of();
        int writes, reads, failAfterWrite;
        boolean idle = true;

        @Override
        protected boolean runIfAllWriteWorkIdle(Runnable action) {
            if (!idle) return false;
            action.run();
            return true;
        }

        @Override
        protected boolean runIfReadExecutorIdle(Runnable action) {
            if (!idle) return false;
            action.run();
            return true;
        }

        @Override
        public Set<SlimefunChunkData> getAllLoadedChunkData() {
            return Set.of();
        }

        @Override
        public Set<SlimefunUniversalData> getAllLoadedUniversalData() {
            return loaded;
        }

        @Override
        protected List<RecordSet> getData(RecordKey key) {
            reads++;
            if (key.getScope() != DataScope.UNIVERSAL_INVENTORY) return List.of();
            List<RecordSet> rows = new ArrayList<>();
            values.forEach((slot, value) -> {
                RecordSet row = new RecordSet();
                row.put(FieldKey.UNIVERSAL_UUID, OWNER);
                row.put(FieldKey.INVENTORY_SLOT, slot);
                if (value instanceof byte[] bytes) row.put(FieldKey.INVENTORY_ITEM, bytes.clone());
                else row.put(FieldKey.INVENTORY_ITEM, (String) value);
                rows.add(row);
            });
            return rows;
        }

        @Override
        protected void setData(RecordKey key, RecordSet row) {
            String slot = row.getString(FieldKey.INVENTORY_SLOT);
            Object value = row.getValue(FieldKey.INVENTORY_ITEM);
            Object copy = value instanceof byte[] bytes ? bytes.clone() : value;
            if (key.getFields().contains(FieldKey.INVENTORY_ITEM)) {
                assertEquals(2, key.getConditions().size());
                assertTrue(key.getConditions().stream()
                        .anyMatch(condition -> condition.getFirstValue() == FieldKey.UNIVERSAL_UUID
                                && OWNER.equals(condition.getSecondValue())));
                assertTrue(key.getConditions().stream()
                        .anyMatch(condition -> condition.getFirstValue() == FieldKey.INVENTORY_SLOT
                                && slot.equals(condition.getSecondValue())));
                values.put(slot, copy);
            } else {
                values.putIfAbsent(slot, copy);
            }
            if (++writes == failAfterWrite) throw new IllegalStateException("Write committed, acknowledgement failed");
        }

        List<PersistedItemStorageMaintenance.RewriteRequest> requests() {
            return maintenance.snapshot().records().stream()
                    .map(record -> new PersistedItemStorageMaintenance.RewriteRequest(
                            record, PersistedItemStorageMaintenance.StoredItemValue.copyOf(new byte[] {9, 8, 7})))
                    .toList();
        }
    }
}
