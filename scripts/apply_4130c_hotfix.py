#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"Could not locate {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Version 4.1.30C
# ---------------------------------------------------------------------------
props = read("gradle.properties")
props = re.sub(r"(?m)^projectVersion=.*$", "projectVersion=4.1.30C", props)
write("gradle.properties", props)


# ---------------------------------------------------------------------------
# Resonance Beacon GUI: display-only icons must never be movable.
# ---------------------------------------------------------------------------
beacon_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java"
beacon = read(beacon_path)

status_old = "        menu.addItem(STATUS_SLOT, createStatusItem(block, owner, enabled, profile, chunkMode));\n"
status_new = status_old + "        menu.addMenuClickHandler(STATUS_SLOT, (pl, slot, item, action) -> false);\n"
if "menu.addMenuClickHandler(STATUS_SLOT" not in beacon:
    beacon = replace_once(beacon, status_old, status_new, "Resonance Beacon status slot")

pyramid_controls_old = (
    "        menu.addItem(PYRAMID_INFO_SLOT, createPyramidItem(profile));\n"
    "        menu.addItem(CONTROLS_SLOT, createControlsItem());\n"
)
pyramid_controls_new = (
    "        menu.addItem(PYRAMID_INFO_SLOT, createPyramidItem(profile));\n"
    "        menu.addMenuClickHandler(PYRAMID_INFO_SLOT, (pl, slot, item, action) -> false);\n"
    "        menu.addItem(CONTROLS_SLOT, createControlsItem());\n"
    "        menu.addMenuClickHandler(CONTROLS_SLOT, (pl, slot, item, action) -> false);\n"
)
if (
    "menu.addMenuClickHandler(PYRAMID_INFO_SLOT" not in beacon
    or "menu.addMenuClickHandler(CONTROLS_SLOT" not in beacon
):
    beacon = replace_once(
        beacon,
        pyramid_controls_old,
        pyramid_controls_new,
        "Resonance Beacon pyramid/control slots",
    )

write(beacon_path, beacon)


# ---------------------------------------------------------------------------
# Gravity Well: preserve Monster-only debuffs, but pull every Bukkit Enemy
# (including Endermen and hostile Enemy implementations) plus dropped items.
# ---------------------------------------------------------------------------
runtime_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusRuntimeEffects.java"
runtime = read(runtime_path)

if "import org.bukkit.entity.Enemy;" not in runtime:
    runtime = replace_once(
        runtime,
        "import org.bukkit.entity.Entity;\n",
        "import org.bukkit.entity.Enemy;\nimport org.bukkit.entity.Entity;\n",
        "Enemy import",
    )

pulse_old = '''        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : getEntities(block, range)) {
            if (entity instanceof Player player) {
                applyPlayerEffects(player, tiers, gameTime);
            } else if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, tiers, center);
            } else if (entity instanceof Item && tiers.getOrDefault(BeaconPlusEffect.GRAVITY_WELL, 0) > 0) {
                pullEntity(entity, center, tiers.get(BeaconPlusEffect.GRAVITY_WELL));
            }
        }
'''
pulse_new = '''        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        int gravityTier = tiers.getOrDefault(BeaconPlusEffect.GRAVITY_WELL, 0);
        for (Entity entity : getEntities(block, range)) {
            if (entity instanceof Player player) {
                applyPlayerEffects(player, tiers, gameTime);
            } else if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, tiers);
            }

            if (gravityTier > 0 && (entity instanceof Enemy || entity instanceof Item)) {
                pullEntity(entity, center, gravityTier);
            }
        }
'''
if "int gravityTier = tiers.getOrDefault(BeaconPlusEffect.GRAVITY_WELL, 0);\n        for (Entity entity" not in runtime:
    runtime = replace_once(runtime, pulse_old, pulse_new, "Gravity Well pulse loop")

