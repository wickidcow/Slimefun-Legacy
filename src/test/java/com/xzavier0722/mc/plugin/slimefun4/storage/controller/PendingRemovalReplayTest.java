package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Real container mutations with recorded persistence submissions; not a database or region-safety fixture. */
class PendingRemovalReplayTest {
    @Test
    void cancellationSubmitsDeletionEvenWhenTheCacheIsAlreadyEmpty() {
        var data = seeded();
        data.setPendingRemove(true);
        data.removeData("recipe");
        assertNull(data.getData("recipe"));
        assertTrue(data.updates.isEmpty());
        assertEquals("old-recipe", data.submitted.get("recipe"));

        data.setPendingRemove(false);

        assertEquals(List.of("recipe"), data.updates, "Cancellation must submit the pending deletion");
        assertFalse(data.submitted.containsKey("recipe"));
        assertEquals(data.getAllData(), data.submitted);
        assertFalse(data.isPendingRemove());
    }

    @Test
    void pendingWritesStillCoalesceToTheLastValue() {
        var data = seeded();
        data.setPendingRemove(true);
        data.setData("recipe", "first");
        data.setData("recipe", "last");
        assertTrue(data.updates.isEmpty());
        data.setPendingRemove(false);
        assertEquals(List.of("recipe"), data.updates);
        assertEquals("last", data.submitted.get("recipe"));
    }

    @Test
    void writeThenDeleteDoesNotForgetTheFinalDeletion() {
        var data = seeded();
        data.setPendingRemove(true);
        data.setData("recipe", "temporary");
        data.removeData("recipe");
        data.setPendingRemove(false);
        assertEquals(List.of("recipe"), data.updates);
        assertFalse(data.submitted.containsKey("recipe"));
    }

    @Test
    void deleteThenWritePreservesTheFinalReplacement() {
        var data = seeded();
        data.setPendingRemove(true);
        data.removeData("recipe");
        data.setData("recipe", "replacement");
        data.setPendingRemove(false);
        assertEquals(List.of("recipe"), data.updates);
        assertEquals("replacement", data.submitted.get("recipe"));
    }

    @Test
    void repeatedDeletionIsSubmittedOnlyOnce() {
        var data = seeded();
        data.setPendingRemove(true);
        data.removeData("recipe");
        data.removeData("recipe");
        data.removeData("recipe");
        data.setPendingRemove(false);
        assertEquals(List.of("recipe"), data.updates);
    }

    @Test
    void repeatedCancellationDoesNotReplayAnOldRemoval() {
        var data = seeded();
        data.setPendingRemove(true);
        data.removeData("recipe");
        data.setPendingRemove(false);
        assertEquals(List.of("recipe"), data.updates);
        data.setData("recipe", "new-active-value");
        data.updates.clear();
        data.setPendingRemove(false);
        data.setPendingRemove(true);
        data.setPendingRemove(false);
        assertTrue(data.updates.isEmpty());
        assertEquals("new-active-value", data.submitted.get("recipe"));
    }

    @Test
    void completedRemovalNeverSubmitsCapturedChanges() {
        var data = seeded();
        data.setPendingRemove(true);
        data.removeData("recipe");
        data.setData("temporary", "not-persisted");
        data.setPendingRemove(true);
        assertTrue(data.updates.isEmpty());
        assertEquals(Map.of("recipe", "old-recipe", "owner", "owner-uuid"), data.submitted);
    }

    @Test
    void missingLoadedKeyKeepsTheExistingNoOpBehavior() {
        var data = seeded();
        data.setPendingRemove(true);
        data.removeData("absent");
        data.setPendingRemove(false);
        assertTrue(data.updates.isEmpty());
        assertEquals(data.getAllData(), data.submitted);
    }

    @Test
    void deletionRecordedBeforeLoadIsNotUndoneByLoadedData() {
        var data = new RecordingContainer("world;10:64:-20", "OLD_MACHINE");
        data.setIsDataLoaded(false);
        data.setPendingRemove(true);
        data.removeData("recipe");
        // Reproduce a completed load filling the cache after the deletion was captured.
        data.setCacheInternal("recipe", "old-recipe", false);
        data.submitted.put("recipe", "old-recipe");
        data.setIsDataLoaded(true);
        data.setPendingRemove(false);
        assertNull(data.getData("recipe"));
        assertEquals(List.of("recipe"), data.updates);
        assertFalse(data.submitted.containsKey("recipe"));
    }

