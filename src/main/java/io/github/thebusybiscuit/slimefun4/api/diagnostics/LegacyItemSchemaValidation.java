package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Immutable read-only result of addon-owned persistent-state validation for a legacy schema candidate. */
@SlimefunAPI
public final class LegacyItemSchemaValidation {

    private static final int MAX_MIGRATION_PAYLOAD_LENGTH = 2048;

    private final Status status;
    private final String detail;
    private final String migrationPayload;

    public LegacyItemSchemaValidation(@Nonnull Status status, @Nonnull String detail) {
        this(status, detail, null);
    }

    /**
     * Creates a validation result with an optional private addon-owned migration payload.
     *
     * <p>The payload is retained only in the short-lived in-memory migration plan and is never displayed or logged
     * by Slimefun core. Addons may use it to pass a validated stable identifier, such as a modern backing-record UUID,
     * to their later item migrator without performing another database lookup during the live repair traversal.</p>
     */
    public LegacyItemSchemaValidation(
            @Nonnull Status status, @Nonnull String detail, @Nullable String migrationPayload) {
        this.status = Objects.requireNonNull(status, "status");
        String text = Objects.requireNonNull(detail, "detail").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("detail cannot be blank");
        }
        this.detail = text;
        this.migrationPayload = normalizePayload(migrationPayload);
        if (status != Status.VERIFIED && this.migrationPayload != null) {
            throw new IllegalArgumentException("Only VERIFIED schema validation may carry a migration payload");
        }
    }

    public @Nonnull Status getStatus() {
        return status;
    }

    /** Non-sensitive operator-facing result. Never include opaque claim contents or personal identifiers. */
    public @Nonnull String getDetail() {
        return detail;
    }

    /**
     * Returns the private addon-owned payload for a future fingerprinted migration plan, when supplied.
     * Slimefun Doctor never prints or logs this value.
     */
    public @Nullable String getMigrationPayload() {
        return migrationPayload;
    }

    private static @Nullable String normalizePayload(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String payload = value.trim();
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("migrationPayload cannot be blank");
        }
        if (payload.length() > MAX_MIGRATION_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("migrationPayload exceeds " + MAX_MIGRATION_PAYLOAD_LENGTH + " characters");
        }
        return payload;
    }

    public enum Status {
        /** Persistent state matched the legacy claim and the addon can safely prepare a later migration plan. */
        VERIFIED,

        /** The referenced backing state does not exist. No automatic migration should be attempted. */
        BACKING_DATA_MISSING,

        /** Backing state exists but does not match the legacy claim. */
        STATE_MISMATCH,

        /** The addon recognized the state but requires manual intervention. */
        MANUAL_ONLY
    }
}
