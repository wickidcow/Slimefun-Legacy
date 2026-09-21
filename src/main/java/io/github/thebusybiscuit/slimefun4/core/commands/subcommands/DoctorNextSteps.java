package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Prints actionable follow-up commands for findings from a completed Item Doctor traversal. */
final class DoctorNextSteps {

    private DoctorNextSteps() {}

    static void send(@Nonnull CommandSender sender, @Nonnull ItemDoctorReport report) {
        boolean any = false;
        send(sender, "&6Slimefun Doctor Next Steps");

        int packEnableMappings = Slimefun.getItemTextureService().getHostedPackEnableCandidateCount();
        if (CuriositiesConfig.getConfig().getBoolean("resource-pack.enabled") && packEnableMappings > 0) {
            send(sender, "&eResource-pack delivery is enabled but bundled item mappings are still disabled: &f"
                    + packEnableMappings);
            send(sender, "&6  /sf doctor item-models enable-pack scan");
            send(sender, "&8  This opt-in audit prevents new resource-pack items from silently splitting from old stored items.");
            any = true;
        }

        int v52Mappings = Slimefun.getItemTextureService().getHostedPackRollbackCandidateCount();
        if (Slimefun.getItemTextureService().wasHostedPackModelMigrationApplied() && v52Mappings > 0) {
            send(sender, "&eHistorical v4.1.52 bundled item-model mappings are still active: &f" + v52Mappings);
            send(sender, "&8  Active mappings may be intentional when a custom/combined pack still uses Slimefun models.");
            send(sender, "&8  Legacy's sender being disabled is not evidence that these mappings should be removed.");
            send(sender, "&7  Only if you intentionally want to unwind the bundled mappings, inspect:");
            send(sender, "&6  /sf doctor item-models remove-resourcepack-texture-ids");
            any = true;
        }

        boolean presentationFound = report.getCjkStacks() > 0 || report.getCjkBlocks() > 0;
        if (!report.isRepairMode() && presentationFound) {
            send(sender, "&eCore-safe name/lore cleanup is available:");
            send(sender, "&6  /sf doctor repair confirm");
            send(sender, "&8  This repairs only presentation Doctor can prove safe; specialized lanes below remain separate.");
            any = true;
        }

        if (report.getSchemaMigrationCandidates() > 0) {
            send(sender, "&eAddon-owned same-ID schema candidates: &f" + report.getSchemaMigrationCandidates());
            send(sender, "&6  /sf doctor migrations schemas scan");
            send(sender, "&8  Then run the exact schemas execute <plugin> <fingerprint> command it prints.");
            send(sender, "&8  Re-scan between providers. /sf doctor repair confirm does not run addon schema migrators.");
            any = true;
        }

        if (report.getItemModelCandidates() > 0) {
            send(sender, "&eStale bundled item-model candidates: &f" + report.getItemModelCandidates());
            send(sender, "&6  /sf doctor item-models scan");
            send(sender, "&6  /sf doctor item-models repair confirm");
            send(sender, "&8  Generic /sf doctor repair confirm intentionally does not remove item-model data.");
            any = true;
        }

        if (report.getLegacyMigrationCandidates() > 0) {
            send(sender, "&eDeclared legacy item-ID candidates: &f" + report.getLegacyMigrationCandidates());
            send(sender, "&6  /sf doctor migrations plan");
            send(sender, "&6  /sf doctor migrations providers");
            send(sender, "&8  Then run /sf doctor migrations scan <plugin> and its printed fingerprinted execute command.");
            any = true;
        }

        if (report.getLegacyBlockIds() > 0) {
            send(sender, "&eLegacy/alias placed-block IDs: &f" + report.getLegacyBlockIds());
            send(sender, "&6  /sf doctor upgrade plan");
            send(sender, "&8  The upgrade plan identifies the correct block/provider migration lane; core repair does not rewrite identity.");
            any = true;
        }

        if (report.getUnknownIds() > 0) {
            send(sender, "&eUnknown Slimefun item IDs: &f" + report.getUnknownIds());
            send(sender, "&6  /sf doctor migrations unknown");
            send(sender, "&8  Unknown IDs are not guessed or rewritten until an addon mapping/provider establishes ownership.");
            any = true;
        }

        if (report.getUnknownBlockIds() > 0) {
            send(sender, "&eUnknown placed-block IDs: &f" + report.getUnknownBlockIds());
            send(sender, "&6  /sf doctor upgrade plan");
            send(sender, "&8  Doctor will not guess a block identity; review the migration/provider lane shown by the plan.");
            any = true;
        }

        if (report.getFailures() > 0) {
            send(sender, "&eDoctor traversal failures: &f" + report.getFailures());
            send(sender, "&6  /sf doctor report");
            send(sender, "&8  Review the support report and console exception before attempting migration or repair.");
            any = true;
        }

        if (report.isRepairMode()) {
            long remainingItems = Math.max(0L, report.getCjkStacks() - report.getRepairedStacks());
            long remainingBlocks = Math.max(0L, report.getCjkBlocks() - report.getRepairedBlocks());
            if (remainingItems > 0
                    || remainingBlocks > 0
                    || report.getUnresolvedTemplates() > 0
                    || report.getUnknownIds() > 0) {
                send(sender, "&eCore repair left protected or unresolved data.");
                send(sender, "&6  /sf doctor scan");
                send(sender, "&8  Re-scan classifies what remains and prints the specialized command for each repair lane.");
                any = true;
            }
        } else if (report.getUnresolvedTemplates() > 0
                && report.getSchemaMigrationCandidates() == 0
                && report.getUnknownIds() == 0) {
            send(sender, "&eProtected presentation remains without an executable schema candidate.");
            send(sender, "&6  /sf doctor report");
            send(sender, "&8  Keep the item unchanged until its addon provides a safe Doctor migration/repair provider.");
            any = true;
        }

        if (!any) {
            send(sender, "&aNo follow-up repair command is required for the findings in this traversal.");
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
