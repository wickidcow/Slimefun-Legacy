package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecordSetBinaryMapContractTest {
    private static final FieldKey ITEM = FieldKey.INVENTORY_ITEM;

    @Test
    void eachViewHasStableValuesEqualityAndHashUntilAValueIsReplaced() {
        var record = new RecordSet();
        record.put(ITEM, new byte[] {1, 2, 3});
        var view = record.getAllValues();
        byte[] exported = (byte[]) view.get(ITEM);
        int hash = view.hashCode();
        var equivalent = new HashMap<>(view);
        assertSame(exported, view.get(ITEM));
        assertSame(exported, view.entrySet().iterator().next().getValue());
        assertTrue(view.containsValue(exported));
        assertEquals(equivalent, view);
        assertEquals(view, equivalent);
        assertEquals(hash, equivalent.hashCode());
        assertEquals(hash, view.entrySet().hashCode());
        assertEquals(hash, view.hashCode());
        exported[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) record.getValue(ITEM));
        assertSame(exported, view.get(ITEM));
        assertEquals(hash, view.hashCode(), "Array-valued maps use array identity, not array content, for hashing");
        record.put(ITEM, new byte[] {4, 5});
        byte[] replacement = (byte[]) view.get(ITEM);
        assertNotSame(exported, replacement);
        assertArrayEquals(new byte[] {4, 5}, replacement);
        assertSame(replacement, view.entrySet().iterator().next().getValue());
    }

    @Test
    void separatelyRequestedViewsCannotShareAnEditableBinaryExport() {
        var record = new RecordSet();
        record.put(ITEM, new byte[] {2, 3, 4});
        var first = record.getAllValues();
        var second = record.getAllValues();
        byte[] a = (byte[]) first.get(ITEM);
        byte[] b = (byte[]) second.get(ITEM);
        assertNotSame(a, b);
        Arrays.fill(a, (byte) 7);
        assertArrayEquals(new byte[] {2, 3, 4}, b);
        assertArrayEquals(new byte[] {2, 3, 4}, (byte[]) record.getValue(ITEM));
    }

    @Test
    void scalarAndNullReplacementDoNotRetainAnOldBinaryExport() {
        var record = new RecordSet();
        record.put(ITEM, new byte[] {1});
        var view = record.getAllValues();
        var first = (byte[]) view.get(ITEM);
        record.put(ITEM, "historical-text");
        assertEquals("historical-text", view.get(ITEM));
        record.put(ITEM, (String) null);
        assertNull(view.get(ITEM));
        assertTrue(view.containsKey(ITEM));
        record.put(ITEM, new byte[] {2});
        assertNotSame(first, view.get(ITEM));
        assertArrayEquals(new byte[] {2}, (byte[]) view.get(ITEM));
        assertNull(view.get("not-a-field"));
    }

    @Test
    void emptyAndTextOnlyViewsKeepStandardMapEquality() {
        var record = new RecordSet();
        var view = record.getAllValues();
        assertEquals(Map.of(), view);
        assertEquals(0, view.hashCode());
        record.put(FieldKey.DATA_VALUE, "unchanged");
        var expected = Map.of(FieldKey.DATA_VALUE, "unchanged");
        assertEquals(expected, view);
        assertEquals(view, expected);
        assertEquals(expected.hashCode(), view.hashCode());
    }
}
