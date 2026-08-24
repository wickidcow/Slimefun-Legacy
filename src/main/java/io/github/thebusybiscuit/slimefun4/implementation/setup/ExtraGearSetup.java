package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Integrates the historical ExtraGear addon into Slimefun Legacy while preserving
 * the original item ids, recipes, enchantments and research identifiers.
 */
final class ExtraGearSetup {

    private static final String LEGACY_RESEARCH_NAMESPACE = "extragear";

    private ExtraGearSetup() {}

    static void setup(Slimefun plugin) {
        Plugin standaloneExtraGear = Bukkit.getPluginManager().getPlugin("ExtraGear");
        if (standaloneExtraGear != null) {
            Slimefun.logger()
                    .log(
                            Level.INFO,
                            "Standalone ExtraGear detected; built-in ExtraGear compatibility content will not be registered.");
            return;
        }

        ItemGroup weapons = findCoreGroup(plugin, "weapons");
        ItemGroup armor = findCoreGroup(plugin, "armor");

        int itemCountBefore = Slimefun.getRegistry().getAllSlimefunItems().size();
        int researchCountBefore = Slimefun.getRegistry().getResearches().size();
        int researchId = 3300;

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "COPPER",
                SlimefunItems.COPPER_INGOT,
                researchId,
                enchant(Enchantment.SMITE, 2));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.LEATHER,
                "COPPER",
                SlimefunItems.COPPER_INGOT,
                researchId,
                enchant(Enchantment.BLAST_PROTECTION, 2));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "TIN",
                SlimefunItems.TIN_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 1));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "TIN",
                SlimefunItems.TIN_INGOT,
                researchId,
                enchant(Enchantment.BLAST_PROTECTION, 3));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "SILVER",
                SlimefunItems.SILVER_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 2));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "SILVER",
                SlimefunItems.SILVER_INGOT,
                researchId,
                enchant(Enchantment.PROTECTION, 2));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "ALUMINUM",
                SlimefunItems.ALUMINUM_INGOT,
                researchId,
                enchant(Enchantment.BANE_OF_ARTHROPODS, 3));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "ALUMINUM",
                SlimefunItems.ALUMINUM_INGOT,
                researchId,
                enchant(Enchantment.BLAST_PROTECTION, 2),
                enchant(Enchantment.UNBREAKING, 2));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "LEAD",
                SlimefunItems.LEAD_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 3),
                enchant(Enchantment.UNBREAKING, 8));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "LEAD",
                SlimefunItems.LEAD_INGOT,
                researchId,
                enchant(Enchantment.PROTECTION, 3),
                enchant(Enchantment.UNBREAKING, 8));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "ZINC",
                SlimefunItems.ZINC_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 2));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "ZINC",
                SlimefunItems.ZINC_INGOT,
                researchId,
                enchant(Enchantment.PROTECTION, 3));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "MAGNESIUM",
                SlimefunItems.MAGNESIUM_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 2),
                enchant(Enchantment.UNBREAKING, 5));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "MAGNESIUM",
                SlimefunItems.MAGNESIUM_INGOT,
                researchId,
                enchant(Enchantment.PROTECTION, 2),
                enchant(Enchantment.UNBREAKING, 5));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "STEEL",
                SlimefunItems.STEEL_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 5),
                enchant(Enchantment.UNBREAKING, 6));
        researchId = registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "STEEL",
                SlimefunItems.STEEL_INGOT,
                researchId,
                enchant(Enchantment.PROTECTION, 3),
                enchant(Enchantment.UNBREAKING, 4));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "BRONZE",
                SlimefunItems.BRONZE_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 3),
                enchant(Enchantment.UNBREAKING, 6));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "DURALUMIN",
                SlimefunItems.DURALUMIN_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 3),
                enchant(Enchantment.UNBREAKING, 6));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "BILLON",
                SlimefunItems.BILLON_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 4),
                enchant(Enchantment.UNBREAKING, 5));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "BRASS",
                SlimefunItems.BRASS_INGOT,
                researchId,
                enchant(Enchantment.SMITE, 4),
                enchant(Enchantment.UNBREAKING, 6));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "ALUMINUM_BRASS",
                SlimefunItems.ALUMINUM_BRASS_INGOT,
                researchId,
                enchant(Enchantment.BANE_OF_ARTHROPODS, 4),
                enchant(Enchantment.UNBREAKING, 4));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "ALUMINUM_BRONZE",
                SlimefunItems.ALUMINUM_BRONZE_INGOT,
                researchId,
                enchant(Enchantment.BANE_OF_ARTHROPODS, 4),
                enchant(Enchantment.UNBREAKING, 5));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "CORINTHIAN_BRONZE",
                SlimefunItems.CORINTHIAN_BRONZE_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 5),
                enchant(Enchantment.UNBREAKING, 5));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "SOLDER",
                SlimefunItems.SOLDER_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 4),
                enchant(Enchantment.UNBREAKING, 6));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "DAMASCUS_STEEL",
                SlimefunItems.DAMASCUS_STEEL_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 6),
                enchant(Enchantment.UNBREAKING, 7));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "HARDENED",
                SlimefunItems.HARDENED_METAL_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 7),
                enchant(Enchantment.UNBREAKING, 10));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "REINFORCED",
                SlimefunItems.REINFORCED_ALLOY_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 8),
                enchant(Enchantment.UNBREAKING, 8));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "FERROSILICON",
                SlimefunItems.FERROSILICON,
                researchId,
                enchant(Enchantment.SMITE, 8),
                enchant(Enchantment.UNBREAKING, 4));
        researchId = registerSword(
                plugin,
                weapons,
                Material.GOLDEN_SWORD,
                "GILDED_IRON",
                SlimefunItems.GILDED_IRON,
                researchId,
                enchant(Enchantment.BANE_OF_ARTHROPODS, 8),
                enchant(Enchantment.UNBREAKING, 10));
        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "NICKEL",
                SlimefunItems.NICKEL_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 6),
                enchant(Enchantment.UNBREAKING, 5));

        researchId = registerSword(
                plugin,
                weapons,
                Material.IRON_SWORD,
                "COBALT",
                SlimefunItems.COBALT_INGOT,
                researchId,
                enchant(Enchantment.SHARPNESS, 7),
                enchant(Enchantment.UNBREAKING, 7));
        registerArmor(
                plugin,
                armor,
                ArmorSet.IRON,
                "COBALT",
                SlimefunItems.COBALT_INGOT,
                researchId,
                enchant(Enchantment.PROTECTION, 7),
                enchant(Enchantment.UNBREAKING, 7));

        int itemsAdded = Slimefun.getRegistry().getAllSlimefunItems().size() - itemCountBefore;
        int researchesAdded = Slimefun.getRegistry().getResearches().size() - researchCountBefore;
        Slimefun.logger()
                .log(
                        Level.INFO,
                        "Registered {0} built-in ExtraGear items and {1} legacy-compatible researches.",
                        new Object[] {itemsAdded, researchesAdded});
    }

    private static ItemGroup findCoreGroup(Slimefun plugin, String key) {
        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
        return Slimefun.getRegistry().getAllItemGroups().stream()
                .filter(group -> group.getKey().equals(namespacedKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing core Slimefun item group: " + namespacedKey));
    }

    private static int registerSword(
            Slimefun plugin,
            ItemGroup itemGroup,
            Material type,
            String component,
            ItemStack ingredient,
            int previousResearchId,
            GearEnchantment... enchantments) {
        int researchId = previousResearchId + 1;
        String itemId = component + "_SWORD";

        if (Slimefun.getRegistry().getSlimefunItemIds().containsKey(itemId)) {
            Slimefun.logger().warning("Skipping built-in ExtraGear item because its id is already registered: " + itemId);
            return researchId;
        }

        SlimefunItemStack stack = new SlimefunItemStack(itemId, type, "&r" + ChatUtils.humanize(component));
        applyEnchantments(stack, enchantments);

        SlimefunItem item = new SlimefunItem(
                itemGroup,
                stack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    null,
                    ingredient,
                    null,
                    null,
                    ingredient,
                    null,
                    null,
                    new ItemStack(Material.STICK),
                    null
                });
        item.register(plugin);

        Research research = new Research(
                legacyResearchKey(component, "sword"),
                researchId,
                ChatUtils.humanize(component) + " Sword",
                3);
        research.addItems(item);
        research.register();
        return researchId;
    }

    private static int registerArmor(
            Slimefun plugin,
            ItemGroup itemGroup,
            ArmorSet armorSet,
            String component,
            ItemStack ingredient,
            int previousResearchId,
            GearEnchantment... enchantments) {
        int researchId = previousResearchId + 1;
        String humanizedComponent = ChatUtils.humanize(component);

        SlimefunItem[] items = new SlimefunItem[] {
            registerArmorPiece(
                    plugin,
                    itemGroup,
                    component + "_HELMET",
                    armorSet.helmet,
                    humanizedComponent + " Helmet",
                    ingredient,
                    new ItemStack[] {ingredient, ingredient, ingredient, ingredient, null, ingredient, null, null, null},
                    enchantments),
            registerArmorPiece(
                    plugin,
                    itemGroup,
                    component + "_CHESTPLATE",
                    armorSet.chestplate,
                    humanizedComponent + " Chestplate",
                    ingredient,
                    new ItemStack[] {
                        ingredient,
                        null,
                        ingredient,
                        ingredient,
                        ingredient,
                        ingredient,
                        ingredient,
                        ingredient,
                        ingredient
                    },
                    enchantments),
            registerArmorPiece(
                    plugin,
                    itemGroup,
                    component + "_LEGGINGS",
                    armorSet.leggings,
                    humanizedComponent + " Leggings",
                    ingredient,
                    new ItemStack[] {
                        ingredient,
                        ingredient,
                        ingredient,
                        ingredient,
                        null,
                        ingredient,
                        ingredient,
                        null,
                        ingredient
                    },
                    enchantments),
            registerArmorPiece(
                    plugin,
                    itemGroup,
                    component + "_BOOTS",
                    armorSet.boots,
                    humanizedComponent + " Boots",
                    ingredient,
                    new ItemStack[] {null, null, null, ingredient, null, ingredient, ingredient, null, ingredient},
                    enchantments)
        };

        Research research = new Research(
                legacyResearchKey(component, "armor"),
                researchId,
                humanizedComponent + " Armor",
                5);
        for (SlimefunItem item : items) {
            if (item != null) {
                research.addItems(item);
            }
        }
        if (research.getAffectedItems().size() > 0) {
            research.register();
        }
        return researchId;
    }

    private static SlimefunItem registerArmorPiece(
            Slimefun plugin,
            ItemGroup itemGroup,
            String itemId,
            Material material,
            String displayName,
            ItemStack ingredient,
            ItemStack[] recipe,
            GearEnchantment... enchantments) {
        if (Slimefun.getRegistry().getSlimefunItemIds().containsKey(itemId)) {
            Slimefun.logger().warning("Skipping built-in ExtraGear item because its id is already registered: " + itemId);
            return null;
        }

        SlimefunItemStack stack = new SlimefunItemStack(itemId, material, "&f" + displayName);
        applyEnchantments(stack, enchantments);

        SlimefunItem item = new SlimefunItem(itemGroup, stack, RecipeType.ARMOR_FORGE, recipe);
        item.register(plugin);
        return item;
    }

    private static void applyEnchantments(SlimefunItemStack stack, GearEnchantment... enchantments) {
        for (GearEnchantment enchantment : enchantments) {
            stack.addUnsafeEnchantment(enchantment.type, enchantment.level);
        }
    }

    private static GearEnchantment enchant(Enchantment type, int level) {
        return new GearEnchantment(type, level);
    }

    private static NamespacedKey legacyResearchKey(String component, String suffix) {
        String key = component.toLowerCase(Locale.ROOT) + '_' + suffix;
        return Objects.requireNonNull(NamespacedKey.fromString(LEGACY_RESEARCH_NAMESPACE + ':' + key));
    }

    private record GearEnchantment(Enchantment type, int level) {}

    private enum ArmorSet {
        LEATHER(
                Material.LEATHER_HELMET,
                Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS),
        IRON(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);

        private final Material helmet;
        private final Material chestplate;
        private final Material leggings;
        private final Material boots;

        ArmorSet(Material helmet, Material chestplate, Material leggings, Material boots) {
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
        }
    }
}
