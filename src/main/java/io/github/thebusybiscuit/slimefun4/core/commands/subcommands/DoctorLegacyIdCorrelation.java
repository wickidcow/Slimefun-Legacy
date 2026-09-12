package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog;
import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog.Hint;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Adds read-only legacy-ID correlation to the normal Slimefun Doctor scan result. */
final class DoctorLegacyIdCorrelation {

    private DoctorLegacyIdCorrelation() {}

    static void send(@Nonnull CommandSender sender, @Nonnull ItemDoctorReport report) {
        Map<String, String> declared = Slimefun.getRegistry().getLegacySlimefunItemIds();
        List<String> itemSamples = report.getUnknownIdSamples();
        List<String> legacyBlockSamples = report.getLegacyBlockIdSamples();
        List<String> unknownBlockSamples = report.getUnknownBlockIdSamples();

        Set<String> samples = new LinkedHashSet<>();
        samples.addAll(itemSamples);
        samples.addAll(legacyBlockSamples);
        samples.addAll(unknownBlockSamples);

        send(sender, "&6Slimefun Legacy-ID Correlation");
        send(sender, "&7Unknown item stacks: &e" + report.getUnknownIds()
                + " &8| &7legacy placed blocks: &e" + report.getLegacyBlockIds()
                + " &8| &7unknown placed blocks: &e" + report.getUnknownBlockIds());
        send(sender, "&7Sampled distinct migration/identity IDs: &e" + samples.size());

        if (!legacyBlockSamples.isEmpty()) {
            send(sender, "&7Legacy block-ID samples: &e" + String.join(", ", legacyBlockSamples));
        }
        if (!unknownBlockSamples.isEmpty()) {
            send(sender, "&7Unknown block-ID samples: &e" + String.join(", ", unknownBlockSamples));
        }

        if (samples.isEmpty()) {
            send(sender, "&aNo unknown item IDs or legacy/unknown placed block IDs were sampled by this scan.");
            send(sender, "&8Read-only diagnostic; no items, blocks, storage, Cargo or Energy data were modified.");
            return;
        }

        int ready = 0;
        int liveAlias = 0;
        int missingTargets = 0;
        int knownHistorical = 0;
        int noMapping = 0;

        for (String id : samples) {
            String declaredTarget = declared.get(id);
            if (declaredTarget != null) {
                if (SlimefunItem.getById(declaredTarget) != null) {
                    ready++;
                    send(sender, "&8- &a[READY] &f" + id + " &8-> &a" + declaredTarget
                            + " &7(addon-declared; provider migration eligible)");
                } else {
                    missingTargets++;
                    send(sender, "&8- &c[TARGET MISSING] &f" + id + " &8-> &c" + declaredTarget
                            + " &7(addon-declared)");
                }
                continue;
            }

            SlimefunItem resolved = SlimefunItem.getById(id);
            if (resolved != null && !id.equals(resolved.getId())) {
                liveAlias++;
                send(sender, "&8- &e[LIVE ALIAS] &f" + id + " &8-> &e" + resolved.getId()
                        + " &7(working compatibility alias; no declared migration authority)");
                continue;
            }

            Hint hint = KnownLegacyItemIdCatalog.find(id).orElse(null);
            if (hint != null) {
                knownHistorical++;
                boolean targetPresent = SlimefunItem.getById(hint.targetId()) != null;
                send(sender, "&8- &e[KNOWN LEGACY] &f" + id + " &8-> "
                        + (targetPresent ? "&a" : "&c") + hint.targetId()
                        + " &8[&7" + hint.source() + "; " + hint.evidence().getDisplayName()
                        + (targetPresent ? "; target registered" : "; target missing") + "&8]");
                continue;
            }

            noMapping++;
            send(sender, "&8- &c[NO MAPPING] &f" + id);
        }

        send(sender, "&7Sample classification: ready &a" + ready
                + " &8| &7live alias only &e" + liveAlias
                + " &8| &7target missing &c" + missingTargets
                + " &8| &7known historical &e" + knownHistorical
                + " &8| &7no mapping &c" + noMapping);

        if (knownHistorical > 0) {
            send(sender, "&eKNOWN LEGACY entries are historical diagnostics only; they do not authorize a migration.");
        }
        if (liveAlias > 0) {
            send(sender, "&eLIVE ALIAS entries prove compatibility resolution only; they do not authorize a migration.");
        }
        if (ready > 0 || missingTargets > 0) {
            send(sender, "&7Declared mappings can be inspected with &e/sf doctor migrations plan&7.");
            send(sender, "&7Addon-owned repair starts with &e/sf doctor migrations providers&7.");
        }
        send(sender, "&8Read-only diagnostic; no items, blocks, storage, registry IDs, Cargo or Energy data were modified.");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
