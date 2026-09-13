package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Read-only classification returned by a {@link LegacyItemSchemaProbe}.
 *
 * <p>A candidate identifies legacy state stored under an item ID that may still be perfectly valid today.
 * Finding a candidate never authorizes Slimefun core to mutate the item. The owning addon remains responsible
 * for any later validation and migration.</p>
 */
@SlimefunAPI
public final class LegacyItemSchemaCandidate {

    private static final int MAX_VALIDATION_CLAIM_LENGTH = 2048;

    private final String candidateType;
    private final Readiness readiness;
    private final String detail;
    private final String validationClaim;

    public LegacyItemSchemaCandidate(
            @Nonnull String candidateType, @Nonnull Readiness readiness, @Nonnull String detail) {
        this(candidateType, readiness, detail, null);
    }

    public LegacyItemSchemaCandidate(
            @Nonnull String candidateType,
            @Nonnull Readiness readiness,
            @Nonnull String detail,
            @Nullable String validationClaim) {
        this.candidateType = requireKey(candidateType);
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.detail = requireDetail(detail);
        this.validationClaim = normalizeClaim(validationClaim);
        if (readiness == Readiness.VALIDATION_REQUIRED && this.validationClaim == null) {
            throw new IllegalArgumentException("VALIDATION_REQUIRED candidates must provide an opaque validation claim");
        }
    }

    /** Stable addon-owned key such as {@code legacy-dolly-backpack-binding}. */
    public @Nonnull String getCandidateType() {
        return candidateType;
    }

    /** Describes how much additional proof is required before the addon may safely migrate the state. */
    public @Nonnull Readiness getReadiness() {
        return readiness;
    }

    /** Non-sensitive operator-facing explanation of why this candidate was reported. */
    public @Nonnull String getDetail() {
        return detail;
    }

    /**
     * Returns an opaque addon-owned validation claim when persistent-state verification is required.
     *
     * <p>Slimefun core treats this only as an in-memory token and never displays or logs its contents.</p>
     */
    public @Nullable String getValidationClaim() {
        return validationClaim;
    }

    private static String requireKey(String value) {
        String key = Objects.requireNonNull(value, "candidateType").trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("candidateType cannot be blank");
        }
        if (!key.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("candidateType may contain only letters, digits, '.', '_' and '-'");
        }
        return key;
    }

    private static String requireDetail(String value) {
        String text = Objects.requireNonNull(value, "detail").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("detail cannot be blank");
        }
        return text;
    }

    private static @Nullable String normalizeClaim(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String claim = value.trim();
        if (claim.isEmpty()) {
            throw new IllegalArgumentException("validationClaim cannot be blank");
        }
        if (claim.length() > MAX_VALIDATION_CLAIM_LENGTH) {
            throw new IllegalArgumentException("validationClaim exceeds " + MAX_VALIDATION_CLAIM_LENGTH + " characters");
        }
        return claim;
    }

    public enum Readiness {
        /** The addon can recognize the legacy format without external lookups. */
        READY,

        /** The candidate requires database or other persistent-state verification before migration. */
        VALIDATION_REQUIRED,

        /** The format is known but should be reviewed or migrated manually. */
        MANUAL_ONLY
    }
}
