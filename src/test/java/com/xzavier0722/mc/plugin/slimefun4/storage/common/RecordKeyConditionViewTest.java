package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import io.github.bakedlibs.dough.collections.Pair;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Preserve the backing list's single-threaded invalidation behavior, not a concurrency guarantee. */
class RecordKeyConditionViewTest {
    @Test
    void retainedSubviewRejectsStructuralChangeInItsBackingList() {
        var key = key();
        var view = key.getConditions().subList(0, 2);
        key.addCondition(FieldKey.DATA_KEY, "later");
        assertThrows(ConcurrentModificationException.class, view::size);
        assertThrows(ConcurrentModificationException.class, () -> view.get(0));
        assertThrows(ConcurrentModificationException.class, () -> view.listIterator(0));
    }

    @Test
    void nestedSubviewUsesTheRealBackingListModificationCount() {
        var key = key();
        var nested = key.getConditions().subList(0, 3).subList(1, 2);
        assertEquals(List.of(condition("second")), nested);
        key.addCondition(FieldKey.DATA_KEY, "later");
        assertThrows(ConcurrentModificationException.class, nested::size);
        assertThrows(ConcurrentModificationException.class, () -> nested.get(0));
    }

    @Test
    void evenAnEmptySubviewRetainsBackingListInvalidation() {
        var key = key();
        var empty = key.getConditions().subList(1, 1);
        assertTrue(empty.isEmpty());
        key.addCondition(FieldKey.DATA_KEY, "later");
        assertThrows(ConcurrentModificationException.class, empty::size);
    }

    @Test
    void iteratorAlreadyCreatedFromSubviewRetainsFailFastChecks() {
        var key = key();
        var iterator = key.getConditions().subList(1, 3).listIterator();
        assertEquals(condition("second"), iterator.next());
        key.addCondition(FieldKey.DATA_KEY, "later");
        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    void rootViewRemainsLiveAndFreshSubviewsWorkAfterSupportedAdditions() {
        var key = key();
        var root = key.getConditions();
        var old = root.subList(0, 1);
        key.addCondition(FieldKey.DATA_KEY, "later");
        assertEquals(4, root.size());
        assertEquals(List.of(condition("third"), condition("later")), root.subList(2, 4));
        assertThrows(ConcurrentModificationException.class, old::size);
    }

    @Test
    void nestedAndReverseExportsStayDetachedWithoutChangingOrder() {
        var key = key();
        var nested = key.getConditions().subList(0, 3).subList(1, 3);
        var exported = nested.reversed().getFirst();
        exported.setFirstValue(FieldKey.LOCATION);
        exported.setSecondValue("not-owned");
        nested.forEach(pair -> pair.setSecondValue("also-not-owned"));
        assertEquals(List.of(condition("second"), condition("third")), nested);
        assertEquals(List.of(condition("first"), condition("second"), condition("third")), key.getConditions());
    }

    @Test
    void subviewStructureRemainsUnmodifiableIncludingEmptyAndReverseViews() {
        var view = key().getConditions().subList(1, 3);
        assertThrows(UnsupportedOperationException.class, () -> view.add(condition("new")));
        assertThrows(UnsupportedOperationException.class, () -> view.set(0, condition("new")));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(0));
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertThrows(UnsupportedOperationException.class, () -> view.replaceAll(pair -> pair));
        assertThrows(UnsupportedOperationException.class, () -> view.sort((left, right) -> 0));
        assertThrows(UnsupportedOperationException.class, () -> view.subList(0, 0).clear());
        assertThrows(UnsupportedOperationException.class, () -> view.reversed().add(condition("new")));
    }

    @Test
    void subviewBoundsNullsAndDuplicateConditionsRemainUnchanged() {
        var key = new RecordKey(
                DataScope.BLOCK_DATA, Set.of(), Arrays.asList(null, condition("same"), condition("same")));
        var root = key.getConditions();
        assertEquals(Arrays.asList(null, condition("same")), root.subList(0, 2));
        assertEquals(List.of(condition("same"), condition("same")), root.subList(1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> root.subList(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> root.subList(0, 4));
        assertThrows(IllegalArgumentException.class, () -> root.subList(2, 1));
    }

    private static RecordKey key() {
        return new RecordKey(
                DataScope.BLOCK_DATA, Set.of(), List.of(condition("first"), condition("second"), condition("third")));
    }

    private static Pair<FieldKey, String> condition(String value) {
        return new Pair<>(FieldKey.DATA_KEY, value);
    }
}
