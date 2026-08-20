package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.TunnelingPickaxe;
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

        SlimefunItemStack tunnelBorer = new SlimefunItemStack(
                "ADVENTURERS_DEEPCORE_TUNNEL_BORER",
                Material.NETHERITE_PICKAXE,
                "&6&lDeepcore Tunnel Borer",
                "&7Cuts a clean rectangular tunnel face",
                "&7forward from the block you are mining.",
                "",
                "&eRight Click &7to cycle bore size",
                "&eSneak while mining &7for precision mode",
                "&bBores: &f3x5, 5x7, 9x11",
                "&8The floor stays at your feet and the bore grows upward",
                "&8Skips machines, storage, custom blocks and unloaded chunks",
                "&8Works with normal pickaxe enchants and drops");

        new TunnelingPickaxe(tools, tunnelBorer, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.CARBONADO,
                    SlimefunItems.EXPLOSIVE_PICKAXE,
                    SlimefunItems.CARBONADO,
                    SlimefunItems.REINFORCED_ALLOY_INGOT,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.REINFORCED_ALLOY_INGOT
                })
                .register(plugin);
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
