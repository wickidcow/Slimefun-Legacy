package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Record ownership tests; these do not claim arbitrary concurrent mutation is supported. */
class RecordSetBinaryOwnershipTest {
    private static final FieldKey ITEM = FieldKey.INVENTORY_ITEM;
    private static final byte[] ORIGINAL = {83, 70, 50, 0, -1, 17, 42};

    enum ReadPath {
        DIRECT,
        MAP_GET,
        MAP_DEFAULT,
        ENTRY_ITERATOR,
        VALUE_ITERATOR,
        MAP_CALLBACK,
        ENTRY_CALLBACK,
        VALUE_CALLBACK,
        ENTRY_STREAM,
        VALUE_STREAM,
        ENTRY_ARRAY,
        VALUE_ARRAY,
        VALUE_GENERATED_ARRAY,
        ENTRY_SPLITERATOR,
        VALUE_SPLITERATOR,
        ITERATOR_REMAINDER;

        byte[] read(RecordSet record) {
            var view = record.getAllValues();
            return switch (this) {
                case DIRECT -> (byte[]) record.getValue(ITEM);
                case MAP_GET -> (byte[]) view.get(ITEM);
                case MAP_DEFAULT -> (byte[]) view.getOrDefault(ITEM, null);
                case ENTRY_ITERATOR -> (byte[]) view.entrySet().iterator().next().getValue();
                case VALUE_ITERATOR -> (byte[]) view.values().iterator().next();
                case MAP_CALLBACK -> {
                    var found = new AtomicReference<byte[]>();
                    view.forEach((key, value) -> found.set((byte[]) value));
                    yield found.get();
                }
                case ENTRY_CALLBACK -> {
                    var found = new AtomicReference<byte[]>();
                    view.entrySet().forEach(entry -> found.set((byte[]) entry.getValue()));
                    yield found.get();
                }
                case VALUE_CALLBACK -> {
                    var found = new AtomicReference<byte[]>();
                    view.values().forEach(value -> found.set((byte[]) value));
                    yield found.get();
                }
                case ENTRY_STREAM -> (byte[]) view.entrySet().stream().findFirst().orElseThrow().getValue();
                case VALUE_STREAM -> (byte[]) view.values().stream().findFirst().orElseThrow();
                case ENTRY_ARRAY -> (byte[]) ((Map.Entry<?, ?>) view.entrySet().toArray()[0]).getValue();
                case VALUE_ARRAY -> (byte[]) view.values().toArray()[0];
                case VALUE_GENERATED_ARRAY -> (byte[]) view.values().toArray(Object[]::new)[0];
                case ENTRY_SPLITERATOR -> {
                    var found = new AtomicReference<byte[]>();
                    assertTrue(view.entrySet().spliterator().tryAdvance(entry -> found.set((byte[]) entry.getValue())));
                    yield found.get();
                }
                case VALUE_SPLITERATOR -> {
                    var found = new AtomicReference<byte[]>();
                    assertTrue(view.values().spliterator().tryAdvance(value -> found.set((byte[]) value)));
                    yield found.get();
                }
                case ITERATOR_REMAINDER -> {
                    var found = new AtomicReference<byte[]>();
                    view.entrySet().iterator().forEachRemaining(entry -> found.set((byte[]) entry.getValue()));
                    yield found.get();
                }
            };
        }
    }

    static Stream<Arguments> readPaths() {
        return Arrays.stream(ReadPath.values())
                .flatMap(path -> Stream.of(Arguments.of(path, false), Arguments.of(path, true)));
    }

    @ParameterizedTest(name = "{0}, readonly={1}")
    @MethodSource("readPaths")
    void exportedArraysCannotModifyTheOwnedRecord(ReadPath path, boolean readonly) {
        var record = binaryRecord();
        if (readonly) {
            record.readonly();
        }
        byte[] exported = path.read(record);
        assertArrayEquals(ORIGINAL, exported);
        Arrays.fill(exported, (byte) 0);
        assertArrayEquals(ORIGINAL, (byte[]) record.getValue(ITEM), "The record exposed its owned array via " + path);
        assertEquals(Base64.getEncoder().encodeToString(ORIGINAL), record.getString(ITEM));
        assertNotSame(exported, path.read(record));
    }

    @Test
    void arrayCapturedBeforeReadonlyCannotAlterTheFrozenRecord() {
        var record = binaryRecord();
        var direct = (byte[]) record.getValue(ITEM);
        var entry = record.getAllValues().entrySet().iterator().next();
        record.readonly();
        Arrays.fill(direct, (byte) 1);
        Arrays.fill((byte[]) entry.getValue(), (byte) 2);
        assertArrayEquals(ORIGINAL, (byte[]) record.getValue(ITEM));
        assertThrows(IllegalStateException.class, () -> record.put(ITEM, new byte[] {3}));
    }

    @Test
    void constructorInputOwnershipAndRepeatedExportsRemainIndependent() {
        var source = ORIGINAL.clone();
        var record = new RecordSet();
        record.put(ITEM, source);
        Arrays.fill(source, (byte) 3);
        byte[] first = (byte[]) record.getValue(ITEM);
        byte[] second = (byte[]) record.getAllValues().get(ITEM);
        assertNotSame(first, second);
        first[1] = 4;
        assertArrayEquals(ORIGINAL, second);
        assertArrayEquals(ORIGINAL, (byte[]) record.getValue(ITEM));
    }

