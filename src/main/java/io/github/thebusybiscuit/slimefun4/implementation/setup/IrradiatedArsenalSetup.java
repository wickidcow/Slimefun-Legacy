package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.weapons.IrradiatedWeapon;
import io.github.thebusybiscuit.slimefun4.implementation.items.weapons.IrradiatedWeaponListener;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Registers the radioactive combat branch of Adventurer's Curios. */
final class IrradiatedArsenalSetup {

    private static boolean registered;

    private IrradiatedArsenalSetup() {}

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
            Slimefun.logger().warning("Could not attach Irradiated Arsenal: Adventurer's Curios was not registered.");
            return;
        }

        registered = true;
        SubItemGroup arsenal = new SubItemGroup(
                new NamespacedKey(plugin, "irradiated_arsenal"), curios, createArsenalIcon(), 2);

        SlimefunItemStack glowbiteBlade = new SlimefunItemStack(
                "IRRADIATED_GLOWBITE_BLADE",
                Material.NETHERITE_SWORD,
                "&a&lGlowbite Blade",
                "&7A blistering-alloy edge with a containment problem.",
                "&7Every cut leaves more than a scar.",
                "",
                "&a☢ Target exposure: &f10 per hit",
                "&c☢ Low radiation leakage while held",
                "&8Civil-defense note: the glow is not decorative.");

        SlimefunItemStack geigerPike = new SlimefunItemStack(
                "IRRADIATED_GEIGER_PIKE",
                Material.NETHERITE_SPEAR,
                "&a&lGeiger Pike",
                "&7A long-reach spear sleeved in blistering alloy.",
                "&7The clicking gets faster near the sharp end.",
                "",
                "&a☢ Target exposure: &f8 per hit",
                "&c☢ Low radiation leakage while held",
                "&8Long reach. Shorter life expectancy.");

        SlimefunItemStack hotZoneCleaver = new SlimefunItemStack(
                "IRRADIATED_HOT_ZONE_CLEAVER",
                Material.NETHERITE_AXE,
                "&6&lHot-Zone Cleaver",
                "&7Heavy alloy plating turns every swing",
                "&7into a portable contamination incident.",
                "",
                "&a☢ Target exposure: &f12 per hit",
                "&c☢ Low radiation leakage while held",
                "&8Cuts timber, armor, and recommended exposure limits.");

        SlimefunItemStack reactorsFork = new SlimefunItemStack(
                "IRRADIATED_REACTORS_FORK",
                Material.TRIDENT,
                "&b&lReactor's Fork",
                "&7Three blistering points carrying one very bad idea.",
                "&7Radiation payload works in melee or when thrown.",
                "",
                "&a☢ Target exposure: &f9 per hit",
                "&c☢ Low radiation leakage while held",
                "&8Three points. Zero approved safety procedures.");

        SlimefunItemStack criticalMass = new SlimefunItemStack(
                "IRRADIATED_CRITICAL_MASS",
                Material.MACE,
                "&2&lCritical Mass",
                "&7A dense impact weapon wrapped in blistering alloy.",
                "&7The warning label was not rated for smash attacks.",
                "",
                "&a☢ Target exposure: &f16 per hit",
                "&c☢ Low radiation leakage while held",
                "&8For problems rated above the shelter's design limit.");

        SlimefunItemStack radspikeArrow = new SlimefunItemStack(
                "IRRADIATED_RADSPIKE_ARROW",
                Material.ARROW,
                "&aRadspike Arrow",
                "&7A blistering-alloy arrowhead with a radioactive payload.",
                "&7Works with normal bows and crossbows.",
                "",
                "&a☢ Target exposure: &f7 per hit",
                "&c☢ Low radiation leakage while held",
                "&8A tiny delivery system with terrible paperwork.");

        new IrradiatedWeapon(
                        arsenal,
                        glowbiteBlade,
                        RecipeType.MAGIC_WORKBENCH,
                        infusedWeaponRecipe(Material.NETHERITE_SWORD),
                        10,
                        1.0D,
                        false)
                .register(plugin);
        new IrradiatedWeapon(
                        arsenal,
                        geigerPike,
                        RecipeType.MAGIC_WORKBENCH,
                        infusedWeaponRecipe(Material.NETHERITE_SPEAR),
                        8,
                        0.75D,
                        false)
                .register(plugin);
        new IrradiatedWeapon(
                        arsenal,
                        hotZoneCleaver,
                        RecipeType.MAGIC_WORKBENCH,
                        infusedWeaponRecipe(Material.NETHERITE_AXE),
                        12,
                        1.25D,
                        false)
                .register(plugin);
        new IrradiatedWeapon(
                        arsenal,
                        reactorsFork,
                        RecipeType.MAGIC_WORKBENCH,
                        infusedWeaponRecipe(Material.TRIDENT),
                        9,
                        1.0D,
                        false)
                .register(plugin);
        new IrradiatedWeapon(
                        arsenal,
                        criticalMass,
                        RecipeType.MAGIC_WORKBENCH,
                        infusedWeaponRecipe(Material.MACE),
                        16,
                        2.0D,
                        false)
                .register(plugin);
        new IrradiatedWeapon(
                        arsenal,
                        radspikeArrow,
                        RecipeType.MAGIC_WORKBENCH,
                        radspikeRecipe(),
                        new SlimefunItemStack(radspikeArrow, 8),
                        7,
                        0.5D,
                        true)
                .register(plugin);

        IrradiatedWeaponListener.register(plugin);
    }

    private static ItemStack[] infusedWeaponRecipe(Material baseWeapon) {
        return new ItemStack[] {
            SlimefunItems.BLISTERING_INGOT_3,
            SlimefunItems.BLISTERING_INGOT_3,
            SlimefunItems.BLISTERING_INGOT_3,
            SlimefunItems.BLISTERING_INGOT_3,
            new ItemStack(baseWeapon),
            SlimefunItems.BLISTERING_INGOT_3,
            SlimefunItems.BLISTERING_INGOT_3,
            SlimefunItems.BLISTERING_INGOT_3,
            SlimefunItems.BLISTERING_INGOT_3
        };
    }

    private static ItemStack[] radspikeRecipe() {
        return new ItemStack[] {
            SlimefunItems.BLISTERING_INGOT_3,
            null,
            SlimefunItems.BLISTERING_INGOT_3,
            null,
            new ItemStack(Material.ARROW, 8),
            null,
            SlimefunItems.BLISTERING_INGOT_3,
            null,
            SlimefunItems.BLISTERING_INGOT_3
        };
    }

    private static ItemStack createArsenalIcon() {
        ItemStack icon = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Irradiated Arsenal");
        meta.setLore(List.of(
                ChatColor.GRAY + "Blistering-alloy weapons with radioactive payloads",
                ChatColor.DARK_GRAY + "Powerful in combat; unsafe to hold forever"));
        icon.setItemMeta(meta);
        return icon;
    }
}
