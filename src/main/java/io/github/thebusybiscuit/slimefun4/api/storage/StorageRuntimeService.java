package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Read-only facade for storage health. It does not alter storage keys, schemas, or saved data. */
@SlimefunAPI
public interface StorageRuntimeService {

    @Nonnull
    StorageRuntimeSnapshot getSnapshot();
}
