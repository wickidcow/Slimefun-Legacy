package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlUtils;
import io.github.bakedlibs.dough.collections.Pair;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Query-key ownership and production SQL rendering with generated, reopened SQLite fixtures. */
class RecordKeyConditionOwnershipTest {
    private static final String FIRST = "world;1:64:1";
    private static final String NEIGHBOR = "world;2:64:2";

    @TempDir
    Path directory;

    @Test
    void constructorOwnsBothValuesOfEachSuppliedPair() {
        var supplied = condition(FIRST);
        var key = key(List.of(supplied));
        supplied.setFirstValue(FieldKey.DATA_KEY);
        supplied.setSecondValue(NEIGHBOR);
        assertEquals(List.of(condition(FIRST)), key.getConditions());
    }

    @Test
    void inputMutationCannotMakeConditionsDisagreeWithCachedIdentity() {
        var supplied = condition(FIRST);
        var key = key(List.of(supplied));
        var expected = key(List.of(condition(FIRST)));
        String text = key.toString();
        int hash = key.hashCode();
        supplied.setSecondValue(NEIGHBOR);
        assertEquals(expected, key);
        assertEquals(text, key.toString());
        assertEquals(hash, key.hashCode());
        assertEquals(
                SqlUtils.buildConditionStr(expected.getConditions()), SqlUtils.buildConditionStr(key.getConditions()));
    }

    @Test
    void keysBuiltFromOnePairRemainIndependent() {
        var supplied = condition(FIRST);
        var first = key(List.of(supplied));
        var second = key(List.of(supplied));
        first.getConditions().getFirst().setSecondValue("other");
        supplied.setSecondValue(NEIGHBOR);
        assertEquals(List.of(condition(FIRST)), first.getConditions());
        assertEquals(first, second);
    }

    @Test
    void indexedExportsAreDetachedButRemainEditable() {
        var key = key(List.of(condition(FIRST)));
        var exported = key.getConditions().getFirst();
        assertDoesNotThrow(() -> exported.setFirstValue(FieldKey.DATA_KEY));
        assertDoesNotThrow(() -> exported.setSecondValue(NEIGHBOR));
        assertEquals(NEIGHBOR, exported.getSecondValue());
        assertEquals(List.of(condition(FIRST)), key.getConditions());
        assertNotSame(exported, key.getConditions().getFirst());
    }

