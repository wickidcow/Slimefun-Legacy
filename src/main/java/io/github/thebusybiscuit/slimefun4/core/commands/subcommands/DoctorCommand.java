package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySummary;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalBlockIntegration;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationCapability;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationStatus;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.stability.AddonDoctorService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.MachineFailureSnapshot;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Administrative item presentation diagnosis and repair commands. */
final class DoctorCommand extends SubCommand {

    private static final int MAX_ADDON_DETAIL_LINES = 50;

    DoctorCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        ItemDoctorService service = Slimefun.getItemDoctorService();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status" -> sendStatus(sender, service);
            case "hand" -> repairHand(sender, service);
            case "inventory" -> repairInventory(sender, args, service);
            case "scan" -> startServerRun(sender, service, false);
            case "addons" -> runAddonDoctors(sender, args);
            case "compatibility", "compat" -> sendAddonCompatibility(sender, args);
            case "runtime", "failures" -> sendRuntimeFailures(sender);
            case "integrations", "integration" -> sendExternalIntegrations(sender, args);
            case "repair", "fix" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    send(sender, "&eThis safely changes visible names and lore across stored items.");
                    send(sender, "&eBack up the server first, then run &6/slimefun doctor repair confirm&e.");
                    return;
                }
                startServerRun(sender, service, true);
            }
            default -> sendUsage(sender);
        }
    }

    private void sendStatus(CommandSender sender, ItemDoctorService service) {
        send(sender, "&6Slimefun Storage and Item Doctor");
        send(sender, "&7Previous clean shutdown: "
                + (Slimefun.getDatabaseManager().wasPreviousShutdownClean() ? "&aYes" : "&cNo"));
        send(sender, "&7Pending database writes: &e"
                + Slimefun.getDatabaseManager().getPendingWriteTaskCount());
        send(sender, "&7Paused machine circuits: &e"
                + Slimefun.getTickerTask().getPausedMachineCount());
        send(sender, "&7Currently failing machines: &e"
                + Slimefun.getTickerTask().getFailingMachineCount());
        send(sender, "&7Observed machine failures since startup: &e"
                + Slimefun.getTickerTask().getObservedMachineFailureCount());
        send(sender, "&7Suppressed duplicate machine reports: &e"
                + Slimefun.getTickerTask().getSuppressedMachineFailureReportCount());
        send(sender, "&7Automatic item repair: " + (service.isEnabled() ? "&aEnabled" : "&cDisabled"));
        AddonDoctorService addonDoctors = new AddonDoctorService(plugin);
        send(sender, "&7Registered addon doctors: &e" + addonDoctors.getProviders().size());

        ItemDoctorReport automatic = service.getAutomaticReport();
        send(sender, "&7Automatic repairs completed: &e" + automatic.getRepairedStacks());

        ItemDoctorReport current = service.getCurrentReport();
        if (current != null) {
            send(sender, "&7Server-wide " + current.getModeName() + ": &eRunning");
            sendProgress(sender, current);
            return;
        }

        ItemDoctorReport last = service.getLastReport();
        if (last != null) {
            send(sender, "&7Last server-wide " + last.getModeName() + ": &aComplete");
            sendProgress(sender, last);
        } else {
            send(sender, "&7Server-wide scan: &fNot run yet");
        }
        sendUsage(sender);
    }

    private void repairHand(CommandSender sender, ItemDoctorService service) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cOnly a player can repair the item in their hand.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        ItemDoctorReport report = service.inspectItem(item, true);
        if (report.getRepairedStacks() > 0) {
            player.getInventory().setItemInMainHand(item);
            send(sender, "&aRepaired the visible English name and lore while preserving item data.");
        } else if (report.getCjkStacks() == 0) {
            send(sender, "&eNo registered Slimefun item with Chinese presentation was found in your hand.");
        } else {
            send(sender, "&cThe item could not be mapped to an English registered template.");
            sendProgress(sender, report);
        }
    }

    private void repairInventory(CommandSender sender, String[] args, ItemDoctorService service) {
        Player target;
        if (args.length > 2) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                send(sender, "&cThat player is not online. Offline inventories repair automatically on next join.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            send(sender, "&cConsole usage: /sf doctor inventory <online-player>");
            return;
        }

        ItemDoctorReport report = service.inspectPlayer(target, true);
        send(sender, "&aFinished repairing &e" + target.getName() + "&a's inventory and ender chest.");
        sendProgress(sender, report);
    }

    private void startServerRun(CommandSender sender, ItemDoctorService service, boolean repair) {
        if (!service.isEnabled()) {
            send(sender, "&cThe item doctor is disabled in config.yml.");
            return;
        }

        boolean started = service.startServerRun(repair, report -> {
            send(sender, "&aSlimefun item doctor " + report.getModeName() + " completed.");
            sendProgress(sender, report);
            if (report.getUnknownIds() > 0 || report.getUnresolvedTemplates() > 0) {
                send(sender, "&eSome items were skipped because no safe English registered template was available.");
            }
            if (report.isRepairMode()) {
                send(sender, "&eBackpack database changes are queued. Keep the server running until");
                send(sender, "&e/slimefun doctor status shows 0 pending database writes, then stop normally.");
            }
        });

        if (!started) {
            send(sender, "&eA server-wide item doctor run is already active. Use /sf doctor status.");
            return;
        }

        send(sender, "&aStarted a batched server-wide item doctor " + (repair ? "repair" : "scan") + '.');
        if (!repair) {
            send(sender, "&7This is a dry run. It will report changes without modifying any item.");
        }
        send(sender, "&7It covers online inventories, loaded chests/machines, nested containers, and all backpacks.");
        send(sender, "&7Offline player inventories and unloaded chests are repaired automatically when loaded.");
    }

    private void sendRuntimeFailures(CommandSender sender) {
        long now = System.currentTimeMillis();
        List<MachineFailureSnapshot> failures = Slimefun.getTickerTask().getMachineFailureSnapshots(25);
        send(sender, "&6Slimefun Runtime Failure Isolation");
        send(sender, "&7Active failing locations: &e" + Slimefun.getTickerTask().getFailingMachineCount()
                + " &8| &7Paused: &e" + Slimefun.getTickerTask().getPausedMachineCount());
        send(sender, "&7Failures observed: &e" + Slimefun.getTickerTask().getObservedMachineFailureCount()
                + " &8| &7Duplicate reports suppressed: &e"
                + Slimefun.getTickerTask().getSuppressedMachineFailureReportCount());
        if (failures.isEmpty()) {
            send(sender, "&aNo currently failing machine locations are tracked.");
            return;
        }
        for (MachineFailureSnapshot failure : failures) {
            String state = failure.isPaused(now)
                    ? "&cPAUSED " + failure.getRetrySeconds(now) + "s"
                    : "&eRETRYING";
            send(sender, "&8- " + state + " &f" + failure.getItemId() + " &7[" + failure.getAddonName() + "]");
            send(sender, "&8  " + failure.getWorldName() + " " + failure.getX() + ", " + failure.getY() + ", "
                    + failure.getZ() + " &8| &7" + simpleFailureName(failure.getFailureType()));
            send(sender, "&8  &7" + failure.getFailureMessage());
        }
        if (Slimefun.getTickerTask().getFailingMachineCount() > failures.size()) {
            send(sender, "&7Only the 25 most recent failing locations are shown.");
        }
    }

    private void sendExternalIntegrations(CommandSender sender, String[] args) {
        Slimefun.getExternalIntegrationService().refresh();
        if (args.length > 2 && args[2].equalsIgnoreCase("probe")) {
            probeExternalIntegrationBlock(sender);
            return;
        }

        List<ExternalIntegrationStatus> statuses = Slimefun.getExternalIntegrationService().getStatuses();
        send(sender, "&6Slimefun External Integration Adapters");
        for (ExternalIntegrationStatus status : statuses) {
            String detected = status.isDetected() ? (status.isEnabled() ? "&aDetected" : "&cDisabled") : "&7Not installed";
            String bridge = status.isProviderRegistered() ? "&aAdapter active" : "&eDetection only";
            String version = status.getPluginVersion() == null ? "" : " v" + status.getPluginVersion();
            send(sender, "&8- &f" + status.getDisplayName() + version + "&7: " + detected + " &8| " + bridge);
            if (!status.getCapabilities().isEmpty()) {
                String capabilities = status.getCapabilities().stream()
                        .sorted(Comparator.comparing(ExternalIntegrationCapability::getDisplayName))
                        .map(ExternalIntegrationCapability::getDisplayName)
                        .collect(Collectors.joining(", "));
                send(sender, "&8  &7Capabilities: &f" + capabilities);
            }
            send(sender, "&8  &7" + status.getDetail());
        }
        send(sender, "&7Look at a Rebar/Pylon block and run &e/sf doctor integrations probe&7.");
        send(sender, "&7Energy exchange remains disabled unless a provider explicitly implements compatible semantics.");
    }

    private void probeExternalIntegrationBlock(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cOnly a player can probe the block they are looking at.");
            return;
        }

        Block block = player.getTargetBlockExact(8);
        if (block == null) {
            send(sender, "&eNo block is targeted within 8 blocks.");
            return;
        }

        List<ExternalBlockIntegration> integrations = Slimefun.getExternalIntegrationService().inspectBlock(block);
        send(sender, "&6External block probe");
        send(sender, "&7Target: &f" + block.getType() + " &8@ &f" + block.getWorld().getName() + " "
                + block.getX() + ", " + block.getY() + ", " + block.getZ());
        if (integrations.isEmpty()) {
            send(sender, "&eNo active external adapter recognized this block.");
            send(sender, "&7The block may be vanilla, unloaded from Rebar storage, or from an unsupported Rebar API version.");
            return;
        }

        for (ExternalBlockIntegration integration : integrations) {
            send(sender, "&a- " + integration.getDisplayName() + " &7[" + integration.getPluginName() + "]");
            send(sender, "&8  &7Type: &f" + simpleFailureName(integration.getBlockType()));
            if (integration.getContentKey() != null) {
                send(sender, "&8  &7Key: &f" + integration.getContentKey());
            }
            if (integration.getCapabilities().isEmpty()) {
                send(sender, "&8  &7Mapped capabilities: &eNone");
            } else {
                String capabilities = integration.getCapabilities().stream()
                        .sorted(Comparator.comparing(ExternalIntegrationCapability::getDisplayName))
                        .map(ExternalIntegrationCapability::getDisplayName)
                        .collect(Collectors.joining(", "));
                send(sender, "&8  &7Mapped capabilities: &f" + capabilities);
            }
            send(sender, "&8  &7" + integration.getDetail());
        }
    }

    private String simpleFailureName(String className) {
        int separator = className.lastIndexOf('.');
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    private void runAddonDoctors(CommandSender sender, String[] args) {
        AddonDoctorService service = new AddonDoctorService(plugin);
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";

        if (action.equals("status") || action.equals("list")) {
            var providers = service.getProviders();
            send(sender, "&6Slimefun Addon Doctors");
            if (providers.isEmpty()) {
                send(sender, "&7No addons have registered a doctor service.");
                return;
            }
            for (var registration : providers) {
                send(sender, "&a- " + service.getProviderName(registration) + " &7("
                        + registration.getPlugin().getName() + ")");
            }
            send(sender, "&7Run &e/sf doctor addons scan &7for a dry run.");
            return;
        }

        boolean repair;
        if (action.equals("scan")) {
            repair = false;
        } else if (action.equals("repair") || action.equals("fix")) {
            if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
                send(sender, "&eBack up the server first, then run");
                send(sender, "&6/slimefun doctor addons repair confirm&e.");
                return;
            }
            repair = true;
        } else {
            send(sender, "&eUsage: /slimefun doctor addons [status|scan|repair confirm]");
            return;
        }

        List<AddonDoctorReport> reports = service.runAll(repair);
        if (reports.isEmpty()) {
            send(sender, "&eNo addon doctor providers are registered.");
            return;
        }

        send(sender, "&6Addon doctor " + (repair ? "repair" : "scan") + " results");
        long totalIssues = 0L;
        long totalRepaired = 0L;
        long totalFailures = 0L;
        for (AddonDoctorReport report : reports) {
            totalIssues += report.getIssuesFound();
            totalRepaired += report.getRepairedEntries();
            totalFailures += report.getFailures();
            send(sender, "&e" + report.getAddonName() + "&7: scanned &f" + report.getScannedEntries()
                    + " &8| &7issues &e" + report.getIssuesFound()
                    + " &8| &7repaired &a" + report.getRepairedEntries()
                    + " &8| &7failures &c" + report.getFailures());
            int shown = 0;
            for (String detail : report.getDetails()) {
                if (shown >= MAX_ADDON_DETAIL_LINES) {
                    break;
                }
                send(sender, "&8  - &7" + detail);
                shown++;
            }
            int omitted = report.getDetails().size() - shown;
            if (omitted > 0) {
                send(sender, "&8  - &7" + omitted + " additional detail line(s) omitted");
            }
        }
        send(sender, "&7Totals: issues &e" + totalIssues + " &8| &7repaired &a" + totalRepaired
                + " &8| &7failures &c" + totalFailures);
    }

    private void sendAddonCompatibility(CommandSender sender, String[] args) {
        Slimefun.getAddonCompatibilityService().refresh();

        if (args.length > 2) {
            var result = Slimefun.getAddonCompatibilityService().getResult(args[2]);
            if (result.isEmpty()) {
                send(sender, "&cNo installed Slimefun addon matched: &e" + args[2]);
                return;
            }
            sendCompatibilityResult(sender, result.orElseThrow());
            return;
        }

        AddonCompatibilitySummary summary = Slimefun.getAddonCompatibilityService().getSummary();
        send(sender, "&6Slimefun Addon Compatibility");
        send(sender, "&7Running core: &e"
                + Slimefun.getAddonCompatibilityService().getRunningCoreVariant().getDisplayName());
        send(sender, "&7Compatible: &a" + summary.getCount(AddonCompatibilityStatus.COMPATIBLE)
                + " &8| &7Warnings: &e" + summary.getCount(AddonCompatibilityStatus.WARNING)
                + " &8| &7Undeclared: &b" + summary.getCount(AddonCompatibilityStatus.UNDECLARED));
        send(sender, "&7Incompatible: &c" + summary.getCount(AddonCompatibilityStatus.INCOMPATIBLE)
                + " &8| &7Disabled: &c" + summary.getCount(AddonCompatibilityStatus.DISABLED));

        if (summary.getTotal() == 0) {
            send(sender, "&7No Slimefun addon plugins are installed.");
            return;
        }

        for (AddonCompatibilityResult result : Slimefun.getAddonCompatibilityService().getResults()) {
            send(sender, statusColor(result.getStatus()) + "- " + result.getPluginName() + " v"
                    + result.getPluginVersion() + " &7[" + result.getStatus().getDisplayName() + "]");
            for (String message : result.getMessages()) {
                send(sender, "&8  - &7" + message);
            }
        }
        send(sender, "&7Inspect one addon: &e/sf doctor compatibility <plugin>");
    }

    private void sendCompatibilityResult(CommandSender sender, AddonCompatibilityResult result) {
        send(sender, "&6Addon Compatibility: &e" + result.getPluginName() + " v" + result.getPluginVersion());
        send(sender, "&7Status: " + statusColor(result.getStatus()) + result.getStatus().getDisplayName());
        send(sender, "&7Declaration source: &e" + result.getSource().getDisplayName());
        if (result.getMessages().isEmpty()) {
            send(sender, "&aNo compatibility problems were detected.");
            return;
        }
        for (String message : result.getMessages()) {
            send(sender, "&8- &7" + message);
        }
    }

    private String statusColor(AddonCompatibilityStatus status) {
        return switch (status) {
            case COMPATIBLE -> "&a";
            case WARNING -> "&e";
            case UNDECLARED -> "&b";
            case DISABLED, INCOMPATIBLE -> "&c";
        };
    }

    private void sendProgress(CommandSender sender, ItemDoctorReport report) {
        send(sender, "&7Inventories: &e" + report.getInventories() + " &8| &7Backpacks: &e" + report.getBackpacks());
        send(sender, "&7Stacks scanned: &e" + report.getScannedStacks() + " &8| &7Slimefun: &e"
                + report.getSlimefunStacks());
        send(sender, "&7Chinese presentation: &e" + report.getCjkStacks() + " &8| &7Repaired: &a"
                + report.getRepairedStacks());
        send(sender, "&7Unknown IDs: &e" + report.getUnknownIds() + " &8| &7No English template: &e"
                + report.getUnresolvedTemplates() + " &8| &7Failures: &c" + report.getFailures());
        if (!report.getUnknownIdSamples().isEmpty()) {
            send(sender, "&7Unknown ID samples: &e" + String.join(", ", report.getUnknownIdSamples()));
        }
        if (!report.getUnresolvedTemplateSamples().isEmpty()) {
            send(sender, "&7Unresolved template samples: &e"
                    + String.join(", ", report.getUnresolvedTemplateSamples()));
        }
        if (report.isComplete()) {
            send(sender, "&7Duration: &e" + Math.max(1L, report.getDurationMillis() / 1000L) + " second(s)");
        }
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /slimefun doctor [status|hand|inventory [player]|scan|repair confirm|addons]");
        send(sender, "&e       /slimefun doctor [compatibility|runtime|integrations [probe]]");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }
}
