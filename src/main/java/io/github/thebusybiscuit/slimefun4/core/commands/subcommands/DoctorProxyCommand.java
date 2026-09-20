package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.ProxyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.ProxyDiagnosticsSnapshot;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Optional;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Read-only proxy-forwarding diagnostics exposed through {@code /sf doctor proxy}. */
final class DoctorProxyCommand {

    private DoctorProxyCommand() {}

    static void send(@Nonnull Slimefun plugin, @Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 2 && args[2].equalsIgnoreCase("player")) {
            sendPlayerIdentity(plugin, sender, args);
            return;
        }

        sendForwardingConfiguration(plugin, sender);
    }

    private static void sendForwardingConfiguration(@Nonnull Slimefun plugin, @Nonnull CommandSender sender) {
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
                "&8Use &e/sf doctor proxy player <name> [research-key] &8while a player is online to inspect the identity Slimefun actually sees.");
        sendLine(
                sender,
                "&8This command validates backend configuration only. It cannot prove firewall rules or identify a Bungee-compatible proxy brand.");
    }

    private static void sendPlayerIdentity(
            @Nonnull Slimefun plugin, @Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 4 || args[3].isBlank()) {
            sendLine(sender, "&eUsage: /sf doctor proxy player <name> [research-key]");
            return;
        }

        Player player = Bukkit.getPlayerExact(args[3]);
        if (player == null || !player.isOnline()) {
            sendLine(sender, "&cPlayer '&f" + args[3] + "&c' is not online on this backend.");
            return;
        }

        String requestedResearch = args.length > 4 && !args[4].isBlank() ? args[4] : null;
        sendLine(sender, "&6Slimefun Proxy Player Identity");
        sendLine(sender, "&7Player: &f" + player.getName());
        sendLine(sender, "&7Bukkit UUID: &f" + player.getUniqueId());

        Optional<PlayerProfile> loaded = PlayerProfile.find(player);
        if (loaded.isPresent()) {
            sendLoadedProfile(sender, player, loaded.get(), requestedResearch);
            return;
        }

        sendLine(sender, "&7Slimefun profile: &eLoading");
        PlayerProfile.get(
                player,
                profile -> Slimefun.getSchedulerService()
                        .runFor(player, () -> sendLoadedProfile(sender, player, profile, requestedResearch)));
    }

    private static void sendLoadedProfile(
            @Nonnull CommandSender sender,
            @Nonnull Player player,
            @Nonnull PlayerProfile profile,
            String requestedResearch) {
        var owner = profile.getOwner();
        var ownerUuid = owner.getUniqueId();
        boolean uuidMatch = player.getUniqueId().equals(ownerUuid);

        sendLine(sender, "&7Slimefun profile: &aLoaded");
        sendLine(sender, "&7Profile owner UUID: &f" + ownerUuid);
        sendLine(sender, "&7UUID match: " + (uuidMatch ? "&aYes" : "&cNo"));
        sendLine(sender, "&7Researches unlocked: &e" + profile.getResearches().size());
        sendLine(sender, "&7Backpack count: &e" + profile.getBackpackCount());

        if (requestedResearch != null) {
            Optional<Research> research = Slimefun.getRegistry().getResearches().stream()
                    .filter(candidate -> candidate.getKey().toString().equalsIgnoreCase(requestedResearch))
                    .findFirst();
            if (research.isEmpty()) {
                sendLine(sender, "&7Research " + requestedResearch + ": &cUnknown");
            } else {
                sendLine(
                        sender,
                        "&7Research " + research.get().getKey() + ": "
                                + (profile.hasUnlocked(research.get()) ? "&aUnlocked" : "&eLocked"));
            }
            return;
        }

        Optional<Research> candidate = Slimefun.getRegistry().getResearches().stream()
                .filter(Research::isEnabled)
                .filter(research -> !profile.hasUnlocked(research))
                .findFirst();
        sendLine(
                sender,
                "&7Persistence research candidate: &f"
                        + candidate.map(research -> research.getKey().toString()).orElse("<none>"));
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
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
