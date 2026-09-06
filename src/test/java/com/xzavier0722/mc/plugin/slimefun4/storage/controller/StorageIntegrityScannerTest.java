package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StorageIntegrityScannerTest {

    @Test
    void findsOnlySecondaryOwnersWithoutRootRecords() {
        Set<String> roots = Set.of("world;1:64:1", "world;2:64:2");
        Set<String> owners = Set.of("world;1:64:1", "world;3:64:3", "world;4:64:4");

        assertEquals(Set.of("world;3:64:3", "world;4:64:4"), StorageIntegrityScanner.findOrphans(owners, roots));
    }

    @Test
    void leavesInputSetsUnchanged() {
        Set<String> roots = Set.of("a", "b");
        Set<String> owners = Set.of("a", "c");

        StorageIntegrityScanner.findOrphans(owners, roots);

        assertEquals(Set.of("a", "b"), roots);
        assertEquals(Set.of("a", "c"), owners);
    }

    @Test
    void samplesAreSortedAndBounded() {
        Set<String> owners = Set.of("k", "j", "i", "h", "g", "f", "e", "d", "c", "b", "a");

        assertEquals(StorageIntegrityScanner.SAMPLE_LIMIT, StorageIntegrityScanner.sample(owners).size());
        assertEquals("a", StorageIntegrityScanner.sample(owners).get(0));
        assertEquals("j", StorageIntegrityScanner.sample(owners).get(9));
    }

    @Test
    void cleanOwnershipProducesNoCandidates() {
        assertTrue(StorageIntegrityScanner.findOrphans(Set.of("one", "two"), Set.of("one", "two", "three")).isEmpty());
    }
}
