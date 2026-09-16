package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBackpackItemIdMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBackpackItemIdMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBackpackItemIdMigrationService.ExecutionResult;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBackpackItemIdMigrationService.ExecutionStatus;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Fingerprint gate for legacy Slimefun Item IDs stored in persisted backpack inventory rows. */
final class DoctorBackpackItemIdMigrationCommand {

    private final PersistedBackpackItemIdMigrationService service = new PersistedBackpackItemIdMigrationService();

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
            send(sender, "&eProfile storage is currently busy. No backpack Item-ID plan was created; try again after pending reads/writes drain.");
            return;
        }

        PersistedBackpackItemIdMigrationPlan plan = result.plan();
        send(sender, "&6Slimefun Doctor Backpack Item-ID Plan");
        send(sender, "&7Persisted backpack rows observed: &e" + plan.getScannedRecords());
        send(sender, "&7Deferred because backpack is cached/live: &e" + plan.getCachedRecords());
        send(sender, "&7Canonical Slimefun IDs: &a" + plan.getCanonicalRecords()
                + " &8| &7non-Slimefun rows: &f" + plan.getNonSlimefunRecords());
        send(sender, "&7Declared legacy-ID rewrites: &e" + plan.getRewriteCount()
                + " &8| &7unknown/unmapped IDs: &c" + plan.getUnknownIdRecords());
        send(sender, "&7Missing canonical targets: &c" + plan.getMissingTargetRecords()
                + " &8| &7unreadable/unsafe rows: &c" + plan.getUnreadableRecords());

        if (plan.getUnknownIdRecords() > 0L || plan.getMissingTargetRecords() > 0L || plan.getUnreadableRecords() > 0L) {
            send(sender, "&eUnresolved rows are preserved unchanged. Doctor never guesses an Item ID from names, lore or historical hints.");
        }
        if (plan.getCachedRecords() > 0L) {
            send(sender, "&eCached backpacks were not inspected for direct persistence migration. Close/unload them and re-scan later.");
        }
        if (plan.getRewriteCount() == 0) {
            send(sender, "&aNo registered legacy Slimefun Item IDs require rewriting in currently uncached backpacks.");
            return;
        }

        send(sender, "&7Fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Execute: &6/sf doctor migrate schemas storage backpacks execute " + plan.getShortFingerprint());
        send(sender, "&7Plan expires after &e" + Math.max(1L, service.getPlanTtlMillis() / 60_000L) + " minute(s)&7 and is single-use.");
        send(sender, "&eMake an offline backup before executing persisted backpack Item-ID migration.");
    }

    private void sendStatus(CommandSender sender) {
        PersistedBackpackItemIdMigrationPlan plan = service.getPreparedPlan().orElse(null);
        send(sender, "&6Slimefun Doctor Backpack Item-ID Migration");
        if (plan == null) {
            send(sender, "&7No active backpack Item-ID plan exists.");
            send(sender, "&7Create one with &e/sf doctor migrate schemas storage backpacks scan&7.");
            return;
        }
        long secondsLeft = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
        send(sender, "&7Candidates: &e" + plan.getRewriteCount()
                + " &8| &7cached/deferred: &e" + plan.getCachedRecords()
                + " &8| &7unknown: &c" + plan.getUnknownIdRecords()
                + " &8| &7missing targets: &c" + plan.getMissingTargetRecords()
                + " &8| &7unreadable: &c" + plan.getUnreadableRecords()
                + " &8| &7expires: &e" + secondsLeft + "s"
                + " &8| &7fingerprint: &b" + plan.getShortFingerprint());
    }

    private void executePlan(CommandSender sender, String[] args) {
        if (args.length < 7 || args[6].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrate schemas storage backpacks execute <fingerprint>");
            return;
        }

        ExecutionResult result = service.execute(args[6]);
        if (result.status() == ExecutionStatus.NO_PLAN) {
            send(sender, "&cNo active backpack Item-ID plan exists, or it expired. Run a fresh scan.");
            return;
        }
        if (result.status() == ExecutionStatus.BAD_FINGERPRINT) {
            send(sender, "&cBackpack Item-ID fingerprint missing or incorrect. No data was changed.");
            return;
        }
        if (result.status() == ExecutionStatus.STORAGE_BUSY) {
            send(sender, "&eProfile storage became busy before execution. The single-use plan is consumed; run a fresh scan later.");
            return;
        }
        if (result.status() == ExecutionStatus.STALE) {
            send(sender, "&eStored backpack state, cache ownership, mapping or target registration changed since the scan.");
            send(sender, "&7Migration stopped safely. The plan is consumed; run a fresh scan before retrying.");
            return;
        }
        if (result.status() == ExecutionStatus.FAILED) {
            var summary = result.summary();
            send(sender, "&cA storage write failed during backpack Item-ID migration.");
            send(sender, summary != null && summary.rollbackComplete()
                    ? "&aAlready-applied rewrites were rolled back to their exact original payloads."
                    : "&cRollback could not be proven complete; restore the offline backup before continuing.");
            return;
        }

        var summary = result.summary();
        send(sender, "&6Slimefun Doctor Backpack Item-ID Execution Report");
        send(sender, "&7Legacy Item IDs rewritten: &a" + summary.rewritten());
        send(sender, "&7Only the Slimefun Item-ID PDC is intentionally changed; all candidates passed semantic round-trip verification.");
        send(sender, "&7The execution fingerprint is consumed. Re-scan later for backpacks that were cached during the earlier scan.");
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrate schemas storage backpacks <status|scan|execute>");
        send(sender, "&7Scan is read-only. Cached/live backpacks are deferred. Execute requires the fresh scan fingerprint.");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
