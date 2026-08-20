package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.HazardProtectionArmorPiece;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BastionResonator;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlus;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ChunkStabilizer;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ContainmentTrap;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EchoLantern;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EchoLocator;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EmergencyFlare;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.EmergencyParachute;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExpeditionJournal;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.ExplorersSpyglass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.FieldRepairKit;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.GeigerCounter;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.MinersCanary;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.RescueWhistle;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.SalvagersMagnet;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.StormGlass;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.SurveyorsRod;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.TravelersBedroll;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfarersLodestone;
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

/** Registers the built-in Adventurer's Curios category, its field gadgets and protective gear. */
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
        SubItemGroup armor = new SubItemGroup(
                new NamespacedKey(plugin, "containment_armor"), curios, createArmorIcon(), 2);

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

        SlimefunItemStack geigerCounter = new SlimefunItemStack(
                "ADVENTURERS_GEIGER_COUNTER",
                Material.CLOCK,
                "&eGeiger Counter",
                "&7Reads your radiation exposure and nearby",
                "&7dropped radioactive Slimefun materials.",
                "",
                "&eRight Click &7to scan",
                "&8Never loads chunks to find a source");

        SlimefunItemStack surveyorsRod = new SlimefunItemStack(
                "ADVENTURERS_SURVEYORS_ROD",
                Material.BLAZE_ROD,
                "&6Surveyor's Rod",
                "&7Reports chunk, region, biome, surface,",
                "&7light, entity and block-entity information.",
                "",
                "&eRight Click &7for a chunk survey",
                "&eSneak & Right Click a block &7for block detail");

        SlimefunItemStack emergencyFlare = new SlimefunItemStack(
                "ADVENTURERS_EMERGENCY_FLARE",
                Material.FIREWORK_ROCKET,
                "&cEmergency Flare",
                "&7Launches a reusable expedition marker.",
                "&7Modes: Help, Rally Point and Danger.",
                "",
                "&eRight Click &7to launch",
                "&eSneak & Right Click &7to change mode",
                "&8Cooldown: 45 seconds");

        SlimefunItemStack fieldRepairKit = new SlimefunItemStack(
                "ADVENTURERS_FIELD_REPAIR_KIT",
                Material.ANVIL,
                "&6Field Repair Kit",
                "&7Makes an emergency partial repair away",
                "&7from proper workshop machinery.",
                "",
                "&ePut damaged gear in your off-hand",
                "&eRight Click &7to consume one repair material",
                "&8Repairs 20% of maximum durability");

        SlimefunItemStack salvagersMagnet = new SlimefunItemStack(
                "ADVENTURERS_SALVAGERS_MAGNET",
                Material.COMPASS,
                "&6Salvager's Magnet",
                "&7Pulls selected dropped items toward you",
                "&7while carried and switched on.",
                "",
                "&eRight Click &7to toggle on/off",
                "&eSneak & Right Click with an off-hand item &7to add/remove its material",
                "&eSneak & Right Click with empty off-hand &7to switch Blacklist/Whitelist",
                "&8Filter settings are stored on the magnet itself");

        SlimefunItemStack rescueWhistle = new SlimefunItemStack(
                "ADVENTURERS_RESCUE_WHISTLE",
                Material.GOAT_HORN,
                "&eRescue Whistle",
                "&7Calls nearby players without teleporting",
                "&7or revealing exact coordinates.",
                "",
                "&eRight Click &7to signal within 128 blocks",
                "&8Cooldown: 20 seconds");

        SlimefunItemStack portableCampKit = new SlimefunItemStack(
                "ADVENTURERS_PORTABLE_CAMP_KIT",
                Material.CAMPFIRE,
                "&6Portable Camp Kit",
                "&7A reusable field campfire for expeditions.",
                "&7Cook food like a normal campfire, then pack it up.",
                "",
                "&ePlace &7to make camp",
                "&eBreak &7to carry it onward");

        SlimefunItemStack echoLocator = new SlimefunItemStack(
                "ADVENTURERS_ECHO_LOCATOR",
                Material.SCULK_SENSOR,
                "&3Echo Locator",
                "&7Listens for hostile concentrations and",
                "&7spawner resonance without exact coordinates.",
                "",
                "&eRight Click &7to listen",
                "&8Uses only loaded nearby activity");

        SlimefunItemStack wayfarersLodestone = new SlimefunItemStack(
                "ADVENTURERS_WAYFARERS_LODESTONE",
                Material.COMPASS,
                "&6Wayfarer's Lodestone",
                "&7Binds a temporary expedition reference point",
                "&7and guides you back with a compass needle.",
                "",
                "&eSneak & Right Click &7to bind here",
                "&eRight Click &7to read distance and direction",
                "&8Does not create a /home or teleport point");

        SlimefunItemStack bastionResonator = new SlimefunItemStack(
                "ADVENTURERS_BASTION_RESONATOR",
                Material.RESPAWN_ANCHOR,
                "&6Bastion Resonator",
                "&7Rates immediate expedition danger from",
                "&7hostiles, spawners, hazards and radiation.",
                "",
                "&eRight Click &7to take a bounded threat reading",
                "&8Never loads additional chunks");

        SlimefunItemStack chunkStabilizer = new SlimefunItemStack(
                "ADVENTURERS_CHUNK_STABILIZER",
                Material.HEART_OF_THE_SEA,
                "&bChunk Stabilizer",
                "&7Temporarily retains one expedition chunk.",
                "&7One active chunk per player; 16 server-wide.",
                "",
                "&eRight Click &7to stabilize your current chunk for 5 minutes",
                "&eSneak & Right Click &7to release it early",
                "&8Consumes 1 Redstone Block per activation");

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

        new ContainmentTrap(fieldCuriosities, containmentTrap, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
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

        new GeigerCounter(fieldCuriosities, geigerCounter, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    SlimefunItems.LEAD_INGOT,
                    new ItemStack(Material.REDSTONE),
                    SlimefunItems.LEAD_INGOT,
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.CLOCK),
                    new ItemStack(Material.COPPER_INGOT),
                    SlimefunItems.LEAD_INGOT,
                    new ItemStack(Material.GLOWSTONE_DUST),
                    SlimefunItems.LEAD_INGOT
                })
                .register(plugin);

        new SurveyorsRod(fieldCuriosities, surveyorsRod, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.PAPER),
                    new ItemStack(Material.BLAZE_ROD),
                    new ItemStack(Material.PAPER),
                    new ItemStack(Material.COMPASS),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.SPYGLASS)
                })
                .register(plugin);

        new EmergencyFlare(fieldCuriosities, emergencyFlare, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.GUNPOWDER),
                    new ItemStack(Material.RED_DYE),
                    new ItemStack(Material.GUNPOWDER),
                    new ItemStack(Material.PAPER),
                    new ItemStack(Material.FIREWORK_ROCKET),
                    new ItemStack(Material.PAPER),
                    new ItemStack(Material.GLOWSTONE_DUST),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.GLOWSTONE_DUST)
                })
                .register(plugin);

        new FieldRepairKit(fieldCuriosities, fieldRepairKit, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.IRON_INGOT),
                    SlimefunItems.REINFORCED_CLOTH,
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.LEATHER),
                    new ItemStack(Material.ANVIL),
                    new ItemStack(Material.LEATHER),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.SLIME_BALL),
                    new ItemStack(Material.STRING)
                })
                .register(plugin);

        new SalvagersMagnet(fieldCuriosities, salvagersMagnet, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.COMPASS),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.IRON_INGOT)
                })
                .register(plugin);

        new RescueWhistle(fieldCuriosities, rescueWhistle, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.IRON_NUGGET),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.IRON_NUGGET),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.GOAT_HORN),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.REDSTONE)
                })
                .register(plugin);

        new SlimefunItem(fieldCuriosities, portableCampKit, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.LEATHER),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.CAMPFIRE),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.STRING),
                    new ItemStack(Material.CHEST),
                    new ItemStack(Material.STRING)
                })
                .register(plugin);

        new EchoLocator(fieldCuriosities, echoLocator, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.ECHO_SHARD),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.ECHO_SHARD),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.SCULK_SENSOR),
                    new ItemStack(Material.COPPER_INGOT),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.SPYGLASS),
                    new ItemStack(Material.REDSTONE)
                })
                .register(plugin);

        new WayfarersLodestone(fieldCuriosities, wayfarersLodestone, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.GOLD_INGOT),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.COMPASS),
                    new ItemStack(Material.LODESTONE),
                    new ItemStack(Material.COMPASS),
                    new ItemStack(Material.ECHO_SHARD),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.ECHO_SHARD)
                })
                .register(plugin);

        new BastionResonator(fieldCuriosities, bastionResonator, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.OBSIDIAN),
                    new ItemStack(Material.REDSTONE),
                    new ItemStack(Material.OBSIDIAN),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.RESPAWN_ANCHOR),
                    new ItemStack(Material.AMETHYST_SHARD),
                    new ItemStack(Material.ECHO_SHARD),
                    new ItemStack(Material.MAGMA_CREAM),
                    new ItemStack(Material.ECHO_SHARD)
                })
                .register(plugin);

        new ChunkStabilizer(fieldCuriosities, chunkStabilizer, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                    new ItemStack(Material.OBSIDIAN),
                    new ItemStack(Material.ENDER_PEARL),
                    new ItemStack(Material.OBSIDIAN),
                    new ItemStack(Material.REDSTONE_BLOCK),
                    new ItemStack(Material.HEART_OF_THE_SEA),
                    new ItemStack(Material.REDSTONE_BLOCK),
                    new ItemStack(Material.OBSIDIAN),
                    new ItemStack(Material.LODESTONE),
                    new ItemStack(Material.OBSIDIAN)
                })
                .register(plugin);

        new HazardProtectionArmorPiece(
                        armor,
                        advancedHazmatHelmet,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.SCUBA_HELMET),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)},
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        armor,
                        advancedHazmatChestplate,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_CHESTPLATE),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)},
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        armor,
                        advancedHazmatLeggings,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_LEGGINGS),
                        new PotionEffect[0],
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        armor,
                        advancedHazmatBoots,
                        RecipeType.ARMOR_FORGE,
                        advancedHazmatRecipe(SlimefunItems.HAZMAT_BOOTS),
                        new PotionEffect[0],
                        ADVANCED_HAZMAT_SET_ID)
                .register(plugin);

        new HazardProtectionArmorPiece(
                        armor,
                        containmentHelmet,
                        RecipeType.ARMOR_FORGE,
                        containmentRecipe(advancedHazmatHelmet),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)},
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        armor,
                        containmentChestplate,
                        RecipeType.ARMOR_FORGE,
                        containmentRecipe(advancedHazmatChestplate),
                        new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)},
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        armor,
                        containmentLeggings,
                        RecipeType.ARMOR_FORGE,
                        containmentRecipe(advancedHazmatLeggings),
                        new PotionEffect[0],
                        NETHERITE_CONTAINMENT_SET_ID)
                .register(plugin);
        new HazardProtectionArmorPiece(
                        armor,
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

    private static ItemStack createArmorIcon() {
        ItemStack icon = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Armor");
        meta.setLore(List.of(
                ChatColor.GRAY + "Advanced Hazmat and Netherite Containment",
                ChatColor.GRAY + "protective armor for hazardous environments"));
        icon.setItemMeta(meta);
        return icon;
    }
}
