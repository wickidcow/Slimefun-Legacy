package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ArachnidWardTorch;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** Registers the 4.1.47 Arachnid Ward Torch in the existing Adventurer's Curios field category. */
final class ArachnidWardTorchSetup {

    private static boolean registered;

    private ArachnidWardTorchSetup() {}

    static void setup(Slimefun plugin) {
        if (registered || !CuriositiesConfig.isEnabled()) {
            return;
        }

        NamespacedKey fieldKey = new NamespacedKey(plugin, "adventurers_curios_field");
        ItemGroup fieldCuriosities = Slimefun.getRegistry().getAllItemGroups().stream()
                .filter(group -> group instanceof SubItemGroup && group.getKey().equals(fieldKey))
                .findFirst()
                .orElse(null);
        if (fieldCuriosities == null) {
            Slimefun.logger().warning("Could not register Arachnid Ward Torch: Adventurer's Curios field group is missing.");
            return;
        }

        registered = true;

        SlimefunItemStack wardTorch = new SlimefunItemStack(
                "ADVENTURERS_ARACHNID_WARD_TORCH",
                Material.SOUL_TORCH,
                "&bArachnid Ward Torch",
                "&7A soul-lit expedition ward that spiders",
                "&7and cave spiders instinctively avoid.",
                "",
                "&ePlace &7to ward the surrounding cave",
                "&8Default radius: 10 blocks",
                "&8Non-lethal; does not damage mobs");

        new ArachnidWardTorch(
                        fieldCuriosities,
                        wardTorch,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.FERMENTED_SPIDER_EYE),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.COPPER_INGOT),
                            new ItemStack(Material.SOUL_TORCH),
                            new ItemStack(Material.COPPER_INGOT),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.REDSTONE),
                            new ItemStack(Material.STRING)
                        })
                .register(plugin);
    }
}
