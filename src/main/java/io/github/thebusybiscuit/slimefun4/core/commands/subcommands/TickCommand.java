package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.SlimefunProfiler;
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
        super(plugin, cmd, "tick", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.tick.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.tick") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        TickerTask ticker = Slimefun.getTickerTask();
        SlimefunProfiler.TickCycleSnapshot snapshot = Slimefun.getProfiler().getLastTickCycleSnapshot();
        int tickRate = ticker.getTickRate();

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

        if (snapshot.isAvailable()) {
            double workMillis = nanosToMillis(snapshot.totalElapsedNanos());
            double amortizedMillis = amortizedMillis(snapshot.totalElapsedNanos(), tickRate);
            double ageSeconds = Math.max(0L, System.currentTimeMillis() - snapshot.completedAtMillis()) / 1000.0D;

            send(
                    sender,
                    "&7Last profiled machine work: &e"
                            + formatMillis(workMillis)
                            + " ms &8("
                            + formatSeconds(ageSeconds)
                            + "s ago)");
            send(
                    sender,
                    "&7Amortized work per server tick: &e"
                            + formatMillis(amortizedMillis)
                            + " ms &8(profiler estimate)");
            send(sender, "&7Profiled machine locations: &e" + snapshot.profiledBlocks());
        } else {
            send(sender, "&7Last profiled machine work: &eNot available yet");
        }

        send(sender, "&7Registered ticking machines: &e" + ticker.getRegisteredTickingLocationCount());
        send(sender, "&7Registered ticking chunks: &e" + ticker.getRegisteredTickingChunkCount());
        send(sender, "&7Queued synchronized ticks: &e" + ticker.getQueuedSynchronousTickCount());
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
        if (ticker.isRunning()) {
            return "&aRUNNING";
        }
        return "&7IDLE";
    }

    static double amortizedMillis(long elapsedNanos, int tickRate) {
        if (tickRate <= 0) {
            return 0.0D;
        }
        return nanosToMillis(elapsedNanos) / tickRate;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.2f", millis);
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }
}
