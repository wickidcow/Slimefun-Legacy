package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkScopeMatcherTest {

    @Test
    void matchesNormalizedChunkScope() {
        assertTrue(ChunkScopeMatcher.matches(new ChunkKey(DataScope.NONE, "world;12:-4"), "world;12:-4"));
        assertFalse(ChunkScopeMatcher.matches(new ChunkKey(DataScope.NONE, "world;12:-3"), "world;12:-4"));
    }

    @Test
    void mapsPositiveLocationScopeToOwningChunk() {
        assertTrue(ChunkScopeMatcher.matches(new LocationKey(DataScope.NONE, "world;192:64:-64"), "world;12:-4"));
        assertFalse(ChunkScopeMatcher.matches(new LocationKey(DataScope.NONE, "world;208:64:-64"), "world;12:-4"));
    }

    @Test
    void mapsNegativeBlockCoordinatesUsingChunkFloorSemantics() {
        assertTrue(ChunkScopeMatcher.matches(new LocationKey(DataScope.NONE, "world;-1:64:-1"), "world;-1:-1"));
        assertTrue(ChunkScopeMatcher.matches(new LocationKey(DataScope.NONE, "world;-16:64:-16"), "world;-1:-1"));
        assertTrue(ChunkScopeMatcher.matches(new LocationKey(DataScope.NONE, "world;-17:64:-17"), "world;-2:-2"));
    }

    @Test
    void matchesRecordKeyWithChunkCondition() {
        var record = new RecordKey(DataScope.CHUNK_DATA);
        record.addCondition(FieldKey.CHUNK, "world;12:-4");
        record.addCondition(FieldKey.DATA_KEY, "owner");

        assertTrue(ChunkScopeMatcher.matches(record, "world;12:-4"));
        assertFalse(ChunkScopeMatcher.matches(record, "world;12:-3"));
    }

    @Test
    void matchesRecordKeyWithLocationCondition() {
        var record = new RecordKey(DataScope.BLOCK_DATA);
        record.addCondition(FieldKey.LOCATION, "world;207:64:-49");
        record.addCondition(FieldKey.DATA_KEY, "progress");

        assertTrue(ChunkScopeMatcher.matches(record, "world;12:-4"));
        assertFalse(ChunkScopeMatcher.matches(record, "world;13:-4"));
    }

    @Test
    void ignoresUnrelatedStorageScopes() {
        assertFalse(ChunkScopeMatcher.matches(
                new UUIDKey(DataScope.NONE, UUID.fromString("00000000-0000-0000-0000-000000000001")),
                "world;0:0"));

        var record = new RecordKey(DataScope.UNIVERSAL_DATA);
        record.addCondition(FieldKey.UNIVERSAL_UUID, "00000000-0000-0000-0000-000000000001");
        assertFalse(ChunkScopeMatcher.matches(record, "world;0:0"));
    }

    @Test
    void malformedLocationIdentityFailsClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChunkScopeMatcher.matches(new LocationKey(DataScope.NONE, "world;bad"), "world;0:0"));
        assertThrows(IllegalArgumentException.class, () -> ChunkScopeMatcher.chunkKeyFromLocation("world;1:x:2"));
    }
}
