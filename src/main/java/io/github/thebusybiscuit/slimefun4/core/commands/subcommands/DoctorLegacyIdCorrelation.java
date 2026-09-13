package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog;
import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog.Hint;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Adds read-only legacy-ID correlation to the normal Slimefun Doctor scan result. */
final class DoctorLegacyIdCorrelation {

    private DoctorLegacyIdCorrelation() {}

    static void send(@Nonnull CommandSender sender, @Nonnull ItemDoctorReport report) {
        Map<String, String> declared = Slimefun.getRegistry().getLegacySlimefunItemIds();
        Map<String, Long> exactCandidates = report.getLegacyMigrationCandidateCounts();
        List<String> itemSamples = report.getUnknownIdSamples();
        List<String> legacyBlockSamples = report.getLegacyBlockIdSamples();
        List<String> unknownBlockSamples = report.getUnknownBlockIdSamples();

        send(sender, "&6Slimefun Legacy-ID Correlation");
        send(sender, "&7Declared legacy item candidates: &e" + report.getLegacyMigrationCandidates()
                + " &8| &7distinct declared IDs: &e" + exactCandidates.size());

        long readyStacks = 0L;
        long missingTargetStacks = 0L;
        for (Map.Entry<String, Long> entry : exactCandidates.entrySet()) {
            String target = declared.get(entry.getKey());
            boolean targetPresent = target != null && SlimefunItem.getById(target) != null;
            if (targetPresent) {
                readyStacks += entry.getValue();
            } else {
                missingTargetStacks += entry.getValue();
            }
            send(sender, "&8- " + (targetPresent ? "&a[READY] " : "&c[TARGET MISSING] ")
                    + "&f" + entry.getKey() + " &8-> "
                    + (targetPresent ? "&a" : "&c") + (target == null ? "<mapping removed>" : target)
                    + " &8x&e" + entry.getValue());
        }

        if (exactCandidates.isEmpty()) {
            send(sender, "&7No addon-declared legacy item IDs were encountered by the full scan.");
        } else {
            send(sender, "&7Exact declared item-stack classification: ready &a" + readyStacks
                    + " &8| &7target missing/changed &c" + missingTargetStacks);
            send(sender, "&7These counts include normal inventories, loaded storage/machines, nested containers and backpacks.");
        }

        send(sender, "&7Placed block identity: legacy/alias &e" + report.getLegacyBlockIds()
                + " &8| &7unknown &c" + report.getUnknownBlockIds());
        for (String id : legacyBlockSamples) {
            String target = declared.get(id);
            if (target != null) {
                boolean targetPresent = SlimefunItem.getById(target) != null;
                send(sender, "&8- " + (targetPresent ? "&a[BLOCK LEGACY] " : "&c[BLOCK TARGET MISSING] ")
                        + "&f" + id + " &8-> " + (targetPresent ? "&a" : "&c") + target
                        + " &7(addon-declared; diagnosis only here)");
                continue;
            }

            SlimefunItem resolved = SlimefunItem.getById(id);
            if (resolved != null && !id.equals(resolved.getId())) {
                send(sender, "&8- &e[BLOCK LIVE ALIAS] &f" + id + " &8-> &e" + resolved.getId()
                        + " &7(compatibility resolution only; no migration authority)");
            } else {
                send(sender, "&8- &e[BLOCK LEGACY/ALIAS] &f" + id + " &7(diagnostic only)");
            }
        }
        for (String id : unknownBlockSamples) {
            send(sender, "&8- &c[UNKNOWN BLOCK ID] &f" + id + " &7(no identity rewrite attempted)");
        }
        if (!legacyBlockSamples.isEmpty() || !unknownBlockSamples.isEmpty()) {
            send(sender, "&ePlaced-block identity findings are diagnostic evidence only; this scan never rewrites stored block IDs.");
        }

        send(sender, "&7Unknown CJK-presentation item stacks: &e" + report.getUnknownIds()
                + " &8| &7sampled distinct IDs: &e" + itemSamples.size());
        if (!itemSamples.isEmpty()) {
            int knownHistorical = 0;
            int noMapping = 0;
            for (String id : itemSamples) {
                if (declared.containsKey(id)) {
                    // Already represented above by the exact candidate accounting.
                    continue;
                }

                Hint hint = KnownLegacyItemIdCatalog.find(id).orElse(null);
                if (hint != null) {
                    knownHistorical++;
                    boolean targetPresent = SlimefunItem.getById(hint.targetId()) != null;
                    send(sender, "&8- &e[KNOWN LEGACY / DIAGNOSTIC ONLY] &f" + id + " &8-> "
                            + (targetPresent ? "&a" : "&c") + hint.targetId()
                            + " &8[&7" + hint.source() + "; " + hint.evidence().getDisplayName()
                            + (targetPresent ? "; target registered" : "; target missing") + "&8]");
                    continue;
                }

                noMapping++;
                send(sender, "&8- &c[NO DECLARED MAPPING] &f" + id);
            }

            if (knownHistorical > 0) {
                send(sender, "&eHistorical catalog matches are identification evidence only; they never authorize repair.");
            }
            if (noMapping > 0) {
                send(sender, "&7Unmapped sample IDs: &c" + noMapping + "&7. Addon ownership must be established before migration.");
            }
        }

        if (!exactCandidates.isEmpty()) {
            send(sender, "&7Declared mappings can be inspected and provider-validated with &e/sf doctor migrations plan&7.");
        }
        send(sender, "&8Read-only diagnostic; no items, blocks, storage, registry IDs, Cargo or Energy data were modified.");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
