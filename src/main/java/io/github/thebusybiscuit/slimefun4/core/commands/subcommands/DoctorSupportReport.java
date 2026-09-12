package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import city.norain.slimefun4.utils.EnvUtil;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySummary;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleSnapshot;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformProfile;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyResolution;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencySnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SchedulerSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Builds a compact, copy/paste-safe support report from existing Slimefun Legacy runtime snapshots. */
final class DoctorSupportReport {

    private DoctorSupportReport() {}

    static void send(@Nonnull Slimefun plugin, @Nonnull CommandSender sender) {
        Slimefun.getAddonCompatibilityService().refresh();
        Slimefun.getExternalIntegrationService().refresh();

        PlatformProfile platform = Slimefun.getPlatformCompatibilityService().getProfile();
        CoreReadinessSnapshot readiness = Slimefun.getCoreReadinessService().getSnapshot();
        CoreLifecycleSnapshot lifecycle = Slimefun.getCoreLifecycleService().getSnapshot();
        RegistryRuntimeSnapshot registry = Slimefun.getRegistryRuntimeService().getSnapshot();
        SchedulerSnapshot scheduler = Slimefun.getSchedulerService().getSnapshot();
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();

        List<AddonCompatibilityResult> addonResults = Slimefun.getAddonCompatibilityService().getResults();
        AddonCompatibilitySummary addonSummary = AddonCompatibilitySummary.from(addonResults);
        PluginDependencyDiagnosticsService dependencies = new PluginDependencyDiagnosticsService(plugin);
        List<PluginDependencySnapshot> dependencySnapshots = dependencies.getSnapshots();

        long missingRequired = dependencySnapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(dependency -> dependency.getState() == PluginDependencyResolution.State.MISSING)
                .count();
        long disabledRequired = dependencySnapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(dependency -> dependency.getState() == PluginDependencyResolution.State.DISABLED)
                .count();
        long disabledPlugins = dependencySnapshots.stream().filter(snapshot -> !snapshot.isEnabled()).count();

        long detectedIntegrations = Slimefun.getExternalIntegrationService().getStatuses().stream()
                .filter(status -> status.isDetected() && status.isEnabled())
                .count();
        long activeIntegrations = Slimefun.getExternalIntegrationService().getStatuses().stream()
                .filter(status -> status.isProviderRegistered() && status.isEnabled())
                .count();

        List<String> warnings = new ArrayList<>();
        if (!storage.wasPreviousShutdownClean()) {
            warnings.add("Previous Slimefun shutdown was not clean.");
        }
        if (storage.getPendingWrites() > 0) {
            warnings.add(storage.getPendingWrites() + " database write(s) are still pending.");
        }
        if (!scheduler.isAcceptingTasks()) {
            warnings.add("Slimefun scheduler is not accepting new tasks.");
        }
        if (!readiness.getReasons().isEmpty()) {
            warnings.add("Core readiness reports " + readiness.getReasons().size() + " reason(s) requiring attention.");
        }
        if (lifecycle.getStartupFailures() > 0 || lifecycle.getShutdownFailures() > 0) {
            warnings.add("Lifecycle failures have been observed this run.");
        }
        if (machines.getActiveMachineFailures() > 0 || machines.getPausedMachineCircuits() > 0) {
            warnings.add("Machine runtime isolation is active.");
        }
        if (Slimefun.getAddonRuntimeHealthService().getFailures().size() > 0) {
            warnings.add("One or more addon callback boundaries currently have failure records.");
        }
        if (Slimefun.getExternalIntegrationService().getActiveFailureCount() > 0) {
            warnings.add("One or more external integration operations are isolated/degraded.");
        }
        if (missingRequired > 0 || disabledRequired > 0) {
            warnings.add("Required plugin dependencies are missing or disabled.");
        }
        if (addonSummary.getCount(AddonCompatibilityStatus.WARNING) > 0
                || addonSummary.getCount(AddonCompatibilityStatus.INCOMPATIBLE) > 0
                || addonSummary.getCount(AddonCompatibilityStatus.DISABLED) > 0) {
            warnings.add("Addon compatibility contains warning/incompatible/disabled results.");
        }
        if (Slimefun.getTickerTask().isPaused() || Slimefun.getTickerTask().isHalted()) {
            warnings.add("The Slimefun machine ticker is not running normally.");
        }

        sendLine(sender, "&6========== Slimefun Legacy Doctor Report ==========");
        sendLine(sender, "&8Public-safe: no paths, IPs, credentials, player data, coordinates, config dumps, or raw exception messages.");
        sendLine(sender, "&7Result: " + (warnings.isEmpty() ? "&aHEALTHY" : "&eATTENTION (&f" + warnings.size() + "&e)"));

        sendLine(sender, "&6[Environment]");
        sendLine(sender, "&7Slimefun: &f" + Slimefun.getVersion() + " &8| &7commit &f" + EnvUtil.getBuildCommitID()
                + " &8| &7branch &f" + EnvUtil.getBranch());
        sendLine(sender, "&7Server: &f" + platform.getSoftwareName() + " " + platform.getServerVersion()
                + " &8| &7Minecraft &f" + platform.getRawMinecraftVersion());
        sendLine(sender, "&7Java: &f" + NumberUtils.getJavaVersion() + " &8| &7profile &f"
                + platform.getFamily().getDisplayName() + " / " + platform.getSupportLevel().getDisplayName());
        sendLine(sender, "&7Scheduler: &f"
                + (scheduler.isRegionOwnedExecution() ? "region-owned" : "main-thread")
                + " &8| &7accepting &f" + scheduler.isAcceptingTasks()
                + " &8| &7tracked tasks &f" + scheduler.getActiveTaskCount());

        sendLine(sender, "&6[Core]");
        sendLine(sender, "&7Readiness: &f" + readiness.getState() + " &8| &7reasons &f" + readiness.getReasons().size());
        sendLine(sender, "&7Lifecycle: &f" + lifecycle.getState() + " / " + lifecycle.getPhase()
                + " &8| &7startup failures &f" + lifecycle.getStartupFailures()
                + " &8| &7shutdown failures &f" + lifecycle.getShutdownFailures());
        sendLine(sender, "&7Registry: &f" + registry.getEnabledItems() + "/" + registry.getTotalItems()
                + " items &8| &7groups &f" + registry.getItemGroups()
                + " &8| &7runtime additions &f" + registry.getRuntimeRegisteredItems());

        sendLine(sender, "&6[Machines + Storage]");
        sendLine(sender, "&7Ticker: &f"
                + (Slimefun.getTickerTask().isPaused()
                        ? "FROZEN"
                        : Slimefun.getTickerTask().isHalted() ? "HALTED" : "RUNNING")
                + " &8| &7rate &f" + machines.getTickRate()
                + " &8| &7chunks &f" + machines.getTickingChunks()
                + " &8| &7locations &f" + machines.getTickingLocations());
        sendLine(sender, "&7Machine failures: active &f" + machines.getActiveMachineFailures()
                + " &8| &7paused &f" + machines.getPausedMachineCircuits()
                + " &8| &7observed &f" + machines.getObservedMachineFailures()
                + " &8| &7suppressed &f" + machines.getSuppressedMachineReports());
        sendLine(sender, "&7Target pauses: locations &f" + Slimefun.getTickerTask().getTargetedPausedMachineCount()
                + " &8| &7item types &f" + Slimefun.getTickerTask().getTargetedPausedItemIds().size());
        sendLine(sender, "&7Profiler last detailed sample: &f" + Slimefun.getProfiler().getTime()
                + " &8| &7rating &f" + Slimefun.getProfiler().getPerformance());
        sendLine(sender, "&7Storage: &f" + (storage.isReady() ? "ready" : "not ready")
                + " &8| &7clean shutdown &f" + storage.wasPreviousShutdownClean()
                + " &8| &7pending writes &f" + storage.getPendingWrites());
        sendLine(sender, "&7Storage backends: blocks &f" + storage.getBlockStorageType()
                + " &8| &7profiles &f" + storage.getProfileStorageType());

        sendLine(sender, "&6[Addons + Dependencies]");
        sendLine(sender, "&7Installed Slimefun addons: &f" + Slimefun.getInstalledAddons().size()
                + " &8| &7compatibility records &f" + addonSummary.getTotal());
        sendLine(sender, "&7Compatibility: &a" + addonSummary.getCount(AddonCompatibilityStatus.COMPATIBLE)
                + " compatible &8| &e" + addonSummary.getCount(AddonCompatibilityStatus.WARNING)
                + " warning &8| &c" + addonSummary.getCount(AddonCompatibilityStatus.INCOMPATIBLE)
                + " incompatible &8| &c" + addonSummary.getCount(AddonCompatibilityStatus.DISABLED)
                + " disabled &8| &7" + addonSummary.getCount(AddonCompatibilityStatus.UNDECLARED) + " undeclared");
        sendLine(sender, "&7Addon runtime failures: active &f"
                + Slimefun.getAddonRuntimeHealthService().getFailures().size()
                + " &8| &7observed &f" + Slimefun.getAddonRuntimeHealthService().getObservedFailureCount());
        sendLine(sender, "&7Plugin dependencies: inspected &f" + dependencySnapshots.size()
                + " &8| &7disabled plugins &f" + disabledPlugins
                + " &8| &7missing required &f" + missingRequired
                + " &8| &7disabled required &f" + disabledRequired);

        sendLine(sender, "&6[External Integrations]");
        sendLine(sender, "&7Adapters: detected &f" + detectedIntegrations + " &8| &7active bridges &f" + activeIntegrations);
        sendLine(sender, "&7Adapter failures: active &f" + Slimefun.getExternalIntegrationService().getActiveFailureCount()
                + " &8| &7observed &f" + Slimefun.getExternalIntegrationService().getObservedFailureCount()
                + " &8| &7suppressed &f" + Slimefun.getExternalIntegrationService().getSuppressedFailureReportCount());

        ItemDoctorReport itemDoctor = Slimefun.getItemDoctorService().getCurrentReport();
        if (itemDoctor == null) {
            itemDoctor = Slimefun.getItemDoctorService().getLastReport();
        }
        sendLine(sender, "&6[Item Doctor]");
        if (itemDoctor == null) {
            sendLine(sender, "&7Server-wide item scan: &fnot run");
        } else {
            sendLine(sender, "&7Server-wide item scan: &f" + itemDoctor.getModeName()
                    + " &8| &7complete &f" + itemDoctor.isComplete()
                    + " &8| &7scanned &f" + itemDoctor.getScannedStacks()
                    + " &8| &7unknown IDs &f" + itemDoctor.getUnknownIds()
                    + " &8| &7failures &f" + itemDoctor.getFailures());
        }

        sendLine(sender, "&6[Curiosities]");
        if (CuriositiesConfig.isEnabled()) {
            sendLine(sender, "&7Adventurer's Curios: &aENABLED &8| &7Universal Leash + Starter Utilities should be registered.");
        } else {
            sendLine(sender, "&7Adventurer's Curios: &eDISABLED");
            sendLine(sender, "&eNote: Universal Leash, Stone/Reinforced Hammers, Wooden Kama and other Curios are not registered while disabled.");
        }

        sendLine(sender, "&6[Warnings]");
        if (warnings.isEmpty()) {
            sendLine(sender, "&aNo report-level health warnings detected.");
        } else {
            for (String warning : warnings) {
                sendLine(sender, "&e- " + warning);
            }
        }
        sendLine(sender, "&8For detailed follow-up use /sf doctor core, compatibility, dependencies, runtime, integrations, or /sf tick top.");
        sendLine(sender, "&6====================================================");
    }

    private static void sendLine(@Nonnull CommandSender sender, @Nonnull String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
