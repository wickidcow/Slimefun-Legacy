package io.github.thebusybiscuit.slimefun4.api.world;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/**
 * Read-only lifecycle state for a chunk observed by Slimefun Legacy.
 *
 * <p>This state does not control whether Bukkit/Paper keeps the chunk loaded. It only describes what Slimefun has
 * observed through world/chunk lifecycle events.
 */
@SlimefunAPI
public enum ChunkRuntimeState {
    /** No lifecycle event has been observed for this chunk since Slimefun started. */
    UNTRACKED,

    /** A chunk load event is currently being processed. */
    LOADING,

    /** Slimefun observed the chunk complete its load event. */
    READY,

    /** A chunk unload event is currently being processed. */
    UNLOADING,

    /** Slimefun observed a failure while processing the chunk's storage-load path. */
    FAILED
}
