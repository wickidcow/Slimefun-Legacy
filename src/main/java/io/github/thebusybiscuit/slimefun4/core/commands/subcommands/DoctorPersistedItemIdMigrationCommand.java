package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemIdMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemIdMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemIdMigrationService.ExecutionResult;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemIdMigrationService.ExecutionStatus;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Fingerprint gate for legacy Slimefun item IDs stored in unloaded machine inventory rows. */
final class DoctorPersistedItemIdMigrationCommand {

    private final PersistedItemIdMigrationService service = new PersistedItemIdMigrationService();

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        String action = args.length > 5 ? args[5].toLowerCase(Locale.ROOT) : "status";
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
            send(sender, "&eMachine storage is currently busy. No persisted Item-ID plan was created; try again after pending reads/writes drain.");
            return;
        }

        PersistedItemIdMigrationPlan plan = result.plan();
        send(sender, "&6Slimefun Doctor Persisted Item-ID Plan");
        send(sender, "&7Unloaded machine inventory rows scanned: &e" + plan.getScannedRecords());
        send(sender, "&7Canonical Slimefun IDs: &a" + plan.getCanonicalRecords()
                + " &8| &7non-Slimefun rows: &f" + plan.getNonSlimefunRecords());
        send(sender, "&7Declared legacy-ID rewrites: &e" + plan.getRewriteCount()
                + " &8| &7unknown/unmapped IDs: &c" + plan.getUnknownIdRecords());
        send(sender, "&7Missing canonical targets: &c" + plan.getMissingTargetRecords()
                + " &8| &7unreadable/unsafe rows: &c" + plan.getUnreadableRecords());

        if (plan.getUnknownIdRecords() > 0L || plan.getMissingTargetRecords() > 0L || plan.getUnreadableRecords() > 0L) {
            send(sender, "&eUnresolved rows are preserved unchanged. Doctor never guesses an Item ID from names, lore or historical hints.");
        }
        if (plan.getRewriteCount() == 0) {
            send(sender, "&aNo registered legacy Slimefun Item IDs require rewriting in currently unloaded machine storage.");
            return;
        }

        send(sender, "&7Fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Execute: &6/sf doctor migrations schemas storage ids execute " + plan.getShortFingerprint());
        send(sender, "&7Plan expires after &e" + Math.max(1L, service.getPlanTtlMillis() / 60_000L) + " minute(s)&7 and is single-use.");
        send(sender, "&eLoaded machines are intentionally excluded. Unload their chunks and re-scan to sweep their persisted rows.");
        send(sender, "&eMake an offline backup before executing persisted Item-ID migration.");
    }

    private void sendStatus(CommandSender sender) {
        PersistedItemIdMigrationPlan plan = service.getPreparedPlan().orElse(null);
        send(sender, "&6Slimefun Doctor Persisted Item-ID Migration");
        if (plan == null) {
            send(sender, "&7No active persisted Item-ID plan exists.");
            send(sender, "&7Create one with &e/sf doctor migrations schemas storage ids scan&7.");
            return;
        }
        long secondsLeft = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
        send(sender, "&7Candidates: &e" + plan.getRewriteCount()
                + " &8| &7unknown: &c" + plan.getUnknownIdRecords()
                + " &8| &7missing targets: &c" + plan.getMissingTargetRecords()
                + " &8| &7unreadable: &c" + plan.getUnreadableRecords()
                + " &8| &7expires: &e" + secondsLeft + "s"
                + " &8| &7fingerprint: &b" + plan.getShortFingerprint());
    }

    private void executePlan(CommandSender sender, String[] args) {
        if (args.length < 7 || args[6].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations schemas storage ids execute <fingerprint>");
            return;
        }

        ExecutionResult result = service.execute(args[6]);
        if (result.status() == ExecutionStatus.NO_PLAN) {
            send(sender, "&cNo active persisted Item-ID plan exists, or it expired. Run a fresh scan.");
            return;
        }
        if (result.status() == ExecutionStatus.BAD_FINGERPRINT) {
            send(sender, "&cPersisted Item-ID fingerprint missing or incorrect. No data was changed.");
            return;
        }
        if (result.status() == ExecutionStatus.STORAGE_BUSY) {
            send(sender, "&eStorage became busy before execution. The single-use plan is consumed; run a fresh scan later.");
            return;
        }
        if (result.status() == ExecutionStatus.STALE) {
            send(sender, "&eStored item state, mapping, target registration or loaded state changed since the scan.");
            send(sender, "&7Migration stopped safely. The plan is consumed; run a fresh scan before retrying.");
            return;
        }
        if (result.status() == ExecutionStatus.FAILED) {
            var summary = result.summary();
            send(sender, "&cA storage write failed during persisted Item-ID migration.");
            send(sender, summary != null && summary.rollbackComplete()
                    ? "&aAlready-applied rewrites were rolled back to their exact original payloads."
                    : "&cRollback could not be proven complete; restore the offline backup before continuing.");
            return;
        }

        var summary = result.summary();
        send(sender, "&6Slimefun Doctor Persisted Item-ID Execution Report");
        send(sender, "&7Legacy Item IDs rewritten: &a" + summary.rewritten());
        send(sender, "&7Only the Slimefun Item-ID PDC is intentionally changed; all candidates passed semantic round-trip verification.");
        send(sender, "&7Changed rows are reserialized in the current SF2/Paper format. Unmapped/unknown rows remain untouched.");
        send(sender, "&7The execution fingerprint is consumed. Re-scan after unloading other machine chunks to continue the sweep.");
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrations schemas storage ids <status|scan|execute>");
        send(sender, "&7Scan is read-only and never loads chunks. Execute requires the fresh scan fingerprint.");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
