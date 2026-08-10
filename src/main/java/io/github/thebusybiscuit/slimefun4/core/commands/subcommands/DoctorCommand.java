package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonApiCompatibilitySnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySummary;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeFailureSnapshot;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalBlockIntegration;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationCapability;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationFailureSnapshot;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationStatus;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleSnapshot;
import io.github.thebusybiscuit.slimefun4.api.registry.AddonRegistrySnapshot;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineChunkCoordinationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.BlockDataRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.world.ChunkRuntimeState;
import io.github.thebusybiscuit.slimefun4.api.world.WorldChunkRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry.KnownAddonSupport;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyResolution;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencySnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SchedulerSnapshot;
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
    private static final int MAX_DEPENDENCY_PROBLEM_LINES = 30;

    private final KnownAddonCompatibilityRegistry knownAddonRegistry;

    DoctorCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        knownAddonRegistry = KnownAddonCompatibilityRegistry.load(DoctorCommand.class.getClassLoader());
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
            case "core", "lifecycle" -> sendCoreHealth(sender);
            case "registry" -> sendRegistryHealth(sender);
            case "chunks", "worlds", "blocks" -> sendChunkHealth(sender);
            case "hand" -> repairHand(sender, service);
            case "inventory" -> repairInventory(sender, args, service);
            case "scan" -> startServerRun(sender, service, false);
            case "addons" -> runAddonDoctors(sender, args);
            case "compatibility", "compat" -> sendAddonCompatibility(sender, args);
            case "runtime", "failures" -> sendRuntimeFailures(sender, args);
            case "integrations", "integration" -> sendExternalIntegrations(sender, args);
            case "dependencies", "dependency", "deps" -> sendPluginDependencies(sender, args);
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
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        send(sender, "&6Slimefun Storage and Item Doctor");
        send(sender, "&7Previous clean shutdown: " + (storage.wasPreviousShutdownClean() ? "&aYes" : "&cNo"));
        send(sender, "&7Pending database writes: &e" + storage.getPendingWrites());
        send(sender, "&7Paused machine circuits: &e" + machines.getPausedMachineCircuits());
        send(sender, "&7Currently failing machines: &e" + machines.getActiveMachineFailures());
        send(sender, "&7Observed machine failures since startup: &e" + machines.getObservedMachineFailures());
        send(sender, "&7Suppressed duplicate machine reports: &e" + machines.getSuppressedMachineReports());
        send(
                sender,
                "&7External adapter failures: &e"
                        + Slimefun.getExternalIntegrationService().getActiveFailureCount()
                        + " &8| &7Observed: &e"
                        + Slimefun.getExternalIntegrationService().getObservedFailureCount());
        send(sender, "&7Automatic item repair: " + (service.isEnabled() ? "&aEnabled" : "&cDisabled"));
        AddonDoctorService addonDoctors = new AddonDoctorService(plugin);
        send(
                sender,
                "&7Registered addon doctors: &e" + addonDoctors.getProviders().size());

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

    private void sendCoreHealth(CommandSender sender) {
        CoreLifecycleSnapshot lifecycle = Slimefun.getCoreLifecycleService().getSnapshot();
        CoreReadinessSnapshot readiness = Slimefun.getCoreReadinessService().getSnapshot();
        RegistryRuntimeSnapshot registry = Slimefun.getRegistryRuntimeService().getSnapshot();
        SchedulerSnapshot scheduler = Slimefun.getSchedulerService().getSnapshot();
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        long addonFailures = Slimefun.getAddonRuntimeHealthService().getObservedFailureCount();

        send(sender, "&6Slimefun Core Runtime Health");
        send(
                sender,
                "&7Readiness: &e" + readiness.getState()
                        + (readiness.getReasons().isEmpty()
                                ? ""
                                : " &8| &7" + String.join("; ", readiness.getReasons())));
        send(sender, "&7Lifecycle: &e" + lifecycle.getState() + " &8/ &e" + lifecycle.getPhase());
        send(
                sender,
                "&7Lifecycle failures: startup &e" + lifecycle.getStartupFailures() + " &8| &7shutdown &e"
                        + lifecycle.getShutdownFailures());
        send(
                sender,
                "&7Registry: " + (registry.isInitialRegistrationFinalized() ? "&aFinalized" : "&eBuilding")
                        + " &8| &7items &e" + registry.getEnabledItems() + "&7/&e" + registry.getTotalItems()
                        + " &8| &7runtime additions &e" + registry.getRuntimeRegisteredItems());
        send(
                sender,
                "&7Scheduler: "
                        + (scheduler.isRegionOwnedExecution() ? "&dRegion-owned" : "&aPaper global/main-thread")
                        + " &8| &7accepting tasks: " + (scheduler.isAcceptingTasks() ? "&aYes" : "&cNo")
                        + " &8| &7tracked: &e" + scheduler.getActiveTaskCount());
        send(
                sender,
                "&7Machines: " + (machines.isHalted() ? "&cHalted" : (machines.isPaused() ? "&ePaused" : "&aRunning"))
                        + " &8| &7rate: &e" + machines.getTickRate() + " &8| &7chunks: &e" + machines.getTickingChunks()
                        + " &8| &7locations: &e" + machines.getTickingLocations());
        send(
                sender,
                "&7Machine failures: active &e" + machines.getActiveMachineFailures() + " &8| &7paused &e"
                        + machines.getPausedMachineCircuits() + " &8| &7observed &e"
                        + machines.getObservedMachineFailures());
        send(
                sender,
                "&7Storage: " + (storage.isReady() ? "&aReady" : "&eNot ready") + " &8| &7block &e"
                        + storage.getBlockStorageType() + " &8| &7profiles &e" + storage.getProfileStorageType());
        send(
                sender,
                "&7Storage cache: chunks &e" + storage.getLoadedChunks() + " &8| &7universal &e"
                        + storage.getLoadedUniversalData() + " &8| &7pending writes &e" + storage.getPendingWrites());
        WorldChunkRuntimeSnapshot chunks =
                Slimefun.getWorldChunkRuntimeService().getSnapshot();
        MachineChunkCoordinationSnapshot coordination =
                Slimefun.getMachineChunkCoordinationService().getSnapshot();
        send(
                sender,
                "&7Chunk lifecycle: ready &a" + chunks.getReadyChunks() + " &8| &7unsafe &e" + chunks.getUnsafeChunks()
                        + " &8| &7tracked &e" + chunks.getTrackedChunks());
        send(
                sender,
                "&7Machine/chunk correlation: unsafe &e" + coordination.getUnsafeLocations() + " &8| &7untracked &e"
                        + coordination.getUntrackedLocations());
        send(
                sender,
                "&7Addon callback failures observed: &e" + addonFailures + " &8| &7active addon records: &e"
                        + Slimefun.getAddonRuntimeHealthService().getFailures().size());
        if (lifecycle.getLastFailureComponent() != null) {
            send(
                    sender,
                    "&7Last lifecycle failure: &e" + lifecycle.getLastFailureComponent() + " &8| &7"
                            + simpleFailureName(lifecycle.getLastFailureType()) + ": "
                            + lifecycle.getLastFailureMessage());
        }
        send(
                sender,
                "&8This view is observational. It does not rewrite Cargo, Energy, machines, recipes, or stored data.");
    }

    private void sendRegistryHealth(CommandSender sender) {
        RegistryRuntimeSnapshot registry = Slimefun.getRegistryRuntimeService().getSnapshot();
        send(sender, "&6Slimefun Registry Runtime");
        send(
                sender,
                "&7Initial registration: "
                        + (registry.isInitialRegistrationFinalized() ? "&aFinalized" : "&eBuilding"));
        send(
                sender,
                "&7Items: total &e" + registry.getTotalItems() + " &8| &7enabled &a" + registry.getEnabledItems()
                        + " &8| &7disabled &e" + registry.getDisabledItems() + " &8| &7runtime-added &b"
                        + registry.getRuntimeRegisteredItems());
        send(
                sender,
                "&7Content: groups &e" + registry.getItemGroups() + " &8| &7researches &e" + registry.getResearches()
                        + " &8| &7ticker IDs &e" + registry.getTickerBlocks());
        send(sender, "&7Represented plugins: &e" + registry.getRepresentedPlugins());
        for (AddonRegistrySnapshot addon : Slimefun.getRegistryRuntimeService().getAddonSnapshots()) {
            send(
                    sender,
                    "&8- &f" + addon.getPluginName() + " &7v" + addon.getPluginVersion() + " &8| &7items &e"
                            + addon.getEnabledItems() + "&7/&e" + addon.getTotalItems() + " &8| &7groups &e"
                            + addon.getItemGroups() + " &8| &7tickers &e" + addon.getTickingItems());
        }
        send(sender, "&8Read-only registry diagnostics; no registered content is changed by this command.");
    }

    private void sendChunkHealth(CommandSender sender) {
        WorldChunkRuntimeSnapshot chunks =
                Slimefun.getWorldChunkRuntimeService().getSnapshot();
        BlockDataRuntimeSnapshot blocks = Slimefun.getBlockDataRuntimeService().getSnapshot();
        MachineChunkCoordinationSnapshot machines =
                Slimefun.getMachineChunkCoordinationService().getSnapshot();

        send(sender, "&6Slimefun World, Chunk and Block Runtime");
        send(
                sender,
                "&7Worlds tracked: &e" + chunks.getTrackedWorlds() + " &8| &7chunks tracked: &e"
                        + chunks.getTrackedChunks());
        send(
                sender,
                "&7Chunk states: ready &a" + chunks.getReadyChunks() + " &8| &7loading &e"
                        + chunks.getLoadingChunks() + " &8| &7unloading &e" + chunks.getUnloadingChunks()
                        + " &8| &7failed &c" + chunks.getFailedChunks());
        send(
                sender,
                "&7Lifecycle events: loads &e" + chunks.getChunkLoadEvents() + " &8| &7unloads &e"
                        + chunks.getChunkUnloadEvents() + " &8| &7world loads &e" + chunks.getWorldLoadEvents()
                        + " &8| &7world unloads &e" + chunks.getWorldUnloadEvents());
        send(
                sender,
                "&7Storage chunk loads: attempts &e" + blocks.getChunkLoadAttempts() + " &8| &7deferred &e"
                        + blocks.getDeferredChunkLoads() + " &8| &7failures &c" + blocks.getChunkLoadFailures());
        send(
                sender,
                "&7Loaded block data: chunks &e" + blocks.getLoadedChunkRecords() + " &8| &7blocks &e"
                        + blocks.getLoadedBlockRecords() + " &8| &7unknown IDs &e" + blocks.getUnknownSlimefunIds());
        send(sender, "&7Loaded storage lifecycle: &e" + blocks.getLifecycleSummary());
        send(
                sender,
                "&7Ticker correlation: chunks &e" + machines.getTickerChunks() + " &8| &7locations &e"
                        + machines.getTickerLocations() + " &8| &7unsafe &e" + machines.getUnsafeLocations()
                        + " &8| &7untracked &e" + machines.getUntrackedLocations());

        if (blocks.getLastFailureMessage() != null) {
            send(sender, "&7Last block-data failure: &c" + blocks.getLastFailureMessage());
        }

        if (sender instanceof Player player) {
            ChunkRuntimeState state = Slimefun.getWorldChunkRuntimeService().getChunkState(player.getLocation());
            send(sender, "&7Your current chunk: &e" + state);
        }

        send(
                sender,
                "&8Diagnostics only: this command does not load/unload chunks or alter machine, Cargo, Energy, or storage data.");
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
            send(sender, "&aRepaired the safely recoverable English presentation while preserving item data.");
        } else if (report.getCjkStacks() == 0) {
            send(sender, "&eNo Slimefun-tagged item with Chinese presentation was found in your hand.");
        } else {
            send(sender, "&cThe remaining Chinese text could not be mapped to a safe English template.");
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
                send(
                        sender,
                        "&eSome lore remains protected because Doctor cannot prove a full English rewrite is safe.");
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

    private void sendRuntimeFailures(CommandSender sender, String[] args) {
        if (args.length > 2 && (args[2].equalsIgnoreCase("retry") || args[2].equalsIgnoreCase("reset"))) {
            retryRuntimeFailures(sender, args);
            return;
        }

        long now = System.currentTimeMillis();
        List<MachineFailureSnapshot> failures = Slimefun.getTickerTask().getMachineFailureSnapshots(25);
        send(sender, "&6Slimefun Runtime Failure Isolation");
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        send(
                sender,
                "&7Active failing locations: &e" + machines.getActiveMachineFailures() + " &8| &7Paused: &e"
                        + machines.getPausedMachineCircuits());
        send(
                sender,
                "&7Failures observed: &e" + machines.getObservedMachineFailures()
                        + " &8| &7Duplicate reports suppressed: &e" + machines.getSuppressedMachineReports());
        if (failures.isEmpty()) {
            send(sender, "&aNo currently failing machine locations are tracked.");
            send(sender, "&7Recovery: &e/sf doctor runtime retry &7while looking at a machine, or &e... retry all&7.");
            return;
        }
        for (MachineFailureSnapshot failure : failures) {
            String state = failure.isPaused(now) ? "&cPAUSED " + failure.getRetrySeconds(now) + "s" : "&eRETRYING";
            send(sender, "&8- " + state + " &f" + failure.getItemId() + " &7[" + failure.getAddonName() + "]");
            send(
                    sender,
                    "&8  " + failure.getWorldName() + " " + failure.getX() + ", " + failure.getY() + ", "
                            + failure.getZ() + " &8| &7" + simpleFailureName(failure.getFailureType()));
            send(sender, "&8  &7" + failure.getFailureMessage());
        }
        if (machines.getActiveMachineFailures() > failures.size()) {
            send(sender, "&7Only the 25 most recent failing locations are shown.");
        }
        send(sender, "&7Recovery: &e/sf doctor runtime retry &7while looking at a machine, or &e... retry all&7.");
    }

    private void retryRuntimeFailures(CommandSender sender, String[] args) {
        if (args.length > 3 && args[3].equalsIgnoreCase("all")) {
            int cleared = Slimefun.getMachineRuntimeService().retryAllMachines();
            send(sender, "&aCleared runtime isolation state for all machines. &7Paused circuits cleared: &e" + cleared);
            send(sender, "&7Machines will resume through their normal Slimefun ticker path on the next eligible tick.");
            return;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "&cConsole usage: /sf doctor runtime retry all");
            return;
        }

        Block block = player.getTargetBlockExact(8);
        if (block == null) {
            send(sender, "&eLook at the failing machine within 8 blocks, then run /sf doctor runtime retry.");
            return;
        }

        Slimefun.getMachineRuntimeService().retryMachine(block.getLocation());
        send(sender, "&aCleared runtime isolation state for the targeted location.");
        send(
                sender,
                "&7Target: &f" + block.getWorld().getName() + " " + block.getX() + ", " + block.getY() + ", "
                        + block.getZ());
        send(sender, "&7The normal Slimefun ticker registration and stored machine data were not changed.");
    }

    private void sendExternalIntegrations(CommandSender sender, String[] args) {
        if (args.length > 2 && args[2].equalsIgnoreCase("retry")) {
            retryExternalIntegrations(sender, args);
            return;
        }
        if (args.length > 2 && args[2].equalsIgnoreCase("reload")) {
            Slimefun.getExternalIntegrationService().retryAll();
            Slimefun.getExternalIntegrationService().refresh();
            send(sender, "&aReloaded external integration adapters and cleared their temporary isolation state.");
        } else {
            Slimefun.getExternalIntegrationService().refresh();
        }
        if (args.length > 2 && args[2].equalsIgnoreCase("probe")) {
            probeExternalIntegrationBlock(sender);
            return;
        }

        List<ExternalIntegrationStatus> statuses =
                Slimefun.getExternalIntegrationService().getStatuses();
        send(sender, "&6Slimefun External Integration Adapters");
        for (ExternalIntegrationStatus status : statuses) {
            String detected =
                    status.isDetected() ? (status.isEnabled() ? "&aDetected" : "&cDisabled") : "&7Not installed";
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
        List<ExternalIntegrationFailureSnapshot> failures =
                Slimefun.getExternalIntegrationService().getFailureSnapshots(10);
        send(
                sender,
                "&7Adapter failures observed: &e"
                        + Slimefun.getExternalIntegrationService().getObservedFailureCount()
                        + " &8| &7Active/isolated operations: &e"
                        + Slimefun.getExternalIntegrationService().getActiveFailureCount()
                        + " &8| &7Suppressed duplicates: &e"
                        + Slimefun.getExternalIntegrationService().getSuppressedFailureReportCount());
        long now = System.currentTimeMillis();
        for (ExternalIntegrationFailureSnapshot failure : failures) {
            String state = failure.isPaused(now) ? "&cISOLATED " + failure.getRetrySeconds(now) + "s" : "&eDEGRADED";
            send(sender, "&8  - " + state + " &f" + failure.getDisplayName() + " &7/ " + failure.getOperation());
            send(sender, "&8    &7" + simpleFailureName(failure.getFailureType()) + ": " + failure.getFailureMessage());
        }
        send(sender, "&7Look at a Rebar/Pylon block and run &e/sf doctor integrations probe&7.");
        send(sender, "&7Recovery: &e/sf doctor integrations retry <id|all> &7or &e... reload&7.");
        send(
                sender,
                "&7Energy exchange remains disabled unless a provider explicitly implements compatible semantics.");
    }

    private void retryExternalIntegrations(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "&eUsage: /sf doctor integrations retry <id|all>");
            return;
        }

        String target = args[3];
        if (target.equalsIgnoreCase("all")) {
            int cleared = Slimefun.getExternalIntegrationService().retryAll();
            Slimefun.getExternalIntegrationService().refresh();
            send(
                    sender,
                    "&aCleared temporary isolation for all external integration adapters. &7States cleared: &e"
                            + cleared);
            return;
        }

        boolean cleared = Slimefun.getExternalIntegrationService().retry(target);
        Slimefun.getExternalIntegrationService().refresh();
        if (cleared) {
            send(sender, "&aCleared temporary isolation for external integration: &e" + target);
        } else {
            send(sender, "&eNo failing/isolated external integration operation matched: &f" + target);
        }
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

        List<ExternalBlockIntegration> integrations =
                Slimefun.getExternalIntegrationService().inspectBlock(block);
        send(sender, "&6External block probe");
        send(
                sender,
                "&7Target: &f" + block.getType() + " &8@ &f" + block.getWorld().getName() + " " + block.getX() + ", "
                        + block.getY() + ", " + block.getZ());
        if (integrations.isEmpty()) {
            send(sender, "&eNo active external adapter recognized this block.");
            send(
                    sender,
                    "&7The block may be vanilla, unloaded from Rebar storage, or from an unsupported Rebar API version.");
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

    private void sendPluginDependencies(CommandSender sender, String[] args) {
        PluginDependencyDiagnosticsService dependencies = new PluginDependencyDiagnosticsService(plugin);
        if (args.length > 2) {
            sendPluginDependencyTarget(sender, dependencies, args[2]);
            return;
        }

        List<PluginDependencySnapshot> snapshots = dependencies.getSnapshots();
        long disabledPlugins =
                snapshots.stream().filter(snapshot -> !snapshot.isEnabled()).count();
        long missingRequired = snapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(dependency -> dependency.getState() == PluginDependencyResolution.State.MISSING)
                .count();
        long disabledRequired = snapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(dependency -> dependency.getState() == PluginDependencyResolution.State.DISABLED)
                .count();
        long providerAliases = snapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(PluginDependencyResolution::isProviderAlias)
                .count();

        send(sender, "&6Slimefun Plugin Dependency Boundaries");
        send(sender, "&7Loaded plugin records: &e" + snapshots.size() + " &8| &7Disabled: &e" + disabledPlugins);
        send(
                sender,
                "&7Required dependency problems: missing &c" + missingRequired + " &8| &7disabled &e"
                        + disabledRequired);
        send(sender, "&7Required dependencies resolved through provider aliases: &e" + providerAliases);

        int shown = 0;
        for (PluginDependencySnapshot snapshot : snapshots) {
            for (PluginDependencyResolution dependency : snapshot.getRequiredDependencies()) {
                if (!dependency.isProblem()) {
                    continue;
                }
                if (shown >= MAX_DEPENDENCY_PROBLEM_LINES) {
                    break;
                }
                send(
                        sender,
                        "&c- " + snapshot.getPluginName() + " &7requires &f" + dependency.getDeclaredName() + " &8— "
                                + dependencyState(dependency));
                shown++;
            }
            if (shown >= MAX_DEPENDENCY_PROBLEM_LINES) {
                break;
            }
        }

        long totalProblems = missingRequired + disabledRequired;
        if (totalProblems == 0) {
            send(sender, "&aNo missing or disabled required plugin dependencies were detected.");
        } else if (totalProblems > shown) {
            send(sender, "&7" + (totalProblems - shown) + " additional required dependency problem(s) omitted.");
        }

        send(
                sender,
                "&8This is descriptor-level diagnostics only. A provider alias does not prove that expected classes "
                        + "or runtime behavior are compatible.");
        send(
                sender,
                "&8Slimefun Legacy does not install, enable, replace, or emulate third-party plugin dependencies.");
        send(sender, "&7Inspect one plugin/dependency: &e/sf doctor dependencies <name>");
    }

    private void sendPluginDependencyTarget(
            CommandSender sender, PluginDependencyDiagnosticsService dependencies, String target) {
        PluginDependencyResolution token = dependencies.resolveDependency(target);
        var pluginSnapshot = dependencies.findPlugin(target);
        List<PluginDependencySnapshot> requiredBy = dependencies.getRequiredConsumers(target);
        List<PluginDependencySnapshot> softBy = dependencies.getSoftConsumers(target);

        if (pluginSnapshot.isEmpty()
                && requiredBy.isEmpty()
                && softBy.isEmpty()
                && token.getState() == PluginDependencyResolution.State.MISSING) {
            send(sender, "&cNo loaded plugin or declared dependency matched: &e" + target);
            return;
        }

        send(sender, "&6Plugin Dependency Detail: &e" + target);
        if (token.getState() == PluginDependencyResolution.State.MISSING) {
            send(sender, "&7Dependency token resolution: &cNot loaded");
        } else {
            send(sender, "&7Dependency token resolution: " + dependencyState(token));
        }

        if (pluginSnapshot.isPresent()) {
            PluginDependencySnapshot snapshot = pluginSnapshot.orElseThrow();
            send(
                    sender,
                    "&7Plugin: &f" + snapshot.getPluginName() + " &7v" + snapshot.getPluginVersion() + " &8| "
                            + (snapshot.isEnabled() ? "&aEnabled" : "&cDisabled"));
            if (!snapshot.getProvidedPlugins().isEmpty()) {
                send(sender, "&7Provides aliases: &e" + String.join(", ", snapshot.getProvidedPlugins()));
            }
            if (snapshot.getRequiredDependencies().isEmpty()) {
                send(sender, "&7Required dependencies: &8None declared");
            } else {
                send(sender, "&7Required dependencies:");
                for (PluginDependencyResolution dependency : snapshot.getRequiredDependencies()) {
                    sendDependencyResolution(sender, dependency);
                }
            }
            if (snapshot.getSoftDependencies().isEmpty()) {
                send(sender, "&7Soft dependencies: &8None declared");
            } else {
                send(sender, "&7Soft dependencies:");
                for (PluginDependencyResolution dependency : snapshot.getSoftDependencies()) {
                    sendDependencyResolution(sender, dependency);
                }
            }
        }

        if (!requiredBy.isEmpty()) {
            send(
                    sender,
                    "&7Required by: &f"
                            + requiredBy.stream()
                                    .map(PluginDependencySnapshot::getPluginName)
                                    .sorted(String.CASE_INSENSITIVE_ORDER)
                                    .collect(Collectors.joining(", ")));
        }
        if (!softBy.isEmpty()) {
            send(
                    sender,
                    "&7Soft-used by: &f"
                            + softBy.stream()
                                    .map(PluginDependencySnapshot::getPluginName)
                                    .sorted(String.CASE_INSENSITIVE_ORDER)
                                    .collect(Collectors.joining(", ")));
        }

        if (token.isProviderAlias()) {
            send(sender, "&eProvider alias warning: descriptor resolution is satisfied by another plugin identity.");
            send(
                    sender,
                    "&8This does not prove that the provider contains every class/API expected by dependent plugins.");
        }
        send(sender, "&8Read-only diagnostics; no plugin load state or dependency metadata was changed.");
    }

    private void sendDependencyResolution(CommandSender sender, PluginDependencyResolution dependency) {
        send(sender, "&8- &f" + dependency.getDeclaredName() + " &8— " + dependencyState(dependency));
    }

    private String dependencyState(PluginDependencyResolution dependency) {
        return switch (dependency.getState()) {
            case ENABLED -> {
                String resolved = dependency.getResolvedPluginName() == null
                        ? dependency.getDeclaredName()
                        : dependency.getResolvedPluginName();
                String version = dependency.getResolvedPluginVersion() == null
                        ? ""
                        : " v" + dependency.getResolvedPluginVersion();
                String alias = dependency.isProviderAlias()
                        ? " &8(provider alias for &f" + dependency.getDeclaredName() + "&8)"
                        : "";
                yield "&aEnabled &f" + resolved + version + alias;
            }
            case DISABLED -> {
                String resolved = dependency.getResolvedPluginName() == null
                        ? dependency.getDeclaredName()
                        : dependency.getResolvedPluginName();
                yield "&eLoaded but disabled &f" + resolved;
            }
            case MISSING -> "&cMissing";
        };
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
                send(
                        sender,
                        "&a- " + service.getProviderName(registration) + " &7("
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
            send(
                    sender,
                    "&e" + report.getAddonName() + "&7: scanned &f" + report.getScannedEntries()
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
        send(
                sender,
                "&7Totals: issues &e" + totalIssues + " &8| &7repaired &a" + totalRepaired + " &8| &7failures &c"
                        + totalFailures);
    }

    private void sendAddonCompatibility(CommandSender sender, String[] args) {
        Slimefun.getAddonCompatibilityService().refresh();

        if (args.length > 2 && args[2].equalsIgnoreCase("api")) {
            sendAddonApiCompatibility(sender, args.length > 3 ? args[3] : null);
            return;
        }

        if (args.length > 2) {
            var result = Slimefun.getAddonCompatibilityService().getResult(args[2]);
            if (result.isEmpty()) {
                send(sender, "&cNo installed Slimefun addon matched: &e" + args[2]);
                return;
            }
            sendCompatibilityResult(sender, result.orElseThrow());
            return;
        }

        List<AddonCompatibilityResult> results =
                Slimefun.getAddonCompatibilityService().getResults();
        AddonCompatibilitySummary summary = AddonCompatibilitySummary.from(results);
        long ciMonitored = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .map(result -> knownAddonRegistry.find(result.getPluginName()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::orElseThrow)
                .filter(KnownAddonSupport::isCiMonitored)
                .count();
        long recognized = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .map(result -> knownAddonRegistry.find(result.getPluginName()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::orElseThrow)
                .filter(KnownAddonSupport::isRecognizedOnly)
                .count();
        long unknown = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .filter(result ->
                        knownAddonRegistry.find(result.getPluginName()).isEmpty())
                .count();

        send(sender, "&6Slimefun Addon Compatibility Evidence");
        send(
                sender,
                "&7Running core: &e"
                        + Slimefun.getAddonCompatibilityService()
                                .getRunningCoreVariant()
                                .getDisplayName());
        send(
                sender,
                "&7Declared compatible: &a" + summary.getCount(AddonCompatibilityStatus.COMPATIBLE)
                        + " &8| &7CI monitored: &b" + ciMonitored
                        + " &8| &7Recognized: &9" + recognized
                        + " &8| &7Unknown: &7" + unknown);
        send(
                sender,
                "&7Warnings: &e" + summary.getCount(AddonCompatibilityStatus.WARNING)
                        + " &8| &7Incompatible: &c" + summary.getCount(AddonCompatibilityStatus.INCOMPATIBLE)
                        + " &8| &7Disabled: &c" + summary.getCount(AddonCompatibilityStatus.DISABLED));

        if (summary.getTotal() == 0) {
            send(sender, "&7No Slimefun addon plugins are installed.");
            return;
        }

        for (AddonCompatibilityResult result : results) {
            sendCompatibilityEvidenceLine(sender, result);
        }
        send(sender, "&7Inspect one addon: &e/sf doctor compatibility <plugin>");
        send(
                sender,
                "&8CI monitoring and recognition are evidence levels; neither silently promotes an undeclared JAR "
                        + "to API status Compatible.");
    }

    private void sendAddonApiCompatibility(CommandSender sender, String pluginName) {
        AddonApiCompatibilitySnapshot api =
                Slimefun.getAddonApiCompatibilityFacade().getSnapshot();
        AddonRegistrationRuntimeSnapshot registration =
                Slimefun.getAddonRegistrationService().getSnapshot();

        send(sender, "&6Slimefun Cross-Fork Addon API");
        send(sender, "&7Running core: &e" + api.getRunningCoreVariant().getDisplayName());
        send(
                sender,
                "&7Compatibility targets: &e"
                        + api.getCompatibilityTargets().stream()
                                .map(variant -> variant.getDisplayName())
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .collect(Collectors.joining(", ")));
        send(sender, "&7Facade capabilities: &e" + api.getCapabilities().size());
        send(
                sender,
                "&7Initial registration: "
                        + (api.isInitialRegistrationFinalized() ? "&aFinalized" : "&eBuilding")
                        + " &8| &7pending callbacks &e" + registration.getPendingCallbacks()
                        + " &8| &7runtime-added items &e" + api.getRuntimeRegisteredItems());
        send(
                sender,
                "&8Targets mean Legacy preserves representative API contracts for those core families; "
                        + "they are not a guarantee for every exact addon JAR.");

        if (pluginName == null || pluginName.isBlank()) {
            send(sender, "&7Inspect one addon: &e/sf doctor compatibility api <plugin>");
            return;
        }

        var result = Slimefun.getAddonCompatibilityService().getResult(pluginName);
        if (result.isEmpty()) {
            send(sender, "&cNo installed Slimefun addon matched: &e" + pluginName);
            return;
        }

        AddonCompatibilityResult compatibility = result.orElseThrow();
        send(sender, "&6Addon API Path: &e" + compatibility.getPluginName() + " v" + compatibility.getPluginVersion());
        send(sender, "&7Compatibility evidence: " + compatibilityEvidence(compatibility));
        send(sender, "&7Declaration source: &e" + compatibility.getSource().getDisplayName());

        var addonRegistration = Slimefun.getAddonRegistrationService().getAddonSnapshot(compatibility.getPluginName());
        if (addonRegistration.isPresent()) {
            AddonRegistrationSnapshot snapshot = addonRegistration.orElseThrow();
            send(
                    sender,
                    "&7Registry ownership: items &e" + snapshot.getRegisteredItems() + " &8| &7groups &e"
                            + snapshot.getItemGroups() + " &8| &7tickers &e" + snapshot.getTickingItems());
            send(
                    sender,
                    "&7Post-registration callbacks: pending &e" + snapshot.getPendingCallbacks()
                            + " &8| &7completed &a" + snapshot.getExecutedCallbacks() + " &8| &7failed &c"
                            + snapshot.getFailedCallbacks() + " &8| &7skipped-disabled &e"
                            + snapshot.getSkippedDisabledCallbacks());
        } else {
            send(sender, "&7Registry ownership: &7No registered items/groups or compatibility callbacks observed");
        }

        var runtimeFailure = Slimefun.getAddonRuntimeHealthService().getFailure(compatibility.getPluginName());
        send(
                sender,
                runtimeFailure.isPresent()
                        ? "&7Guarded callback boundary: &eFailures have been observed; use /sf doctor compatibility <plugin> for details"
                        : "&7Guarded callback boundary: &aNo failures observed");
    }

    private void sendCompatibilityEvidenceLine(CommandSender sender, AddonCompatibilityResult result) {
        String evidence = compatibilityEvidence(result);
        send(
                sender,
                statusColor(result.getStatus()) + "- " + result.getPluginName() + " v" + result.getPluginVersion()
                        + " &7— " + evidence);
        if (result.getStatus() == AddonCompatibilityStatus.WARNING
                || result.getStatus() == AddonCompatibilityStatus.INCOMPATIBLE
                || result.getStatus() == AddonCompatibilityStatus.DISABLED) {
            for (String message : result.getMessages()) {
                send(sender, "&8  - &7" + message);
            }
        }
    }

    private void sendCompatibilityResult(CommandSender sender, AddonCompatibilityResult result) {
        send(sender, "&6Addon Compatibility Evidence: &e" + result.getPluginName() + " v" + result.getPluginVersion());
        boolean disabled = result.getStatus() == AddonCompatibilityStatus.DISABLED;
        send(sender, "&7Runtime load: " + (disabled ? "&cDisabled" : "&aPlugin enabled"));
        send(
                sender,
                "&7Compatibility result: " + statusColor(result.getStatus())
                        + result.getStatus().getDisplayName());
        send(sender, "&7Declaration source: &e" + result.getSource().getDisplayName());

        var knownSupport = knownAddonRegistry.find(result.getPluginName());
        if (knownSupport.isPresent()) {
            KnownAddonSupport support = knownSupport.orElseThrow();
            if (support.isCiMonitored()) {
                send(
                        sender,
                        "&7Legacy registry: &bCI monitored &8(" + support.getTierDisplayName() + ", " + support.slug()
                                + ")");
                send(sender, "&8  CI coverage is evidence for the addon family, not a guarantee for this exact JAR.");
            } else {
                send(
                        sender,
                        "&7Legacy registry: &9Recognized only &8(" + support.displayName() + ", " + support.slug()
                                + ")");
                send(sender, "&8  Recognition confirms identity only; this addon is not currently CI monitored.");
            }
        } else {
            send(sender, "&7Legacy registry: &7No known addon-family mapping");
        }

        long activeMachineFailures = Slimefun.getTickerTask().getMachineFailureSnapshots(100).stream()
                .filter(failure -> failure.getAddonName().equalsIgnoreCase(result.getPluginName()))
                .count();
        if (activeMachineFailures == 0) {
            send(sender, "&7Runtime machine health: &aNo active isolated machine failures");
        } else {
            send(sender, "&7Runtime machine health: &e" + activeMachineFailures + " active failure location(s)");
        }

        var callbackFailure = Slimefun.getAddonRuntimeHealthService().getFailure(result.getPluginName());
        if (callbackFailure.isPresent()) {
            AddonRuntimeFailureSnapshot failure = callbackFailure.orElseThrow();
            send(sender, "&7Addon callback health: &e" + failure.getObservedFailures() + " failure(s) observed");
            send(
                    sender,
                    "&8  Last guarded callback: &7" + failure.getOperation() + " &8| &7"
                            + simpleFailureName(failure.getExceptionClass()) + ": " + failure.getMessage());
            if (isLinkageFailureClass(failure.getExceptionClass())) {
                send(sender, "&7Guarded runtime linkage evidence: &eObserved");
            }
        } else {
            send(sender, "&7Addon callback health: &aNo guarded callback failures observed");
            send(sender, "&7Guarded runtime linkage evidence: &aNone observed");
        }
        send(
                sender,
                "&8  Guarded callback evidence covers callbacks executed through Slimefun's boundary only; "
                        + "it does not intercept arbitrary third-party plugin onEnable failures.");

        var dependencySnapshot = new PluginDependencyDiagnosticsService(plugin).findPlugin(result.getPluginName());
        if (dependencySnapshot.isPresent()) {
            PluginDependencySnapshot snapshot = dependencySnapshot.orElseThrow();
            long problems = snapshot.getRequiredDependencyProblemCount();
            long providerAliases = snapshot.getRequiredDependencies().stream()
                    .filter(PluginDependencyResolution::isProviderAlias)
                    .count();
            send(
                    sender,
                    "&7Declared hard dependencies: &e"
                            + snapshot.getRequiredDependencies().size()
                            + " &8| &7problems: " + (problems == 0 ? "&a0" : "&c" + problems)
                            + " &8| &7provider aliases: " + (providerAliases == 0 ? "&a0" : "&e" + providerAliases));
            for (PluginDependencyResolution dependency : snapshot.getRequiredDependencies()) {
                if (dependency.isProblem()) {
                    send(sender, "&8  - &7" + dependency.getDeclaredName() + ": " + dependencyState(dependency));
                } else if (dependency.isProviderAlias()) {
                    send(
                            sender,
                            "&8  - &eProvider alias warning: &7" + dependency.getDeclaredName()
                                    + " -> " + dependency.getResolvedPluginName()
                                    + " &8(descriptor resolution only; not Java/API proof)");
                }
            }

            if (disabled) {
                if (problems > 0) {
                    send(
                            sender,
                            "&7Startup evidence: &eA missing/disabled declared hard dependency can prevent this addon from enabling.");
                } else {
                    send(
                            sender,
                            "&7Startup evidence: &eDeclared hard dependencies are satisfied, but the addon is disabled.");
                    send(
                            sender,
                            "&8  Slimefun cannot infer the plugin-side startup cause; inspect the server console and addon configuration.");
                }
            }
        } else if (disabled) {
            send(sender, "&7Startup evidence: &eDependency metadata was unavailable for this disabled addon.");
            send(
                    sender,
                    "&8  Slimefun cannot infer the plugin-side startup cause; inspect the server console and addon configuration.");
        }

        boolean compatibilityLinkageWarning = result.getMessages().stream()
                .map(message -> message.toLowerCase(Locale.ROOT))
                .anyMatch(message -> message.contains("linkage") || message.contains("provider failed"));
        send(
                sender,
                compatibilityLinkageWarning
                        ? "&7Compatibility-layer linkage signal: &eA provider/linkage warning was observed"
                        : "&7Compatibility-layer linkage signal: &aNo provider/linkage failure observed during inspection");
        send(
                sender,
                "&8  This compatibility-layer signal is not a full bytecode proof; GitHub compatibility CI remains "
                        + "the stronger binary/source check for monitored addon builds.");

        if (result.getMessages().isEmpty()) {
            send(sender, "&aNo compatibility-layer problems were detected.");
            return;
        }
        for (String message : result.getMessages()) {
            send(sender, "&8- &7" + message);
        }
    }

    private boolean isLinkageFailureClass(String exceptionClass) {
        String name = simpleFailureName(exceptionClass).toLowerCase(Locale.ROOT);
        return name.equals("linkageerror")
                || name.equals("noclassdeffounderror")
                || name.equals("classnotfoundexception")
                || name.equals("nosuchmethoderror")
                || name.equals("nosuchfielderror")
                || name.equals("incompatibleclasschangeerror")
                || name.equals("abstractmethoderror")
                || name.equals("unsatisfiedlinkerror");
    }

    private String compatibilityEvidence(AddonCompatibilityResult result) {
        return switch (result.getStatus()) {
            case COMPATIBLE -> "&a✔ Compatible &8(declared + runtime checks passed)";
            case WARNING -> "&e⚠ Compatible with warnings";
            case INCOMPATIBLE -> "&c✕ Incompatible";
            case DISABLED -> "&c✕ Disabled";
            case UNDECLARED ->
                knownAddonRegistry
                        .find(result.getPluginName())
                        .map(support -> support.isCiMonitored()
                                ? "&b◉ Known addon — Legacy CI monitored"
                                : "&9● Recognized addon — compatibility not verified")
                        .orElse("&7? Slimefun addon — compatibility unknown");
        };
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
        send(
                sender,
                "&7Stacks scanned: &e" + report.getScannedStacks() + " &8| &7Slimefun: &e"
                        + report.getSlimefunStacks());
        send(
                sender,
                "&7Chinese presentation: &e" + report.getCjkStacks() + " &8| &7Repaired: &a"
                        + report.getRepairedStacks());
        send(
                sender,
                "&7Unknown IDs: &e" + report.getUnknownIds() + " &8| &7No English template: &e"
                        + report.getUnresolvedTemplates() + " &8| &7Failures: &c" + report.getFailures());
        if (!report.getUnknownIdSamples().isEmpty()) {
            send(sender, "&7Unknown ID samples: &e" + String.join(", ", report.getUnknownIdSamples()));
        }
        if (!report.getUnresolvedTemplateSamples().isEmpty()) {
            send(
                    sender,
                    "&7Unresolved template samples: &e" + String.join(", ", report.getUnresolvedTemplateSamples()));
        }
        if (report.isComplete()) {
            send(sender, "&7Duration: &e" + Math.max(1L, report.getDurationMillis() / 1000L) + " second(s)");
        }
    }

    private void sendUsage(CommandSender sender) {
        send(
                sender,
                "&eUsage: /slimefun doctor [status|core|registry|chunks|hand|inventory [player]|scan|repair confirm|addons]");
        send(
                sender,
                "&e       /slimefun doctor [compatibility [api <plugin>]|dependencies [plugin]|runtime [retry [all]]]");
        send(sender, "&e       /slimefun doctor [integrations [probe|reload|retry <id|all>]]");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }
}