monster_old = '''    private static void applyMonsterEffects(Monster monster, Map<BeaconPlusEffect, Integer> tiers, Location center) {
        applyPotionIfPresent(
                monster, tiers, BeaconPlusEffect.SLOWDOWN, PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS);
        applyPotionIfPresent(monster, tiers, BeaconPlusEffect.POISON, PotionEffectType.POISON, EFFECT_DURATION_TICKS);
        int burnerTier = tiers.getOrDefault(BeaconPlusEffect.BURNER, 0);
        if (burnerTier > 0 && isUndead(monster.getType())) {
            monster.setFireTicks(Math.max(monster.getFireTicks(), 40 + burnerTier * 40));
        }
        if (tiers.getOrDefault(BeaconPlusEffect.PEACEFUL, 0) > 0) {
            monster.setTarget(null);
        }
        int gravityTier = tiers.getOrDefault(BeaconPlusEffect.GRAVITY_WELL, 0);
        if (gravityTier > 0) {
            pullEntity(monster, center, gravityTier);
        }
    }
'''
monster_new = '''    private static void applyMonsterEffects(Monster monster, Map<BeaconPlusEffect, Integer> tiers) {
        applyPotionIfPresent(
                monster, tiers, BeaconPlusEffect.SLOWDOWN, PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS);
        applyPotionIfPresent(monster, tiers, BeaconPlusEffect.POISON, PotionEffectType.POISON, EFFECT_DURATION_TICKS);
        int burnerTier = tiers.getOrDefault(BeaconPlusEffect.BURNER, 0);
        if (burnerTier > 0 && isUndead(monster.getType())) {
            monster.setFireTicks(Math.max(monster.getFireTicks(), 40 + burnerTier * 40));
        }
        if (tiers.getOrDefault(BeaconPlusEffect.PEACEFUL, 0) > 0) {
            monster.setTarget(null);
        }
    }
'''
if "applyMonsterEffects(Monster monster, Map<BeaconPlusEffect, Integer> tiers, Location center)" in runtime:
    runtime = replace_once(runtime, monster_old, monster_new, "Monster effect handler")

write(runtime_path, runtime)


# ---------------------------------------------------------------------------
# Advanced Hazmat Suit: a distinct full-set ProtectiveArmor implementation
# wired directly into Slimefun's native radiation protection checks.
# ---------------------------------------------------------------------------
advanced_class = '''package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectiveArmor;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Lead-lined upgrade of the classic Hazmat Suit.
 *
 * <p>The full four-piece set participates in Slimefun's native radiation and bee protection checks while retaining
 * the individual helmet/chestplate utility effects supplied by the registration recipe.
 */
public final class AdvancedHazmatArmorPiece extends SlimefunArmorPiece implements ProtectiveArmor {

    private static final ProtectionType[] PROTECTION_TYPES = {
        ProtectionType.BEES, ProtectionType.RADIATION
    };

    private final NamespacedKey armorSetId;

    @ParametersAreNonnullByDefault
    public AdvancedHazmatArmorPiece(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            PotionEffect[] effects) {
        super(itemGroup, item, recipeType, recipe, effects);
        armorSetId = new NamespacedKey(Slimefun.instance(), "advanced_hazmat_suit");
    }

    @Override
    public ProtectionType[] getProtectionTypes() {
        return PROTECTION_TYPES.clone();
    }

    @Override
    public boolean isFullSetRequired() {
        return true;
    }

    @Override
    public NamespacedKey getArmorSetId() {
        return armorSetId;
    }
}
'''
write(
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/armor/AdvancedHazmatArmorPiece.java",
    advanced_class,
)


# ---------------------------------------------------------------------------
# Item definitions. These are deliberately separate IDs from the classic suit
# so old Hazmat items keep their exact migration/recipe behavior.
# ---------------------------------------------------------------------------
items_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java"
items = read(items_path)

