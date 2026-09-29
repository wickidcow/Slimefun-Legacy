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

        boolean desired;
        if (args[0].equalsIgnoreCase("enable")) {
            desired = true;
        } else if (args[0].equalsIgnoreCase("disable")) {
            desired = false;
        } else {
            message(sender, "&e" + "Usage: /beacon <enable|disable|status|perf>");
            return true;
        }

        boolean current = BeaconPlusChunkLoadingControl.isEnabled();
        if (current == desired) {
            message(sender, "&7" + "Resonance Beacon chunk loading is already " + colorState(desired)
                    + BeaconPlusChunkLoadingControl.stateWord(desired) + "&7" + ".");
            sendStatus(sender);
            return true;
        }

        if (!BeaconPlusChunkLoadingControl.setEnabled(plugin, desired)) {
            message(sender, 
                    "&c" + "Could not save the Resonance Beacon chunk-loading setting. Check console.");
            return true;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            manager.applyGlobalChunkLoadingState();
        }

        if (desired) {
            message(sender, "&a" + "Resonance Beacon chunk loading ENABLED." + "&7"
                    + " Configured Activators were restored within the global safety caps.");
        } else {
            message(sender, "&c" + "Resonance Beacon chunk loading DISABLED." + "&7"
                    + " All Resonance Beacon chunk tickets were released; Activator selections were preserved.");
        }
        sendStatus(sender);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        boolean enabled = BeaconPlusChunkLoadingControl.isEnabled();
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        message(sender, "&6" + "Resonance Beacon chunk loading: " + colorState(enabled)
                + BeaconPlusChunkLoadingControl.stateWord(enabled));
        if (manager == null) {
            message(sender, "&7" + "Beacon manager is still initializing.");
            return;
        }
        message(sender, "&7" + "Configured Activator beacons: " + "&b"
                + manager.getActiveBeaconCount() + "&8" + "/64");
        message(sender, "&7" + "Currently ticketed chunks: " + "&b"
                + manager.getLoadedChunkCount() + "&8" + "/256");
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
            return Stream.of("enable", "disable", "status", "perf")
                    .filter(value -> value.startsWith(prefix))
                    .toList();
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
