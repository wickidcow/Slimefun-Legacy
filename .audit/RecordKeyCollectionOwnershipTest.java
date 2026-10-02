package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import io.github.bakedlibs.dough.collections.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** In-memory query-key ownership regressions; these are not database or world-upgrade tests. */
class RecordKeyCollectionOwnershipTest {
    @Test
    void defaultConstructorStillCreatesMutableOwnedCollections() {
        var key = new RecordKey(DataScope.CHUNK_DATA);
        key.addField(FieldKey.CHUNK);
        key.addCondition(FieldKey.CHUNK, "world;0:0");
        assertEquals(Set.of(FieldKey.CHUNK), key.getFields());
        assertEquals(List.of(condition("world;0:0")), key.getConditions());
    }

    @Test
    void immutableEmptyInputsCanBeExtendedThroughTheKeyApi() {
        var key = new RecordKey(DataScope.CHUNK_DATA, Set.of(), List.of());
        assertDoesNotThrow(() -> key.addField(FieldKey.CHUNK));
        assertDoesNotThrow(() -> key.addCondition(FieldKey.CHUNK, "world;0:0"));
        assertEquals(Set.of(FieldKey.CHUNK), key.getFields());
        assertEquals(List.of(condition("world;0:0")), key.getConditions());
    }

    @Test
    void externallyAddingToOriginallyEmptyFieldsDoesNotChangeTheKey() {
        var supplied = new HashSet<FieldKey>();
        var key = new RecordKey(DataScope.CHUNK_DATA, supplied);
        supplied.add(FieldKey.CHUNK);
        assertTrue(key.getFields().isEmpty());
    }

    @Test
    void externallyAddingToOriginallyEmptyConditionsDoesNotChangeTheKey() {
        var supplied = new ArrayList<Pair<FieldKey, String>>();
        var key = new RecordKey(DataScope.CHUNK_DATA, Set.of(), supplied);
        supplied.add(condition("different-world;4:5"));
        assertTrue(key.getConditions().isEmpty());
    }

    @Test
    void keyFieldMutationsDoNotModifyAnEmptyCallerCollection() {
        var supplied = new HashSet<FieldKey>();
        var key = new RecordKey(DataScope.CHUNK_DATA, supplied);
        key.addField(FieldKey.CHUNK);
        assertTrue(supplied.isEmpty());
    }

    @Test
    void keyConditionMutationsDoNotModifyAnEmptyCallerCollection() {
        var supplied = new ArrayList<Pair<FieldKey, String>>();
        var key = new RecordKey(DataScope.CHUNK_DATA, Set.of(), supplied);
        key.addCondition(FieldKey.CHUNK, "world;0:0");
        assertTrue(supplied.isEmpty());
    }

    @Test
    void nonemptyFieldInputsRetainTheirExistingDefensiveCopyBehavior() {
        var supplied = new HashSet<>(Set.of(FieldKey.CHUNK));
        var key = new RecordKey(DataScope.CHUNK_DATA, supplied);
        supplied.clear();
        assertEquals(Set.of(FieldKey.CHUNK), key.getFields());
    }

    @Test
    void nonemptyConditionInputsRetainTheirExistingOrderAndCopyBehavior() {
        var supplied = new ArrayList<>(List.of(condition("first"), condition("second")));
        var key = new RecordKey(DataScope.CHUNK_DATA, Set.of(), supplied);
        supplied.clear();
        assertEquals(List.of(condition("first"), condition("second")), key.getConditions());
    }

    @Test
    void twoKeysBuiltFromSharedEmptyInputsRemainIndependent() {
        var fields = new HashSet<FieldKey>();
        var conditions = new ArrayList<Pair<FieldKey, String>>();
        var first = new RecordKey(DataScope.CHUNK_DATA, fields, conditions);
        var second = new RecordKey(DataScope.CHUNK_DATA, fields, conditions);
        first.addField(FieldKey.CHUNK);
        first.addCondition(FieldKey.CHUNK, "only-first");
        assertTrue(second.getFields().isEmpty());
        assertTrue(second.getConditions().isEmpty());
        assertNotEquals(first, second);
    }

    @Test
    void cachedKeyIdentityIsUnaffectedByLaterCallerCollectionChanges() {
        var fields = new HashSet<FieldKey>();
        var conditions = new ArrayList<Pair<FieldKey, String>>();
        var key = new RecordKey(DataScope.CHUNK_DATA, fields, conditions);
        var expected = new RecordKey(DataScope.CHUNK_DATA);
        int originalHash = key.hashCode();
        String originalText = key.toString();
        fields.add(FieldKey.CHUNK);
        conditions.add(condition("not-this-record"));
        assertEquals(expected, key);
        assertEquals(originalHash, key.hashCode());
        assertEquals(originalText, key.toString());
    }

    @Test
    void externallyExposedViewsRemainUnmodifiable() {
        var key = new RecordKey(DataScope.CHUNK_DATA);
        assertThrows(UnsupportedOperationException.class, () -> key.getFields().add(FieldKey.CHUNK));
        assertThrows(UnsupportedOperationException.class, () -> key.getConditions().add(condition("forbidden")));
    }

    @Test
    void equivalentValidConstructionRetainsEqualsHashAndText() {
        var first = new RecordKey(DataScope.CHUNK_DATA);
        first.addField(FieldKey.CHUNK);
        first.addCondition(FieldKey.CHUNK, "world;0:0");
        var second = new RecordKey(DataScope.CHUNK_DATA, Set.of(FieldKey.CHUNK), List.of(condition("world;0:0")));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.toString(), second.toString());
    }

    @Test
    void repeatedConstructionDoesNotAccumulateOtherRecordsConditions() {
        var fields = new HashSet<FieldKey>();
        var conditions = new ArrayList<Pair<FieldKey, String>>();
        for (int index = 0; index < 1000; index++) {
            var key = new RecordKey(DataScope.CHUNK_DATA, fields, conditions);
            key.addField(FieldKey.CHUNK);
            key.addCondition(FieldKey.CHUNK, "world;" + index + ":0");
            assertEquals(Set.of(FieldKey.CHUNK), key.getFields());
            assertEquals(List.of(condition("world;" + index + ":0")), key.getConditions());
            assertTrue(fields.isEmpty());
            assertTrue(conditions.isEmpty());
        }
    }

    private static Pair<FieldKey, String> condition(String value) {
        return new Pair<>(FieldKey.CHUNK, value);
    }
}
