package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.DeepcoreTunnelTool;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.DeepcoreTunnelTool.ExcavationType;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.Paxel;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Registers the field-engineering tool branch of Adventurer's Curios. */
final class AdventurersToolsSetup {

    private static boolean registered;

    private AdventurersToolsSetup() {}

    static void setup(Slimefun plugin) {
        if (registered || !CuriositiesConfig.isEnabled()) {
            return;
        }

        NamespacedKey parentKey = new NamespacedKey(plugin, "adventurers_curios");
        NestedItemGroup curios = null;
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (parentKey.equals(group.getKey()) && group instanceof NestedItemGroup nested) {
                curios = nested;
                break;
            }
        }

        if (curios == null) {
            Slimefun.logger().warning("Could not attach Curios Tools: Adventurer's Curios was not registered.");
            return;
        }

        registered = true;
        SubItemGroup tools =
                new SubItemGroup(new NamespacedKey(plugin, "adventurers_curios_tools"), curios, createToolsIcon(), 2);

        SlimefunItemStack paxel = new SlimefunItemStack(
                "ADVENTURERS_PAXEL",
                Material.DIAMOND_PICKAXE,
                "&bPaxel",
                "",
                "&7A pickaxe, axe, and shovel in one tool!",
                "&8Automatically adapts to the block you mine",
                "&8Upgrade it to Netherite in a Smithing Table");
        new Paxel(
                        tools,
                        paxel,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            SlimefunItems.SYNTHETIC_EMERALD,
                            new ItemStack(Material.DIAMOND_PICKAXE),
                            SlimefunItems.SYNTHETIC_EMERALD,
                            SlimefunItems.REINFORCED_ALLOY_INGOT,
                            new ItemStack(Material.DIAMOND_AXE),
                            SlimefunItems.REINFORCED_ALLOY_INGOT,
                            SlimefunItems.SYNTHETIC_DIAMOND,
                            new ItemStack(Material.DIAMOND_SHOVEL),
                            SlimefunItems.SYNTHETIC_DIAMOND
                        })
                .register(plugin);

        SlimefunItemStack pickaxe3 = tool(
                "ADVENTURERS_DEEPCORE_TUNNEL_BORER",
                Material.NETHERITE_PICKAXE,
                "&6&lDeepcore Tunnel Pickaxe &f3x3",
                3,
                "&7Stone and pickaxe-mineable terrain");
        register(
                plugin,
                tools,
                pickaxe3,
                3,
                ExcavationType.PICKAXE,
                new ItemStack[] {
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.CARBONADO,
                    SlimefunItems.EXPLOSIVE_PICKAXE,
                    SlimefunItems.CARBONADO,
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.REINFORCED_ALLOY_INGOT
                });

        SlimefunItemStack pickaxe5 = tool(
                "ADVENTURERS_DEEPCORE_PICKAXE_5X5",
                Material.NETHERITE_PICKAXE,
                "&6&lDeepcore Tunnel Pickaxe &f5x5",
                5,
                "&7Stone and pickaxe-mineable terrain");
        register(plugin, tools, pickaxe5, 5, ExcavationType.PICKAXE, upgradeRecipe(pickaxe3));

        SlimefunItemStack pickaxe9 = tool(
                "ADVENTURERS_DEEPCORE_PICKAXE_9X9",
                Material.NETHERITE_PICKAXE,
                "&6&lDeepcore Tunnel Pickaxe &f9x9",
                9,
                "&7Stone and pickaxe-mineable terrain");
        register(plugin, tools, pickaxe9, 9, ExcavationType.PICKAXE, heavyUpgradeRecipe(pickaxe5));

        SlimefunItemStack shovel3 = tool(
                "ADVENTURERS_DEEPCORE_SHOVEL_3X3",
                Material.NETHERITE_SHOVEL,
                "&e&lDeepcore Tunnel Shovel &f3x3",
                3,
                "&7Dirt and shovel-mineable terrain");
        register(
                plugin,
                tools,
                shovel3,
                3,
                ExcavationType.SHOVEL,
                new ItemStack[] {
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.CARBONADO,
                    SlimefunItems.EXPLOSIVE_SHOVEL,
                    SlimefunItems.CARBONADO,
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.REINFORCED_ALLOY_INGOT
                });

        SlimefunItemStack shovel5 = tool(
                "ADVENTURERS_DEEPCORE_SHOVEL_5X5",
                Material.NETHERITE_SHOVEL,
                "&e&lDeepcore Tunnel Shovel &f5x5",
                5,
                "&7Dirt and shovel-mineable terrain");
        register(plugin, tools, shovel5, 5, ExcavationType.SHOVEL, upgradeRecipe(shovel3));

        SlimefunItemStack shovel9 = tool(
                "ADVENTURERS_DEEPCORE_SHOVEL_9X9",
                Material.NETHERITE_SHOVEL,
                "&e&lDeepcore Tunnel Shovel &f9x9",
                9,
                "&7Dirt and shovel-mineable terrain");
        register(plugin, tools, shovel9, 9, ExcavationType.SHOVEL, heavyUpgradeRecipe(shovel5));

        SlimefunItemStack paxel3 = tool(
                "ADVENTURERS_DEEPCORE_PAXEL_3X3",
                Material.NETHERITE_PICKAXE,
                "&b&lDeepcore Tunnel Paxel &f3x3",
                3,
                "&7Mines both pickaxe and shovel terrain");

        ItemStack netheritePaxelPickaxe = netheritePaxel(paxel, Material.NETHERITE_PICKAXE);
        ItemStack[] paxel3Recipe = new ItemStack[] {
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.STEEL_PLATE,
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.CARBONADO,
            netheritePaxelPickaxe,
            SlimefunItems.CARBONADO,
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.STEEL_PLATE,
            SlimefunItems.REINFORCED_ALLOY_INGOT
        };
        register(plugin, tools, paxel3, 3, ExcavationType.PAXEL, paxel3Recipe);

        // Paxels retain their Slimefun ID while switching vanilla tool material. Accept every
        // Netherite form here so a player does not need to mine a stone block just before crafting.
        RecipeType.ENHANCED_CRAFTING_TABLE.register(
                recipeWithCenter(paxel3Recipe, netheritePaxel(paxel, Material.NETHERITE_AXE)), paxel3);
        RecipeType.ENHANCED_CRAFTING_TABLE.register(
                recipeWithCenter(paxel3Recipe, netheritePaxel(paxel, Material.NETHERITE_SHOVEL)), paxel3);

        // FluffyMachines owns the legacy PAXEL id. Resolve it only at runtime so Slimefun Legacy
        // keeps no compile-time dependency on the addon while still accepting its Netherite Paxel.
        registerOptionalFluffyPaxelRecipes(paxel3Recipe, paxel3);

        SlimefunItemStack paxel5 = tool(
                "ADVENTURERS_DEEPCORE_PAXEL_5X5",
                Material.NETHERITE_PICKAXE,
                "&b&lDeepcore Tunnel Paxel &f5x5",
                5,
                "&7Mines both pickaxe and shovel terrain");
        register(plugin, tools, paxel5, 5, ExcavationType.PAXEL, upgradeRecipe(paxel3));

        SlimefunItemStack paxel9 = tool(
                "ADVENTURERS_DEEPCORE_PAXEL_9X9",
                Material.NETHERITE_PICKAXE,
                "&b&lDeepcore Tunnel Paxel &f9x9",
                9,
                "&7Mines both pickaxe and shovel terrain");
        register(plugin, tools, paxel9, 9, ExcavationType.PAXEL, heavyUpgradeRecipe(paxel5));
    }

    private static SlimefunItemStack tool(String id, Material material, String name, int size, String terrainLore) {
        return new SlimefunItemStack(
                id,
                material,
                name,
                "&7Cuts a fixed " + size + "x" + size + " tunnel face",
                "&7three blocks forward from the block mined.",
                "",
                terrainLore,
                "&eSneak while mining &7for precision mode",
                "&8The floor stays at your feet and the tunnel grows upward",
                "&8Skips machines, storage, custom blocks and unloaded chunks",
                "&8Uses normal tool enchantments and drops");
    }

    private static void register(
            Slimefun plugin,
            ItemGroup tools,
            SlimefunItemStack item,
            int size,
            ExcavationType type,
            ItemStack[] recipe) {
        new DeepcoreTunnelTool(tools, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe, size, type).register(plugin);
    }

    private static void registerOptionalFluffyPaxelRecipes(ItemStack[] baseRecipe, SlimefunItemStack output) {
        SlimefunItem fluffyPaxel = SlimefunItem.getById("PAXEL");
        if (fluffyPaxel == null || fluffyPaxel.isDisabled()) {
            return;
        }

        ItemStack template = fluffyPaxel.getItem();
        for (Material material :
                List.of(Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL)) {
            ItemStack upgraded = template.clone();
            upgraded.setType(material);
            RecipeType.ENHANCED_CRAFTING_TABLE.register(recipeWithCenter(baseRecipe, upgraded), output);
        }
    }

    private static ItemStack netheritePaxel(SlimefunItemStack paxel, Material material) {
        if (material != Material.NETHERITE_PICKAXE
                && material != Material.NETHERITE_AXE
                && material != Material.NETHERITE_SHOVEL) {
            throw new IllegalArgumentException("A Netherite Paxel recipe ingredient must use a Netherite tool material");
        }

        ItemStack upgraded = paxel.clone();
        upgraded.setType(material);
        return upgraded;
    }

    private static ItemStack[] recipeWithCenter(ItemStack[] recipe, ItemStack center) {
        ItemStack[] variant = recipe.clone();
        variant[4] = center;
        return variant;
    }

    private static ItemStack[] upgradeRecipe(ItemStack previous) {
        return new ItemStack[] {
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.CARBONADO,
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.STEEL_PLATE,
            previous,
            SlimefunItems.STEEL_PLATE,
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.CARBONADO,
            SlimefunItems.REINFORCED_ALLOY_INGOT
        };
    }

    private static ItemStack[] heavyUpgradeRecipe(ItemStack previous) {
        return new ItemStack[] {
            SlimefunItems.CARBONADO,
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.CARBONADO,
            SlimefunItems.STEEL_PLATE,
            previous,
            SlimefunItems.STEEL_PLATE,
            SlimefunItems.CARBONADO,
            SlimefunItems.REINFORCED_ALLOY_INGOT,
            SlimefunItems.CARBONADO
        };
    }

    private static ItemStack createToolsIcon() {
        ItemStack icon = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Tools");
        meta.setLore(List.of(
                ChatColor.GRAY + "Excavation, field engineering",
                ChatColor.GRAY + "and specialized expedition tools"));
        icon.setItemMeta(meta);
        return icon;
    }
}