advanced_items = '''    public static final SlimefunItemStack ADVANCED_HAZMAT_HELMET = new SlimefunItemStack(
            "ADVANCED_HAZMAT_HELMET",
            Material.LEATHER_HELMET,
            Color.TEAL,
            "&3&lAdvanced Hazmat Helmet",
            "",
            "&7Lead-lined expedition protection",
            "&9+ Water Breathing",
            "",
            "&6Full set effects:",
            "&e- Radiation immunity",
            "&e- Bee sting protection");
    public static final SlimefunItemStack ADVANCED_HAZMAT_CHESTPLATE = new SlimefunItemStack(
            "ADVANCED_HAZMAT_CHESTPLATE",
            Material.LEATHER_CHESTPLATE,
            Color.TEAL,
            "&3&lAdvanced Hazmat Chestplate",
            "",
            "&7Reinforced lead-lined body protection",
            "&9+ Fire and lava protection",
            "",
            "&6Full set effects:",
            "&e- Radiation immunity",
            "&e- Bee sting protection");
    public static final SlimefunItemStack ADVANCED_HAZMAT_LEGGINGS = new SlimefunItemStack(
            "ADVANCED_HAZMAT_LEGGINGS",
            Material.LEATHER_LEGGINGS,
            Color.TEAL,
            "&3&lAdvanced Hazmat Leggings",
            "",
            "&7Reinforced lead-lined leg protection",
            "",
            "&6Full set effects:",
            "&e- Radiation immunity",
            "&e- Bee sting protection");
    public static final SlimefunItemStack ADVANCED_HAZMAT_BOOTS = new SlimefunItemStack(
            "ADVANCED_HAZMAT_BOOTS",
            Material.LEATHER_BOOTS,
            Color.BLACK,
            "&3&lAdvanced Hazmat Boots",
            "",
            "&7Sealed reinforced field boots",
            "",
            "&6Full set effects:",
            "&e- Radiation immunity",
            "&e- Bee sting protection");

    static {
        Map<Enchantment, Integer> advancedHazmatEnchants = new HashMap<>();
        advancedHazmatEnchants.put(VersionedEnchantment.PROTECTION, 4);
        advancedHazmatEnchants.put(VersionedEnchantment.UNBREAKING, 6);

        ADVANCED_HAZMAT_HELMET.addUnsafeEnchantments(advancedHazmatEnchants);
        ADVANCED_HAZMAT_CHESTPLATE.addUnsafeEnchantments(advancedHazmatEnchants);
        ADVANCED_HAZMAT_LEGGINGS.addUnsafeEnchantments(advancedHazmatEnchants);
        ADVANCED_HAZMAT_BOOTS.addUnsafeEnchantments(advancedHazmatEnchants);
    }

'''
advanced_anchor = "    public static final SlimefunItemStack GILDED_IRON_HELMET =\n"
if "ADVANCED_HAZMAT_HELMET" not in items:
    if advanced_anchor not in items:
        raise SystemExit("Could not locate Hazmat/Gilded armor insertion point")
    items = items.replace(advanced_anchor, advanced_items + advanced_anchor, 1)
write(items_path, items)


# ---------------------------------------------------------------------------
# Register the new set in the existing Armor item group and Armor Forge.
# The non-original-additions flag governs the set, matching other Legacy-only
# additions while leaving the classic Hazmat Suit untouched.
# ---------------------------------------------------------------------------
setup_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java"
setup = read(setup_path)

if "import io.github.thebusybiscuit.slimefun4.implementation.items.armor.AdvancedHazmatArmorPiece;" not in setup:
    setup = replace_once(
        setup,
        "import io.github.thebusybiscuit.slimefun4.implementation.items.armor.ElytraCap;\n",
        "import io.github.thebusybiscuit.slimefun4.implementation.items.armor.AdvancedHazmatArmorPiece;\n"
        "import io.github.thebusybiscuit.slimefun4.implementation.items.armor.ElytraCap;\n",
        "Advanced Hazmat import",
    )

