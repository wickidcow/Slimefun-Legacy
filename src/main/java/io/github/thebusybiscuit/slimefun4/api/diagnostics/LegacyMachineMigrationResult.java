package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable result for one exact legacy live-machine migration attempt. */
@SlimefunAPI
public record LegacyMachineMigrationResult(@Nonnull Status status, @Nonnull String detail) {

    public LegacyMachineMigrationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    public static @Nonnull LegacyMachineMigrationResult migrated(@Nonnull String detail) {
        return new LegacyMachineMigrationResult(Status.MIGRATED, detail);
    }

    public static @Nonnull LegacyMachineMigrationResult skipped(@Nonnull String detail) {
        return new LegacyMachineMigrationResult(Status.SKIPPED_CHANGED, detail);
    }

    public static @Nonnull LegacyMachineMigrationResult blocked(@Nonnull String detail) {
        return new LegacyMachineMigrationResult(Status.BLOCKED, detail);
    }

    public static @Nonnull LegacyMachineMigrationResult failed(@Nonnull String detail) {
        return new LegacyMachineMigrationResult(Status.FAILED, detail);
    }

    public enum Status {
        MIGRATED,
        SKIPPED_CHANGED,
        BLOCKED,
        FAILED
    }
}
