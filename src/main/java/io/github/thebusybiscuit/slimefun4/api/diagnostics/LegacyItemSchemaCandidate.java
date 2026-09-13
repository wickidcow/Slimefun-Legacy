package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Read-only classification returned by a {@link LegacyItemSchemaProbe}.
 *
 * <p>A candidate identifies legacy state stored under an item ID that may still be perfectly valid today.
 * Finding a candidate never authorizes Slimefun core to mutate the item. The owning addon remains responsible
 * for any later validation and migration.</p>
 */
@SlimefunAPI
public final class LegacyItemSchemaCandidate {

    private final String candidateType;
    private final Readiness readiness;
    private final String detail;

    public LegacyItemSchemaCandidate(
            @Nonnull String candidateType, @Nonnull Readiness readiness, @Nonnull String detail) {
        this.candidateType = requireKey(candidateType);
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.detail = requireDetail(detail);
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

    public enum Readiness {
        /** The addon can recognize the legacy format without external lookups. */
        READY,

        /** The candidate requires database or other persistent-state verification before migration. */
        VALIDATION_REQUIRED,

        /** The format is known but should be reviewed or migrated manually. */
        MANUAL_ONLY
    }
}
