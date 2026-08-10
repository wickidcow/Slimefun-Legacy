package io.github.thebusybiscuit.slimefun4.core.services.runtime;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunDatabaseManager;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Internal storage-health adapter. It only reads existing controller/cache state. */
@SlimefunInternal
public final class DefaultStorageRuntimeService implements StorageRuntimeService {

    private final SlimefunDatabaseManager databaseManager;

    public DefaultStorageRuntimeService(@Nonnull SlimefunDatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public @Nonnull StorageRuntimeSnapshot getSnapshot() {
        try {
            BlockDataController blocks = databaseManager.getBlockDataController();
            String blockType = databaseManager.getBlockDataStorageType() == null
                    ? "UNINITIALIZED"
                    : databaseManager.getBlockDataStorageType().name();
            String profileType = databaseManager.getProfileStorageType() == null
                    ? "UNINITIALIZED"
                    : databaseManager.getProfileStorageType().name();
            int loadedChunks =
                    blocks == null ? 0 : blocks.getAllLoadedChunkData().size();
            int loadedUniversal =
                    blocks == null ? 0 : blocks.getAllLoadedUniversalData().size();
            boolean ready = blocks != null || databaseManager.getProfileDataController() != null;
            return new StorageRuntimeSnapshot(
                    ready,
                    databaseManager.wasPreviousShutdownClean(),
                    databaseManager.getPendingWriteTaskCount(),
                    blockType,
                    profileType,
                    loadedChunks,
                    loadedUniversal);
        } catch (RuntimeException | LinkageError failure) {
            return new StorageRuntimeSnapshot(
                    false, databaseManager.wasPreviousShutdownClean(), 0, "UNAVAILABLE", "UNAVAILABLE", 0, 0);
        }
    }
}
