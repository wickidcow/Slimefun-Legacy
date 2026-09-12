package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Runs the normal read-only Item Doctor scan and appends legacy-ID correlation to its completion report. */
final class DoctorScanWithLegacyCorrelation {

    private DoctorScanWithLegacyCorrelation() {}

    static void run(@Nonnull Slimefun plugin, @Nonnull CommandSender sender) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        ItemDoctorService service = Slimefun.getItemDoctorService();
        if (!service.isEnabled()) {
            send(sender, "&cThe item doctor is disabled in config.yml.");
            return;
        }

        boolean started = service.startServerRun(false, report -> {
            send(sender, "&aSlimefun item doctor " + report.getModeName() + " completed.");
            sendProgress(sender, report);
            DoctorLegacyIdCorrelation.send(sender, report);
            if (report.getUnknownIds() > 0 || report.getUnresolvedTemplates() > 0 || report.getUnknownBlockIds() > 0) {
                send(sender, "&eSome old or unknown data remains protected because Doctor cannot prove a safe migration.");
            }
        });

        if (!started) {
            send(sender, "&eA server-wide item doctor run is already active. Use /sf doctor status.");
            return;
        }

        send(sender, "&aStarted a batched server-wide Slimefun Doctor scan.");
        send(sender, "&7This is a dry run. It reports item, presentation and world-migration findings without changing data.");
        send(sender, "&7It covers online inventories, loaded Slimefun blocks/machines, nested containers, and all backpacks.");
        send(sender, "&7The scan does not force-load the world. Unloaded block data is handled through normal chunk loads.");
    }

    private static void sendProgress(CommandSender sender, ItemDoctorReport report) {
        send(sender, "&7Inventories: &e" + report.getInventories() + " &8| &7Backpacks: &e" + report.getBackpacks());
        send(sender, "&7Stacks scanned: &e" + report.getScannedStacks() + " &8| &7Slimefun: &e"
                + report.getSlimefunStacks());
        send(sender, "&7Chinese item presentation: &e" + report.getCjkStacks() + " &8| &7Item repairs: &a"
                + report.getRepairedStacks());
        send(sender, "&7Placed Slimefun blocks: &e" + report.getScannedBlocks() + " &8| &7Chinese names: &e"
                + report.getCjkBlocks() + " &8| &7Block-name repairs: &a" + report.getRepairedBlocks());
        send(sender, "&7Stored block IDs: legacy &e" + report.getLegacyBlockIds() + " &8| &7unknown &c"
                + report.getUnknownBlockIds());
        send(sender, "&7Unknown item IDs: &e" + report.getUnknownIds() + " &8| &7No English template: &e"
                + report.getUnresolvedTemplates() + " &8| &7Failures: &c" + report.getFailures());
        if (!report.getUnknownIdSamples().isEmpty()) {
            send(sender, "&7Unknown item-ID samples: &e" + String.join(", ", report.getUnknownIdSamples()));
        }
        if (!report.getLegacyBlockIdSamples().isEmpty()) {
            send(sender, "&7Legacy block-ID samples: &e" + String.join(", ", report.getLegacyBlockIdSamples()));
        }
        if (!report.getUnknownBlockIdSamples().isEmpty()) {
            send(sender, "&7Unknown block-ID samples: &e" + String.join(", ", report.getUnknownBlockIdSamples()));
        }
        if (!report.getUnresolvedTemplateSamples().isEmpty()) {
            send(sender, "&7Unresolved template samples: &e" + String.join(", ", report.getUnresolvedTemplateSamples()));
        }
        if (report.isComplete()) {
            send(sender, "&7Duration: &e" + Math.max(1L, report.getDurationMillis() / 1000L) + " second(s)");
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
