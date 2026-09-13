package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/** Runs the optional read-only persistent-state validation phase after Doctor item discovery. */
public final class LegacyItemSchemaValidationRunner {

    private LegacyItemSchemaValidationRunner() {}

    @Nonnull
    public static CompletionStage<Void> validate(@Nonnull ItemDoctorReport report) {
        LegacyItemSchemaProbeService.Session session = report.getSchemaProbeSession();
        return session == null ? CompletableFuture.completedFuture(null) : session.validatePending(report);
    }
}
