package io.github.thebusybiscuit.slimefun4.core.commands;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ProfileDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ProfileDataController.BackpackRecoveryExecution;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ProfileDataController.BackpackRecoveryScan;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Explicit destructive recovery for backpack rows that Minecraft can no longer deserialize.
 *
 * <p>The scan is read-only. Quarantine requires the exact whole-backpack fingerprint printed by
 * that scan, writes the original unreadable payloads to an on-disk ZIP first, and then deletes only
 * those unreadable rows. The existing incomplete-load hold remains until a later normal complete
 * backpack load succeeds.
 */
public final class BackpackRecoveryCommand {
    private static final String PERMISSION = "slimefun.command.doctor";

    private BackpackRecoveryCommand() {}

    public static void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "&cYou do not have permission to use Slimefun Doctor.");
            return;
        }

        if (args.length < 5) {
            usage(sender);
            return;
        }

        String action = args[3].toLowerCase(java.util.Locale.ROOT);
        String backpackId = args[4];
        try {
            UUID.fromString(backpackId);
        } catch (IllegalArgumentException invalid) {
            send(sender, "&cBackpack ID must be a UUID.");
            usage(sender);
            return;
        }

        ProfileDataController profiles = profiles();
        if (profiles == null) {
            send(sender, "&cSlimefun profile storage is not ready yet.");
            return;
        }

        if (action.equals("scan") && args.length == 5) {
            startScan(sender, profiles, backpackId);
            return;
        }

        if (action.equals("quarantine") && args.length == 6) {
            String fingerprint = args[5];
            if (!fingerprint.matches("(?i)[0-9a-f]{64}")) {
                send(sender, "&cThe quarantine fingerprint must be the full 64-character SHA-256 value.");
                return;
            }
            startQuarantine(sender, profiles, backpackId, fingerprint);
            return;
        }

        usage(sender);
    }

    private static void startScan(CommandSender sender, ProfileDataController profiles, String backpackId) {
        send(sender, "&6Slimefun Backpack Recovery &8[&aREAD ONLY&8]");
        send(sender, "&7Scanning stored rows for backpack &e" + backpackId + "&7...");
        profiles.scanBackpackRecoveryAsync(backpackId).whenComplete((scan, failure) -> Slimefun.runSync(() -> {
            if (failure != null) {
                sendFailure(sender, "Backpack recovery scan failed", failure);
                return;
            }
            renderScan(sender, scan);
        }));
    }

    private static void startQuarantine(
            CommandSender sender, ProfileDataController profiles, String backpackId, String fingerprint) {
        send(sender, "&6Slimefun Backpack Recovery &8[&cDESTRUCTIVE&8]");
        send(sender, "&7Revalidating the complete stored backpack state against the supplied fingerprint...");
        profiles.quarantineUnreadableBackpackRowsAsync(backpackId, fingerprint)
                .whenComplete((execution, failure) -> Slimefun.runSync(() -> {
                    if (failure != null) {
                        sendFailure(sender, "Backpack quarantine failed", failure);
                        return;
                    }
                    renderExecution(sender, execution);
                }));
    }

    private static void renderScan(CommandSender sender, BackpackRecoveryScan scan) {
        send(sender, "&7Backpack: &e" + scan.backpackId());
        send(sender, "&7Owner UUID: &e" + scan.ownerUuid());
        send(sender, "&7Size: &e" + scan.backpackSize() + " &8| &7stored rows: &e" + scan.storedRows());
        send(sender, "&7Recovery hold: " + yesNo(scan.loadHeld())
                + " &8| &7cached/live: " + yesNo(scan.cached())
                + " &8| &7pending save: " + yesNo(scan.savePending()));
        send(sender, "&7Unreadable rows: &e" + scan.unreadableRows().size());

        for (var row : scan.unreadableRows()) {
            send(sender, "&8- &7slot &e" + row.slot()
                    + " &8| &7payload &e" + row.payloadSha256().substring(0, 12)
                    + " &8| &7" + InventoryRecoveryDiagnostics.display(row.failure()));
        }

        send(sender, "&7Fingerprint: &e" + scan.fingerprint());
        if (scan.unreadableRows().isEmpty()) {
            send(sender, "&aNo unreadable rows were found. Nothing can be quarantined.");
            return;
        }
        if (!scan.loadHeld()) {
            send(sender, "&eQuarantine is not authorized because this backpack is not currently under a failed-load hold.");
            send(sender, "&7Reproduce the real backpack load failure first, then inspect &e/sf doctor storage recovery&7.");
            return;
        }
        if (scan.cached()) {
            send(sender, "&eQuarantine is not authorized while this backpack is cached/live.");
            return;
        }
        if (scan.savePending()) {
            send(sender, "&eQuarantine is not authorized while a backpack persistence chain is pending.");
            return;
        }

        send(sender, "&cThe next command permanently removes only the unreadable rows from active storage.");
        send(sender, "&7Their exact raw payloads are written to a quarantine ZIP before deletion.");
        send(sender, "&e/sf doctor storage backpacks quarantine " + scan.backpackId() + " " + scan.fingerprint());
    }

    private static void renderExecution(CommandSender sender, BackpackRecoveryExecution execution) {
        send(sender, "&aBackpack quarantine completed.");
        send(sender, "&7Removed unreadable rows: &e" + execution.quarantinedRows());
        send(sender, "&7Recovery archive: &e" + InventoryRecoveryDiagnostics.display(execution.archivePath()));
        send(sender, "&eThe backpack load hold was intentionally retained.");
        send(sender, "&7A normal complete backpack load must succeed before that hold disappears.");
        send(sender, "&7Next: &e/sf doctor storage recovery &7then &e/sf doctor scan&7.");
    }

    private static String yesNo(boolean value) {
        return value ? "&aYes" : "&7No";
    }

    private static ProfileDataController profiles() {
        var database = Slimefun.getDatabaseManager();
        return database == null ? null : database.getProfileDataController();
    }

    private static void sendFailure(CommandSender sender, String prefix, Throwable failure) {
        Throwable root = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        send(sender, "&c" + prefix + ": &f" + root.getClass().getSimpleName()
                + (message == null || message.isBlank()
                        ? ""
                        : " &8- &7" + InventoryRecoveryDiagnostics.display(message)));
    }

    private static void usage(CommandSender sender) {
        send(sender, "&eUsage:");
        send(sender, "&7/sf doctor storage backpacks scan <backpack-uuid>");
        send(sender, "&7/sf doctor storage backpacks quarantine <backpack-uuid> <full-fingerprint>");
    }

    private static void send(CommandSender sender, String text) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }
}
