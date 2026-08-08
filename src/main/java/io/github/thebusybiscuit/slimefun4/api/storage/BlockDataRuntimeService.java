package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/**
 * Read-only diagnostic facade for Slimefun block-data runtime state.
 *
 * <p>The service does not rewrite storage records, migrate schemas, or load/generate chunks when queried.
 */
@SlimefunAPI
public interface BlockDataRuntimeService {

    @Nonnull
    BlockDataRuntimeSnapshot getSnapshot();

    boolean isChunkAccessReady(@Nonnull Location location);
}
