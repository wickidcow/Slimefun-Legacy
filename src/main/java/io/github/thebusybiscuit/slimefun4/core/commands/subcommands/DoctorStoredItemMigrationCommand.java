package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemFormatMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemFormatMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemFormatMigrationService.ExecutionResult;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemFormatMigrationService.ExecutionStatus;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Explicit fingerprint gate for upgrading legacy serialized items in unloaded machine storage. */
final class DoctorStoredItemMigrationCommand {

    private final PersistedItemFormatMigrationService service = new PersistedItemFormatMigrationService();
    private final DoctorPersistedItemIdMigrationCommand itemIdMigrations = new DoctorPersistedItemIdMigrationCommand();
    private final DoctorBackpackItemIdMigrationCommand backpackItemIdMigrations = new DoctorBackpackItemIdMigrationCommand();

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 4 && (args[4].equalsIgnoreCase("ids")
                || args[4].equalsIgnoreCase("item-ids")
                || args[4].equalsIgnoreCase("itemids"))) {
            itemIdMigrations.execute(sender, args);
            return;
        }
        if (args.length > 4 && (args[4].equalsIgnoreCase("backpacks")
                || args[4].equalsIgnoreCase("backpack"))) {
            backpackItemIdMigrations.execute(sender, args);
            return;
        }

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
            send(sender, "&eMachine storage is currently busy. No item-format plan was created; try again after pending reads/writes drain.");
            return;
        }

        PersistedItemFormatMigrationPlan plan = result.plan();
        send(sender, "&6Slimefun Doctor Persisted Item-Format Plan");
        send(sender, "&7Unloaded machine inventory rows scanned: &e" + plan.getScannedRecords());
        send(sender, "&7Current SF2 rows: &a" + plan.getCurrentRecords()
                + " &8| &7verified legacy rows: &e" + plan.getRewriteCount());
        send(sender, "&7Unreadable/unsafe legacy rows: &c" + plan.getUnreadableLegacyRecords());

        if (!plan.getUnreadableSamples().isEmpty()) {
            send(sender, "&cUnsafe samples were preserved unchanged: &f" + String.join(", ", plan.getUnreadableSamples()));
        }
        if (plan.getUnreadableLegacyRecords() != 0L) {
            send(sender, "&cExecution is BLOCKED because not every legacy row in this snapshot passed a lossless round-trip check.");
            send(sender, "&7No stored item has been changed. Resolve or inspect those rows before creating a new plan.");
            return;
        }
        if (plan.getRewriteCount() == 0) {
            send(sender, "&aNo legacy serialized items require conversion in currently unloaded machine storage.");
            send(sender, "&7Persisted machine Item IDs: &e/sf doctor migrate schemas storage ids scan");
            send(sender, "&7Persisted backpack Item IDs: &e/sf doctor migrate schemas storage backpacks scan");
            return;
        }

        send(sender, "&7Fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Execute: &6/sf doctor migrate schemas storage execute " + plan.getShortFingerprint());
        send(sender, "&7Plan expires after &e" + Math.max(1L, service.getPlanTtlMillis() / 60_000L) + " minute(s)&7 and is single-use.");
        send(sender, "&eLoaded machines are intentionally excluded. Unload their chunks and re-scan to sweep their persisted rows.");
        send(sender, "&eMake an offline backup before executing persisted item-format migration.");
        send(sender, "&7Persisted machine Item IDs: &e/sf doctor migrate schemas storage ids scan");
        send(sender, "&7Persisted backpack Item IDs: &e/sf doctor migrate schemas storage backpacks scan");
    }

    private void sendStatus(CommandSender sender) {
        PersistedItemFormatMigrationPlan plan = service.getPreparedPlan().orElse(null);
        send(sender, "&6Slimefun Doctor Persisted Item-Format Migration");
        if (plan == null) {
            send(sender, "&7No active persisted item-format plan exists.");
            send(sender, "&7Create one with &e/sf doctor migrate schemas storage scan&7.");
            send(sender, "&7Persisted machine Item IDs: &e/sf doctor migrate schemas storage ids scan");
            send(sender, "&7Persisted backpack Item IDs: &e/sf doctor migrate schemas storage backpacks scan");
            return;
        }
        long secondsLeft = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
        send(sender, "&7Candidates: &e" + plan.getRewriteCount()
                + " &8| &7unreadable: &c" + plan.getUnreadableLegacyRecords()
                + " &8| &7expires: &e" + secondsLeft + "s"
                + " &8| &7fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Persisted machine Item IDs: &e/sf doctor migrate schemas storage ids scan");
        send(sender, "&7Persisted backpack Item IDs: &e/sf doctor migrate schemas storage backpacks scan");
    }

    private void executePlan(CommandSender sender, String[] args) {
        if (args.length < 6 || args[5].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrate schemas storage execute <fingerprint>");
            return;
        }

        ExecutionResult result = service.execute(args[5]);
        if (result.status() == ExecutionStatus.NO_PLAN) {
            send(sender, "&cNo active persisted item-format plan exists, or it expired. Run a fresh scan.");
            return;
        }
        if (result.status() == ExecutionStatus.BAD_FINGERPRINT) {
            send(sender, "&cPersisted item-format fingerprint missing or incorrect. No data was changed.");
            return;
        }
        if (result.status() == ExecutionStatus.BLOCKED_UNREADABLE) {
            send(sender, "&cThis plan contains unreadable or non-lossless legacy rows, so execution is blocked.");
            send(sender, "&7No data was changed. The plan is consumed; inspect the scan samples and re-scan afterwards.");
            return;
        }
        if (result.status() == ExecutionStatus.STORAGE_BUSY) {
            send(sender, "&eStorage became busy before execution. The single-use plan is consumed; run a fresh scan later.");
            return;
        }
        if (result.status() == ExecutionStatus.STALE) {
            send(sender, "&eStored item state changed, became loaded, or disappeared since the scan. Migration stopped safely.");
            send(sender, "&7The plan is consumed. Run a fresh scan; do not reuse its fingerprint.");
            return;
        }
        if (result.status() == ExecutionStatus.FAILED) {
            var summary = result.summary();
            send(sender, "&cA storage write failed during persisted item-format migration.");
            send(sender, summary != null && summary.rollbackComplete()
                    ? "&aAlready-applied conversions were rolled back to their exact original payloads."
                    : "&cRollback could not be proven complete; restore the offline backup before continuing.");
            return;
        }

        var summary = result.summary();
        send(sender, "&6Slimefun Doctor Persisted Item-Format Execution Report");
        send(sender, "&7Converted to current SF2/Paper format: &a" + summary.rewritten());
        send(sender, "&7No Slimefun item IDs or metadata are intentionally rewritten by this migration; it is serialization-format only.");
        send(sender, "&7The execution fingerprint is consumed. Re-scan after unloading other machine chunks to continue the sweep.");
        send(sender, "&7Persisted machine Item IDs: &e/sf doctor migrate schemas storage ids scan");
        send(sender, "&7Persisted backpack Item IDs: &e/sf doctor migrate schemas storage backpacks scan");
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrate schemas storage <status|scan|execute|ids|backpacks>");
        send(sender, "&7Scan is read-only and never loads chunks. Execute requires the fresh scan fingerprint.");
        send(sender, "&7Machine Item IDs: &e/sf doctor migrate schemas storage ids <status|scan|execute>");
        send(sender, "&7Backpack Item IDs: &e/sf doctor migrate schemas storage backpacks <status|scan|execute>");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
