package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBlockIdMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBlockIdMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBlockIdMigrationService.ExecutionResult;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBlockIdMigrationService.ExecutionStatus;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Explicit fingerprint gate for storage-level legacy block-id replacement. */
final class DoctorBlockIdMigrationCommand {

    private final PersistedBlockIdMigrationService service = new PersistedBlockIdMigrationService();

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        String action = args.length > 4 ? args[4].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status", "plan", "list" -> sendStatus(sender);
            case "scan" -> scan(sender);
            case "execute", "repair" -> executePlan(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void scan(CommandSender sender) {
        var result = service.preparePlan();
        if (result.busy()) {
            send(sender, "&eSlimefun storage is currently busy. No persisted-ID plan was created; try again after pending writes drain.");
            return;
        }

        PersistedBlockIdMigrationPlan plan = result.plan();
        send(sender, "&6Slimefun Doctor Persisted Block-ID Plan");
        send(sender, "&7Persisted identity records scanned (block + universal): &e" + plan.getScannedRecords());
        send(sender, "&7Canonical/current IDs: &a" + plan.getCanonicalRecords()
                + " &8| &7rewrite candidates: &e" + plan.getRewriteCount());
        send(sender, "&7Unknown IDs preserved: &e" + plan.getUnknownRecords()
                + " &8| &7aliases with missing current targets: &c" + plan.getMissingTargetRecords());
        send(sender, "&7Candidates currently loaded and protected from in-place rewrite: &e" + result.loadedCandidates());
        if (plan.getRewriteCount() == 0) {
            send(sender, "&aNo verified persisted block-ID replacements are currently required.");
            return;
        }

        send(sender, "&7Fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Execute: &6/sf doctor migrations schemas blocks execute " + plan.getShortFingerprint());
        send(sender, "&7Plan expires after &e" + Math.max(1L, service.getPlanTtlMillis() / 60_000L) + " minute(s)&7 and is single-use.");
        send(sender, "&eLoaded candidates are skipped during execution; only uncached persisted records are rewritten.");
        send(sender, "&eMake an offline backup before executing persisted block-ID migration.");
    }

    private void sendStatus(CommandSender sender) {
        PersistedBlockIdMigrationPlan plan = service.getPreparedPlan().orElse(null);
        send(sender, "&6Slimefun Doctor Persisted Block-ID Migration");
        if (plan == null) {
            send(sender, "&7No active persisted block-ID plan exists.");
            send(sender, "&7Create one with &e/sf doctor migrations schemas blocks scan&7.");
            return;
        }
        long secondsLeft = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
        send(sender, "&7Candidates: &e" + plan.getRewriteCount()
                + " &8| &7expires: &e" + secondsLeft + "s"
                + " &8| &7fingerprint: &b" + plan.getShortFingerprint());
    }

    private void executePlan(CommandSender sender, String[] args) {
        if (args.length < 6 || args[5].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations schemas blocks execute <fingerprint>");
            return;
        }

        ExecutionResult result = service.execute(args[5]);
        if (result.status() == ExecutionStatus.NO_PLAN) {
            send(sender, "&cNo active persisted block-ID plan exists, or it expired. Run a fresh scan.");
            return;
        }
        if (result.status() == ExecutionStatus.BAD_FINGERPRINT) {
            send(sender, "&cPersisted block-ID migration fingerprint missing or incorrect. No data was changed.");
            return;
        }
        if (result.status() == ExecutionStatus.STORAGE_BUSY) {
            send(sender, "&eStorage became busy before execution. No rewrites were attempted; the single-use plan is consumed.");
            send(sender, "&7Run a fresh block-ID scan after pending storage work drains.");
            return;
        }
        if (result.status() == ExecutionStatus.FAILED) {
            var summary = result.summary();
            send(sender, "&cA storage write failed during block-ID migration.");
            send(sender, summary != null && summary.rollbackComplete()
                    ? "&aAlready-applied replacements were rolled back."
                    : "&cRollback could not be proven complete; restore the offline backup before continuing.");
            return;
        }

        var summary = result.summary();
        send(sender, "&6Slimefun Doctor Persisted Block-ID Execution Report");
        send(sender, "&7Rewritten: &a" + summary.rewritten()
                + " &8| &7stale since scan: &e" + summary.stale()
                + " &8| &7loaded/protected: &e" + summary.loaded()
                + " &8| &7missing since scan: &e" + summary.missing());
        if (summary.loaded() > 0) {
            send(sender, "&eLoaded records were intentionally untouched; run a fresh scan after they are no longer cached.");
        }
        if (summary.stale() > 0 || summary.missing() > 0) {
            send(sender, "&eChanged/missing records were rejected by the plan's expected-old-ID check.");
        }
        send(sender, "&7The execution fingerprint is consumed. Re-scan before any further persisted block-ID migration.");
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrations schemas blocks <status|scan|execute>");
        send(sender, "&7Scan is storage-level and read-only; it covers block and universal identities without loading chunks.");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
