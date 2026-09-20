package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.ProxyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.ProxyDiagnosticsSnapshot;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Read-only proxy-forwarding diagnostics exposed through {@code /sf doctor proxy}. */
final class DoctorProxyCommand {

    private DoctorProxyCommand() {}

    static void send(@Nonnull Slimefun plugin, @Nonnull CommandSender sender) {
        ProxyDiagnosticsSnapshot snapshot = new ProxyDiagnosticsService(plugin).inspect();

        sendLine(sender, "&6Slimefun Proxy / Forwarding Diagnostics");
        sendLine(sender, "&7Backend online-mode: " + yesNo(snapshot.isBackendOnlineMode()));
        sendLine(sender, "&7Forwarding mode: " + mode(snapshot));
        sendLine(
                sender,
                "&7Velocity modern: "
                        + enabled(snapshot.isVelocityEnabled())
                        + " &8| &7proxy online-mode expected: &e"
                        + snapshot.isVelocityOnlineMode());
        sendLine(
                sender,
                "&7Velocity secret: "
                        + (snapshot.isVelocitySecretConfigured() ? "&aConfigured" : "&cNot found")
                        + " &8(value is never displayed)");
        sendLine(
                sender,
                "&7Bungee-compatible forwarding: "
                        + enabled(snapshot.isBungeeEnabled())
                        + " &8| &7proxy online-mode expected: &e"
                        + snapshot.isBungeeOnlineMode());
        sendLine(
                sender,
                "&7Config evidence: Paper "
                        + found(snapshot.isPaperConfigFound())
                        + " &8| &7Spigot "
                        + found(snapshot.isSpigotConfigFound()));

        if (snapshot.isLegacyPaperConfig()) {
            sendLine(sender, "&8Paper evidence came from legacy paper.yml Velocity settings.");
        }

        if (snapshot.getFailures().isEmpty()) {
            sendLine(sender, "&aNo blocking proxy-forwarding configuration problem was detected.");
        } else {
            sendLine(sender, "&cBlocking configuration findings:");
            for (String failure : snapshot.getFailures()) {
                sendLine(sender, "&8- &c" + failure);
            }
        }

        for (String warning : snapshot.getWarnings()) {
            sendLine(sender, "&8- &e" + warning);
        }

        sendLine(
                sender,
                "&7Slimefun profiles are UUID-based; correct proxy UUID forwarding is required to preserve research and player data.");
        sendLine(
                sender,
                "&8This command validates backend configuration only. It cannot prove firewall rules or identify a Bungee-compatible proxy brand.");
    }

    private static String mode(ProxyDiagnosticsSnapshot snapshot) {
        return switch (snapshot.getForwardingMode()) {
            case VELOCITY_MODERN -> "&aVelocity modern";
            case BUNGEE_COMPATIBLE -> "&eBungee-compatible legacy forwarding";
            case CONFLICTING -> "&cConflicting forwarding modes";
            case STANDALONE_OR_UNKNOWN -> "&fStandalone / no proxy forwarding detected";
            case UNSAFE_OFFLINE -> "&cOffline backend with no forwarding";
        };
    }

    private static String yesNo(boolean value) {
        return value ? "&aTrue" : "&eFalse";
    }

    private static String enabled(boolean value) {
        return value ? "&aEnabled" : "&7Disabled";
    }

    private static String found(boolean value) {
        return value ? "&aFound" : "&eNot found";
    }

    private static void sendLine(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }
}