advanced_registration = '''        if (Slimefun.getCfg().getBoolean("options.enable-non-original-slimefun-additions")) {
            new AdvancedHazmatArmorPiece(
                            itemGroups.armor,
                            SlimefunItems.ADVANCED_HAZMAT_HELMET,
                            RecipeType.ARMOR_FORGE,
                            new ItemStack[] {
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.SCUBA_HELMET,
                                SlimefunItems.REINFORCED_CLOTH,
                                null,
                                null,
                                null
                            },
                            new PotionEffect[] {new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 1)})
                    .register(plugin);

            new AdvancedHazmatArmorPiece(
                            itemGroups.armor,
                            SlimefunItems.ADVANCED_HAZMAT_CHESTPLATE,
                            RecipeType.ARMOR_FORGE,
                            new ItemStack[] {
                                SlimefunItems.REINFORCED_CLOTH,
                                null,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.HAZMAT_CHESTPLATE,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.REINFORCED_CLOTH
                            },
                            new PotionEffect[] {new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300, 1)})
                    .register(plugin);

            new AdvancedHazmatArmorPiece(
                            itemGroups.armor,
                            SlimefunItems.ADVANCED_HAZMAT_LEGGINGS,
                            RecipeType.ARMOR_FORGE,
                            new ItemStack[] {
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.HAZMAT_LEGGINGS,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.REINFORCED_CLOTH,
                                null,
                                SlimefunItems.REINFORCED_CLOTH
                            },
                            new PotionEffect[0])
                    .register(plugin);

            new AdvancedHazmatArmorPiece(
                            itemGroups.armor,
                            SlimefunItems.ADVANCED_HAZMAT_BOOTS,
                            RecipeType.ARMOR_FORGE,
                            new ItemStack[] {
                                SlimefunItems.REINFORCED_CLOTH,
                                null,
                                SlimefunItems.REINFORCED_CLOTH,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                SlimefunItems.HAZMAT_BOOTS,
                                SlimefunItems.REINFORCED_ALLOY_INGOT,
                                null,
                                null,
                                null
                            },
                            new PotionEffect[0])
                    .register(plugin);
        }

'''
registration_anchor = '''        new HazmatArmorPiece(
                        itemGroups.armor,
                        SlimefunItems.SCUBA_HELMET,
'''
if "SlimefunItems.ADVANCED_HAZMAT_HELMET" not in setup:
    if registration_anchor not in setup:
        raise SystemExit("Could not locate classic Hazmat registration block")
    setup = setup.replace(registration_anchor, advanced_registration + registration_anchor, 1)

write(setup_path, setup)


# ---------------------------------------------------------------------------
# Regression assertions for every requested 4.1.30C change.
# ---------------------------------------------------------------------------
checks = {
    "gradle.properties": ["projectVersion=4.1.30C"],
    beacon_path: [
        "addMenuClickHandler(STATUS_SLOT",
        "addMenuClickHandler(PYRAMID_INFO_SLOT",
        "addMenuClickHandler(CONTROLS_SLOT",
    ],
    runtime_path: [
        "import org.bukkit.entity.Enemy;",
        "entity instanceof Enemy || entity instanceof Item",
        "applyMonsterEffects(monster, tiers);",
    ],
    items_path: [
        "ADVANCED_HAZMAT_HELMET",
        "ADVANCED_HAZMAT_CHESTPLATE",
        "ADVANCED_HAZMAT_LEGGINGS",
        "ADVANCED_HAZMAT_BOOTS",
    ],
    setup_path: [
        "new AdvancedHazmatArmorPiece(",
        "SlimefunItems.ADVANCED_HAZMAT_HELMET",
        "SlimefunItems.ADVANCED_HAZMAT_BOOTS",
    ],
}
for path, needles in checks.items():
    content = read(path)
    for needle in needles:
        if needle not in content:
            raise SystemExit(f"4.1.30C verification failed: {needle!r} missing from {path}")

print("4.1.30C source patch applied and verified")
