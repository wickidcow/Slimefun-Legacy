package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import city.norain.slimefun4.utils.EnvUtil;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySummary;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeFailureSnapshot;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformProfile;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry.KnownAddonSupport;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyResolution;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencySnapshot;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

/**
 * This is our class for the /sf versions subcommand.
 *
 * @author TheBusyBiscuit
 * @author Walshy
 */
class VersionsCommand extends SubCommand {

    private final KnownAddonCompatibilityRegistry knownAddonRegistry;

    /**
     * This is the Java version we recommend to use.
     * Bump as necessary and adjust the warning.
     */
    private static final int RECOMMENDED_JAVA_VERSION = 21;

    /**
     * This is the notice that will be displayed when an
     * older version of Java is detected.
     */
    private static final String JAVA_VERSION_NOTICE =
            "Slimefun Legacy targets Java 21 bytecode and is built and tested using Java 25.";

    @ParametersAreNonnullByDefault
    VersionsCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "versions", false);
        knownAddonRegistry = KnownAddonCompatibilityRegistry.load(VersionsCommand.class.getClassLoader());
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (sender.hasPermission("slimefun.command.versions") || sender instanceof ConsoleCommandSender) {
            PlatformProfile platformProfile = Slimefun.getPlatformCompatibilityService().getProfile();
            String serverSoftware = platformProfile.getSoftwareName();
            String schedulerPlatform = platformProfile.isRegionOwnedExecution() ? "Region-owned" : "Main-thread";
            String capabilitySummary = platformProfile.getCapabilities().stream()
                    .map(PlatformCapability::getDisplayName)
                    .collect(Collectors.joining(", "));

            net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
            builder.append(Component.text("Slimefun server environment:\n", Style.style(NamedTextColor.GRAY)))
                    .append(Component.text(serverSoftware, Style.style(NamedTextColor.GREEN))
                            .append(Component.text(
                                    " " + platformProfile.getServerVersion() + '\n',
                                    Style.style(NamedTextColor.DARK_GREEN))))
                    .append(Component.text("Scheduler platform ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(schedulerPlatform + '\n', Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Compatibility profile ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(
                            platformProfile.getFamily().getDisplayName()
                                    + " / "
                                    + platformProfile.getSupportLevel().getDisplayName()
                                    + '\n',
                            Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Minecraft version ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(
                            platformProfile.getRawMinecraftVersion() + '\n', Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Detected capabilities ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(
                            (capabilitySummary.isEmpty() ? "None" : capabilitySummary) + '\n',
                            Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Slimefun ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(
                            Slimefun.getVersion()
                                    + (Slimefun.getVersion().toLowerCase(Locale.ROOT).contains("release")
                                            ? ""
                                            : " @" + EnvUtil.getBranch())
                                    + '\n',
                            Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Build time ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(EnvUtil.getBuildTime(), Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("\n"));

            if (Slimefun.getMetricsService().getVersion() != null) {
                builder.append(Component.text("Metrics-component ", Style.style(NamedTextColor.GREEN)))
                        .append(Component.text(
                                "#" + Slimefun.getMetricsService().getVersion() + '\n',
                                Style.style(NamedTextColor.DARK_GREEN)));
            }
            addJavaVersion(builder);

            builder.append(Component.text("\nEnglish-Albion community build", Style.style(NamedTextColor.WHITE)))
                    .append(Component.text(
                            "\nThis is an unofficial community build. Report issues to this fork.\n",
                            Style.style(NamedTextColor.RED)));
            if (Slimefun.getConfigManager().isBypassEnvironmentCheck()) {
                builder.append(Component.text(
                        "\n\nEnvironment compatibility check is disabled", Style.style(NamedTextColor.RED)));
            }

            if (Slimefun.getConfigManager().isBypassItemLengthCheck()) {
                builder.append(Component.text("\n\nItem length check is disabled", Style.style(NamedTextColor.RED)));
            }
            builder.append(Component.text("\n"));

            Slimefun.getAddonCompatibilityService().refresh();
            Slimefun.getExternalIntegrationService().refresh();
            PluginDependencyDiagnosticsService dependencyDiagnostics = new PluginDependencyDiagnosticsService(plugin);
            Collection<Plugin> addons = Slimefun.getInstalledAddons();

            addAddonCompatibilitySummary(builder);
            addExternalIntegrationSummary(builder);
            addAddonBoundarySummary(builder, addons, dependencyDiagnostics);
            addPluginVersions(builder, addons, dependencyDiagnostics);

            sendVersionReport(sender, builder.build());
        } else {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
        }
    }

    /**
     * Sends the rich Adventure report and guarantees a response if a Paper/Purpur command bridge
     * rejects component delivery. The fallback only loses hover/click metadata; it never loses the
     * diagnostic report itself.
     */
    private void sendVersionReport(@Nonnull CommandSender sender, @Nonnull Component report) {
        try {
            sender.sendMessage(report);
        } catch (RuntimeException | LinkageError ignored) {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(report));
        }
    }

    private void addJavaVersion(@Nonnull net.kyori.adventure.text.TextComponent.Builder builder) {
        int version = NumberUtils.getJavaVersion();

        if (version < RECOMMENDED_JAVA_VERSION) {
            Component hover = Component.text("Your Java version is outdated!\n"
                    + "We recommend Java "
                    + RECOMMENDED_JAVA_VERSION
                    + " or newer.\n"
                    + JAVA_VERSION_NOTICE);

            builder.append(Component.text("Java " + version, NamedTextColor.RED).hoverEvent(HoverEvent.showText(hover)))
                    .append(Component.text("\n"));
        } else {
            builder.append(Component.text("Java ", NamedTextColor.GREEN))
                    .append(Component.text(version + "\n", NamedTextColor.DARK_GREEN));
        }
    }

    private void addAddonCompatibilitySummary(
            @Nonnull net.kyori.adventure.text.TextComponent.Builder builder) {
        List<AddonCompatibilityResult> results = Slimefun.getAddonCompatibilityService().getResults();
        AddonCompatibilitySummary summary = AddonCompatibilitySummary.from(results);
        int compatible = summary.getCount(AddonCompatibilityStatus.COMPATIBLE);
        int warning = summary.getCount(AddonCompatibilityStatus.WARNING);
        int incompatible = summary.getCount(AddonCompatibilityStatus.INCOMPATIBLE);
        int disabled = summary.getCount(AddonCompatibilityStatus.DISABLED);
        long known = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .map(result -> knownAddonRegistry.find(result.getPluginName()))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .filter(KnownAddonSupport::isCiMonitored)
                .count();
        long recognized = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .map(result -> knownAddonRegistry.find(result.getPluginName()))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .filter(KnownAddonSupport::isRecognizedOnly)
                .count();
        long unknown = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .filter(result -> knownAddonRegistry.find(result.getPluginName()).isEmpty())
                .count();

        builder.append(Component.text("Compatibility: ", NamedTextColor.GREEN))
                .append(Component.text(compatible + " Compatible", NamedTextColor.DARK_GREEN))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(known + " Known", NamedTextColor.AQUA))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(recognized + " Recognized", NamedTextColor.BLUE))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(warning + " Warning", NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(unknown + " Unknown", NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(incompatible + " Incompatible", NamedTextColor.RED))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(disabled + " Disabled\n", NamedTextColor.DARK_RED))
                .append(Component.text("Hover an addon's status for compatibility details.\n", NamedTextColor.DARK_GRAY));
    }

    private void addExternalIntegrationSummary(
            @Nonnull net.kyori.adventure.text.TextComponent.Builder builder) {
        long detected = Slimefun.getExternalIntegrationService().getStatuses().stream()
                .filter(status -> status.isDetected() && status.isEnabled())
                .count();
        long bridged = Slimefun.getExternalIntegrationService().getStatuses().stream()
                .filter(status -> status.isProviderRegistered() && status.isEnabled())
                .count();
        if (detected > 0 || bridged > 0) {
            builder.append(Component.text("External systems ", NamedTextColor.GREEN))
                    .append(Component.text(detected + " detected, " + bridged + " bridged\n", NamedTextColor.DARK_GREEN));
        }
    }

    private void addAddonBoundarySummary(
            @Nonnull net.kyori.adventure.text.TextComponent.Builder builder,
            @Nonnull Collection<Plugin> addons,
            @Nonnull PluginDependencyDiagnosticsService dependencies) {
        if (addons.isEmpty()) {
            return;
        }

        long ready = 0L;
        long attention = 0L;
        long unknown = 0L;
        long providerAliasAddons = 0L;
        Set<String> addonNames = new HashSet<>();

        for (Plugin addon : addons) {
            addonNames.add(addon.getName().toLowerCase(Locale.ROOT));
            Optional<PluginDependencySnapshot> snapshot = dependencies.findPlugin(addon.getName());
            if (snapshot.isEmpty()) {
                unknown++;
                continue;
            }

            PluginDependencySnapshot dependencySnapshot = snapshot.orElseThrow();
            if (dependencySnapshot.hasRequiredDependencyProblems()) {
                attention++;
            } else {
                ready++;
            }
            if (hasRequiredProviderAlias(dependencySnapshot)) {
                providerAliasAddons++;
            }
        }

        List<AddonRuntimeFailureSnapshot> guardedFailures = Slimefun.getAddonRuntimeHealthService().getFailures().stream()
                .filter(failure -> addonNames.contains(failure.getPluginName().toLowerCase(Locale.ROOT)))
                .toList();
        long observedGuardedFailures = guardedFailures.stream()
                .mapToLong(AddonRuntimeFailureSnapshot::getObservedFailures)
                .sum();

        builder.append(Component.text("Addon dependency health: ", NamedTextColor.GREEN))
                .append(Component.text(ready + " ready", NamedTextColor.DARK_GREEN))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(attention + " attention", attention == 0 ? NamedTextColor.DARK_GREEN : NamedTextColor.RED))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(unknown + " unknown", unknown == 0 ? NamedTextColor.DARK_GREEN : NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(providerAliasAddons + " provider-alias", providerAliasAddons == 0
                        ? NamedTextColor.DARK_GREEN
                        : NamedTextColor.YELLOW))
                .append(Component.text("\n"));
        builder.append(Component.text("Guarded addon callbacks: ", NamedTextColor.GREEN))
                .append(Component.text(
                        guardedFailures.size() + " addon(s) with failures", guardedFailures.isEmpty()
                                ? NamedTextColor.DARK_GREEN
                                : NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(
                        observedGuardedFailures + " observed", observedGuardedFailures == 0
                                ? NamedTextColor.DARK_GREEN
                                : NamedTextColor.YELLOW))
                .append(Component.text("\n", NamedTextColor.DARK_GRAY));
        builder.append(Component.text(
                "Boundary evidence is observational: Slimefun does not intercept arbitrary third-party plugin startup failures.\n",
                NamedTextColor.DARK_GRAY));
    }

    private Component compatibilityComponent(
            @Nonnull AddonCompatibilityResult result,
            @Nonnull Optional<PluginDependencySnapshot> dependencySnapshot,
            @Nonnull Optional<AddonRuntimeFailureSnapshot> runtimeFailure) {
        NamedTextColor color;
        String label;
        String explanation;
        Optional<KnownAddonSupport> knownSupport = knownAddonRegistry.find(result.getPluginName());

        switch (result.getStatus()) {
            case COMPATIBLE -> {
                color = NamedTextColor.DARK_GREEN;
                label = "Compatible";
                explanation = "This addon declared compatibility and passed the current Slimefun Legacy runtime checks.";
            }
            case WARNING -> {
                color = NamedTextColor.YELLOW;
                label = "Warning";
                explanation = "No hard incompatibility was detected, but one or more compatibility warnings need review.";
            }
            case UNDECLARED -> {
                if (knownSupport.isPresent()) {
                    KnownAddonSupport support = knownSupport.orElseThrow();
                    if (support.isCiMonitored()) {
                        color = NamedTextColor.AQUA;
                        label = "Known";
                        explanation = "Slimefun Legacy recognizes this addon family and monitors it in compatibility CI. "
                                + "The exact installed JAR did not declare compatibility, so this is useful evidence but "
                                + "not a guarantee for this exact build.";
                    } else {
                        color = NamedTextColor.BLUE;
                        label = "Recognized";
                        explanation = "Slimefun Legacy recognizes this addon family/name, but it is not currently in "
                                + "the Legacy compatibility CI matrix and this installed JAR did not declare compatibility.";
                    }
                } else {
                    color = NamedTextColor.GRAY;
                    label = "Unknown";
                    explanation = "Slimefun detected this as an addon, but it has no Legacy compatibility declaration and "
                            + "is not currently mapped to the Legacy addon recognition registry. This does not mean it is "
                            + "incompatible.";
                }
            }
            case DISABLED -> {
                color = NamedTextColor.DARK_RED;
                label = "Disabled";
                explanation = "The addon is disabled, so Slimefun cannot verify runtime compatibility.";
            }
            case INCOMPATIBLE -> {
                color = NamedTextColor.RED;
                label = "Incompatible";
                explanation = "The addon's declared requirements do not match the current Slimefun/server environment.";
            }
            default -> throw new IllegalStateException("Unhandled addon compatibility status: " + result.getStatus());
        }

        StringBuilder hoverText = new StringBuilder(result.getPluginName())
                .append(" v")
                .append(result.getPluginVersion())
                .append("\nStatus: ")
                .append(label)
                .append("\n\n")
                .append(explanation)
                .append("\nCompatibility source: ")
                .append(result.getSource().getDisplayName());
        knownSupport.ifPresent(support -> hoverText.append("\nLegacy registry: ")
                .append(support.displayName())
                .append(" (")
                .append(support.getTierDisplayName())
                .append(", ")
                .append(support.slug())
                .append(')'));
        appendBoundaryEvidence(hoverText, dependencySnapshot, runtimeFailure, result.getStatus() == AddonCompatibilityStatus.DISABLED);
        if (!result.getMessages().isEmpty()) {
            hoverText.append("\n\nDetails:\n- ").append(String.join("\n- ", result.getMessages()));
        }

        return Component.text(label, color)
                .hoverEvent(HoverEvent.showText(Component.text(hoverText.toString())));
    }

    private Component uncheckedCompatibilityComponent(
            @Nonnull Plugin addon,
            @Nonnull Optional<PluginDependencySnapshot> dependencySnapshot,
            @Nonnull Optional<AddonRuntimeFailureSnapshot> runtimeFailure) {
        StringBuilder hoverText = new StringBuilder("Status: Unknown\n\n")
                .append("Slimefun did not receive a compatibility result for this addon. ")
                .append("The addon may still be loaded, but its compatibility status is unknown.");
        appendBoundaryEvidence(hoverText, dependencySnapshot, runtimeFailure, !addon.isEnabled());
        return Component.text("Unknown", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(hoverText.toString())));
    }

    private void appendBoundaryEvidence(
            @Nonnull StringBuilder hoverText,
            @Nonnull Optional<PluginDependencySnapshot> dependencySnapshot,
            @Nonnull Optional<AddonRuntimeFailureSnapshot> runtimeFailure,
            boolean disabled) {
        hoverText.append("\n\nAddon boundary evidence:");

        if (dependencySnapshot.isPresent()) {
            PluginDependencySnapshot snapshot = dependencySnapshot.orElseThrow();
            long problems = snapshot.getRequiredDependencyProblemCount();
            long aliases = snapshot.getRequiredDependencies().stream()
                    .filter(PluginDependencyResolution::isProviderAlias)
                    .count();
            hoverText.append("\nHard dependencies: ")
                    .append(problems == 0 ? "Ready" : problems + " problem(s)");
            for (PluginDependencyResolution dependency : snapshot.getRequiredDependencies()) {
                if (dependency.isProblem()) {
                    hoverText.append("\n- ")
                            .append(dependency.getDeclaredName())
                            .append(": ")
                            .append(dependency.getState().name().toLowerCase(Locale.ROOT));
                }
            }
            if (aliases > 0) {
                hoverText.append("\nProvider aliases: ").append(aliases)
                        .append(" (descriptor resolution only; not Java/API proof)");
            }
            if (disabled) {
                if (problems > 0) {
                    hoverText.append("\nStartup evidence: a missing/disabled declared hard dependency can prevent enable.");
                } else {
                    hoverText.append("\nStartup evidence: declared hard dependencies are satisfied. ")
                            .append("Slimefun cannot infer the plugin-side startup cause; inspect the console/config.");
                }
            }
        } else {
            hoverText.append("\nHard dependencies: metadata unavailable");
            if (disabled) {
                hoverText.append("\nStartup evidence: Slimefun cannot infer the plugin-side startup cause; inspect the console/config.");
            }
        }

        if (runtimeFailure.isPresent()) {
            AddonRuntimeFailureSnapshot failure = runtimeFailure.orElseThrow();
            hoverText.append("\nGuarded callbacks: ")
                    .append(failure.getObservedFailures())
                    .append(" failure(s)")
                    .append("\nLast guarded failure: ")
                    .append(failure.getOperation())
                    .append(" | ")
                    .append(simpleFailureName(failure.getExceptionClass()))
                    .append(": ")
                    .append(failure.getMessage());
            if (isLinkageFailureClass(failure.getExceptionClass())) {
                hoverText.append("\nGuarded runtime linkage evidence: observed");
            }
        } else {
            hoverText.append("\nGuarded callbacks: no failures observed");
        }

        hoverText.append("\nScope: guarded callbacks only; arbitrary Paper plugin onEnable failures are not intercepted.");
    }

    private Component boundaryMarkers(
            @Nonnull Plugin addon,
            @Nonnull Optional<PluginDependencySnapshot> dependencySnapshot,
            @Nonnull Optional<AddonRuntimeFailureSnapshot> runtimeFailure) {
        net.kyori.adventure.text.TextComponent.Builder markers = Component.text();
        boolean added = false;

        if (dependencySnapshot.isPresent()) {
            PluginDependencySnapshot snapshot = dependencySnapshot.orElseThrow();
            if (snapshot.hasRequiredDependencyProblems()) {
                markers.append(Component.text(" · Deps!", NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(Component.text(requiredDependencyProblemText(snapshot)))));
                added = true;
            }
            if (hasRequiredProviderAlias(snapshot)) {
                markers.append(Component.text(" · Alias", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text(providerAliasText(snapshot)))));
                added = true;
            }
        }

        if (runtimeFailure.isPresent()) {
            AddonRuntimeFailureSnapshot failure = runtimeFailure.orElseThrow();
            String label = isLinkageFailureClass(failure.getExceptionClass()) ? " · Linkage!" : " · Runtime!";
            markers.append(Component.text(label, NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text(guardedRuntimeFailureText(failure)))));
            added = true;
        }

        if (!addon.isEnabled()
                && dependencySnapshot.map(snapshot -> !snapshot.hasRequiredDependencyProblems()).orElse(true)) {
            markers.append(Component.text(" · Startup?", NamedTextColor.GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "The addon is disabled, but Slimefun did not find a declared hard-dependency problem.\n"
                                    + "Slimefun does not intercept arbitrary third-party plugin startup failures; inspect the console/config."))));
            added = true;
        }

        return added ? markers.build() : Component.empty();
    }

    private String requiredDependencyProblemText(@Nonnull PluginDependencySnapshot snapshot) {
        StringBuilder text = new StringBuilder("Declared hard-dependency problem(s):");
        for (PluginDependencyResolution dependency : snapshot.getRequiredDependencies()) {
            if (dependency.isProblem()) {
                text.append("\n- ")
                        .append(dependency.getDeclaredName())
                        .append(": ")
                        .append(dependency.getState().name().toLowerCase(Locale.ROOT));
            }
        }
        text.append("\nUse /sf doctor dependencies ").append(snapshot.getPluginName()).append(" for details.");
        return text.toString();
    }

    private String providerAliasText(@Nonnull PluginDependencySnapshot snapshot) {
        String aliases = snapshot.getRequiredDependencies().stream()
                .filter(PluginDependencyResolution::isProviderAlias)
                .map(dependency -> dependency.getDeclaredName() + " -> " + dependency.getResolvedPluginName())
                .collect(Collectors.joining("\n- ", "- ", ""));
        return "Required dependency resolved through Paper provider alias:\n"
                + aliases
                + "\nProvider aliases satisfy descriptor lookup only; they do not prove expected Java classes/APIs exist.";
    }

    private String guardedRuntimeFailureText(@Nonnull AddonRuntimeFailureSnapshot failure) {
        return "Slimefun-guarded callback failures observed: "
                + failure.getObservedFailures()
                + "\nLast operation: "
                + failure.getOperation()
                + "\n"
                + simpleFailureName(failure.getExceptionClass())
                + ": "
                + failure.getMessage()
                + "\nThis evidence covers Slimefun-guarded callbacks only; it is not a complete plugin-startup log.";
    }

    private boolean hasRequiredProviderAlias(@Nonnull PluginDependencySnapshot snapshot) {
        return snapshot.getRequiredDependencies().stream().anyMatch(PluginDependencyResolution::isProviderAlias);
    }

    private boolean isLinkageFailureClass(@Nonnull String exceptionClass) {
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

    private String simpleFailureName(@Nonnull String className) {
        int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    private String compactVersion(@Nonnull String version) {
        final int maxLength = 26;
        return version.length() <= maxLength ? version : version.substring(0, maxLength - 1) + "…";
    }

    @SuppressWarnings("deprecation")
    private void addPluginVersions(
            @Nonnull net.kyori.adventure.text.TextComponent.Builder builder,
            @Nonnull Collection<Plugin> addons,
            @Nonnull PluginDependencyDiagnosticsService dependencies) {
        if (addons.isEmpty()) {
            builder.append(Component.text("No addon plugins installed", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            return;
        }

        builder.append(Component.text("Installed addon plugins: ", NamedTextColor.GRAY))
                .append(Component.text("(" + addons.size() + ")", NamedTextColor.DARK_GRAY));
        for (Plugin addonPlugin : addons.stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList()) {
            String version = addonPlugin.getDescription().getVersion();
            Optional<PluginDependencySnapshot> dependencySnapshot = dependencies.findPlugin(addonPlugin.getName());
            Optional<AddonRuntimeFailureSnapshot> runtimeFailure =
                    Slimefun.getAddonRuntimeHealthService().getFailure(addonPlugin.getName());

            HoverEvent<Component> hoverEvent;
            ClickEvent clickEvent = null;
            NamedTextColor primaryColor;
            NamedTextColor secondaryColor;
            if (Bukkit.getPluginManager().isPluginEnabled(addonPlugin)) {
                primaryColor = NamedTextColor.GREEN;
                secondaryColor = NamedTextColor.DARK_GREEN;
                String authors = String.join(", ", addonPlugin.getDescription().getAuthors());

                if (addonPlugin instanceof SlimefunAddon addon && addon.getBugTrackerURL() != null) {
                    try {
                        String url = addon.getBugTrackerURL();
                        if (url != null) {
                            URI uri = URI.create(!url.contains("://") ? "https://" + url : url);
                            clickEvent = ClickEvent.openUrl(uri.toString());
                        }
                        Component hoverComp = Component.text()
                                .append(Component.text("Authors: ", NamedTextColor.YELLOW))
                                .append(Component.text(authors, NamedTextColor.YELLOW))
                                .append(Component.text("\n> Click to open the issue tracker", NamedTextColor.GOLD))
                                .build();
                        hoverEvent = HoverEvent.showText(hoverComp);
                    } catch (IllegalArgumentException e) {
                        Component hoverComp = Component.text()
                                .append(Component.text("Authors: ", NamedTextColor.YELLOW))
                                .append(Component.text(authors, NamedTextColor.YELLOW))
                                .append(Component.text(
                                        "\n> The addon provided an invalid issue tracker URL!", NamedTextColor.RED))
                                .build();
                        hoverEvent = HoverEvent.showText(hoverComp);
                    }

                } else {
                    Component hoverComp = Component.text()
                            .append(Component.text("Authors: ", NamedTextColor.YELLOW))
                            .append(Component.text(authors, NamedTextColor.YELLOW))
                            .build();
                    hoverEvent = HoverEvent.showText(hoverComp);
                }
            } else {
                primaryColor = NamedTextColor.RED;
                secondaryColor = NamedTextColor.DARK_RED;
                if (addonPlugin instanceof SlimefunAddon addon && addon.getBugTrackerURL() != null) {
                    try {
                        String url = addon.getBugTrackerURL();
                        if (url != null) {
                            URI uri = URI.create(!url.contains("://") ? "https://" + url : url);
                            clickEvent = ClickEvent.openUrl(uri.toString());
                        }
                        Component hoverComp = Component.text()
                                .append(Component.text(
                                        "This plugin is disabled.\nCheck the console for errors.", NamedTextColor.RED))
                                .append(Component.text("\n> Click to open the issue tracker", NamedTextColor.DARK_RED))
                                .build();
                        hoverEvent = HoverEvent.showText(hoverComp);
                    } catch (IllegalArgumentException e) {
                        Component hoverComp = Component.text()
                                .append(Component.text(
                                        "This plugin is disabled.\nCheck the console for errors.", NamedTextColor.RED))
                                .append(Component.text(
                                        "\n> The plugin provided an invalid issue tracker URL", NamedTextColor.DARK_RED))
                                .build();
                        hoverEvent = HoverEvent.showText(hoverComp);
                    }
                } else {
                    Component hoverComp = Component.text("This plugin is disabled; check the console for errors.");
                    hoverEvent = HoverEvent.showText(hoverComp);
                }
            }

            Component nameComp = Component.text("\n  " + addonPlugin.getName(), primaryColor).hoverEvent(hoverEvent);
            if (clickEvent != null) {
                nameComp = nameComp.clickEvent(clickEvent);
            }
            String displayedVersion = compactVersion(version);
            Component versionComp = Component.text(" v" + displayedVersion, secondaryColor);
            if (!displayedVersion.equals(version)) {
                versionComp = versionComp.hoverEvent(HoverEvent.showText(Component.text("Full version: " + version)));
            }
            Component compatibilityComp = Slimefun.getAddonCompatibilityService()
                    .getResult(addonPlugin.getName())
                    .map(result -> compatibilityComponent(result, dependencySnapshot, runtimeFailure))
                    .orElseGet(() -> uncheckedCompatibilityComponent(addonPlugin, dependencySnapshot, runtimeFailure));
            Component boundaryComp = boundaryMarkers(addonPlugin, dependencySnapshot, runtimeFailure);

            builder.append(nameComp)
                    .append(versionComp)
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(compatibilityComp)
                    .append(boundaryComp);
        }
    }
}
