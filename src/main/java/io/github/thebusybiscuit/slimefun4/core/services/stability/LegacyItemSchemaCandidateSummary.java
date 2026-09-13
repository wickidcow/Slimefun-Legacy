package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate.Readiness;
import java.util.Objects;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/** Immutable aggregate of one addon-owned legacy item schema candidate type found by Doctor. */
public final class LegacyItemSchemaCandidateSummary {

    private final String providerId;
    private final String migrationName;
    private final String candidateType;
    private final Readiness readiness;
    private final String detail;
    private final long count;

    LegacyItemSchemaCandidateSummary(
            @Nonnull String providerId,
            @Nonnull String migrationName,
            @Nonnull String candidateType,
            @Nonnull Readiness readiness,
            @Nonnull String detail,
            @Nonnegative long count) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.migrationName = Objects.requireNonNull(migrationName, "migrationName");
        this.candidateType = Objects.requireNonNull(candidateType, "candidateType");
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.detail = Objects.requireNonNull(detail, "detail");
        if (count < 0L) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        this.count = count;
    }

    public @Nonnull String getProviderId() {
        return providerId;
    }

    public @Nonnull String getMigrationName() {
        return migrationName;
    }

    public @Nonnull String getCandidateType() {
        return candidateType;
    }

    public @Nonnull Readiness getReadiness() {
        return readiness;
    }

    public @Nonnull String getDetail() {
        return detail;
    }

    public long getCount() {
        return count;
    }
}
