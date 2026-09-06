package io.github.thebusybiscuit.slimefun4.core.commands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;

class SlimefunTabCompleter implements TabCompleter {

    private static final int MAX_SUGGESTIONS = 80;

    private final SlimefunCommand command;

    public SlimefunTabCompleter(@Nonnull SlimefunCommand command) {
        this.command = command;
    }

    @Nullable @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return createReturnList(command.getSubCommandNames(), args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("banitem")) {
                return createReturnList(getSlimefunItems(), args[1]);
            } else if (args[0].equalsIgnoreCase("unbanitem")) {
                List<String> list = Slimefun.getRegistry().getDisabledSlimefunItems().stream()
                        .map(SlimefunItem::getId)
                        .collect(Collectors.toList());
                return createReturnList(list, args[1]);
            } else if (args[0].equalsIgnoreCase("cleardata")) {
                List<String> list = new ArrayList<>(
                        Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
                list.add("*");
                return createReturnList(list, args[1]);
            } else if (args[0].equalsIgnoreCase("tick")) {
                List<String> list =
                        new ArrayList<>(List.of("query", "show", "at", "freeze", "unfreeze", "frozen", "top", "rate"));
                list.addAll(getSlimefunItems());
                return createReturnList(list, args[1]);
            } else if (args[0].equalsIgnoreCase("owner")) {
                return createReturnList(List.of("query", "claim", "clear", "transfer"), args[1]);
            } else if (args[0].equalsIgnoreCase("chunkinfo")) {
                List<String> list = new ArrayList<>();
                list.add("top");
                list.addAll(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
                if (sender instanceof Player player) {
                    list.add(String.valueOf(player.getLocation().getChunk().getX()));
                }
                return createReturnList(list, args[1]);
            } else if (args[0].equalsIgnoreCase("doctor")) {
                return createReturnList(
                        List.of(
                                "status",
                                "upgrade",
                                "core",
                                "registry",
                                "chunks",
                                "hand",
                                "inventory",
                                "scan",
                                "addons",
                                "compatibility",
                                "runtime",
                                "integrations",
                                "dependencies",
                                "repair",
                                "ie2"),
                        args[1]);
            }
            return null;
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                return createReturnList(getSlimefunItems(), args[2]);
            } else if (args[0].equalsIgnoreCase("research")) {
                List<Research> researches = Slimefun.getRegistry().getResearches();
                List<String> suggestions = new LinkedList<>();

                suggestions.add("all");
                suggestions.add("reset");

                for (Research research : researches) {
                    suggestions.add(research.getKey().toString().toLowerCase(Locale.ROOT));
                }

                return createReturnList(suggestions, args[2]);
            } else if (args[0].equalsIgnoreCase("cleardata")) {
                return createReturnList(List.of("block", "oil", "*"), args[2]);
            } else if (args[0].equalsIgnoreCase("tick")
                    && (args[1].equalsIgnoreCase("freeze") || args[1].equalsIgnoreCase("unfreeze"))) {
                List<String> list = new ArrayList<>(List.of("at"));
                list.addAll(getTickingSlimefunItems());
                return createReturnList(list, args[2]);
            } else if (args[0].equalsIgnoreCase("chunkinfo")) {
                if (args[1].equalsIgnoreCase("top")) {
                    return createReturnList(
                            Bukkit.getWorlds().stream().map(WorldInfo::getName).toList(), args[2]);
                }

                World explicitWorld = Bukkit.getWorld(args[1]);
                if (explicitWorld != null) {
                    return createReturnList(List.of(currentChunkCoordinate(sender, explicitWorld, true)), args[2]);
                } else if (sender instanceof Player player) {
                    return createReturnList(List.of(String.valueOf(player.getLocation().getChunk().getZ())), args[2]);
                }
                return null;
            } else if (args[0].equalsIgnoreCase("doctor") && args[1].equalsIgnoreCase("ie2")) {
                return createReturnList(List.of("status", "scan", "migrate", "refresh"), args[2]);
            } else {
                // Returning null will make it fallback to the default arguments (all online players)
                return null;
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return createReturnList(Arrays.asList("1", "2", "4", "8", "16", "32", "64"), args[3]);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("chunkinfo")) {
            if (args[1].equalsIgnoreCase("top")) {
                return Collections.emptyList();
            }

            World explicitWorld = Bukkit.getWorld(args[1]);
            return explicitWorld == null
                    ? null
                    : createReturnList(List.of(currentChunkCoordinate(sender, explicitWorld, false)), args[3]);
        } else {
            // Returning null will make it fallback to the default arguments (all online players)
            return null;
        }
    }

    private String currentChunkCoordinate(CommandSender sender, World world, boolean xAxis) {
        if (sender instanceof Player player && player.getWorld().equals(world)) {
            return String.valueOf(xAxis
                    ? player.getLocation().getChunk().getX()
                    : player.getLocation().getChunk().getZ());
        }

        return "0";
    }

    /***
     * Returns a sublist from a given list containing items that start with the given string if string is not empty
     *
     * @param list
     *            The list to process
     * @param string
     *            The typed string
     * @return Sublist if string is not empty
     */
    @Nonnull
    private List<String> createReturnList(@Nonnull List<String> list, @Nonnull String string) {
        if (string.isEmpty()) {
            if (list.size() >= MAX_SUGGESTIONS) {
                return list.subList(0, MAX_SUGGESTIONS);
            } else {
                return list;
            }
        }

        String input = string.toLowerCase(Locale.ROOT);
        List<String> returnList = new LinkedList<>();

        for (String item : list) {
            if (item.toLowerCase(Locale.ROOT).contains(input)) {
                returnList.add(item);

                if (returnList.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            } else if (item.equalsIgnoreCase(input)) {
                return Collections.emptyList();
            }
        }

        return returnList;
    }

    @Nonnull
    private List<String> getSlimefunItems() {
        List<SlimefunItem> items = Slimefun.getRegistry().getEnabledSlimefunItems();
        List<String> list = new ArrayList<>(items.size());

        for (SlimefunItem item : items) {
            list.add(item.getId());
        }

        return list;
    }

    @Nonnull
    private List<String> getTickingSlimefunItems() {
        List<String> list = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item.getBlockTicker() != null) {
                list.add(item.getId());
            }
        }
        return list;
    }
}
