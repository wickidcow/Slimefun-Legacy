package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

/** Operator command for Resonance Beacon controls and focused performance diagnostics. */
final class BeaconPlusAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "slimefun.command.beacon";

    private final Slimefun plugin;

    private BeaconPlusAdminCommand(Slimefun plugin) {
        this.plugin = plugin;
    }

    static void register(@Nonnull Slimefun plugin) {
        BeaconPlusChunkLoadingControl.initialize(plugin);

        PluginCommand command = plugin.getCommand("beacon");
        if (command == null) {
            plugin.getLogger().severe("The /beacon command is missing from plugin.yml and could not be registered.");
            return;
        }

        BeaconPlusAdminCommand handler = new BeaconPlusAdminCommand(plugin);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            message(sender, "&c" + "You do not have permission to control Resonance Beacons.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("perf")) {
            if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("reset"))) {
                message(sender, "&e" + "Usage: /beacon perf [reset]");
                return true;
            }
            sendPerformance(sender, args.length == 2);
            return true;
        }

        BeaconPlusChunkLoadingPolicy requested;
        if (args[0].equalsIgnoreCase("enable")) {
            requested = BeaconPlusChunkLoadingPolicy.ONLINE;
        } else if (args[0].equalsIgnoreCase("disable")) {
            requested = BeaconPlusChunkLoadingPolicy.OFF;
        } else if (args[0].equalsIgnoreCase("mode") && args.length == 2) {
            try {
                requested = BeaconPlusChunkLoadingPolicy.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                message(sender, "&eUsage: /beacon mode <online|always|off>");
                return true;
            }
        } else {
            message(sender, "&eUsage: /beacon <enable|disable|mode|status|perf>");
            return true;
        }

        if (requested != BeaconPlusChunkLoadingPolicy.OFF && Slimefun.getSchedulerService().isFolia()) {
            message(sender, "&cResonance Beacon chunk tickets are unavailable on region-owned runtimes. "
                    + "&7The saved mode and existing Activator purchases are preserved.");
            sendStatus(sender);
            return true;
        }

        if (BeaconPlusChunkLoadingControl.getMode() == requested) {
            message(sender, "&7Resonance Beacon chunk loading is already " + requested + ".");
            sendStatus(sender);
            return true;
        }

        if (!BeaconPlusChunkLoadingControl.setMode(plugin, requested)) {
            message(sender, "&cCould not save the Resonance Beacon chunk-loading mode. Check console.");
            return true;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            manager.applyGlobalChunkLoadingState();
        }
        message(sender, "&aResonance Beacon policy is now " + requested
                + "&7; existing beacon Activator selections are preserved.");
        sendStatus(sender);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        BeaconPlusChunkLoadingPolicy mode = BeaconPlusChunkLoadingControl.getMode();
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        message(sender, "&6Resonance Beacon chunk loading: &b" + mode.name());
        if (Slimefun.getSchedulerService().isFolia()) {
            message(sender, "&cRegion-owned runtime: chunk ticketing is safely suspended; saved modes unchanged.");
        }
        message(sender, "&7Holding tickets now: "
                + (BeaconPlusChunkLoadingControl.shouldHoldTickets() ? "&aYES" : "&cNO"));
        if (manager == null) {
            message(sender, "&7Beacon manager is still initializing.");
            return;
        }
        message(sender, "&7Configured Activator beacons: &b"
                + manager.getActiveBeaconCount() + "&8/64");
        message(sender, "&7Currently ticketed chunks: &b"
                + manager.getLoadedChunkCount() + "&8/256");
    }

    private void sendPerformance(CommandSender sender, boolean reset) {
        message(sender, "&6" + "Resonance Beacon performance buckets"
                + (reset ? "&7" + " (snapshot reset)" : ""));
        for (BeaconPlusPerformance.Entry entry : BeaconPlusPerformance.snapshot(reset)) {
            if (entry.samples() <= 0L) {
                continue;
            }
            message(sender, "&7" + entry.name() + ": " + "&b"
                    + String.format(Locale.ROOT, "%.3fms", entry.totalMillis()) + "&8" + " / "
                    + entry.samples() + " samples (avg "
                    + String.format(Locale.ROOT, "%.4fms", entry.averageMillis()) + ")");
        }
        message(sender, "&8" + "Use /beacon perf reset before a fresh /sf tick top comparison.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("enable", "disable", "mode", "status", "perf")
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("online", "always", "off")
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("perf")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("reset").filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private static String colorState(boolean enabled) {
        return enabled ? "&a" : "&c";
    }
    private static void message(CommandSender sender, String value) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }

}
