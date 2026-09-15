package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * One exact loaded machine candidate proposed by an addon-owned Doctor migration provider.
 *
 * <p>The opaque {@code stateClaim} is addon-defined. It must deterministically represent every
 * persistent or menu state element the addon needs to prove that this exact migration is still
 * safe. Doctor includes the claim in the execution fingerprint but never prints it to operators.</p>
 */
@SlimefunAPI
public record LegacyMachineMigrationCandidate(
        @Nonnull UUID worldId,
        int x,
        int y,
        int z,
        @Nonnull String sourceId,
        @Nonnull String targetId,
        @Nonnull String stateClaim) {

    public LegacyMachineMigrationCandidate {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(stateClaim, "stateClaim");
        if (sourceId.isBlank() || targetId.isBlank() || stateClaim.isBlank()) {
            throw new IllegalArgumentException("Legacy machine migration candidate fields must not be blank");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Legacy machine migration source and target IDs must differ");
        }
    }

    /** Stable location identity used for duplicate detection inside one Doctor plan. */
    public @Nonnull String locationKey() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
