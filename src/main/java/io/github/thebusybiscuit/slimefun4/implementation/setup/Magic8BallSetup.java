package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.magic8ball.Magic8BallFragmentResource;
import io.github.thebusybiscuit.slimefun4.implementation.items.magic8ball.Magic8BallItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.magic8ball.Magic8BallItems;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Integrates the historical Magic 8 Ball addon as optional Slimefun Legacy content. */
final class Magic8BallSetup {

    private static final Set<String> STANDALONE_PLUGIN_NAMES = Set.of("MAGIC8BALL", "MAGICBALL8", "SFMAGIC8BALL");

    private Magic8BallSetup() {}

    static void setup(Slimefun plugin) {
        Plugin standalonePlugin = findStandalonePlugin();
        if (standalonePlugin != null) {
            Slimefun.logger()
                    .log(
                            Level.INFO,
                            "Standalone {0} detected; built-in Magic 8 Ball content will not be registered.",
                            standalonePlugin.getName());
            return;
        }

        if (isRegistered("MAGIC_8_BALL_FRAGMENT") || isRegistered("MAGIC_8_BALL")) {
            Slimefun.logger()
                    .warning("Built-in Magic 8 Ball content was skipped because one of its legacy item ids is already "
                            + "registered.");
            return;
        }

        SlimefunItem enderWiseTalisman = SlimefunItem.getById("ENDER_WISE_TALISMAN");
        if (enderWiseTalisman == null) {
            Slimefun.logger()
                    .warning("Built-in Magic 8 Ball content was skipped because ENDER_WISE_TALISMAN is unavailable.");
            return;
        }

        Magic8BallItems.ITEM_GROUP.register(plugin);
        SlimefunItem fragment = new SlimefunItem(
                Magic8BallItems.ITEM_GROUP,
                Magic8BallItems.MAGIC_8_BALL_FRAGMENT,
                RecipeType.GEO_MINER,
                new ItemStack[9]);
        fragment.register(plugin);

        Magic8BallItem magic8Ball = new Magic8BallItem(
                Magic8BallItems.ITEM_GROUP,
                Magic8BallItems.MAGIC_8_BALL,
                RecipeType.MAGIC_WORKBENCH,
                new ItemStack[] {
                    Magic8BallItems.MAGIC_8_BALL_FRAGMENT,
                    SlimefunItems.TALISMAN_WISE,
                    Magic8BallItems.MAGIC_8_BALL_FRAGMENT,
                    SlimefunItems.MAGICAL_BOOK_COVER,
                    SlimefunItems.MAGIC_EYE_OF_ENDER,
                    SlimefunItems.MAGICAL_GLASS,
                    Magic8BallItems.MAGIC_8_BALL_FRAGMENT,
                    enderWiseTalisman.getItem(),
                    Magic8BallItems.MAGIC_8_BALL_FRAGMENT
                });
        magic8Ball.register(plugin);

        new Magic8BallFragmentResource().register();

        Research research = new Research(
                Objects.requireNonNull(NamespacedKey.fromString("magic8ball:magic8ball_research")),
                726854259,
                "Magic 8 Ball Research",
                88);
        research.addItems(magic8Ball);
        research.register();

        Slimefun.logger().info("Registered 2 built-in Magic 8 Ball items and 1 legacy-compatible research.");
    }

    private static boolean isRegistered(String itemId) {
        return Slimefun.getRegistry().getSlimefunItemIds().containsKey(itemId);
    }

    private static Plugin findStandalonePlugin() {
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            String normalizedName = candidate.getName().replaceAll("[^A-Za-z0-9]", "")
                    .toUpperCase(Locale.ROOT);
            if (STANDALONE_PLUGIN_NAMES.contains(normalizedName)) {
                return candidate;
            }
        }
        return null;
    }
}
