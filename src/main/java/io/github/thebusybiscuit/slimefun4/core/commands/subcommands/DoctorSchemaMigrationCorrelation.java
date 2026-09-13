package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate.Readiness;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidation.Status;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaCandidateSummary;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaValidationSummary;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Formats addon-owned same-ID legacy schema candidates discovered by a migration-aware Doctor scan. */
final class DoctorSchemaMigrationCorrelation {

    private static final int MAX_DETAIL_LINES = 30;

    private DoctorSchemaMigrationCorrelation() {}

    static void send(@Nonnull CommandSender sender, @Nonnull ItemDoctorReport report) {
        List<LegacyItemSchemaCandidateSummary> summaries = report.getSchemaMigrationCandidateSummaries();
        List<LegacyItemSchemaValidationSummary> validations = report.getSchemaValidationSummaries();
        send(sender, "&6Slimefun Legacy Schema Correlation");
        send(sender, "&7Same-ID legacy schema candidates: &e" + report.getSchemaMigrationCandidates()
                + " &8| &7candidate groups: &e" + summaries.size());

        if (summaries.isEmpty()) {
            send(sender, "&aNo addon schema probe identified legacy metadata under a current Slimefun item ID.");
            send(sender, "&8Read-only probe pass; no item or persistent state was changed.");
            return;
        }

        long ready = 0L;
        long validationRequired = 0L;
        long manualOnly = 0L;
        int shown = 0;
        for (LegacyItemSchemaCandidateSummary summary : summaries) {
            switch (summary.getReadiness()) {
                case READY -> ready += summary.getCount();
                case VALIDATION_REQUIRED -> validationRequired += summary.getCount();
                case MANUAL_ONLY -> manualOnly += summary.getCount();
            }
            if (shown < MAX_DETAIL_LINES) {
                send(sender, "&8- " + readinessColor(summary.getReadiness()) + '[' + summary.getReadiness().name() + "] &f"
                        + summary.getProviderId() + "&8/" + summary.getCandidateType() + " &8x&e" + summary.getCount());
                send(sender, "&8  &7" + summary.getMigrationName() + ": " + summary.getDetail());
                shown++;
            }
        }

        send(sender, "&7Schema classification: ready &a" + ready
                + " &8| &7validation required &e" + validationRequired
                + " &8| &7manual only &c" + manualOnly);
        if (summaries.size() > shown) {
            send(sender, "&8... " + (summaries.size() - shown) + " more schema candidate group(s)");
        }

        if (!validations.isEmpty()) {
            long verified = 0L;
            long missing = 0L;
            long mismatch = 0L;
            long validationManual = 0L;
            int validationShown = 0;
            for (LegacyItemSchemaValidationSummary validation : validations) {
                switch (validation.getStatus()) {
                    case VERIFIED -> verified += validation.getCount();
                    case BACKING_DATA_MISSING -> missing += validation.getCount();
                    case STATE_MISMATCH -> mismatch += validation.getCount();
                    case MANUAL_ONLY -> validationManual += validation.getCount();
                }
                if (validationShown < MAX_DETAIL_LINES) {
                    send(sender, "&8- " + statusColor(validation.getStatus()) + "[VALIDATION "
                            + validation.getStatus().name() + "] &f" + validation.getProviderId() + "&8/"
                            + validation.getCandidateType() + " &8x&e" + validation.getCount());
                    send(sender, "&8  &7" + validation.getDetail());
                    validationShown++;
                }
            }
            send(sender, "&7Persistent-state validation: verified &a" + verified
                    + " &8| &7missing &c" + missing
                    + " &8| &7mismatch &c" + mismatch
                    + " &8| &7manual &e" + validationManual);
            send(sender, "&8Opaque validation claims are never displayed by Slimefun Doctor.");
        }

        send(sender, "&eSchema discovery and validation do not authorize core mutation; repair remains addon-owned.");
        send(sender, "&8Read-only probe/validation pass; no item or persistent state was changed.");
    }

    private static String readinessColor(Readiness readiness) {
        return switch (readiness) {
            case READY -> "&a";
            case VALIDATION_REQUIRED -> "&e";
            case MANUAL_ONLY -> "&c";
        };
    }

    private static String statusColor(Status status) {
        return switch (status) {
            case VERIFIED -> "&a";
            case BACKING_DATA_MISSING, STATE_MISMATCH -> "&c";
            case MANUAL_ONLY -> "&e";
        };
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
