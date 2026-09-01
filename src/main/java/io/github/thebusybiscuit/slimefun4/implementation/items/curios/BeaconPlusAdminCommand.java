package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
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
            sender.sendMessage(ChatColor.RED + "You do not have permission to control Resonance Beacons.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("perf")) {
            if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("reset"))) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /beacon perf [reset]");
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
            sender.sendMessage(ChatColor.YELLOW + "Usage: /beacon <enable|disable|status|perf>");
            return true;
        }

        boolean current = BeaconPlusChunkLoadingControl.isEnabled();
        if (current == desired) {
            sender.sendMessage(ChatColor.GRAY + "Resonance Beacon chunk loading is already " + colorState(desired)
                    + BeaconPlusChunkLoadingControl.stateWord(desired) + ChatColor.GRAY + ".");
            sendStatus(sender);
            return true;
        }

        if (!BeaconPlusChunkLoadingControl.setEnabled(plugin, desired)) {
            sender.sendMessage(
                    ChatColor.RED + "Could not save the Resonance Beacon chunk-loading setting. Check console.");
            return true;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            manager.applyGlobalChunkLoadingState();
        }

        if (desired) {
            sender.sendMessage(ChatColor.GREEN + "Resonance Beacon chunk loading ENABLED." + ChatColor.GRAY
                    + " Configured Activators were restored within the global safety caps.");
        } else {
            sender.sendMessage(ChatColor.RED + "Resonance Beacon chunk loading DISABLED." + ChatColor.GRAY
                    + " All Resonance Beacon chunk tickets were released; Activator selections were preserved.");
        }
        sendStatus(sender);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        boolean enabled = BeaconPlusChunkLoadingControl.isEnabled();
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        sender.sendMessage(ChatColor.GOLD + "Resonance Beacon chunk loading: " + colorState(enabled)
                + BeaconPlusChunkLoadingControl.stateWord(enabled));
        if (manager == null) {
            sender.sendMessage(ChatColor.GRAY + "Beacon manager is still initializing.");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "Configured Activator beacons: " + ChatColor.AQUA
                + manager.getActiveBeaconCount() + ChatColor.DARK_GRAY + "/64");
        sender.sendMessage(ChatColor.GRAY + "Currently ticketed chunks: " + ChatColor.AQUA
                + manager.getLoadedChunkCount() + ChatColor.DARK_GRAY + "/256");
    }

    private void sendPerformance(CommandSender sender, boolean reset) {
        sender.sendMessage(ChatColor.GOLD + "Resonance Beacon performance buckets"
                + (reset ? ChatColor.GRAY + " (snapshot reset)" : ""));
        for (BeaconPlusPerformance.Entry entry : BeaconPlusPerformance.snapshot(reset)) {
            if (entry.samples() <= 0L) {
                continue;
            }
            sender.sendMessage(ChatColor.GRAY + entry.name() + ": " + ChatColor.AQUA
                    + String.format(Locale.ROOT, "%.3fms", entry.totalMillis()) + ChatColor.DARK_GRAY + " / "
                    + entry.samples() + " samples (avg "
                    + String.format(Locale.ROOT, "%.4fms", entry.averageMillis()) + ")");
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "Use /beacon perf reset before a fresh /sf tick top comparison.");
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

    private static ChatColor colorState(boolean enabled) {
        return enabled ? ChatColor.GREEN : ChatColor.RED;
    }
}
