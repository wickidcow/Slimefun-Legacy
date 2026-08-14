package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EchoLantern;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExplorersSpyglass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfindersCompass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Registers the built-in Adventurer's Curios category and its field gadgets.
 */
final class AdventurersCuriosSetup {

    private static boolean registered;

    private AdventurersCuriosSetup() {}

    static void setup(Slimefun plugin) {
        if (registered) {
            return;
        }
        registered = true;

        ItemGroup curios = new ItemGroup(
                new NamespacedKey(plugin, "adventurers_curios"),
                new CustomItemStack(
                        Material.RECOVERY_COMPASS,
                        "&6Adventurer's Curios",
                        "",
                        "&7Exploration tools, navigation",
                        "&7and strange field gadgets"),
                2);

        SlimefunItemStack wayfindersCompass = new SlimefunItemStack(
                "ADVENTURERS_WAYFINDERS_COMPASS",
                Material.COMPASS,
                "&6Wayfinder's Compass",
                "&7Tunes itself to your last death.",
                "&7With no recorded death, it points to spawn.",
                "",
                "&eRight Click &7to retune");

        SlimefunItemStack echoLantern = new SlimefunItemStack(
                "ADVENTURERS_ECHO_LANTERN",
                Material.SOUL_LANTERN,
                "&bEcho Lantern",
                "&7Sends out a spectral pulse that",
                "&7reveals nearby hostile mobs.",
                "",
                "&eRight Click &7to pulse",
                "&8Cooldown: 30 seconds");

        SlimefunItemStack explorersSpyglass = new SlimefunItemStack(
                "ADVENTURERS_EXPLORERS_SPYGLASS",
                Material.SPYGLASS,
                "&6Explorer's Spyglass",
                "&7A surveyor's tool for explorers.",
                "&7Shows coordinates, biome and heading.",
                "",
                "&eRight Click &7to survey");

        new WayfindersCompass(
                        curios,
                        wayfindersCompass,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.REDSTONE),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.GOLD_INGOT),
                            new ItemStack(Material.COMPASS),
                            new ItemStack(Material.GOLD_INGOT),
                            null,
                            new ItemStack(Material.ECHO_SHARD),
                            null
                        })
                .register(plugin);

        new EchoLantern(
                        curios,
                        echoLantern,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.GLOW_INK_SAC),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.IRON_NUGGET),
                            new ItemStack(Material.SOUL_LANTERN),
                            new ItemStack(Material.IRON_NUGGET),
                            null,
                            new ItemStack(Material.ECHO_SHARD),
                            null
                        })
                .register(plugin);

        new ExplorersSpyglass(
                        curios,
                        explorersSpyglass,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.COMPASS),
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.COPPER_INGOT),
                            new ItemStack(Material.SPYGLASS),
                            new ItemStack(Material.COPPER_INGOT),
                            null,
                            new ItemStack(Material.AMETHYST_SHARD),
                            null
                        })
                .register(plugin);
    }
}
