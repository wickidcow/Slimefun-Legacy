package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter.ReinforcedHammer;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter.StoneHammer;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter.UniversalLeash;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter.WoodenKama;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** Registers lightweight starter and field-utility tools beneath Adventurer's Curios. */
final class StarterCuriosSetup {

    private static boolean registered;

    private StarterCuriosSetup() {}

    static void setup(Slimefun plugin) {
        if (registered) {
            return;
        }

        NestedItemGroup curios = findCuriosGroup(plugin);
        if (curios == null) {
            return;
        }

        registered = true;

        SubItemGroup starterUtilities = new SubItemGroup(
                new NamespacedKey(plugin, "adventurers_curios_starter"),
                curios,
                new CustomItemStack(
                        Material.WOODEN_HOE,
                        "&6Starter Utilities",
                        "&7Simple expedition tools and",
                        "&7early Slimefun resource helpers."),
                1);

        SlimefunItemStack universalLeash = new SlimefunItemStack(
                "ADVENTURERS_UNIVERSAL_LEASH",
                Material.LEAD,
                "&6Universal Leash",
                "&7A reinforced lead for unruly creatures.",
                "",
                "&eRight Click a mob &7to leash it",
                "&eRight Click a fence &7to tie leashed mobs",
                "&eSneak + Right Click a mob &7to chain mobs together",
                "&8Maximum chain: 32 mobs");

        SlimefunItemStack stoneHammer = new SlimefunItemStack(
                "ADVENTURERS_STONE_HAMMER",
                Material.STONE_PICKAXE,
                "&7Stone Hammer",
                "&7A crude hand crusher for the first",
                "&7steps into Slimefun processing.",
                "",
                "&eRight Click Cobblestone &7to crush it into Gravel");

        SlimefunItemStack reinforcedHammer = new SlimefunItemStack(
                "ADVENTURERS_REINFORCED_HAMMER",
                Material.IRON_PICKAXE,
                "&fReinforced Hammer",
                "&7Manually crush common ores into",
                "&7basic Slimefun or vanilla materials.",
                "",
                "&eRight Click an ore block &7to crush it");

        SlimefunItemStack woodenKama = new SlimefunItemStack(
                "ADVENTURERS_WOODEN_KAMA",
                Material.WOODEN_HOE,
                "&6Wooden Kama",
                "&7A light leaf-cutting starter tool.",
                "&7Leaves keep their normal drops and",
                "&7also yield a stick, with an 8% string chance.");

        new UniversalLeash(
                        starterUtilities,
                        universalLeash,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.SLIME_BALL),
                            new ItemStack(Material.LEAD),
                            new ItemStack(Material.SLIME_BALL),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.TRIPWIRE_HOOK),
                            new ItemStack(Material.STRING)
                        })
                .register(plugin);

        new StoneHammer(
                        starterUtilities,
                        stoneHammer,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.COBBLESTONE),
                            new ItemStack(Material.COBBLESTONE),
                            new ItemStack(Material.COBBLESTONE),
                            new ItemStack(Material.COBBLESTONE),
                            new ItemStack(Material.STICK),
                            new ItemStack(Material.COBBLESTONE),
                            null,
                            new ItemStack(Material.STICK),
                            null
                        })
                .register(plugin);

        new ReinforcedHammer(
                        starterUtilities,
                        reinforcedHammer,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.IRON_INGOT),
                            new ItemStack(Material.IRON_INGOT),
                            new ItemStack(Material.IRON_INGOT),
                            new ItemStack(Material.IRON_INGOT),
                            stoneHammer,
                            new ItemStack(Material.IRON_INGOT),
                            null,
                            new ItemStack(Material.STICK),
                            null
                        })
                .register(plugin);

        WoodenKama kama = new WoodenKama(
                starterUtilities,
                woodenKama,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    new ItemStack(Material.OAK_PLANKS),
                    new ItemStack(Material.OAK_PLANKS),
                    null,
                    null,
                    new ItemStack(Material.STICK),
                    null,
                    null,
                    new ItemStack(Material.STICK),
                    null
                });
        kama.register(plugin);
        kama.registerListener(plugin);
    }

    private static NestedItemGroup findCuriosGroup(Slimefun plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "adventurers_curios");

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof NestedItemGroup nested && group.getKey().equals(key)) {
                return nested;
            }
        }

        Slimefun.logger().warning("Starter utilities were skipped because Adventurer's Curios is unavailable.");
        return null;
    }
}