    @Test
    void retainedMapKeysAndValuesStayLiveAcrossSupportedPuts() {
        var record = new RecordSet();
        var map = record.getAllValues();
        var keys = map.keySet();
        var values = map.values();
        var entries = map.entrySet();
        assertTrue(map.isEmpty());
        record.put(ITEM, ORIGINAL);
        assertTrue(keys.contains(ITEM));
        assertEquals(1, values.size());
        assertEquals(1, entries.size());
        record.put(FieldKey.DATA_VALUE, "opaque-owner-data");
        assertEquals(2, map.size());
        assertTrue(values.contains("opaque-owner-data"));
        byte[] replacement = {9, 0, -8};
        record.put(ITEM, replacement);
        assertArrayEquals(replacement, (byte[]) map.get(ITEM));
        record.readonly();
        assertThrows(IllegalStateException.class, () -> record.put(ITEM, ORIGINAL));
        assertArrayEquals(replacement, (byte[]) map.get(ITEM));
    }

    @Test
    void mapAndAllViewsRemainStructurallyReadOnly() {
        var record = binaryRecord();
        var map = record.getAllValues();
        assertThrows(UnsupportedOperationException.class, () -> map.put(ITEM, new byte[] {0}));
        assertThrows(UnsupportedOperationException.class, () -> map.remove(ITEM));
        assertThrows(UnsupportedOperationException.class, map::clear);
        assertThrows(UnsupportedOperationException.class, () -> map.compute(ITEM, (key, value) -> null));
        assertThrows(UnsupportedOperationException.class, () -> map.replaceAll((key, value) -> null));
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet().iterator().next().setValue(null));
        assertThrows(UnsupportedOperationException.class, () -> map.keySet().remove(ITEM));
        assertThrows(UnsupportedOperationException.class, () -> map.values().removeIf(value -> true));
        var entries = map.entrySet().iterator();
        entries.next();
        assertThrows(UnsupportedOperationException.class, entries::remove);
        assertArrayEquals(ORIGINAL, (byte[]) record.getValue(ITEM));
    }

    @Test
    void defaultsNullsScalarsAndLegacyBase64RetainTheirTypes() {
        var record = new RecordSet();
        var map = record.getAllValues();
        Object fallback = new Object();
        assertNull(record.getValue(ITEM));
        assertSame(fallback, map.getOrDefault(ITEM, fallback));
        record.put(ITEM, (String) null);
        assertTrue(map.containsKey(ITEM));
        assertNull(map.getOrDefault(ITEM, fallback));
        String text = new String("9007199254740993");
        record.put(FieldKey.DATA_VALUE, text);
        assertSame(text, record.getValue(FieldKey.DATA_VALUE));
        assertSame(text, map.get(FieldKey.DATA_VALUE));
        record.put(FieldKey.INVENTORY_SLOT, "37");
        assertEquals(37, record.getInt(FieldKey.INVENTORY_SLOT));
        record.put(FieldKey.DATA_KEY, true);
        assertTrue(record.getBoolean(FieldKey.DATA_KEY));
        UUID owner = UUID.fromString("f55799ac-2dd7-42f0-91a2-123456abcdef");
        record.put(FieldKey.PLAYER_UUID, owner.toString());
        assertEquals(owner, record.getUUID(FieldKey.PLAYER_UUID));
        record.put(ITEM, ORIGINAL);
        assertEquals(Base64.getEncoder().encodeToString(ORIGINAL), record.getString(ITEM));
        assertEquals(text, record.getOrDef(FieldKey.DATA_VALUE, "fallback"));
    }

    @Test
    void emptyAndLargeArraysRemainDetachedWithoutChangingOtherFields() {
        var random = new Random(0x5245434f52444cL);
        for (int sample = 0; sample < 1000; sample++) {
            byte[] expected = new byte[sample % 129];
            random.nextBytes(expected);
            var record = new RecordSet();
            record.put(ITEM, expected);
            record.put(FieldKey.DATA_VALUE, "unrelated");
            record.readonly();
            byte[] copy = (byte[]) record.getAllValues().get(ITEM);
            assertNotSame(copy, record.getValue(ITEM));
            Arrays.fill(copy, (byte) 77);
            assertArrayEquals(expected, (byte[]) record.getValue(ITEM), "sample " + sample);
            assertEquals("unrelated", record.getString(FieldKey.DATA_VALUE));
        }
    }

    @Test
    void multipleBinaryFieldsDoNotShareBuffers() {
        var record = binaryRecord();
        record.put(FieldKey.DATA_VALUE, ORIGINAL);
        record.readonly();
        byte[] first = (byte[]) record.getValue(ITEM);
        byte[] other = (byte[]) record.getValue(FieldKey.DATA_VALUE);
        assertNotSame(first, other);
        first[0] = 0;
        assertArrayEquals(ORIGINAL, other);
        assertArrayEquals(ORIGINAL, (byte[]) record.getValue(ITEM));
    }

    private static RecordSet binaryRecord() {
        var record = new RecordSet();
        record.put(ITEM, ORIGINAL);
        return record;
    }
}
