package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.PerformanceInspector;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.SummaryOrderType;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.inspectors.ConsolePerformanceInspector;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.inspectors.PlayerPerformanceInspector;
import io.github.thebusybiscuit.slimefun4.core.ticker.TickLocation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Lightweight ticker diagnostics inspired by the Slimefun Gugu tick-freeze command.
 *
 * <p>This deliberately keeps Slimefun Legacy's normal ticker execution path intact. Freezing uses the ticker's
 * existing pause switch and timing reports reuse the existing profiler rather than moving machine callbacks into a
 * new queue or executor.
 */
final class TickCommand extends SubCommand {

    private static final int TARGET_DISTANCE = 8;

    TickCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "tick", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.tick")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "query";
        switch (action) {
            case "query", "show", "status" -> sendStatus(sender);
            case "freeze" -> freeze(sender);
            case "unfreeze" -> unfreeze(sender);
            case "at" -> inspectTarget(sender);
            case "top" -> showTop(sender);
            case "rate" -> showRate(sender);
            default -> inspectItem(sender, args[1]);
        }
    }

    private void sendStatus(CommandSender sender) {
        TickerTask ticker = Slimefun.getTickerTask();
        Map<?, ? extends Set<TickLocation>> locations = ticker.getTickLocations();
        int machineCount = locations.values().stream().mapToInt(Set::size).sum();

        send(sender, "&6Slimefun Legacy Ticker");
        send(sender, "&7State: " + (ticker.isPaused() ? "&cFROZEN" : ticker.isHalted() ? "&cHALTED" : "&aRUNNING"));
        send(sender, "&7Platform: &e" + (Slimefun.getSchedulerService().isFolia() ? "Folia" : "Paper/Purpur"));
        send(sender, "&7Ticker rate: &e" + ticker.getTickRate() + " tick(s)");
        send(sender, "&7Registered machine locations: &e" + machineCount);
        send(sender, "&7Registered machine chunks: &e" + locations.size());
        send(sender, "&7Paused machine circuits: &e" + ticker.getPausedMachineCount());
        send(sender, "&7Failing machines: &e" + ticker.getFailingMachineCount());
        send(sender, "&8Use /sf tick top for the current profiler ranking or /sf tick at while looking at a machine.");
    }

    private void freeze(CommandSender sender) {
        TickerTask ticker = Slimefun.getTickerTask();
        if (ticker.isPaused()) {
            send(sender, "&eThe Slimefun machine ticker is already frozen.");
            return;
        }

        ticker.setPaused(true);
        send(sender, "&cSlimefun machine ticker frozen. &7Already-dispatched work may finish, but new ticker cycles are paused.");
    }

    private void unfreeze(CommandSender sender) {
        TickerTask ticker = Slimefun.getTickerTask();
        if (!ticker.isPaused()) {
            send(sender, "&eThe Slimefun machine ticker is already running.");
            return;
        }

        ticker.setPaused(false);
        send(sender, "&aSlimefun machine ticker resumed.");
    }

    private void inspectTarget(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cOnly a player can use /sf tick at.");
            return;
        }

        Block target = player.getTargetBlockExact(TARGET_DISTANCE, FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            send(sender, "&cLook directly at a Slimefun machine within " + TARGET_DISTANCE + " blocks.");
            return;
        }

        Location location = target.getLocation();
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(location);
        if (item == null || item.getBlockTicker() == null) {
            send(sender, "&eThat block is not a ticking Slimefun machine.");
            return;
        }

        send(sender, "&6Ticker target");
        send(sender, "&7Item: &e" + item.getId());
        send(sender, "&7Location: &e" + location.getWorld().getName() + " " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ());
        send(sender, "&7Ticker mode: &e" + (item.getBlockTicker().isSynchronized() ? "synchronized" : "asynchronous"));
        send(sender, "&7Global ticker: " + (Slimefun.getTickerTask().isPaused() ? "&cfrozen" : "&arunning"));
        send(sender, "&8Use /sf tick top to compare this machine type with the current profiler sample.");
    }

    private void showTop(CommandSender sender) {
        if (Slimefun.getTickerTask().isPaused()) {
            send(sender, "&eThe ticker is frozen, so a new timing sample cannot complete. Use /sf tick unfreeze first.");
            return;
        }

        PerformanceInspector inspector;
        if (sender instanceof Player player) {
            inspector = new PlayerPerformanceInspector(player, SummaryOrderType.HIGHEST);
        } else {
            inspector = new ConsolePerformanceInspector(sender, false, SummaryOrderType.HIGHEST);
        }

        send(sender, "&7Collecting the next Slimefun ticker profiler sample...");
        Slimefun.getProfiler().requestSummary(inspector);
    }

    private void showRate(CommandSender sender) {
        send(sender, "&7Current Slimefun ticker rate: &e" + Slimefun.getTickerTask().getTickRate() + " tick(s)");
        send(sender, "&8Runtime rate mutation is intentionally not exposed; configure URID.custom-ticker-delay instead.");
    }

    private void inspectItem(CommandSender sender, String itemId) {
        SlimefunItem item = SlimefunItem.getById(itemId.toUpperCase(Locale.ROOT));
        if (item == null) {
            sendUsage(sender);
            return;
        }

        if (item.getBlockTicker() == null) {
            send(sender, "&e" + item.getId() + " does not have a BlockTicker.");
            return;
        }

        send(sender, "&6Ticker item &e" + item.getId());
        send(sender, "&7Mode: &e" + (item.getBlockTicker().isSynchronized() ? "synchronized" : "asynchronous"));
        send(sender, "&8Use /sf tick top for live timing data.");
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf tick [query|show|at|freeze|unfreeze|top|rate|<Slimefun item ID>]");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }
}
