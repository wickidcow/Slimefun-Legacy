package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.HazardProtectionArmorPiece;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlus;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ContainmentTrap;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EchoLantern;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EmergencyParachute;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExpeditionJournal;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExplorersSpyglass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.MinersCanary;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.StormGlass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.TravelersBedroll;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfindersCompass;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

        if (!CuriositiesConfig.isEnabled()) {
            Slimefun.logger()
                    .info("Adventurer's Curios is disabled in " + CuriositiesConfig.FILE_NAME
                            + "; skipping its item groups.");
            return;
        }

        registered = true;

        NestedItemGroup curios =
                new NestedItemGroup(new NamespacedKey(plugin, "adventurers_curios"), createCategoryIcon(), 2);
        SubItemGroup fieldCuriosities = new SubItemGroup(
                new NamespacedKey(plugin, "adventurers_curios_field"), curios, createCuriositiesIcon(), 2);
        // Keep the original key so existing guide data and integrations remain compatible.
        SubItemGroup containment = new SubItemGroup(
                new NamespacedKey(plugin, "containment_armor"), curios, createContainmentIcon(), 2);

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
                "&7A carried early-warning charm.",
                "&7It chirps for exposed lava, approaching",
                "&7hostile mobs and immediate danger.",
                "",
                "&eCarry it &7for passive warnings",
                "&eRight Click &7for an immediate scan");

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

        SlimefunItemStack travelersBedroll = new SlimefunItemStack(
                "ADVENTURERS_TRAVELERS_BEDROLL",
                Material.RED_BED,
                "&6Traveler's Bedroll",
                "&7A portable bed for long expeditions.",
                "&7Sleeping here does not replace your saved bed spawn.",
                "",
                "&ePlace and sleep &7like a normal bed",
                "&8Your /home bed location stays unchanged");

        SlimefunItemStack emergencyParachute = new SlimefunItemStack(
                "ADVENTURERS_EMERGENCY_PARACHUTE",
                Material.PHANTOM_MEMBRANE,
                "&bEmergency Parachute",
                "&7A reusable last-second fall saver.",
                "&7Automatically catches dangerous or lethal falls",
                "&7while carried in your inventory.",
                "",
                "&8Ignores small falls",
                "&8Cooldown: 60 seconds");

        SlimefunItemStack resonanceBeacon = new SlimefunItemStack(
                "BEACON_PLUS",
                Material.BEACON,
                "&6&lResonance Beacon",
                "&7A Slimefun-powered expedition beacon",
                "&7with 29 configurable three-tier powers.",
                "",
                "&eRight Click &7to unlock, enable and upgrade powers",
                "&8Pyramid size and mineral resonance cap power tier",
                "&8Includes a tiered Radiation Absorber field",
                "&8Legacy BeaconData folders can be imported directly");

        SlimefunItemStack containmentTrap = new SlimefunItemStack(
                "CONTAINMENT_TRAP",
                Material.IRON_TRAPDOOR,
                "&6Containment Trap",
                "&7A reusable field trap for dangerous cargo.",
                "&7It can safely seal one dropped radioactive stack.",
                "",
                "&eRight Click &7to throw",
                "&8Lands near radioactive material to capture it");
        ContainmentTrap.ensureSingleStack(containmentTrap);

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

        new WayfindersCompass(fieldCuriosities, wayfindersCompass, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
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

        new EchoLantern(fieldCuriosities, echoLantern, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
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

        new ExplorersSpyglass(fieldCuriosities, explorersSpyglass, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
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

        MinersCanary canary =
                new MinersCanary(fieldCuriosities, minersCanary, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.FEATHER),
                    new ItemStack(Material.GOLD_NUGGET),
                    new ItemStack(Material.FEATHER),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.YELLOW_DYE),
                    new ItemStack(Material.STRING),
                    null,
                    new ItemStack(Material.REDSTONE),
                    null
                });
        canary.register(plugin);
        canary.registerListener(plugin);

        new StormGlass(fieldCuriosities, stormGlass, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
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

        new ExpeditionJournal(fieldCuriosities, expeditionJournal, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
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

        new TravelersBedroll(fieldCuriosities, travelersBedroll, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.WHITE_WOOL),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.LEATHER),
                    new ItemStack(Material.RED_BED),
                    new ItemStack(Material.LEATHER),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.WHITE_WOOL),
                    new ItemStack(Material.STRING)
                })
                .register(plugin);

        EmergencyParachute parachute = new EmergencyParachute(
                fieldCuriosities,
                emergencyParachute,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    new ItemStack(Material.PHANTOM_MEMBRANE),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.PHANTOM_MEMBRANE),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.FEATHER),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.LEATHER),
                    new ItemStack(Material.SLIME_BALL),
                    new ItemStack(Material.LEATHER)
                });
        parachute.register(plugin);
        parachute.registerListener(plugin);

        new BeaconPlus(fieldCuriosities, resonanceBeacon, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.ECHO_SHARD),
                    SlimefunItems.ESSENCE_OF_AFTERLIFE,
                    new ItemStack(Material.ECHO_SHARD),
                    SlimefunItems.MAGICAL_GLASS,
                    new ItemStack(Material.BEACON),
                    SlimefunItems.MAGICAL_GLASS,
                    SlimefunItems.BLISTERING_INGOT_3,
                    SlimefunItems.SYNTHETIC_DIAMOND,
                    SlimefunItems.BLISTERING_INGOT_3
                })
                .register(plugin);

        new ContainmentTrap(containment, containmentTrap, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    SlimefunItems.LEAD_INGOT,
                    new ItemStack(Material.REDSTONE),
                    SlimefunItems.LEAD_INGOT,
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.IRON_TRAPDOOR),
                    new ItemStack(Material.COPPER_INGOT),
                    SlimefunItems.LEAD_INGOT,
                    new ItemStack(Material.REDSTONE),
                    SlimefunItems.LEAD_INGOT
                })
                .register(plugin);

        new HazardProtectionArmorPiece(
                        containment,
                        advancedHazmatHelmet,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.SCUBA_HELMET),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)},
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containment,
                        advancedHazmatChestplate,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_CHESTPLATE),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)},
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containment,
                        advancedHazmatLeggings,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_LEGGINGS),
                        new PotionEffect[0],
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containment,
                        advancedHazmatBoots,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_BOOTS),
                        new PotionEffect[0],
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);

        new HazardProtectionArmorPiece(
                        containment,
                        containmentHelmet,
                        RecipeType.ARMOR_FORGE,
                        containmentRecipe(advancedHazmatHelmet),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)},
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containment,
                        containmentChestplate,
                        RecipeType.ARMOR_FORGE,
                        containmentRecipe(advancedHazmatChestplate),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)},
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containment,
                        containmentLeggings,
                        RecipeType.ARMOR_FORGE,
                        containmentRecipe(advancedHazmatLeggings),
                        new PotionEffect[0],
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        containment,
                        containmentBoots,
                        RecipeType.ARMOR_FORGE,
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

    private static ItemStack createContainmentIcon() {
        ItemStack icon = new ItemStack(Material.IRON_TRAPDOOR);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Containment");
        meta.setLore(List.of(
                ChatColor.GRAY + "Protective armor and field containment tools",
                ChatColor.GRAY + "for radioactive and hazardous materials"));
        icon.setItemMeta(meta);
        return icon;
    }
}
