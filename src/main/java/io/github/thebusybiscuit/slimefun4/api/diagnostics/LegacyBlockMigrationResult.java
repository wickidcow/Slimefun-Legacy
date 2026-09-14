package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable result for one exact legacy placed-block migration attempt. */
@SlimefunAPI
public record LegacyBlockMigrationResult(@Nonnull Status status, @Nonnull String detail) {

    public LegacyBlockMigrationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    public static @Nonnull LegacyBlockMigrationResult migrated(@Nonnull String detail) {
        return new LegacyBlockMigrationResult(Status.MIGRATED, detail);
    }

    public static @Nonnull LegacyBlockMigrationResult skipped(@Nonnull String detail) {
        return new LegacyBlockMigrationResult(Status.SKIPPED_CHANGED, detail);
    }

    public static @Nonnull LegacyBlockMigrationResult blocked(@Nonnull String detail) {
        return new LegacyBlockMigrationResult(Status.BLOCKED, detail);
    }

    public static @Nonnull LegacyBlockMigrationResult failed(@Nonnull String detail) {
        return new LegacyBlockMigrationResult(Status.FAILED, detail);
    }

    public enum Status {
        MIGRATED,
        SKIPPED_CHANGED,
        BLOCKED,
        FAILED
    }
}
