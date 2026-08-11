package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import city.norain.slimefun4.utils.EnvUtil;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySummary;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformProfile;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformSupportLevel;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessSnapshot;
import io.github.thebusybiscuit.slimefun4.api.runtime.CoreReadinessState;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.BlockDataRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.world.WorldChunkRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyResolution;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencySnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SchedulerSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/** Read-only runtime evidence intended to help operators evaluate a Slimefun upgrade. */
final class UpgradeDiagnostics {

    private static final Pattern CANDIDATE_VERSION = Pattern.compile(
            "\\\"candidate\\\"\\s*:\\s*\\{[^}]*\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.DOTALL);
    private static final Pattern PREVIOUS_STABLE_VERSION = Pattern.compile(
            "\\\"previous_stable\\\"\\s*:\\s*\\{[^}]*\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.DOTALL);
    private static final String BASELINE_RESOURCE = "compatibility/release-baselines.json";

    private UpgradeDiagnostics() {}

    static void send(@Nonnull Slimefun plugin, @Nonnull CommandSender sender) {
        PlatformProfile platform = Slimefun.getPlatformCompatibilityService().getProfile();
        CoreReadinessSnapshot readiness = Slimefun.getCoreReadinessService().getSnapshot();
        RegistryRuntimeSnapshot registry = Slimefun.getRegistryRuntimeService().getSnapshot();
        SchedulerSnapshot scheduler = Slimefun.getSchedulerService().getSnapshot();
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        WorldChunkRuntimeSnapshot chunks = Slimefun.getWorldChunkRuntimeService().getSnapshot();
        BlockDataRuntimeSnapshot blocks = Slimefun.getBlockDataRuntimeService().getSnapshot();
        BaselineInfo baselines = readBaselines();

        PluginDependencyDiagnosticsService dependencyDiagnostics = new PluginDependencyDiagnosticsService(plugin);
        List<PluginDependencySnapshot> dependencySnapshots = dependencyDiagnostics.getSnapshots();
        long missingRequired = dependencySnapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(dependency -> dependency.getState() == PluginDependencyResolution.State.MISSING)
                .count();
        long disabledRequired = dependencySnapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(dependency -> dependency.getState() == PluginDependencyResolution.State.DISABLED)
                .count();
        long providerAliases = dependencySnapshots.stream()
                .flatMap(snapshot -> snapshot.getRequiredDependencies().stream())
                .filter(PluginDependencyResolution::isProviderAlias)
                .count();

        List<AddonCompatibilityResult> compatibilityResults =
                Slimefun.getAddonCompatibilityService().getResults();
        AddonCompatibilitySummary compatibility = AddonCompatibilitySummary.from(compatibilityResults);
        long undeclared = compatibility.getCount(AddonCompatibilityStatus.UNDECLARED);
        long addonLinkageSignals = Slimefun.getAddonRuntimeHealthService().getFailures().stream()
                .filter(failure -> isLinkageFailureClass(failure.getExceptionClass()))
                .count();
        long externalLinkageSignals = Slimefun.getExternalIntegrationService().getFailureSnapshots(100).stream()
                .filter(failure -> isLinkageFailureClass(failure.getFailureType()))
                .count();
        long compatibilityLinkageSignals = compatibilityResults.stream()
                .flatMap(result -> result.getMessages().stream())
                .map(message -> message.toLowerCase(Locale.ROOT))
                .filter(message -> message.contains("linkage") || message.contains("provider failed"))
                .count();
        long linkageSignals = addonLinkageSignals + externalLinkageSignals + compatibilityLinkageSignals;

        AddonRegistrationRuntimeSnapshot registration =
                Slimefun.getAddonRegistrationService().getSnapshot();
        ItemDoctorReport itemDoctor = Slimefun.getItemDoctorService().getLastReport();

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        evaluatePlatform(platform, blockers, warnings);
        evaluateCore(readiness, registry, scheduler, storage, blockers, warnings);

        if (missingRequired > 0 || disabledRequired > 0) {
            blockers.add("Required plugin dependencies are missing or disabled");
        }
        if (providerAliases > 0) {
            warnings.add("Required dependencies are satisfied through provider aliases; descriptor resolution is not API proof");
        }
        if (compatibility.getCount(AddonCompatibilityStatus.INCOMPATIBLE) > 0) {
            blockers.add("One or more installed Slimefun addons report an incompatible compatibility result");
        }
        if (compatibility.getCount(AddonCompatibilityStatus.DISABLED) > 0) {
            warnings.add("One or more detected Slimefun addons are disabled");
        }
        if (compatibility.getCount(AddonCompatibilityStatus.WARNING) > 0) {
            warnings.add("One or more Slimefun addons report compatibility warnings");
        }
        if (machines.getActiveMachineFailures() > 0 || machines.getPausedMachineCircuits() > 0) {
            warnings.add("Machine runtime isolation currently has failing or paused locations");
        }
        if (Slimefun.getAddonRuntimeHealthService().getFailures().size() > 0) {
            warnings.add("Guarded addon callback failures have been observed since startup");
        }
        if (Slimefun.getExternalIntegrationService().getActiveFailureCount() > 0) {
            warnings.add("External integration operations are currently degraded or isolated");
        }
        if (linkageSignals > 0) {
            warnings.add("Runtime/provider linkage signals have been observed");
        }
        if (!storage.wasPreviousShutdownClean()) {
            warnings.add("The previous Slimefun storage shutdown was not clean");
        }
        if (storage.getPendingWrites() > 0) {
            warnings.add("Database writes are currently pending");
        }
        if (chunks.getFailedChunks() > 0 || chunks.getUnsafeChunks() > 0) {
            warnings.add("Chunk lifecycle diagnostics currently contain failed or unsafe chunks");
        }
        if (blocks.getChunkLoadFailures() > 0 || blocks.getUnknownSlimefunIds() > 0) {
            warnings.add("Block-data diagnostics contain load failures or unknown Slimefun IDs");
        }
        if (registration.getPendingCallbacks() > 0) {
            warnings.add("Addon post-registration callbacks are still pending");
        }
        if (itemDoctor != null
                && (itemDoctor.getUnknownIds() > 0
                        || itemDoctor.getUnresolvedTemplates() > 0
                        || itemDoctor.getFailures() > 0)) {
            warnings.add("The last Item Doctor run retained unresolved IDs/templates or failures");
        }
        if (!baselines.available()) {
            warnings.add("Packaged release-baseline metadata is unavailable");
        } else if (!Slimefun.getVersion().equals(baselines.candidate())) {
            blockers.add("Running Slimefun version does not match the packaged candidate baseline");
        }

        String status;
        String statusColor;
        if (!blockers.isEmpty()) {
            status = "BLOCKED";
            statusColor = "&c";
        } else if (!warnings.isEmpty()) {
            status = "ATTENTION";
            statusColor = "&e";
        } else {
            status = "READY";
            statusColor = "&a";
        }

        send(sender, "&6Slimefun Upgrade Readiness");
        send(sender, "&7Overall status: " + statusColor + status);
        send(
                sender,
                "&7Core: &f" + Slimefun.getVersion() + " &8| &7candidate &e" + baselines.candidate()
                        + " &8| &7previous stable &e" + baselines.previousStable());
        send(
                sender,
                "&7Source: &f" + EnvUtil.getBuildCommitID() + " &8| &7branch &f" + EnvUtil.getBranch()
                        + " &8| &7build time &f" + EnvUtil.getBuildTime());
        send(
                sender,
                "&7Platform: &f" + platform.getSoftwareName() + " " + platform.getServerVersion() + " &8| &7Minecraft &f"
                        + platform.getRawMinecraftVersion() + " &8| &7Java &f" + platform.getJavaFeatureVersion()
                        + " &8| " + supportColor(platform.getSupportLevel()) + platform.getSupportLevel().getDisplayName());
        send(
                sender,
                "&7Core runtime: &f" + readiness.getState() + " &8| &7registry "
                        + (registry.isInitialRegistrationFinalized() ? "&aFinalized" : "&eBuilding")
                        + " &8| &7scheduler accepting " + (scheduler.isAcceptingTasks() ? "&aYes" : "&cNo"));
        send(
                sender,
                "&7Storage: " + (storage.isReady() ? "&aReady" : "&cNot ready") + " &8| &7previous shutdown "
                        + (storage.wasPreviousShutdownClean() ? "&aClean" : "&eUnclean")
                        + " &8| &7pending writes &e" + storage.getPendingWrites());
        send(
                sender,
                "&7Dependencies: missing required &c" + missingRequired + " &8| &7disabled required &e"
                        + disabledRequired + " &8| &7provider aliases &e" + providerAliases);
        send(
                sender,
                "&7Addons: installed &e" + Slimefun.getInstalledAddons().size() + " &8| &7compatible &a"
                        + compatibility.getCount(AddonCompatibilityStatus.COMPATIBLE) + " &8| &7warning &e"
                        + compatibility.getCount(AddonCompatibilityStatus.WARNING) + " &8| &7undeclared &7" + undeclared
                        + " &8| &7incompatible/disabled &c"
                        + (compatibility.getCount(AddonCompatibilityStatus.INCOMPATIBLE)
                                + compatibility.getCount(AddonCompatibilityStatus.DISABLED)));
        send(
                sender,
                "&7Runtime failures: machines &e" + machines.getActiveMachineFailures() + " &8| &7paused &e"
                        + machines.getPausedMachineCircuits() + " &8| &7addon records &e"
                        + Slimefun.getAddonRuntimeHealthService().getFailures().size() + " &8| &7external isolated &e"
                        + Slimefun.getExternalIntegrationService().getActiveFailureCount());
        send(sender, "&7Linkage/provider signals: &e" + linkageSignals);
        send(
                sender,
                "&7World/storage evidence: failed chunks &e" + chunks.getFailedChunks() + " &8| &7unsafe chunks &e"
                        + chunks.getUnsafeChunks() + " &8| &7unknown IDs &e" + blocks.getUnknownSlimefunIds()
                        + " &8| &7block-load failures &e" + blocks.getChunkLoadFailures());
        send(
                sender,
                "&7Addon API path: targets &e"
                        + Slimefun.getAddonApiCompatibilityFacade().getSnapshot().getCompatibilityTargets().size()
                        + " &8| &7capabilities &e"
                        + Slimefun.getAddonApiCompatibilityFacade().getSnapshot().getCapabilities().size()
                        + " &8| &7pending callbacks &e" + registration.getPendingCallbacks());
        if (itemDoctor == null) {
            send(sender, "&7Item Doctor: &7No completed server-wide scan recorded this runtime");
        } else {
            send(
                    sender,
                    "&7Item Doctor: unknown IDs &e" + itemDoctor.getUnknownIds() + " &8| &7unresolved templates &e"
                            + itemDoctor.getUnresolvedTemplates() + " &8| &7failures &e" + itemDoctor.getFailures());
        }

        sendReasons(sender, "Blockers", "&c", blockers);
        sendReasons(sender, "Attention", "&e", warnings);
        send(
                sender,
                "&8READY means no current diagnostic blocker was observed; it is not a guarantee that every addon feature is compatible.");
        send(
                sender,
                "&8Read-only snapshot: this command does not migrate storage, repair items, enable/disable plugins, reload adapters, "
                        + "or alter Cargo, Energy, machines, recipes, or saved data.");
    }

    private static void evaluatePlatform(
            PlatformProfile platform, List<String> blockers, List<String> warnings) {
        if (platform.getSupportLevel() == PlatformSupportLevel.UNSUPPORTED) {
            blockers.add("Detected server platform is unsupported by this Slimefun Legacy release");
        } else if (platform.getSupportLevel() == PlatformSupportLevel.UNKNOWN) {
            warnings.add("Detected server platform support level is unknown");
        } else if (platform.getSupportLevel() == PlatformSupportLevel.EXPERIMENTAL
                || platform.getSupportLevel() == PlatformSupportLevel.BEST_EFFORT) {
            warnings.add("Detected server platform is not on the primary fully-supported runtime path");
        }
        if (platform.getJavaFeatureVersion() != 25) {
            warnings.add("This release is built and tested primarily on Java 25");
        }
    }

    private static void evaluateCore(
            CoreReadinessSnapshot readiness,
            RegistryRuntimeSnapshot registry,
            SchedulerSnapshot scheduler,
            StorageRuntimeSnapshot storage,
            List<String> blockers,
            List<String> warnings) {
        if (readiness.getState() == CoreReadinessState.FAILED
                || readiness.getState() == CoreReadinessState.STOPPED
                || readiness.getState() == CoreReadinessState.STOPPING) {
            blockers.add("Slimefun core readiness is " + readiness.getState());
        } else if (readiness.getState() != CoreReadinessState.READY) {
            warnings.add("Slimefun core readiness is " + readiness.getState());
        }
        if (!registry.isInitialRegistrationFinalized()) {
            warnings.add("Initial addon/item registration has not finalized");
        }
        if (!scheduler.isAcceptingTasks()) {
            blockers.add("Slimefun scheduler is not accepting tasks");
        }
        if (!storage.isReady()) {
            blockers.add("Slimefun storage runtime is not ready");
        }
    }

    private static BaselineInfo readBaselines() {
        try (InputStream stream = UpgradeDiagnostics.class.getClassLoader().getResourceAsStream(BASELINE_RESOURCE)) {
            if (stream == null) {
                return BaselineInfo.unavailable();
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String candidate = extractVersion(CANDIDATE_VERSION, json);
            String previous = extractVersion(PREVIOUS_STABLE_VERSION, json);
            if (candidate == null || previous == null) {
                return BaselineInfo.unavailable();
            }
            return new BaselineInfo(candidate, previous, true);
        } catch (IOException error) {
            return BaselineInfo.unavailable();
        }
    }

    private static String extractVersion(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean isLinkageFailureClass(String exceptionClass) {
        int separator = exceptionClass.lastIndexOf('.');
        String name = (separator >= 0 ? exceptionClass.substring(separator + 1) : exceptionClass)
                .toLowerCase(Locale.ROOT);
        return name.equals("linkageerror")
                || name.equals("noclassdeffounderror")
                || name.equals("classnotfoundexception")
                || name.equals("nosuchmethoderror")
                || name.equals("nosuchfielderror")
                || name.equals("incompatibleclasschangeerror")
                || name.equals("abstractmethoderror")
                || name.equals("unsatisfiedlinkerror");
    }

    private static String supportColor(PlatformSupportLevel level) {
        return switch (level) {
            case SUPPORTED -> "&a";
            case EXPERIMENTAL, BEST_EFFORT -> "&e";
            case UNSUPPORTED -> "&c";
            case UNKNOWN -> "&7";
        };
    }

    private static void sendReasons(CommandSender sender, String label, String color, List<String> reasons) {
        if (reasons.isEmpty()) {
            return;
        }
        send(sender, color + label + ":");
        for (String reason : reasons) {
            send(sender, "&8- " + color + reason);
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private record BaselineInfo(String candidate, String previousStable, boolean available) {
        private static BaselineInfo unavailable() {
            return new BaselineInfo("Unknown", "Unknown", false);
        }
    }
}
