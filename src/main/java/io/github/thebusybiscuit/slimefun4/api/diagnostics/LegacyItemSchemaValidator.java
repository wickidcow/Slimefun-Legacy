package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Optional addon-owned validator for legacy item schema candidates that require persistent-state verification.
 *
 * <p>Validators are invoked only after an operator-triggered migration-aware Doctor traversal has completed its
 * item discovery phase. A validator is only allowed to consume claims produced by a schema probe registered by
 * the same Bukkit plugin. Validation may perform asynchronous database reads, but must remain read-only: it must
 * not mutate ItemStacks, databases, worlds, chunks or player state.</p>
 */
@SlimefunAPI
public interface LegacyItemSchemaValidator {

    /** Candidate types understood by this validator. Wildcards are intentionally unsupported. */
    @Nonnull
    Set<String> getSupportedCandidateTypes();

    /**
     * Validates one opaque addon-owned claim asynchronously.
     *
     * <p>The claim is never displayed by Slimefun Doctor and must not be logged by core.</p>
     */
    @Nonnull
    CompletionStage<LegacyItemSchemaValidation> validateCandidate(
            @Nonnull String candidateType, @Nonnull String validationClaim);
}
