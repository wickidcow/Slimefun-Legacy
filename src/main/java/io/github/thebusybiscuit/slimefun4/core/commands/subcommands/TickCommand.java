package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * <p>This deliberately keeps Slimefun Legacy's normal ticker execution path intact. Global and targeted pauses are
 * checked by the existing ticker task, while timing reports reuse the existing profiler rather than moving machine
 * callbacks into a new queue or executor.
 */
final class TickCommand extends SubCommand {

    private static final int TARGET_DISTANCE = 8;
    private static final int FROZEN_ITEM_DISPLAY_LIMIT = 10;

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
            case "freeze" -> freeze(sender, args);
            case "unfreeze" -> unfreeze(sender, args);
            case "frozen" -> showFrozen(sender);
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

        send(sender, "Slimefun Legacy Ticker");
        send(sender, "State: " + (ticker.isPaused() ? "FROZEN" : ticker.isHalted() ? "HALTED" : "RUNNING"));
        send(sender, "Platform: " + (Slimefun.getSchedulerService().isFolia() ? "Folia" : "Paper/Purpur"));
        send(sender, "Ticker rate: " + ticker.getTickRate() + " tick(s)");
        send(sender, "Registered machine locations: " + machineCount);
        send(sender, "Registered machine chunks: " + locations.size());
        send(sender, "Target-paused machine locations: " + ticker.getTargetedPausedMachineCount());
        send(sender, "Target-paused item types: " + ticker.getTargetedPausedItemIds().size());
        send(sender, "Paused machine circuits: " + ticker.getPausedMachineCount());
        send(sender, "Failing machines: " + ticker.getFailingMachineCount());
        send(sender, "Use /sf tick frozen to list targeted pauses, /sf tick top for profiler data, or /sf tick at on a machine.");
    }

    private void freeze(CommandSender sender, String[] args) {
        TickerTask ticker = Slimefun.getTickerTask();
        if (args.length == 2) {
            if (ticker.isPaused()) {
                send(sender, "The Slimefun machine ticker is already frozen.");
                return;
            }

            ticker.setPaused(true);
            send(sender, "Slimefun machine ticker frozen. Already-dispatched work may finish, but new ticker cycles are paused.");
            return;
        }

        if (args.length != 3) {
            sendUsage(sender);
            return;
        }

        if (args[2].equalsIgnoreCase("at")) {
            setTargetPause(sender, true);
            return;
        }

        SlimefunItem item = resolveTickerItem(args[2]);
        if (item == null) {
            send(sender, "Unknown or non-ticking Slimefun item: " + args[2]);
            return;
        }

        if (ticker.pauseItemTicker(item.getId())) {
            send(sender, "Paused ticker callbacks for every registered " + item.getId() + " machine.");
            send(sender, "Already-running callbacks may finish; future ticker callbacks for this item type are skipped.");
        } else {
            send(sender, item.getId() + " is already target-paused by item type.");
        }
    }

    private void unfreeze(CommandSender sender, String[] args) {
        TickerTask ticker = Slimefun.getTickerTask();
        if (args.length == 2) {
            if (!ticker.isPaused()) {
                send(sender, "The Slimefun machine ticker is already running.");
                return;
            }

            ticker.setPaused(false);
            send(sender, "Slimefun machine ticker resumed.");
            return;
        }

        if (args.length != 3) {
            sendUsage(sender);
            return;
        }

        if (args[2].equalsIgnoreCase("at")) {
            setTargetPause(sender, false);
            return;
        }

        SlimefunItem item = resolveTickerItem(args[2]);
        if (item == null) {
            send(sender, "Unknown or non-ticking Slimefun item: " + args[2]);
            return;
        }

        if (ticker.resumeItemTicker(item.getId())) {
            send(sender, "Resumed ticker callbacks for " + item.getId() + ".");
        } else {
            send(sender, item.getId() + " was not target-paused by item type.");
        }
    }

    private void setTargetPause(CommandSender sender, boolean pause) {
        if (!(sender instanceof Player player)) {
            send(sender, "Only a player can use /sf tick " + (pause ? "freeze" : "unfreeze") + " at.");
            return;
        }

        Block target = player.getTargetBlockExact(TARGET_DISTANCE, FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            send(sender, "Look directly at a Slimefun machine within " + TARGET_DISTANCE + " blocks.");
            return;
        }

        Location location = target.getLocation();
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(location);
        if (item == null || item.getBlockTicker() == null) {
            send(sender, "That block is not a ticking Slimefun machine.");
            return;
        }

        TickerTask ticker = Slimefun.getTickerTask();
        boolean changed = pause ? ticker.pauseMachineTicker(location) : ticker.resumeMachineTicker(location);
        if (changed) {
            send(sender, (pause ? "Paused " : "Resumed ") + item.getId() + " at " + formatLocation(location) + ".");
            if (pause) {
                send(sender, "Already-running work may finish; future callbacks for this machine location are skipped.");
            }
        } else {
            send(sender, item.getId() + " at " + formatLocation(location) + " is already "
                    + (pause ? "target-paused." : "not location-paused."));
        }
    }

    private void showFrozen(CommandSender sender) {
        TickerTask ticker = Slimefun.getTickerTask();
        Set<String> pausedItems = ticker.getTargetedPausedItemIds();

        send(sender, "Slimefun Legacy ticker pauses");
        send(sender, "Global ticker: " + (ticker.isPaused() ? "FROZEN" : "running"));
        send(sender, "Target-paused machine locations: " + ticker.getTargetedPausedMachineCount());
        send(sender, "Target-paused item types: " + pausedItems.size());

        if (!pausedItems.isEmpty()) {
            List<String> sorted = new ArrayList<>(pausedItems);
            Collections.sort(sorted);
            int shown = Math.min(sorted.size(), FROZEN_ITEM_DISPLAY_LIMIT);
            for (int i = 0; i < shown; i++) {
                send(sender, " - " + sorted.get(i));
            }
            if (sorted.size() > shown) {
                send(sender, " - ... and " + (sorted.size() - shown) + " more");
            }
        }

        send(sender, "Circuit-breaker pauses are separate: " + ticker.getPausedMachineCount() + " machine(s).");
    }

    private void inspectTarget(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "Only a player can use /sf tick at.");
            return;
        }

        Block target = player.getTargetBlockExact(TARGET_DISTANCE, FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            send(sender, "Look directly at a Slimefun machine within " + TARGET_DISTANCE + " blocks.");
            return;
        }

        Location location = target.getLocation();
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(location);
        if (item == null || item.getBlockTicker() == null) {
            send(sender, "That block is not a ticking Slimefun machine.");
            return;
        }

        TickerTask ticker = Slimefun.getTickerTask();
        boolean locationPaused = ticker.isMachineTickerPaused(location);
        boolean itemPaused = ticker.isItemTickerPaused(item.getId());

        send(sender, "Ticker target");
        send(sender, "Item: " + item.getId());
        send(sender, "Location: " + formatLocation(location));
        send(sender, "Ticker mode: " + (item.getBlockTicker().isSynchronized() ? "synchronized" : "asynchronous"));
        send(sender, "Global ticker: " + (ticker.isPaused() ? "frozen" : "running"));
        send(sender, "Targeted pause: " + pauseDescription(locationPaused, itemPaused));
        send(sender, "Use /sf tick freeze at to pause only this machine or /sf tick top for live profiler data.");
    }

    private void showTop(CommandSender sender) {
        if (Slimefun.getTickerTask().isPaused()) {
            send(sender, "The ticker is frozen, so a new timing sample cannot complete. Use /sf tick unfreeze first.");
            return;
        }

        PerformanceInspector inspector;
        if (sender instanceof Player player) {
            inspector = new PlayerPerformanceInspector(player, SummaryOrderType.HIGHEST);
        } else {
            inspector = new ConsolePerformanceInspector(sender, false, SummaryOrderType.HIGHEST);
        }

        send(sender, "Collecting the next Slimefun ticker profiler sample...");
        Slimefun.getProfiler().requestSummary(inspector);
    }

    private void showRate(CommandSender sender) {
        send(sender, "Current Slimefun ticker rate: " + Slimefun.getTickerTask().getTickRate() + " tick(s)");
        send(sender, "Runtime rate mutation is intentionally not exposed; configure URID.custom-ticker-delay instead.");
    }

    private void inspectItem(CommandSender sender, String itemId) {
        SlimefunItem item = SlimefunItem.getById(itemId.toUpperCase(Locale.ROOT));
        if (item == null) {
            sendUsage(sender);
            return;
        }

        if (item.getBlockTicker() == null) {
            send(sender, item.getId() + " does not have a BlockTicker.");
            return;
        }

        send(sender, "Ticker item " + item.getId());
        send(sender, "Mode: " + (item.getBlockTicker().isSynchronized() ? "synchronized" : "asynchronous"));
        send(sender, "Target-paused by item type: " + (Slimefun.getTickerTask().isItemTickerPaused(item.getId()) ? "yes" : "no"));
        send(sender, "Use /sf tick freeze " + item.getId() + " to pause this machine type or /sf tick top for live timing data.");
    }

    private SlimefunItem resolveTickerItem(String itemId) {
        SlimefunItem item = SlimefunItem.getById(itemId.toUpperCase(Locale.ROOT));
        return item == null || item.getBlockTicker() == null ? null : item;
    }

    private String pauseDescription(boolean locationPaused, boolean itemPaused) {
        if (locationPaused && itemPaused) {
            return "location + item type";
        } else if (locationPaused) {
            return "location";
        } else if (itemPaused) {
            return "item type";
        }
        return "none";
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " "
                + location.getBlockZ();
    }

    private void sendUsage(CommandSender sender) {
        send(sender,
                "Usage: /sf tick [query|show|at|freeze [at|<item>]|unfreeze [at|<item>]|frozen|top|rate|<Slimefun item ID>]");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message);
    }
}
