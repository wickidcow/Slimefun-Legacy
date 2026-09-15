package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.List;
import javax.annotation.Nonnull;

/** Immutable core summary for one fingerprint-authorized live-machine migration execution. */
public record LegacyMachineMigrationExecutionReport(
        long authorized,
        long revalidated,
        long migrated,
        long skippedChanged,
        long blocked,
        long failures,
        @Nonnull List<String> details) {

    public LegacyMachineMigrationExecutionReport {
        details = List.copyOf(details);
    }
}
