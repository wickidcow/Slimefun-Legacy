#!/usr/bin/env python3
"""Verify the bundled ExtraGear and ExtraTools compatibility contracts."""

from __future__ import annotations

import re
import sys
from pathlib import Path

EXPECTED_GEAR_SWORDS = (
    "COPPER",
    "TIN",
    "SILVER",
    "ALUMINUM",
    "LEAD",
    "ZINC",
    "MAGNESIUM",
    "STEEL",
    "BRONZE",
    "DURALUMIN",
    "BILLON",
    "BRASS",
    "ALUMINUM_BRASS",
    "ALUMINUM_BRONZE",
    "CORINTHIAN_BRONZE",
    "SOLDER",
    "DAMASCUS_STEEL",
    "HARDENED",
    "REINFORCED",
    "FERROSILICON",
    "GILDED_IRON",
    "NICKEL",
    "COBALT",
)
EXPECTED_GEAR_ARMOR = (
    "COPPER",
    "TIN",
    "SILVER",
    "ALUMINUM",
    "LEAD",
    "ZINC",
    "MAGNESIUM",
    "STEEL",
    "COBALT",
)
EXPECTED_TOOLS_IDS = (
    "HAMMER",
    "GOLD_TRANSMUTER",
    "ELECTRIC_COMPOSTER",
    "ELECTRIC_COMPOSTER_2",
    "COBBLESTONE_GENERATOR",
    "VAPORIZER",
    "CONCRETE_FACTORY",
    "PULVERIZER",
)
EXPECTED_TOOLS_RESEARCH_KEYS = (
    "hammer",
    "gold_transmuter",
    "electric_composter",
    "electric_composter_2",
    "cobblestone_generator",
    "vaporizer",
    "concrete_factory",
    "pulverizer",
)


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise AssertionError(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(source: str, needle: str, message: str) -> None:
    if needle not in source:
        raise AssertionError(message)


def require_regex(source: str, pattern: str, message: str) -> None:
    if re.search(pattern, source, re.MULTILINE | re.DOTALL) is None:
        raise AssertionError(message)


def verify_wiring(root: Path) -> None:
    config = read(root, "src/main/resources/configSFLAddons.yml")
    setup = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/LegacyAddonSetup.java",
    )
    post_setup = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java",
    )

    require(config, "SlimefunLegacyAddition:", "Missing SlimefunLegacyAddition config root")
    require_regex(
        config,
        r"^  ExtraGear:\s*\n    enabled: true\s*$",
        "ExtraGear must remain independently enabled by default in configSFLAddons.yml",
    )
    require_regex(
        config,
        r"^  ExtraTools:\s*\n    enabled: true\s*$",
        "ExtraTools must remain independently enabled by default in configSFLAddons.yml",
    )

    require(
        setup,
        'private static final String ADDITIONS_ROOT = "SlimefunLegacyAddition.";',
        "Legacy addon config root no longer matches configSFLAddons.yml",
    )
    require(
        setup,
        'register(plugin, "ExtraGear", () -> ExtraGearSetup.setup(plugin));',
        "ExtraGear is no longer registered through the shared toggle gate",
    )
    require(
        setup,
        'register(plugin, "ExtraTools", () -> ExtraToolsSetup.setup(plugin));',
        "ExtraTools is no longer registered through the shared toggle gate",
    )
    require(
        setup,
        "config.setDefaultValue(path, true);",
        "Bundled addon config defaults are no longer protected",
    )
    require(
        setup,
        "if (!config.getBoolean(path))",
        "Bundled addon enabled/disabled gating is missing",
    )
    require(
        post_setup,
        "LegacyAddonSetup.setup(Slimefun.instance());",
        "Bundled legacy addons are no longer wired into item registration",
    )


