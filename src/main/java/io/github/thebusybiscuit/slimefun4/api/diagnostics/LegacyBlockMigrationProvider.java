package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Addon-owned migration boundary for exact legacy placed Slimefun blocks and machines.
 *
 * <p>Unlike the older broad {@link LegacyItemMigrationProvider}, this API is candidate-based.
 * Slimefun Doctor fingerprints exact loaded locations and addon-owned state claims, then
 * revalidates each candidate immediately before allowing that one location to mutate.</p>
 *
 * <p>Implementations must never force-load chunks solely for migration. Scans must be read-only.
 * A migration must fail closed when state is ambiguous or changed, preserve all representable
 * persistent/menu state, and roll back any partial mutation when the target cannot be created or
 * restored losslessly.</p>
 */
@SlimefunAPI
public interface LegacyBlockMigrationProvider {

    /** Human-readable migration name shown in Doctor output. */
    @Nonnull
    String getMigrationName();

    /**
     * Declares the legacy block IDs and their current registered replacements owned by this addon.
     * These mappings must agree with Slimefun's legacy-ID registry.
     */
    @Nonnull
    Map<String, String> getLegacyBlockMappings();

    /**
     * Returns exact candidates from the provider's currently loaded supported scope.
     * This method must not mutate data or load chunks.
     */
    @Nonnull
    Collection<LegacyBlockMigrationCandidate> scanLoadedCandidates();

    /**
     * Revalidates the candidate's location, source/target identity and opaque state claim.
     * Return {@code false} on any uncertainty.
     */
    boolean isCandidateStillValid(@Nonnull LegacyBlockMigrationCandidate candidate);

    /**
     * Migrates exactly one previously fingerprinted and revalidated candidate.
     *
     * <p>The provider must independently fail closed if state changed between revalidation and
     * mutation. Any partial destructive change must be rolled back before returning FAILED.</p>
     */
    @Nonnull
    LegacyBlockMigrationResult migrate(@Nonnull LegacyBlockMigrationCandidate candidate);
}
