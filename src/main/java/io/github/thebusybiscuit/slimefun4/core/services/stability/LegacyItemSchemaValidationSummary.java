package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidation.Status;
import java.util.Objects;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/** Immutable aggregate of addon-owned persistent-state validation results from one Doctor scan. */
public final class LegacyItemSchemaValidationSummary {

    private final String providerId;
    private final String migrationName;
    private final String slimefunId;
    private final String candidateType;
    private final Status status;
    private final String detail;
    private final long count;

    LegacyItemSchemaValidationSummary(
            @Nonnull String providerId,
            @Nonnull String migrationName,
            @Nonnull String slimefunId,
            @Nonnull String candidateType,
            @Nonnull Status status,
            @Nonnull String detail,
            @Nonnegative long count) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.migrationName = Objects.requireNonNull(migrationName, "migrationName");
        this.slimefunId = Objects.requireNonNull(slimefunId, "slimefunId");
        this.candidateType = Objects.requireNonNull(candidateType, "candidateType");
        this.status = Objects.requireNonNull(status, "status");
        this.detail = Objects.requireNonNull(detail, "detail");
        if (count < 0L) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        this.count = count;
    }

    public @Nonnull String getProviderId() { return providerId; }
    public @Nonnull String getMigrationName() { return migrationName; }
    public @Nonnull String getSlimefunId() { return slimefunId; }
    public @Nonnull String getCandidateType() { return candidateType; }
    public @Nonnull Status getStatus() { return status; }
    public @Nonnull String getDetail() { return detail; }
    public long getCount() { return count; }
}
