package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.HazardProtectionArmorPiece;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlus;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.DungeonChalk;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EchoLantern;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExpeditionJournal;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExplorersSpyglass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.MinersCanary;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.StormGlass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfindersCompass;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Registers the built-in Adventurer's Curios category, its field gadgets and protective gear.
 */
final class AdventurersCuriosSetup {

    private static final String ADVANCED_HAZMAT_SET_ID = "advanced_hazmat_gear";
    private static final String NETHERITE_CONTAINMENT_SET_ID = "netherite_containment_armor";

    private static boolean registered;

    private AdventurersCuriosSetup() {}

    static void setup(Slimefun plugin) {
        if (registered) {
            return;
        }
        registered = true;

        NestedItemGroup curios = new NestedItemGroup(
                new NamespacedKey(plugin, "adventurers_curios"), createCategoryIcon(), 2);
        SubItemGroup fieldCuriosities = new SubItemGroup(
                new NamespacedKey(plugin, "adventurers_curios_field"), curios, createCuriositiesIcon(), 2);
        SubItemGroup advancedHazmatGear = new SubItemGroup(
                new NamespacedKey(plugin, "advanced_hazmat_gear"), curios, createAdvancedHazmatIcon(), 2);
        SubItemGroup containmentArmor = new SubItemGroup(
                new NamespacedKey(plugin, "netherite_containment_armor"), curios, createContainmentArmorIcon(), 2);

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

        SlimefunItemStack minersCanary = new SlimefunItemStack(
                "ADVENTURERS_MINERS_CANARY",
                Material.YELLOW_DYE,
                "&eMiner's Canary",
                "&7A reusable warning charm for miners.",
                "&7It squawks when exposed lava is nearby.",
                "",
                "&eRight Click &7to listen");

        SlimefunItemStack dungeonChalk = new SlimefunItemStack(
                "ADVENTURERS_DUNGEON_CHALK",
                Material.WHITE_DYE,
                "&fDungeon Chalk",
                "&7Keep one personal breadcrumb without",
                "&7placing or changing blocks in the world.",
                "",
                "&eRight Click a block &7to mark it",
                "&eRight Click air &7to recall it",
                "&eSneak & Right Click &7to clear it");

        SlimefunItemStack stormGlass = new SlimefunItemStack(
                "ADVENTURERS_STORM_GLASS",
                Material.GLASS_BOTTLE,
                "&bStorm Glass",
                "&7Reads the sky without changing it.",
                "&7Shows weather, day phase and moon phase.",
                "",
                "&eRight Click &7to read");

        SlimefunItemStack expeditionJournal = new SlimefunItemStack(
                "ADVENTURERS_EXPEDITION_JOURNAL",
                Material.WRITABLE_BOOK,
                "&6Expedition Journal",
                "&7Records biomes as you deliberately",
                "&7make field entries during your travels.",
                "",
                "&eRight Click &7to record the current biome",
                "&eSneak & Right Click &7for recent discoveries");

        SlimefunItemStack beaconPlus = new SlimefunItemStack(
                "BEACON_PLUS",
                Material.BEACON,
                "&6&lBeacon Plus",
                "&7A native Slimefun expedition beacon",
                "&7with 30 independently toggleable effects.",
                "",
                "&eRight Click &7to open the configuration menu",
                "&8Field effects require a powered beacon pyramid",
                "&8and Slimefun Energy; Extra Power costs 30 XP levels",
                "&8Activator uses bounded plugin chunk tickets");

        SlimefunItemStack advancedHazmatHelmet = new SlimefunItemStack(
                "ADVANCED_HAZMAT_HELMET",
                Material.LEATHER_HELMET,
                Color.YELLOW,
                "&eAdvanced Hazmat Helmet",
                "",
                "&7Upgraded protection for hazardous environments.",
                "&7Built for handling sensitive radioactive materials.",
                "",
                "&bFull Set: &fRadiation and bee protection");
        SlimefunItemStack advancedHazmatChestplate = new SlimefunItemStack(
                "ADVANCED_HAZMAT_CHESTPLATE",
                Material.LEATHER_CHESTPLATE,
                Color.YELLOW,
                "&eAdvanced Hazmat Chestplate",
                "",
                "&7Upgraded protection for hazardous environments.",
                "&7Built for handling sensitive radioactive materials.",
                "",
                "&bFull Set: &fRadiation and bee protection");
        SlimefunItemStack advancedHazmatLeggings = new SlimefunItemStack(
                "ADVANCED_HAZMAT_LEGGINGS",
                Material.LEATHER_LEGGINGS,
                Color.YELLOW,
                "&eAdvanced Hazmat Leggings",
                "",
                "&7Upgraded protection for hazardous environments.",
                "&7Built for handling sensitive radioactive materials.",
                "",
                "&bFull Set: &fRadiation and bee protection");
        SlimefunItemStack advancedHazmatBoots = new SlimefunItemStack(
                "ADVANCED_HAZMAT_BOOTS",
                Material.LEATHER_BOOTS,
                Color.YELLOW,
                "&eAdvanced Hazmat Boots",
                "",
                "&7Upgraded protection for hazardous environments.",
                "&7Built for handling sensitive radioactive materials.",
                "",
                "&bFull Set: &fRadiation and bee protection");

        SlimefunItemStack containmentHelmet = new SlimefunItemStack(
                "NETHERITE_CONTAINMENT_HELMET",
                Material.NETHERITE_HELMET,
                "&8Netherite Containment Helmet",
                "",
                "&7For when you need protection from the world",
                "&7while handling sensitive or hazardous materials.",
                "",
                "&bFull Set: &fRadiation and bee protection",
                "&8Lead-lined and netherite reinforced",
                "&8Sealed head protection for contaminated environments.");
        SlimefunItemStack containmentChestplate = new SlimefunItemStack(
                "NETHERITE_CONTAINMENT_CHESTPLATE",
                Material.NETHERITE_CHESTPLATE,
                "&8Netherite Containment Chestplate",
                "",
                "&7For when you need protection from the world",
                "&7while handling sensitive or hazardous materials.",
                "",
                "&bFull Set: &fRadiation and bee protection",
                "&8Lead-lined and netherite reinforced",
                "&8Heavy shielding protects the core of the suit.");
        SlimefunItemStack containmentLeggings = new SlimefunItemStack(
                "NETHERITE_CONTAINMENT_LEGGINGS",
                Material.NETHERITE_LEGGINGS,
                "&8Netherite Containment Leggings",
                "",
                "&7For when you need protection from the world",
                "&7while handling sensitive or hazardous materials.",
                "",
                "&bFull Set: &fRadiation and bee protection",
                "&8Lead-lined and netherite reinforced",
                "&8Flexible shielding keeps hazardous exposure contained.");
        SlimefunItemStack containmentBoots = new SlimefunItemStack(
                "NETHERITE_CONTAINMENT_BOOTS",
                Material.NETHERITE_BOOTS,
                "&8Netherite Containment Boots",
                "",
                "&7For when you need protection from the world",
                "&7while handling sensitive or hazardous materials.",
                "",
                "&bFull Set: &fRadiation and bee protection",
                "&8Lead-lined and netherite reinforced",
                "&8Keeps hazardous ground safely beneath you.");

        new WayfindersCompass(
                        fieldCuriosities,
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
                        fieldCuriosities,
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
                        fieldCuriosities,
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

        new MinersCanary(
                        fieldCuriosities,
                        minersCanary,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.FEATHER),
                            new ItemStack(Material.GOLD_NUGGET),
                            new ItemStack(Material.FEATHER),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.YELLOW_DYE),
                            new ItemStack(Material.STRING),
                            null,
                            new ItemStack(Material.REDSTONE),
                            null
                        })
                .register(plugin);

        new DungeonChalk(
                        fieldCuriosities,
                        dungeonChalk,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.CALCITE),
                            new ItemStack(Material.GLOW_INK_SAC),
                            new ItemStack(Material.CALCITE),
                            null,
                            new ItemStack(Material.WHITE_DYE),
                            null,
                            null,
                            new ItemStack(Material.PAPER),
                            null
                        })
                .register(plugin);

        new StormGlass(
                        fieldCuriosities,
                        stormGlass,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.COPPER_INGOT),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.COPPER_INGOT),
                            null,
                            new ItemStack(Material.GLASS_BOTTLE),
                            null,
                            null,
                            new ItemStack(Material.REDSTONE),
                            null
                        })
                .register(plugin);

        new ExpeditionJournal(
                        fieldCuriosities,
                        expeditionJournal,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.COMPASS),
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.MAP),
                            new ItemStack(Material.WRITABLE_BOOK),
                            new ItemStack(Material.SPYGLASS),
                            null,
                            new ItemStack(Material.AMETHYST_SHARD),
                            null
                        })
                .register(plugin);

        new BeaconPlus(
                        fieldCuriosities,
                        beaconPlus,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.ECHO_SHARD),
                            new ItemStack(Material.NETHERITE_INGOT),
                            new ItemStack(Material.ECHO_SHARD),
                            new ItemStack(Material.REDSTONE_BLOCK),
                            new ItemStack(Material.BEACON),
                            new ItemStack(Material.REDSTONE_BLOCK),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.ENDER_EYE),
                            new ItemStack(Material.AMETHYST_SHARD)
                        })
                .register(plugin);

        new HazardProtectionArmorPiece(
                        advancedHazmatGear,
                        advancedHazmatHelmet,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.SCUBA_HELMET),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)},
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        advancedHazmatGear,
                        advancedHazmatChestplate,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_CHESTPLATE),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)},
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        advancedHazmatGear,
                        advancedHazmatLeggings,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_LEGGINGS),
                        new PotionEffect[0],
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        advancedHazmatGear,
                        advancedHazmatBoots,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_BOOTS),
                        new PotionEffect[0],
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);

        new HazardProtectionArmorPiece(
                        containmentArmor,
                        containmentHelmet,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        containmentRecipe(advancedHazmatHelmet),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)},
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containmentArmor,
                        containmentChestplate,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        containmentRecipe(advancedHazmatChestplate),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)},
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containmentArmor,
                        containmentLeggings,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        containmentRecipe(advancedHazmatLeggings),
                        new PotionEffect[0],
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containmentArmor,
                        containmentBoots,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        containmentRecipe(advancedHazmatBoots),
                        new PotionEffect[0],
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
    }

    private static ItemStack[] advancedHazmatRecipe(ItemStack basePiece) {
        return new ItemStack[] {
            SlimefunItems.LEAD_INGOT,
            SlimefunItems.REINFORCED_CLOTH,
            SlimefunItems.LEAD_INGOT,
            SlimefunItems.REINFORCED_CLOTH,
            basePiece,
            SlimefunItems.REINFORCED_CLOTH,
            SlimefunItems.LEAD_INGOT,
            SlimefunItems.REINFORCED_CLOTH,
            SlimefunItems.LEAD_INGOT
        };
    }

    private static ItemStack[] containmentRecipe(ItemStack advancedHazmatPiece) {
        return new ItemStack[] {
            SlimefunItems.LEAD_INGOT,
            new ItemStack(Material.NETHERITE_INGOT),
            SlimefunItems.LEAD_INGOT,
            new ItemStack(Material.NETHERITE_INGOT),
            advancedHazmatPiece,
            new ItemStack(Material.NETHERITE_INGOT),
            SlimefunItems.LEAD_INGOT,
            new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
            SlimefunItems.LEAD_INGOT
        };
    }

    private static ItemStack createCategoryIcon() {
        ItemStack icon = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Adventurer's Curios");
        meta.setLore(List.of(
                ChatColor.GRAY + "Exploration tools, navigation,",
                ChatColor.GRAY + "field safety and protective equipment"));
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack createCuriositiesIcon() {
        ItemStack icon = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Curiosities");
        meta.setLore(List.of(
                ChatColor.GRAY + "Exploration tools, navigation,",
                ChatColor.GRAY + "field support and expedition gear"));
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack createAdvancedHazmatIcon() {
        ItemStack icon = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) icon.getItemMeta();
        meta.setColor(Color.YELLOW);
        meta.setDisplayName(ChatColor.YELLOW + "Advanced Hazmat Gear");
        meta.setLore(List.of(
                ChatColor.GRAY + "Upgraded hazardous-material protection",
                ChatColor.GRAY + "for sensitive and radioactive materials"));
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack createContainmentArmorIcon() {
        ItemStack icon = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "Netherite Containment Armor");
        meta.setLore(List.of(
                ChatColor.GRAY + "For when you need protection from the world",
                ChatColor.GRAY + "while handling sensitive or hazardous materials"));
        icon.setItemMeta(meta);
        return icon;
    }
}
