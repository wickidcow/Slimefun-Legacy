package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import java.util.Arrays;
import org.bukkit.Chunk;
import org.junit.jupiter.api.Test;

class ChunkStorageIdentityTest {
    @Test
    void storageContainersDoNotRetainBukkitChunkInstances() {
        assertFalse(hasChunkField(ChunkKey.class));
        assertFalse(hasChunkField(SlimefunChunkData.class));
    }

    @Test
    void preservesLegacyChunkApiDescriptors() throws Exception {
        assertSame(Chunk.class, SlimefunChunkData.class.getMethod("getChunk").getReturnType());
        assertSame(
                ChunkKey.class,
                ChunkKey.class.getConstructor(DataScope.class, Chunk.class).getDeclaringClass());
    }

    @Test
    void stringBackedChunkKeysKeepStableIdentity() {
        var first = new ChunkKey(DataScope.NONE, "world;12:-4");
        var same = new ChunkKey(DataScope.NONE, "world;12:-4");
        var otherChunk = new ChunkKey(DataScope.NONE, "world;12:-3");
        var otherScope = new ChunkKey(DataScope.CHUNK_DATA, "world;12:-4");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, otherChunk);
        assertNotEquals(first, otherScope);
    }

    private static boolean hasChunkField(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> Chunk.class.isAssignableFrom(field.getType()));
    }
}
