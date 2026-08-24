package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.PerformanceRating;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/** Provides a lightweight, immediate snapshot of Slimefun's machine ticker health. */
final class TickCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    TickCommand(Slimefun plugin, SlimefunCommand cmd) {
        // This is an administrative diagnostic, so keep it out of the general player help list.
        super(plugin, cmd, "tick", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.tick") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        TickerTask ticker = Slimefun.getTickerTask();
        int tickRate = ticker.getTickRate();
        int tickingChunks = ticker.getTickLocations().size();
        int tickingMachines = ticker.getTickLocations().values().stream().mapToInt(java.util.Set::size).sum();
        PerformanceRating rating = Slimefun.getProfiler().getPerformance();

        send(sender, "");
        send(sender, "&a===== Slimefun Tick Snapshot =====");
        send(sender, "&7Ticker state: " + tickerState(ticker));
        send(
                sender,
                "&7Ticker interval: &e"
                        + tickRate
                        + " server ticks &8("
                        + formatSeconds(tickRate / 20.0D)
                        + "s)");
        send(sender, "&7Last profiled machine work: &e" + Slimefun.getProfiler().getTime());
        send(sender, "&7Profiler rating: " + rating.getColor() + rating.name());
        send(sender, "&7Registered ticking machines: &e" + tickingMachines);
        send(sender, "&7Registered ticking chunks: &e" + tickingChunks);
        send(
                sender,
                "&7Paused / failing machines: &e"
                        + ticker.getPausedMachineCount()
                        + " &7/ &e"
                        + ticker.getFailingMachineCount());
        send(sender, "&8Profiler work can include asynchronous machines and is not whole-server MSPT.");
        send(sender, "&8Use &7/sf timings &8for the detailed item, addon and chunk breakdown.");
    }

    private static String tickerState(TickerTask ticker) {
        if (ticker.isHalted()) {
            return "&cHALTED";
        }
        if (ticker.isPaused()) {
            return "&ePAUSED";
        }
        return "&aACTIVE";
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }
}
