package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * One exact loaded placed-block candidate proposed by an addon-owned Doctor migration provider.
 *
 * <p>The opaque {@code stateClaim} is intentionally addon-defined. It must be deterministic for
 * all persistent state that the addon requires to prove this exact migration remains safe. Doctor
 * includes it in the execution fingerprint but never prints it to operators.</p>
 */
@SlimefunAPI
public record LegacyBlockMigrationCandidate(
        @Nonnull UUID worldId,
        int x,
        int y,
        int z,
        @Nonnull String sourceId,
        @Nonnull String targetId,
        @Nonnull String stateClaim) {

    public LegacyBlockMigrationCandidate {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(stateClaim, "stateClaim");
        if (sourceId.isBlank() || targetId.isBlank() || stateClaim.isBlank()) {
            throw new IllegalArgumentException("Legacy block migration candidate fields must not be blank");
        }
    }

    /** Stable location identity used for duplicate detection inside one Doctor plan. */
    public @Nonnull String locationKey() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
