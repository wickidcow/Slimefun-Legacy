package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.List;
import javax.annotation.Nonnull;

/** Immutable core summary for one fingerprint-authorized legacy block migration execution. */
public record LegacyBlockMigrationExecutionReport(
        long authorized,
        long revalidated,
        long migrated,
        long skippedChanged,
        long blocked,
        long failures,
        @Nonnull List<String> details) {

    public LegacyBlockMigrationExecutionReport {
        details = List.copyOf(details);
    }
}