def verify_extra_gear(root: Path) -> None:
    source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/ExtraGearSetup.java",
    )

    require(
        source,
        'private static final String LEGACY_RESEARCH_NAMESPACE = "extragear";',
        "ExtraGear legacy research namespace changed",
    )
    require(
        source,
        'getPlugin("ExtraGear")',
        "ExtraGear standalone-plugin duplicate guard is missing",
    )
    require(source, "int researchId = 3300;", "ExtraGear legacy research id base changed")
    require(
        source,
        'String itemId = component + "_SWORD";',
        "ExtraGear legacy sword id format changed",
    )
    for suffix in ("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"):
        require(
            source,
            f'component + "{suffix}"',
            f"ExtraGear legacy armor id format changed for {suffix}",
        )
    require(
        source,
        "Slimefun.getRegistry().getSlimefunItemIds().containsKey(itemId)",
        "ExtraGear item-id collision protection is missing",
    )
    require(
        source,
        "int researchId = previousResearchId + 1;",
        "ExtraGear sequential legacy research ids are no longer preserved",
    )
    require(
        source,
        "NamespacedKey.fromString(LEGACY_RESEARCH_NAMESPACE + ':' + key)",
        "ExtraGear legacy research key construction changed",
    )

    swords = tuple(
        re.findall(
            r'registerSword\(\s*plugin,\s*weapons,\s*Material\.[A-Z_]+,\s*"([A-Z_]+)"',
            source,
            re.MULTILINE,
        )
    )
    armor = tuple(
        re.findall(
            r'registerArmor\(\s*plugin,\s*armor,\s*ArmorSet\.[A-Z_]+,\s*"([A-Z_]+)"',
            source,
            re.MULTILINE,
        )
    )
    if swords != EXPECTED_GEAR_SWORDS:
        raise AssertionError(
            "ExtraGear sword component contract changed: "
            f"expected {EXPECTED_GEAR_SWORDS}, found {swords}"
        )
    if armor != EXPECTED_GEAR_ARMOR:
        raise AssertionError(
            "ExtraGear armor component contract changed: "
            f"expected {EXPECTED_GEAR_ARMOR}, found {armor}"
        )

    item_count = len(swords) + 4 * len(armor)
    research_count = len(swords) + len(armor)
    if item_count != 59 or research_count != 32:
        raise AssertionError(
            f"ExtraGear contract must remain 59 items / 32 researches, got {item_count} / {research_count}"
        )


def verify_extra_tools(root: Path) -> None:
    source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/ExtraToolsSetup.java",
    )

    require(
        source,
        'private static final String LEGACY_RESEARCH_NAMESPACE = "extratools";',
        "ExtraTools legacy research namespace changed",
    )
    require(
        source,
        'getPlugin("ExtraTools")',
        "ExtraTools standalone-plugin duplicate guard is missing",
    )
    require(source, "int researchId = 4100;", "ExtraTools legacy research id base changed")
    require(
        source,
        ".filter(Slimefun.getRegistry().getSlimefunItemIds()::containsKey)",
        "ExtraTools item-id collision protection is missing",
    )
    require(
        source,
        "NamespacedKey.fromString(LEGACY_RESEARCH_NAMESPACE + ':' + key)",
        "ExtraTools legacy research key construction changed",
    )

    ids_match = re.search(
        r"private static final List<String> ITEM_IDS = List\.of\((.*?)\);",
        source,
        re.MULTILINE | re.DOTALL,
    )
    if ids_match is None:
        raise AssertionError("ExtraTools ITEM_IDS compatibility contract is missing")
    item_ids = tuple(re.findall(r'"([A-Z0-9_]+)"', ids_match.group(1)))
    if item_ids != EXPECTED_TOOLS_IDS:
        raise AssertionError(
            "ExtraTools item-id contract changed: "
            f"expected {EXPECTED_TOOLS_IDS}, found {item_ids}"
        )

    research_keys = tuple(
        re.findall(
            r'registerResearch\(\+\+researchId,\s*"([a-z0-9_]+)"',
            source,
            re.MULTILINE,
        )
    )
    if research_keys != EXPECTED_TOOLS_RESEARCH_KEYS:
        raise AssertionError(
            "ExtraTools research contract changed: "
            f"expected {EXPECTED_TOOLS_RESEARCH_KEYS}, found {research_keys}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    try:
        verify_wiring(root)
        verify_extra_gear(root)
        verify_extra_tools(root)
    except AssertionError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(
        "Bundled addon compatibility verified: "
        "ExtraGear (59 items / 32 researches) and ExtraTools (8 item ids / 8 researches)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
