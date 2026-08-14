package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlus;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.DungeonChalk;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

        if (!Slimefun.getCfg().getBoolean("options.enable-non-original-slimefun-additions")) {
            Slimefun.logger().info("Non-original Slimefun additions are disabled; skipping Adventurer's Curios.");
            return;
        }

        registered = true;

        ItemGroup curios = new ItemGroup(
                new NamespacedKey(plugin, "adventurers_curios"), createCategoryIcon(), 2);

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

        SlimefunItemStack travelersBedroll = new SlimefunItemStack(
                "ADVENTURERS_TRAVELERS_BEDROLL",
                Material.BROWN_BED,
                "&6Traveler's Bedroll",
                "&7A portable personal rest for long trips.",
                "&7Resets phantom rest and restores a little health and food.",
                "",
                "&eRight Click at night &7to rest",
                "&8Does not change time or your respawn point",
                "&8Cooldown: 5 minutes");

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

        SlimefunItemStack beaconPlus = new SlimefunItemStack(
                "BEACON_PLUS",
                Material.BEACON,
                "&6&lBeacon Plus",
                "&7A native Slimefun expedition beacon",
                "&7with 28 independently toggleable powers.",
                "",
                "&eRight Click &7to open the configuration menu",
                "&8Field effects require a powered beacon pyramid",
                "&8Activator uses bounded plugin chunk tickets");

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

        new MinersCanary(
                        curios,
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
                        curios,
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
                        curios,
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
                        curios,
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

        new TravelersBedroll(
                        curios,
                        travelersBedroll,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.WHITE_WOOL),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.LEATHER),
                            new ItemStack(Material.BROWN_BED),
                            new ItemStack(Material.LEATHER),
                            new ItemStack(Material.STRING),
                            new ItemStack(Material.RABBIT_HIDE),
                            new ItemStack(Material.STRING)
                        })
                .register(plugin);

        EmergencyParachute parachute = new EmergencyParachute(
                curios,
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

        new BeaconPlus(
                        curios,
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
    }

    private static ItemStack createCategoryIcon() {
        ItemStack icon = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Adventurer's Curios");
        meta.setLore(List.of(
                ChatColor.GRAY + "Exploration tools, navigation,",
                ChatColor.GRAY + "field safety and expedition support"));
        icon.setItemMeta(meta);
        return icon;
    }
}
