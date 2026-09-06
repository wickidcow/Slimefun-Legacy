package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageIntegrityRepairBackupTest {

    @Test
    void backupSerializationIsDeterministicAndTypeTagged() {
        Map<FieldKey, Object> first = new LinkedHashMap<>();
        first.put(FieldKey.LOCATION, "world;0:64:0");
        first.put(FieldKey.INVENTORY_ITEM, new byte[] {1, 2, 3});

        Map<FieldKey, Object> reversed = new LinkedHashMap<>();
        reversed.put(FieldKey.INVENTORY_ITEM, new byte[] {1, 2, 3});
        reversed.put(FieldKey.LOCATION, "world;0:64:0");

        String left = StorageIntegrityRepairBackup.serialize(DataScope.BLOCK_INVENTORY, first);
        String right = StorageIntegrityRepairBackup.serialize(DataScope.BLOCK_INVENTORY, reversed);

        assertEquals(left, right);
        assertTrue(left.startsWith("BLOCK_INVENTORY\tINVENTORY_ITEM=B:AQID\tLOCATION=S:"));
    }

    @Test
    void nullValuesRemainExplicitInBackupFormat() {
        Map<FieldKey, Object> values = Map.of(FieldKey.DATA_KEY, "owner");
        var mutable = new LinkedHashMap<>(values);
        mutable.put(FieldKey.DATA_VALUE, null);

        String line = StorageIntegrityRepairBackup.serialize(DataScope.BLOCK_DATA, mutable);

        assertTrue(line.contains("DATA_KEY=S:b3duZXI="));
        assertTrue(line.contains("DATA_VALUE=N:"));
    }
}
