package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemUpgradePlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemUpgradeReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemUpgradeService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Guided, item-stack-only upgrade workflow for legacy addon IDs and translated presentation. */
final class ItemUpgradeDoctorCommand extends SubCommand {

    private final ItemUpgradeService service;
    private volatile ItemUpgradePlan preparedPlan;

    ItemUpgradeDoctorCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        service = new ItemUpgradeService(plugin);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status" -> sendStatus(sender);
            case "scan", "check" -> startScan(sender);
            case "fix", "repair", "apply" -> startFix(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void sendStatus(CommandSender sender) {
        send(sender, "&6Slimefun Item Upgrade Doctor");
        send(sender, "&7Purpose: &fupgrade legacy item IDs and safely refresh translated item names/lore.");
        send(sender, "&7Scope: &fitem stacks only &8— &7no placed block IDs, Cargo, Energy or addon database migrations.");

        ItemUpgradeReport current = service.getCurrentReport();
        if (current != null) {
            send(sender, "&7Current run: &e" + (current.isRepairMode() ? "FIX" : "SCAN") + " running");
            sendSummary(sender, current, false);
            return;
        }

        ItemUpgradeReport last = service.getLastReport();
        if (last == null) {
            send(sender, "&7Last run: &fNone");
        } else {
            send(sender, "&7Last run: &a" + (last.isRepairMode() ? "FIX" : "SCAN") + " complete");
            sendSummary(sender, last, false);
        }

        ItemUpgradePlan plan = validPlan();
        if (plan == null) {
            send(sender, "&7Fix authorization: &7No active scan fingerprint.");
            send(sender, "&eSTEP 1: Run &6/sf doctor item-upgrade scan");
        } else {
            long seconds = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
            send(sender, "&7Fix authorization: &aReady &8| &7expires in &e" + seconds + "s");
            send(sender, "&eNEXT: Ensure you have a current backup, then run:");
            send(sender, "&6/sf doctor item-upgrade fix " + plan.getShortFingerprint());
        }
    }

    private void startScan(CommandSender sender) {
        preparedPlan = null;
        boolean started = service.startServerRun(false, report -> {
            send(sender, "&aItem Upgrade Doctor scan completed.");
            sendDetailedReport(sender, report);

            if (!report.hasSafeWork()) {
                send(sender, "&aNo automatically safe item upgrades were found in the scanned item stacks.");
                if (report.getUnknownUnmappedIds() > 0) {
                    send(sender, "&eUnknown IDs remain. They need an addon-declared old-ID -> current-ID mapping first.");
                }
                return;
            }

            preparedPlan = ItemUpgradePlan.create(
                    Slimefun.getRegistry().getLegacySlimefunItemIds(),
                    report.getCandidateLegacyIds(),
                    report.getPresentationCandidates());
            ItemUpgradePlan plan = preparedPlan;
            send(sender, "&6----------------------------------------");
            send(sender, "&eSTEP 2: Make sure you have a current backup before changing items.");
            send(sender, "&7If your backup requires a shutdown, back up, restart, and run the scan again for a new fingerprint.");
            send(sender, "&eSTEP 3: Review READY/BLOCKED/NO MAPPING lines above.");
            send(sender, "&eSTEP 4: To apply only the safe item-stack upgrades, run:");
            send(sender, "&6/sf doctor item-upgrade fix " + plan.getShortFingerprint());
            send(sender, "&7Fingerprint expires in 10 minutes and can be used once.");
        });

        if (!started) {
            send(sender, "&eAn Item Upgrade Doctor run is already active, or Slimefun is shutting down.");
            return;
        }

        send(sender, "&6Slimefun Item Upgrade Doctor — STEP 1/4");
        send(sender, "&aStarted a READ-ONLY item-upgrade scan. No item will be changed.");
        send(sender, "&7Looking for addon-declared legacy IDs such as IE1 -> IE2 and DynaTech migrations,");
        send(sender, "&7plus safely repairable translated/Chinese presentation on registered items.");
        send(sender, "&7Scanning online inventories, loaded inventories/machines, dropped items and all Slimefun backpacks.");
        send(sender, "&8Offline player inventories are not opened directly; scan again after those players have joined.");
    }

    private void startFix(CommandSender sender, String[] args) {
        ItemUpgradePlan plan = validPlan();
        if (plan == null) {
            preparedPlan = null;
            send(sender, "&cNo current Item Upgrade Doctor scan fingerprint is available.");
            send(sender, "&eRun &6/sf doctor item-upgrade scan &efirst and review its results.");
            return;
        }

        if (args.length < 4 || args[3].isBlank()) {
            send(sender, "&cMissing scan fingerprint.");
            send(sender, "&eUse exactly: &6/sf doctor item-upgrade fix " + plan.getShortFingerprint());
            return;
        }

        if (!plan.matchesFingerprint(args[3])) {
            send(sender, "&cThat fingerprint does not match the latest Item Upgrade Doctor scan.");
            send(sender, "&eUse: &6/sf doctor item-upgrade fix " + plan.getShortFingerprint());
            return;
        }

        Map<String, String> currentMappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        if (!plan.matchesMappings(currentMappings)) {
            preparedPlan = null;
            send(sender, "&cLegacy-ID mappings changed after the scan. The old plan was invalidated.");
            send(sender, "&eRun &6/sf doctor item-upgrade scan &eagain.");
            return;
        }

        boolean started = service.startServerRun(true, report -> {
            send(sender, "&aItem Upgrade Doctor fix completed.");
            sendDetailedReport(sender, report);
            send(sender, "&6What was changed:");
            send(sender, "&7- Safe declared legacy item IDs were rewritten to registered current IDs.");
            send(sender, "&7- Safely recoverable translated/Chinese visible name/lore was refreshed.");
            send(sender, "&6What was NOT changed:");
            send(sender, "&7- Placed Slimefun block IDs, Cargo, Energy, machine storage records or addon DB schemas.");
            send(sender, "&eWait for &6/sf doctor status &eto show 0 pending database writes before a normal shutdown.");
            if (report.getUnknownUnmappedIds() > 0 || report.getMissingTargets() > 0 || report.getMaterialMismatches() > 0) {
                send(sender, "&eSome items were intentionally skipped. Resolve BLOCKED/NO MAPPING entries, then scan again.");
            } else {
                send(sender, "&aAll upgrade candidates found in this run were handled safely.");
            }
        });

        if (!started) {
            send(sender, "&eAn Item Upgrade Doctor run is already active, or Slimefun is shutting down.");
            return;
        }

        preparedPlan = null;
        send(sender, "&6Slimefun Item Upgrade Doctor — STEP 4/4");
        send(sender, "&aFingerprint accepted. Starting the ITEM-ONLY fix run.");
        send(sender, "&7The authorization is now consumed and cannot be reused.");
    }

    private ItemUpgradePlan validPlan() {
        ItemUpgradePlan plan = preparedPlan;
        if (plan != null && plan.isExpired(System.currentTimeMillis())) {
            preparedPlan = null;
            return null;
        }
        return plan;
    }

    private void sendDetailedReport(CommandSender sender, ItemUpgradeReport report) {
        sendSummary(sender, report, true);

        if (!report.getReadyByAddon().isEmpty()) {
            send(sender, "&6READY by target addon:");
            report.getReadyByAddon().forEach((addon, count) -> send(sender, "&a- " + addon + ": &f" + count));
        }
        for (String sample : report.getReadySamples()) {
            send(sender, "&a[READY] &f" + sample);
        }
        for (String sample : report.getBlockedSamples()) {
            send(sender, "&c[BLOCKED] &f" + sample);
        }
        for (String sample : report.getUnknownSamples()) {
            send(sender, "&c[NO MAPPING] &f" + sample);
        }

        if (report.getUnknownUnmappedIds() > report.getUnknownSamples().size()) {
            send(sender, "&7Additional NO MAPPING occurrences were omitted from the sample list.");
        }
        send(sender, "&8Counts are stack occurrences; sample lists are intentionally bounded.");
    }

    private void sendSummary(CommandSender sender, ItemUpgradeReport report, boolean includeDuration) {
        send(sender, "&7Inventories: &e" + report.getInventories() + " &8| &7Backpacks: &e" + report.getBackpacks());
        send(sender, "&7Stacks scanned: &e" + report.getScannedStacks() + " &8| &7Slimefun stacks: &e"
                + report.getSlimefunStacks());
        send(sender, "&7Legacy-ID upgrades READY: &a" + report.getReadyIdUpgrades()
                + " &8| &7IDs rewritten: &a" + report.getRewrittenIds());
        send(sender, "&7Translated/CJK presentation: &e" + report.getPresentationCandidates()
                + " &8| &7presentation repaired: &a" + report.getPresentationRepairs());
        send(sender, "&7BLOCKED target missing: &c" + report.getMissingTargets()
                + " &8| &7material changed: &c" + report.getMaterialMismatches());
        send(sender, "&7NO MAPPING unknown IDs: &c" + report.getUnknownUnmappedIds()
                + " &8| &7unresolved presentation: &e" + report.getUnresolvedPresentations()
                + " &8| &7failures: &c" + report.getFailures());
        if (includeDuration && report.isComplete()) {
            send(sender, "&7Duration: &e" + Math.max(1L, report.getDurationMillis() / 1000L) + " second(s)");
        }
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&6Slimefun Item Upgrade Doctor");
        send(sender, "&eSTEP 1: /sf doctor item-upgrade scan");
        send(sender, "&eSTEP 2: Review READY, BLOCKED and NO MAPPING results.");
        send(sender, "&eSTEP 3: Ensure you have a current backup.");
        send(sender, "&eSTEP 4: /sf doctor item-upgrade fix <scan-fingerprint>");
        send(sender, "&7Status at any time: &e/sf doctor item-upgrade status");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(io.github.bakedlibs.dough.common.ChatColors.color(message));
    }
}
