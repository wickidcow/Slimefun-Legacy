package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import java.util.Objects;
import org.bukkit.Chunk;

public class ChunkKey extends ScopeKey {
    private final String chunkKey;

    public ChunkKey(DataScope scope, Chunk chunk) {
        this(scope, LocationUtils.getChunkKey(chunk));
    }

    ChunkKey(DataScope scope, String chunkKey) {
        super(scope);
        this.chunkKey = Objects.requireNonNull(chunkKey, "Chunk key must not be null");
    }

    @Override
    protected String getKeyStr() {
        return scope + "/" + chunkKey;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof ChunkKey other && scope == other.scope && chunkKey.equals(other.chunkKey));
    }
}
