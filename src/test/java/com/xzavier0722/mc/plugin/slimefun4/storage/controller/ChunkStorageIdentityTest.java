package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
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

    @Test
    void schedulerAnchorUsesChunkIdentityWithoutBlockLookup() {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, args) -> {
                    throw new AssertionError("Unexpected world access: " + method.getName());
                });
        Chunk chunk = (Chunk) Proxy.newProxyInstance(
                Chunk.class.getClassLoader(),
                new Class<?>[] {Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> 12;
                    case "getZ" -> -4;
                    case "getBlock" -> throw new AssertionError("Scheduler anchor must not read a block");
                    default -> throw new AssertionError("Unexpected chunk access: " + method.getName());
                });

        Location anchor = BlockDataController.chunkSchedulerAnchor(chunk);

        assertSame(world, anchor.getWorld());
        assertEquals(192, anchor.getBlockX());
        assertEquals(0, anchor.getBlockY());
        assertEquals(-64, anchor.getBlockZ());
    }

    private static boolean hasChunkField(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> Chunk.class.isAssignableFrom(field.getType()));
    }
}
