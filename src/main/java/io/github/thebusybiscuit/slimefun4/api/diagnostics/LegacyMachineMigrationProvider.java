package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Addon-owned migration boundary for legacy placed machines whose live state cannot be repaired by
 * a raw persisted-ID rewrite alone.
 *
 * <p>Slimefun Doctor fingerprints exact loaded locations and addon-owned state claims, then
 * revalidates each candidate immediately before allowing that one location to mutate.</p>
 *
 * <p>Implementations must never force-load chunks solely for migration. Scans must be read-only.
 * A migration must fail closed when state is ambiguous or changed, preserve all representable
 * persistent/menu state, and roll back any partial destructive mutation before reporting failure.</p>
 */
@SlimefunAPI
public interface LegacyMachineMigrationProvider {

    /** Human-readable migration name shown in Doctor output. */
    @Nonnull
    String getMigrationName();

    /**
     * Declares legacy machine IDs and their current registered replacements owned by this addon.
     * Each source must resolve through Slimefun's legacy-ID registry to the declared target.
     */
    @Nonnull
    Map<String, String> getLegacyMachineMappings();

    /**
     * Returns exact candidates from the provider's currently loaded supported scope.
     * This method must not mutate data or load chunks.
     */
    @Nonnull
    Collection<LegacyMachineMigrationCandidate> scanLoadedCandidates();

    /**
     * Revalidates the candidate's location, source/target identity and opaque state claim.
     * Return {@code false} on any uncertainty.
     */
    boolean isCandidateStillValid(@Nonnull LegacyMachineMigrationCandidate candidate);

    /**
     * Migrates exactly one previously fingerprinted and revalidated candidate.
     *
     * <p>The provider must independently fail closed if state changes between revalidation and
     * mutation. Any partial destructive change must be rolled back before returning FAILED.</p>
     */
    @Nonnull
    LegacyMachineMigrationResult migrate(@Nonnull LegacyMachineMigrationCandidate candidate);
}