    @Test
    void supportedAddConditionAlsoOwnsItsExports() {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.LOCATION, FIRST);
        key.getConditions().getFirst().setSecondValue(NEIGHBOR);
        assertEquals(List.of(condition(FIRST)), key.getConditions());
    }

    @Test
    void iteratorsListsArraysAndStreamsNeverExposeOwnedPairs() {
        List<Function<List<Pair<FieldKey, String>>, Pair<FieldKey, String>>> exports = List.of(
                view -> view.iterator().next(),
                view -> view.listIterator().next(),
                view -> view.listIterator(view.size()).previous(),
                view -> view.toArray(new Pair[0])[0],
                view -> view.stream().findFirst().orElseThrow(),
                view -> view.parallelStream().findFirst().orElseThrow(),
                view -> view.subList(0, 1).getFirst(),
                view -> view.reversed().getLast(),
                List::getFirst,
                List::getLast);
        for (var export : exports) {
            var key = key(List.of(condition(FIRST)));
            export.apply(key.getConditions()).setSecondValue(NEIGHBOR);
            assertEquals(List.of(condition(FIRST)), key.getConditions());
        }
    }

    @Test
    void untypedArraysAndForEachAlsoDetachEntries() {
        var key = key(List.of(condition(FIRST)));
        Object[] array = key.getConditions().toArray();
        assertInstanceOf(Pair.class, array[0]);
        @SuppressWarnings("unchecked")
        var pair = (Pair<FieldKey, String>) array[0];
        pair.setSecondValue(NEIGHBOR);
        key.getConditions().forEach(entry -> entry.setSecondValue("changed"));
        assertEquals(List.of(condition(FIRST)), key.getConditions());
    }

    @Test
    void liveViewStillSeesSubsequentSupportedAdditionsInOrder() {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        var view = key.getConditions();
        assertTrue(view.isEmpty());
        key.addCondition(FieldKey.LOCATION, FIRST);
        key.addCondition(FieldKey.DATA_KEY, "owner");
        key.addCondition(FieldKey.DATA_VALUE, true);
        key.addCondition(FieldKey.DATA_VALUE, false);
        assertEquals(
                List.of(
                        condition(FIRST),
                        new Pair<>(FieldKey.DATA_KEY, "owner"),
                        new Pair<>(FieldKey.DATA_VALUE, "1"),
                        new Pair<>(FieldKey.DATA_VALUE, "0")),
                view);
    }

    @Test
    void allStructuralMutationPathsRemainUnsupported() {
        var view = key(List.of(condition(FIRST))).getConditions();
        assertThrows(UnsupportedOperationException.class, () -> view.add(condition(NEIGHBOR)));
        assertThrows(UnsupportedOperationException.class, () -> view.set(0, condition(NEIGHBOR)));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(0));
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertThrows(UnsupportedOperationException.class, () -> view.removeIf(entry -> false));
        assertThrows(UnsupportedOperationException.class, () -> view.replaceAll(entry -> entry));
        var iterator = view.listIterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertThrows(UnsupportedOperationException.class, () -> iterator.set(condition(NEIGHBOR)));
        assertThrows(UnsupportedOperationException.class, () -> iterator.add(condition(NEIGHBOR)));
        assertThrows(
                UnsupportedOperationException.class, () -> view.subList(0, 1).clear());
        assertThrows(UnsupportedOperationException.class, () -> view.reversed().add(condition(NEIGHBOR)));
    }

    @Test
    void bidirectionalIteratorPreservesIndicesBoundsAndOrder() {
        var view = key(List.of(condition("first"), condition("second"))).getConditions();
        var iterator = view.listIterator(1);
        assertEquals(0, iterator.previousIndex());
        assertEquals(1, iterator.nextIndex());
        assertTrue(iterator.hasPrevious());
        assertEquals(condition("first"), iterator.previous());
        assertFalse(iterator.hasPrevious());
        assertEquals(condition("first"), iterator.next());
        assertEquals(condition("second"), iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(java.util.NoSuchElementException.class, iterator::next);
        assertThrows(IndexOutOfBoundsException.class, () -> view.listIterator(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.listIterator(3));
    }

    @Test
    void inputOrderDuplicateConditionsAndOpaqueValuesAreNotNormalized() {
        var conditions = List.of(condition(""), condition("owner's:opaque/%"), condition(FIRST), condition(FIRST));
        var key = key(conditions);
        assertEquals(conditions, key.getConditions());
        assertEquals(SqlUtils.buildConditionStr(conditions), SqlUtils.buildConditionStr(key.getConditions()));
    }

    @Test
    void existingNullEntriesAndValuesAreNotEagerlyReinterpreted() {
        var supplied = new Pair<FieldKey, String>(FieldKey.DATA_KEY, null);
        var key = key(Arrays.asList(null, supplied));
        assertNull(key.getConditions().getFirst());
        assertNull(key.getConditions().get(1).getSecondValue());
        supplied.setSecondValue("changed");
        assertNull(key.getConditions().get(1).getSecondValue());
    }

    @Test
    void successfulKeyMutationStillInvalidatesTextAndHashAsBefore() {
        var key = key(List.of(condition(FIRST)));
        String before = key.toString();
        key.addCondition(FieldKey.DATA_KEY, "owner");
        var expected = key(List.of(condition(FIRST), new Pair<>(FieldKey.DATA_KEY, "owner")));
        assertNotEquals(before, key.toString());
        assertEquals(expected, key);
        assertEquals(expected.toString(), key.toString());
        assertEquals(expected.hashCode(), key.hashCode());
    }

    @Test
    void conditionOrderRemainsSignificantForKeyEquality() {
        var first = key(List.of(condition("one"), condition("two")));
        var same = key(List.of(condition("one"), condition("two")));
        var reversed = key(List.of(condition("two"), condition("one")));
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, reversed);
    }

    @Test
    void exportedPairCannotPoisonMapLookupForAnEquivalentKey() {
        var key = key(List.of(condition(FIRST)));
        Map<RecordKey, String> entries = new HashMap<>();
        entries.put(key, "pending-save");
        key.getConditions().getFirst().setSecondValue(NEIGHBOR);
        assertEquals("pending-save", entries.get(key(List.of(condition(FIRST)))));
        assertNull(entries.get(key(List.of(condition(NEIGHBOR)))));
    }

    @Test
    void manySharedInputPairsKeepIndependentCachedKeysAndQueries() {
        var shared = condition(FIRST);
        var keys = new ArrayList<RecordKey>();
        var expected = new ArrayList<String>();
        for (int index = 0; index < 1000; index++) {
            shared.setSecondValue("world;" + index + ":64:1");
            var key = key(List.of(shared));
            keys.add(key);
            expected.add(SqlUtils.buildConditionStr(key.getConditions()));
            key.hashCode();
        }
        shared.setSecondValue(NEIGHBOR);
        for (int index = 0; index < keys.size(); index++) {
            assertEquals(
                    expected.get(index),
                    SqlUtils.buildConditionStr(keys.get(index).getConditions()));
        }
    }

    @Test
    void constructorAliasCannotRedirectARealSqliteUpdate() throws Exception {
        String url = database("input-update.db");
        var supplied = condition(FIRST);
        var key = key(List.of(supplied));
        key.toString();
        supplied.setSecondValue(NEIGHBOR);
        try (var connection = DriverManager.getConnection(url)) {
            try (var statement = connection.createStatement()) {
                assertEquals(
                        1,
                        statement.executeUpdate("UPDATE preservation_rows SET "
                                + SqlUtils.buildKvStr(FieldKey.DATA_VALUE, (Object) "updated")
                                + SqlUtils.buildConditionStr(key.getConditions())));
            }
        }
        assertEquals(Map.of(FIRST, "updated", NEIGHBOR, "neighbor-value"), reopen(url));
    }

    @Test
    void returnedPairCannotRedirectARealSqliteDeletion() throws Exception {
        String url = database("export-delete.db");
        var key = key(List.of(condition(FIRST)));
        key.hashCode();
        key.getConditions().getFirst().setSecondValue(NEIGHBOR);
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            assertEquals(
                    1,
                    statement.executeUpdate(
                            "DELETE FROM preservation_rows" + SqlUtils.buildConditionStr(key.getConditions())));
        }
        assertEquals(Map.of(NEIGHBOR, "neighbor-value"), reopen(url));
    }

    @Test
    void querySelectionAfterCallerMutationReadsTheIntendedRecord() throws Exception {
        String url = database("input-read.db");
        var supplied = condition(FIRST);
        var key = key(List.of(supplied));
        supplied.setSecondValue(NEIGHBOR);
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT " + SqlUtils.mapField(FieldKey.DATA_VALUE)
                        + " FROM preservation_rows" + SqlUtils.buildConditionStr(key.getConditions()))) {
            assertTrue(rows.next());
            assertEquals("original-value", rows.getString(1));
            assertFalse(rows.next());
        }
        assertEquals(Map.of(FIRST, "original-value", NEIGHBOR, "neighbor-value"), reopen(url));
    }

    private String database(String name) throws Exception {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + directory.resolve(name);
        try (Connection connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE preservation_rows ("
                    + SqlUtils.mapField(FieldKey.LOCATION) + " TEXT PRIMARY KEY, "
                    + SqlUtils.mapField(FieldKey.DATA_VALUE) + " TEXT NOT NULL)");
            try (var insert = connection.prepareStatement("INSERT INTO preservation_rows VALUES (?, ?)")) {
                insert.setString(1, FIRST);
                insert.setString(2, "original-value");
                insert.executeUpdate();
                insert.setString(1, NEIGHBOR);
                insert.setString(2, "neighbor-value");
                insert.executeUpdate();
            }
        }
        return url;
    }

    private Map<String, String> reopen(String url) throws Exception {
        var result = new HashMap<String, String>();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT * FROM preservation_rows")) {
            while (rows.next()) result.put(rows.getString(1), rows.getString(2));
        }
        return result;
    }

    private static RecordKey key(List<Pair<FieldKey, String>> conditions) {
        return new RecordKey(DataScope.BLOCK_DATA, Set.of(FieldKey.DATA_VALUE), conditions);
    }

    private static Pair<FieldKey, String> condition(String value) {
        return new Pair<>(FieldKey.LOCATION, value);
    }
}
