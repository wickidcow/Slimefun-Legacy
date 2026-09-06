package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ArachnidWardTorch;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** Registers the Arachnid Ward Torch into the existing Adventurer's Curios field group. */
final class ArachnidWardTorchSetup {

    private static final String ROOT = "SlimefunLegacyAddition.ArachnidWardTorch";
    private static final int DEFAULT_HELD_RADIUS = 8;
    private static final int DEFAULT_PLACED_RADIUS = 12;
    private static final int DEFAULT_SCAN_INTERVAL_TICKS = 20;

    private ArachnidWardTorchSetup() {}

    static void setup(Slimefun plugin) {
        if (!CuriositiesConfig.isEnabled()) {
            return;
        }

        CuriositiesConfig config = CuriositiesConfig.getConfig();
        config.setDefaultValue(ROOT + ".enabled", true);
        config.setDefaultValue(ROOT + ".held-radius", DEFAULT_HELD_RADIUS);
        config.setDefaultValue(ROOT + ".placed-radius", DEFAULT_PLACED_RADIUS);
        config.setDefaultValue(ROOT + ".scan-interval-ticks", DEFAULT_SCAN_INTERVAL_TICKS);
        config.save();

        if (!config.getBoolean(ROOT + ".enabled")) {
            return;
        }

        NamespacedKey fieldKey = new NamespacedKey(plugin, "adventurers_curios_field");
        ItemGroup fieldCuriosities = Slimefun.getRegistry().getAllItemGroups().stream()
                .filter(group -> fieldKey.equals(group.getKey()))
                .findFirst()
                .orElse(null);
        if (fieldCuriosities == null) {
            Slimefun.logger().warning("Could not register the Arachnid Ward Torch: Adventurer's Curios field group was not found.");
            return;
        }

        int heldRadius = clamp(config.getInt(ROOT + ".held-radius"), 1, 32, DEFAULT_HELD_RADIUS);
        int placedRadius = clamp(config.getInt(ROOT + ".placed-radius"), 1, 32, DEFAULT_PLACED_RADIUS);
        int scanIntervalTicks = clamp(
                config.getInt(ROOT + ".scan-interval-ticks"), 5, 100, DEFAULT_SCAN_INTERVAL_TICKS);

        SlimefunItemStack wardTorch = new SlimefunItemStack(
                "ADVENTURERS_ARACHNID_WARD_TORCH",
                Material.SOUL_TORCH,
                "&3Arachnid Ward Torch",
                "&7A cold-burning ward that unsettles spiders.",
                "&7It drives them away without harming them.",
                "",
                "&eHold in either hand &7for a " + heldRadius + "-block mobile ward",
                "&ePlace it &7for a " + placedRadius + "-block stationary ward",
                "&8Affects Spiders and Cave Spiders");

        ArachnidWardTorch ward = new ArachnidWardTorch(
                fieldCuriosities,
                wardTorch,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.SPIDER_EYE),
                    new ItemStack(Material.SOUL_TORCH),
                    new ItemStack(Material.SPIDER_EYE),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.COPPER_INGOT)
                },
                heldRadius,
                placedRadius,
                scanIntervalTicks);
        ward.register(plugin);
        if (!ward.isDisabled()) {
            ward.registerListener(plugin);
        }
    }

    private static int clamp(int value, int minimum, int maximum, int fallback) {
        int actual = value <= 0 ? fallback : value;
        return Math.max(minimum, Math.min(maximum, actual));
    }
}
