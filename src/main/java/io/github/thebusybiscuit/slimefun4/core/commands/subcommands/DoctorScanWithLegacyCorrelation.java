package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaValidationRunner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Runs the read-only Item Doctor scan with legacy ID and same-ID schema correlation enabled. */
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

        boolean started = service.startMigrationAwareServerRun(report -> {
            send(sender, "&7Item traversal complete. Validating addon-owned persistent-state claims...");
            LegacyItemSchemaValidationRunner.validate(report).whenComplete((ignored, error) ->
                    Slimefun.getSchedulerService().run(() -> {
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Legacy schema validation phase ended unexpectedly; no migration was authorized.", error);
                            send(sender, "&cSchema validation ended unexpectedly. Treat validation-required candidates as manual-only.");
                        }
                        send(sender, "&aSlimefun item doctor migration scan completed.");
                        sendProgress(sender, report);
                        DoctorLegacyIdCorrelation.send(sender, report);
                        DoctorSchemaMigrationCorrelation.send(sender, report);
                        if (report.getUnknownIds() > 0
                                || report.getUnresolvedTemplates() > 0
                                || report.getUnknownBlockIds() > 0) {
                            send(sender, "&eSome old or unknown data remains protected because Doctor cannot prove a safe rewrite.");
                        }
                    }));
        });

        if (!started) {
            send(sender, "&eA server-wide item doctor run is already active. Use /sf doctor status.");
            return;
        }

        send(sender, "&aStarted a batched server-wide Slimefun Doctor migration scan.");
        send(sender, "&7This is a dry run. It reports legacy IDs, placed-block identity and addon-owned schema candidates without changing data.");
        send(sender, "&7It covers online inventories, loaded Slimefun blocks/machines, nested containers, and all backpacks.");
        send(sender, "&7Schema probes receive cloned items; persistent-state validators are read-only and run after traversal.");
        send(sender, "&7The scan does not force-load the world. Offline/unloaded data is handled only when loaded normally.");
    }

    private static void sendProgress(CommandSender sender, ItemDoctorReport report) {
        send(sender, "&7Inventories: &e" + report.getInventories() + " &8| &7Backpacks: &e" + report.getBackpacks());
        send(sender, "&7Stacks scanned: &e" + report.getScannedStacks() + " &8| &7Slimefun: &e"
                + report.getSlimefunStacks());
        send(sender, "&7Chinese item presentation: &e" + report.getCjkStacks() + " &8| &7Item repairs: &a"
                + report.getRepairedStacks());
        send(sender, "&7Placed Slimefun blocks: &e" + report.getScannedBlocks() + " &8| &7Chinese names: &e"
                + report.getCjkBlocks() + " &8| &7Block-name repairs: &a" + report.getRepairedBlocks());
        send(sender, "&7Stored block IDs: legacy/alias &e" + report.getLegacyBlockIds() + " &8| &7unknown &c"
                + report.getUnknownBlockIds());
        send(sender, "&7Declared legacy-ID item candidates: &e" + report.getLegacyMigrationCandidates()
                + " &8| &7Distinct IDs: &e" + report.getLegacyMigrationCandidateCounts().size());
        send(sender, "&7Same-ID schema candidates: &e" + report.getSchemaMigrationCandidates()
                + " &8| &7Validated candidate stacks: &e" + report.getSchemaValidatedCandidates());
        send(sender, "&7Unknown item IDs: &e" + report.getUnknownIds() + " &8| &7No English template: &e"
                + report.getUnresolvedTemplates() + " &8| &7Failures: &c" + report.getFailures());
        if (!report.getUnknownIdSamples().isEmpty()) {
            send(sender, "&7Unknown item-ID samples: &e" + String.join(", ", report.getUnknownIdSamples()));
        }
        if (!report.getLegacyBlockIdSamples().isEmpty()) {
            send(sender, "&7Legacy/alias block-ID samples: &e" + String.join(", ", report.getLegacyBlockIdSamples()));
        }
        if (!report.getUnknownBlockIdSamples().isEmpty()) {
            send(sender, "&7Unknown block-ID samples: &e" + String.join(", ", report.getUnknownBlockIdSamples()));
        }
        if (!report.getUnresolvedTemplateSamples().isEmpty()) {
            send(sender, "&7Unresolved template samples: &e" + String.join(", ", report.getUnresolvedTemplateSamples()));
        }
        if (report.isComplete()) {
            send(sender, "&7Traversal duration: &e" + Math.max(1L, report.getDurationMillis() / 1000L) + " second(s)");
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