    @Test
    void emptyAndOpaqueStringsAreNotDeletionMarkers() {
        var data = seeded();
        String payload = "  {\"id\":\"UNREGISTERED_OLD_ID\",\"count\":\"9007199254740993\"}\n";
        data.setPendingRemove(true);
        data.setData("empty", "");
        data.setData("payload", payload);
        data.removeData("recipe");
        data.setPendingRemove(false);
        assertEquals("", data.submitted.get("empty"));
        assertEquals(payload, data.submitted.get("payload"));
        assertEquals("owner-uuid", data.submitted.get("owner"));
        assertEquals("OLD_MACHINE", data.getSfId());
        assertEquals("world;10:64:-20", data.getKey());
        assertEquals(Set.of("empty", "payload", "recipe"), new HashSet<>(data.updates));
    }

    @Test
    void sameLocationDoesNotMixDifferentContainerInstances() {
        var first = seeded();
        var second = seeded();
        first.setPendingRemove(true);
        second.setPendingRemove(true);
        first.removeData("recipe");
        second.setData("recipe", "other-container");
        first.setPendingRemove(false);
        assertFalse(first.submitted.containsKey("recipe"));
        assertTrue(second.updates.isEmpty());
        second.setPendingRemove(false);
        assertEquals("other-container", second.submitted.get("recipe"));
        assertFalse(first.submitted.containsKey("recipe"));
    }

    @Test
    void activeMutationsKeepTheirExistingSchedulingBehavior() {
        var data = seeded();
        data.removeData("recipe");
        data.removeData("recipe");
        data.setData("recipe", "active");
        assertEquals(List.of("recipe", "recipe"), data.updates);
        assertEquals("active", data.submitted.get("recipe"));
    }

    @Test
    void newThenDeletedKeyDoesNotLeaveAPendingWrite() {
        var data = seeded();
        data.setPendingRemove(true);
        data.setData("new", "temporary");
        data.removeData("new");
        data.setPendingRemove(false);
        assertFalse(data.submitted.containsKey("new"));
        assertEquals(data.getAllData(), data.submitted);
        assertEquals(List.of("new"), data.updates);
    }

    @Test
    void randomizedSequentialChangesMatchTheFinalCacheAfterCancellation() {
        var random = new Random(20261001L);
        for (int sample = 0; sample < 1000; sample++) {
            var data = seeded();
            data.setPendingRemove(true);
            var touched = new HashSet<String>();
            for (int step = 0; step < 80; step++) {
                String key = "field-" + random.nextInt(8);
                if (random.nextBoolean()) {
                    data.setData(key, "opaque-value-" + random.nextLong());
                    touched.add(key);
                } else {
                    if (data.getData(key) != null) touched.add(key);
                    data.removeData(key);
                }
            }
            assertTrue(data.updates.isEmpty());
            data.setPendingRemove(false);
            assertEquals(data.getAllData(), data.submitted, "sample " + sample);
            assertEquals(touched, new HashSet<>(data.updates));
            assertEquals(touched.size(), data.updates.size());
        }
    }

    private static RecordingContainer seeded() {
        var data = new RecordingContainer("world;10:64:-20", "OLD_MACHINE");
        data.setData("recipe", "old-recipe");
        data.setData("owner", "owner-uuid");
        data.updates.clear();
        return data;
    }

    private static final class RecordingContainer extends ASlimefunDataContainer {
        private final List<String> updates = new ArrayList<>();
        private final Map<String, String> submitted = new HashMap<>();

        RecordingContainer(String key, String itemId) {
            super(key, itemId);
            setIsDataLoaded(true);
        }

        @Override
        public void scheduleUpdateData(String key) {
            updates.add(key);
            String value = getCacheInternal(key);
            if (value == null) submitted.remove(key);
            else submitted.put(key, value);
        }
    }
}
