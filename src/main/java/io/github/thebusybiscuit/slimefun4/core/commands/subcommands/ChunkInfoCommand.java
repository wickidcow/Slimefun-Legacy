package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunChunkData;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.ticker.TickLocation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Native replacement for the old SfChunkInfo addon.
 *
 * <p>The command uses Slimefun's chunk-indexed storage cache and ticker registry. It never scans every physical block
 * position in a chunk and it never force-loads an unloaded Minecraft chunk just to produce a report.
 */
final class ChunkInfoCommand extends SubCommand {

    private static final int TOP_LIMIT = 10;

    ChunkInfoCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "chunkinfo", false);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.chunkinfo")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        ChunkTarget target = resolveTarget(sender, args);
        if (target == null) {
            sendUsage(sender);
            return;
        }

        Location anchor = new Location(
                target.world(),
                (target.chunkX() << 4) + 8,
                target.world().getMinHeight(),
                (target.chunkZ() << 4) + 8);

        var task = Slimefun.getSchedulerService().runAt(anchor, () -> inspectChunk(sender, target));
        if (task.isCancelled()) {
            send(sender, "&cUnable to schedule chunk diagnostics for that region.");
        }
    }

    @Override
    public @Nonnull String getDescription(@Nonnull CommandSender sender) {
        return "Shows Slimefun blocks, addons and ticker registrations in a loaded chunk";
    }

    private void inspectChunk(CommandSender sender, ChunkTarget target) {
        World world = target.world();
        int chunkX = target.chunkX();
        int chunkZ = target.chunkZ();

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            deliver(sender, List.of(
                    "&6Slimefun Chunk Info",
                    "&7World: &f" + world.getName() + " &7Chunk: &f" + chunkX + ", " + chunkZ,
                    "&eThat chunk is not loaded. Slimefun Legacy will not force-load it for diagnostics."));
            return;
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        Set<TickLocation> tickingLocations = new HashSet<>(Slimefun.getTickerTask().getTickLocations(chunk));
        List<TickerSnapshot> tickerSnapshot = new ArrayList<>(tickingLocations.size());

        // Resolve ticker IDs while we still own the target region. The asynchronous storage completion below only
        // consumes immutable strings/booleans and never reaches back into Bukkit world state.
        for (TickLocation location : tickingLocations) {
            String id = resolveTickerId(location);
            boolean synchronizedTicker = false;
            SlimefunItem item = SlimefunItem.getById(id);
            if (item != null && item.getBlockTicker() != null) {
                synchronizedTicker = item.getBlockTicker().isSynchronized();
            }
            tickerSnapshot.add(new TickerSnapshot(id, location.isUniversal(), synchronizedTicker));
        }

        Slimefun.getDatabaseManager()
                .getBlockDataController()
                .getChunkDataAsync(chunk)
                .whenComplete((chunkData, error) -> {
                    if (error != null || chunkData == null) {
                        deliver(sender, List.of(
                                "&6Slimefun Chunk Info",
                                "&cUnable to read Slimefun data for " + world.getName() + " " + chunkX + ", " + chunkZ
                                        + (error == null || error.getMessage() == null ? "." : ": " + error.getMessage())));
                        return;
                    }

                    deliver(sender, buildReport(world.getName(), chunkX, chunkZ, chunkData, tickerSnapshot));
                });
    }

    private List<String> buildReport(
            String worldName, int chunkX, int chunkZ, SlimefunChunkData chunkData, List<TickerSnapshot> tickerLocations) {
        Map<String, Integer> items = new HashMap<>();
        Map<String, Integer> addons = new HashMap<>();
        int pendingRemoval = 0;
        int unresolvedItems = 0;

        for (SlimefunBlockData data : chunkData.getAllBlockData()) {
            if (data.isPendingRemove()) {
                pendingRemoval++;
            }

            String id = data.getSfId();
            items.merge(id, 1, Integer::sum);

            SlimefunItem item = SlimefunItem.getById(id);
            if (item == null) {
                unresolvedItems++;
                addons.merge("Missing/unknown addon", 1, Integer::sum);
                continue;
            }

            SlimefunAddon addon = item.getAddon();
            addons.merge(addon == null ? "Slimefun" : addon.getName(), 1, Integer::sum);
        }

        int universalTickers = (int) tickerLocations.stream().filter(TickerSnapshot::universal).count();
        int synchronizedTickers = (int) tickerLocations.stream().filter(TickerSnapshot::synchronizedTicker).count();
        int asynchronousTickers = tickerLocations.size() - synchronizedTickers;

        List<String> lines = new ArrayList<>();
        lines.add("&6&m----------------------------------------");
        lines.add("&6&lSlimefun Chunk Info");
        lines.add("&7World: &f" + worldName + " &7Chunk: &f" + chunkX + ", " + chunkZ);
        lines.add("&7Stored Slimefun blocks: &f" + items.values().stream().mapToInt(Integer::intValue).sum());
        lines.add("&7Unique item types: &f" + items.size());
        lines.add("&7Registered ticking locations: &f" + tickerLocations.size() + " &8(&f" + synchronizedTickers
                + " sync&8, &f" + asynchronousTickers + " async&8)");

        if (universalTickers > 0) {
            lines.add("&7Universal ticker registrations: &f" + universalTickers
                    + " &8(separate from normal chunk block storage)");
        }
        if (pendingRemoval > 0) {
            lines.add("&ePending removal records: &f" + pendingRemoval);
        }
        if (unresolvedItems > 0) {
            lines.add("&cStored IDs with no registered item: &f" + unresolvedItems);
        }

        appendRanking(lines, "&bTop block types", items, TOP_LIMIT);
        appendRanking(lines, "&dAddon breakdown", addons, TOP_LIMIT);
        lines.add("&8Tip: /sf tick top collects live machine timing data.");
        lines.add("&6&m----------------------------------------");
        return lines;
    }

    private String resolveTickerId(TickLocation tickLocation) {
        if (tickLocation.isUniversal()) {
            var data = Slimefun.getDatabaseManager()
                    .getBlockDataController()
                    .getUniversalBlockDataFromCache(tickLocation.getUuid());
            return data == null ? "" : data.getSfId();
        }

        var data = Slimefun.getDatabaseManager()
                .getBlockDataController()
                .getBlockDataFromCache(tickLocation.getLocation());
        return data == null ? "" : data.getSfId();
    }

    private void appendRanking(List<String> lines, String title, Map<String, Integer> counts, int limit) {
        if (counts.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int count = Integer.compare(right.getValue(), left.getValue());
            return count != 0 ? count : left.getKey().compareToIgnoreCase(right.getKey());
        });

        lines.add(title + ":");
        int shown = Math.min(limit, entries.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            lines.add("&8 - &f" + entry.getKey() + " &7x" + entry.getValue());
        }

        if (entries.size() > shown) {
            lines.add("&8 - ... and " + (entries.size() - shown) + " more");
        }
    }

    private ChunkTarget resolveTarget(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                Chunk chunk = player.getLocation().getChunk();
                return new ChunkTarget(player.getWorld(), chunk.getX(), chunk.getZ());
            }
            return null;
        }

        if (args.length == 3 && sender instanceof Player player) {
            Integer x = parseInt(args[1]);
            Integer z = parseInt(args[2]);
            return x == null || z == null ? null : new ChunkTarget(player.getWorld(), x, z);
        }

        if (args.length == 4) {
            World world = Bukkit.getWorld(args[1]);
            Integer x = parseInt(args[2]);
            Integer z = parseInt(args[3]);
            return world == null || x == null || z == null ? null : new ChunkTarget(world, x, z);
        }

        return null;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf chunkinfo [<chunkX> <chunkZ> | <world> <chunkX> <chunkZ>]");
    }

    private void deliver(CommandSender sender, List<String> lines) {
        Runnable delivery = () -> lines.forEach(line -> sender.sendMessage(ChatColors.color(line)));
        if (sender instanceof Player player) {
            Slimefun.getSchedulerService().runFor(player, delivery, () -> {});
        } else {
            Slimefun.getSchedulerService().run(delivery);
        }
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }

    private record ChunkTarget(World world, int chunkX, int chunkZ) {}

    private record TickerSnapshot(String itemId, boolean universal, boolean synchronizedTicker) {}
}
