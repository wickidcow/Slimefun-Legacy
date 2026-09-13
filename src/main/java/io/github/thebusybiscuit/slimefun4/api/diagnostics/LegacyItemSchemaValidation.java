package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable read-only result of addon-owned persistent-state validation for a legacy schema candidate. */
@SlimefunAPI
public final class LegacyItemSchemaValidation {

    private final Status status;
    private final String detail;

    public LegacyItemSchemaValidation(@Nonnull Status status, @Nonnull String detail) {
        this.status = Objects.requireNonNull(status, "status");
        String text = Objects.requireNonNull(detail, "detail").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("detail cannot be blank");
        }
        this.detail = text;
    }

    public @Nonnull Status getStatus() {
        return status;
    }

    /** Non-sensitive operator-facing result. Never include opaque claim contents or personal identifiers. */
    public @Nonnull String getDetail() {
        return detail;
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
