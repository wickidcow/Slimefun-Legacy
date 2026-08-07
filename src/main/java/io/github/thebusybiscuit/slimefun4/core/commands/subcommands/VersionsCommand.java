package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import city.norain.slimefun4.utils.EnvUtil;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySummary;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformProfile;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry.KnownAddonSupport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
 *
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
                                    + (Slimefun.getVersion()
                                                    .toLowerCase(Locale.ROOT)
                                                    .contains("release")
                                            ? ""
                                            : " @" + EnvUtil.getBranch())
                                    + '\n',
                            Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Build time ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(EnvUtil.getBuildTime(), Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("\n"));

            // @formatter:on

            if (Slimefun.getMetricsService().getVersion() != null) {
                // @formatter:off
                builder.append(Component.text("Metrics-component ", Style.style(NamedTextColor.GREEN)))
                        .append(Component.text(
                                "#" + Slimefun.getMetricsService().getVersion() + '\n',
                                Style.style(NamedTextColor.DARK_GREEN)));
                // @formatter:on
            }

            addJavaVersion(builder);

            // Declare that we are NOT OFFICIAL build so no support from upstream
            builder.append(Component.text("\nEnglish-Albion community build", Style.style(NamedTextColor.WHITE)))
                    .append(Component.text(
                            "\nThis is an unofficial community build. Report issues to this fork.\n", Style.style(NamedTextColor.RED)));

            if (Slimefun.getConfigManager().isBypassEnvironmentCheck()) {
                builder.append(Component.text("\n\nEnvironment compatibility check is disabled", Style.style(NamedTextColor.RED)));
            }

            if (Slimefun.getConfigManager().isBypassItemLengthCheck()) {
                builder.append(Component.text("\n\nItem length check is disabled", Style.style(NamedTextColor.RED)));
            }

            builder.append(Component.text("\n"));
            Slimefun.getAddonCompatibilityService().refresh();
            Slimefun.getExternalIntegrationService().refresh();
            addAddonCompatibilitySummary(builder);
            addExternalIntegrationSummary(builder);
            addPluginVersions(builder);

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
        long ciMonitored = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .filter(result -> knownAddonRegistry.find(result.getPluginName()).isPresent())
                .count();
        long unknown = results.stream()
                .filter(result -> result.getStatus() == AddonCompatibilityStatus.UNDECLARED)
                .filter(result -> knownAddonRegistry.find(result.getPluginName()).isEmpty())
                .count();

        builder.append(Component.text("Addon compatibility: ", NamedTextColor.GREEN))
                .append(Component.text("✔ " + compatible + " declared compatible", NamedTextColor.DARK_GREEN))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("◉ " + ciMonitored + " CI monitored", NamedTextColor.AQUA))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠ " + warning + " warning", NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("? " + unknown + " unknown", NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("✕ " + incompatible + " incompatible", NamedTextColor.RED))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⏸ " + disabled + " disabled\n", NamedTextColor.DARK_RED));

        if (incompatible == 0 && disabled == 0) {
            NamedTextColor overallColor = warning == 0 && unknown == 0
                    ? NamedTextColor.GREEN
                    : NamedTextColor.YELLOW;
            String overall = warning == 0 && unknown == 0
                    ? "Overall: ✔ No known compatibility problems"
                    : "Overall: ⚠ No declared incompatibilities; review warnings/unknown addons";
            builder.append(Component.text(overall + '\n', overallColor));
        } else {
            builder.append(Component.text(
                    "Overall: ✕ One or more addons are disabled or incompatible\n", NamedTextColor.RED));
        }

        if (ciMonitored > 0) {
            builder.append(Component.text(
                    "◉ CI monitored means the addon family is in the Slimefun Legacy compatibility matrix; "
                            + "the exact installed JAR may differ from the build tested by CI.\n",
                    NamedTextColor.AQUA));
        }
        if (unknown > 0) {
            builder.append(Component.text(
                    "? Unknown means Slimefun detected the addon, but it has no compatibility declaration and is not "
                            + "yet in the Legacy runtime recognition registry.\n",
                    NamedTextColor.GRAY));
        }
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

    private Component compatibilityComponent(AddonCompatibilityResult result) {
        NamedTextColor color;
        String label;
        String explanation;
        Optional<KnownAddonSupport> knownSupport = knownAddonRegistry.find(result.getPluginName());

        switch (result.getStatus()) {
            case COMPATIBLE -> {
                color = NamedTextColor.DARK_GREEN;
                label = "✔ Compatible";
                explanation = "This addon declared compatibility and passed the current Slimefun Legacy runtime checks.";
            }
            case WARNING -> {
                color = NamedTextColor.YELLOW;
                label = "⚠ Compatible with warnings";
                explanation = "No hard incompatibility was detected, but one or more compatibility warnings need review.";
            }
            case UNDECLARED -> {
                if (knownSupport.isPresent()) {
                    KnownAddonSupport support = knownSupport.orElseThrow();
                    color = NamedTextColor.AQUA;
                    label = "◉ Known addon — Legacy CI monitored";
                    explanation = "This addon family is recognized by Slimefun Legacy and is included as a "
                            + support.getTierDisplayName()
                            + ". The exact installed JAR did not declare compatibility, so CI coverage is useful evidence "
                            + "but not a guarantee for this exact build.";
                } else {
                    color = NamedTextColor.GRAY;
                    label = "? Slimefun addon — compatibility unknown";
                    explanation = "Slimefun detected this as an addon, but it has no Legacy compatibility declaration and "
                            + "is not currently mapped to a known Legacy CI target. This does not mean it is incompatible.";
                }
            }
            case DISABLED -> {
                color = NamedTextColor.RED;
                label = "✕ Disabled";
                explanation = "The addon is disabled, so Slimefun cannot verify runtime compatibility.";
            }
            case INCOMPATIBLE -> {
                color = NamedTextColor.RED;
                label = "✕ Incompatible";
                explanation = "The addon's declared requirements do not match the current Slimefun/server environment.";
            }
            default -> throw new IllegalStateException("Unhandled addon compatibility status: " + result.getStatus());
        }

        StringBuilder hoverText = new StringBuilder(label)
                .append("\n")
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
        if (!result.getMessages().isEmpty()) {
            hoverText.append("\n\nDetails:\n- ").append(String.join("\n- ", result.getMessages()));
        }

        return Component.text(label, color)
                .hoverEvent(HoverEvent.showText(Component.text(hoverText.toString())));
    }

    private Component uncheckedCompatibilityComponent() {
        return Component.text("? Compatibility not checked", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Slimefun did not receive a compatibility result for this addon. "
                                + "The addon may still be loaded, but its compatibility status is unknown.")));
    }

    @SuppressWarnings("deprecation")
    private void addPluginVersions(@Nonnull net.kyori.adventure.text.TextComponent.Builder builder) {
        Collection<Plugin> addons = Slimefun.getInstalledAddons();

        if (addons.isEmpty()) {
            builder.append(Component.text("No addon plugins installed", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            return;
        }

        builder.append(Component.text("Installed addon plugins: ", NamedTextColor.GRAY))
                .append(Component.text("(" + addons.size() + ")", NamedTextColor.DARK_GRAY));

        for (Plugin plugin : addons.stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList()) {
            String version = plugin.getDescription().getVersion();

            HoverEvent<Component> hoverEvent;
            ClickEvent clickEvent = null;
            NamedTextColor primaryColor;
            NamedTextColor secondaryColor;

            if (Bukkit.getPluginManager().isPluginEnabled(plugin)) {
                primaryColor = NamedTextColor.GREEN;
                secondaryColor = NamedTextColor.DARK_GREEN;
                String authors = String.join(", ", plugin.getDescription().getAuthors());

                if (plugin instanceof SlimefunAddon addon && addon.getBugTrackerURL() != null) {

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
                                .append(Component.text("\n> The addon provided an invalid issue tracker URL!", NamedTextColor.RED))
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

                if (plugin instanceof SlimefunAddon addon && addon.getBugTrackerURL() != null) {
                    try {
                        String url = addon.getBugTrackerURL();
                        if (url != null) {
                            URI uri = URI.create(!url.contains("://") ? "https://" + url : url);
                            clickEvent = ClickEvent.openUrl(uri.toString());
                        }
                        Component hoverComp = Component.text()
                                .append(Component.text("This plugin is disabled.\nCheck the console for errors.", NamedTextColor.RED))
                                .append(Component.text("\n> Click to open the issue tracker", NamedTextColor.DARK_RED))
                                .build();

                        hoverEvent = HoverEvent.showText(hoverComp);
                    } catch (IllegalArgumentException e) {
                        Component hoverComp = Component.text()
                                .append(Component.text("This plugin is disabled.\nCheck the console for errors.", NamedTextColor.RED))
                                .append(Component.text("\n> The plugin provided an invalid issue tracker URL", NamedTextColor.DARK_RED))
                                .build();

                        hoverEvent = HoverEvent.showText(hoverComp);
                    }
                } else {
                    Component hoverComp = Component.text("This plugin is disabled; check the console for errors.");
                    hoverEvent = HoverEvent.showText(hoverComp);
                }
            }

            Component nameComp =
                    Component.text("\n  " + plugin.getName(), primaryColor).hoverEvent(hoverEvent);

            if (clickEvent != null) nameComp = nameComp.clickEvent(clickEvent);

            Component versionComp = Component.text(" v" + version, secondaryColor);
            Component compatibilityComp = Slimefun.getAddonCompatibilityService()
                    .getResult(plugin.getName())
                    .map(this::compatibilityComponent)
                    .orElseGet(this::uncheckedCompatibilityComponent);

            builder.append(nameComp)
                    .append(versionComp)
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(compatibilityComp);
        }
    }
}
