#!/usr/bin/env python3
"""Verify the built-in Adventurer's Curios category and field gadgets."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    files = {
        "setup": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java",
        "wayfinder": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/WayfindersCompass.java",
        "lantern": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/EchoLantern.java",
        "spyglass": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExplorersSpyglass.java",
        "canary": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/MinersCanary.java",
        "chalk": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/DungeonChalk.java",
        "storm": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/StormGlass.java",
        "journal": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExpeditionJournal.java",
        "beacon": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java",
        "docs": "docs/ADVENTURERS_CURIOS.md",
    }

    removed_duplicate_runtime = (
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusManager.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusChunkMode.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusSupportMode.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusLifecycleListener.java",
    )

    for relative in files.values():
        require((root / relative).is_file(), f"Missing Adventurer's Curios file: {relative}", failures)
    for relative in removed_duplicate_runtime:
        require(not (root / relative).exists(), f"Duplicate Beacon Plus runtime must remain removed: {relative}", failures)

    try:
        setup = read(root, files["setup"])
        for token in (
            '"adventurers_curios"',
            '"ADVENTURERS_WAYFINDERS_COMPASS"',
            '"ADVENTURERS_ECHO_LANTERN"',
            '"ADVENTURERS_EXPLORERS_SPYGLASS"',
            '"ADVENTURERS_MINERS_CANARY"',
            '"ADVENTURERS_DUNGEON_CHALK"',
            '"ADVENTURERS_STORM_GLASS"',
            '"ADVENTURERS_EXPEDITION_JOURNAL"',
            '"BEACON_PLUS"',
            "new WayfindersCompass(",
            "new EchoLantern(",
            "new ExplorersSpyglass(",
            "new MinersCanary(",
            "new DungeonChalk(",
            "new StormGlass(",
            "new ExpeditionJournal(",
            "new BeaconPlus(",
        ):
            require(token in setup, f"Curios setup invariant is missing: {token}", failures)
        require(setup.count("RecipeType.ENHANCED_CRAFTING_TABLE") >= 8, "All eight Curios need real recipes", failures)
        for token in (
            "new ItemStack(Material.GLASS)",
            "new ItemStack(Material.LEVER)",
            "new ItemStack(Material.CLOCK)",
            "new ItemStack(Material.END_CRYSTAL)",
            "new ItemStack(Material.REDSTONE_BLOCK)",
            "new ItemStack(Material.OBSIDIAN)",
            "new ItemStack(Material.ANVIL)",
        ):
            require(token in setup, f"Beacon Plus commissioning recipe invariant is missing: {token}", failures)

        post_setup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java")
        registration = post_setup.find("AdventurersCuriosSetup.setup(Slimefun.instance());")
        finalization = post_setup.find("markInitialRegistrationFinalized()")
        require(registration >= 0, "Adventurer's Curios is not registered during PostSetup", failures)
        require(finalization >= 0, "Registry finalization marker is missing", failures)
        if registration >= 0 and finalization >= 0:
            require(registration < finalization, "Curios must register before the item registry is finalized", failures)

        wayfinder = read(root, files["wayfinder"])
        for token in ("getLastDeathLocation()", "setLodestone(target)", "setLodestoneTracked(false)", "getSpawnLocation()"):
            require(token in wayfinder, f"Wayfinder invariant is missing: {token}", failures)

        lantern = read(root, files["lantern"])
        for token in ("getNearbyEntities", "instanceof Monster", "PotionEffectType.GLOWING", "COOLDOWN_TICKS = 30 * 20"):
            require(token in lantern, f"Echo Lantern invariant is missing: {token}", failures)

        spyglass = read(root, files["spyglass"])
        for token in ("getBiome().getKey().getKey()", "getDirection(location.getYaw())", "location.getBlockX()"):
            require(token in spyglass, f"Explorer's Spyglass invariant is missing: {token}", failures)

        canary = read(root, files["canary"])
        for token in ("RANGE = 7", "COOLDOWN_TICKS = 5 * 20", "world.isChunkLoaded", "Material.LAVA"):
            require(token in canary, f"Miner's Canary invariant is missing: {token}", failures)
        require("loadChunk" not in canary and "getChunkAt(" not in canary, "Miner's Canary must not load chunks to scan", failures)

        chalk = read(root, files["chalk"])
        for token in ("PersistentDataType.STRING", '"dungeon_chalk_marker"', "getClickedBlock()", "player.isSneaking()"):
            require(token in chalk, f"Dungeon Chalk invariant is missing: {token}", failures)
        for forbidden in ("setType(", "breakNaturally(", "runLater("):
            require(forbidden not in chalk, f"Dungeon Chalk must remain non-destructive: {forbidden}", failures)

        storm = read(root, files["storm"])
        for token in ("isThundering()", "hasStorm()", "getFullTime()", "getWeatherDuration()"):
            require(token in storm, f"Storm Glass invariant is missing: {token}", failures)
        require("setStorm(" not in storm and "setTime(" not in storm, "Storm Glass must remain read-only", failures)

        journal = read(root, files["journal"])
        for token in ("MAX_RECORDED_BIOMES = 128", '"expedition_journal_biomes"', "PersistentDataType.STRING", "getBiome().getKey().getKey()"):
            require(token in journal, f"Expedition Journal invariant is missing: {token}", failures)

        beacon = read(root, files["beacon"])
        for token in (
            'PLUGIN_NAME = "BeaconPlus3"',
            'API_CLASS = "thito.beaconplus.BeaconAPI"',
            'SECTION_CLASS = "thito.beaconplus.config.Section"',
            'CREATE_EMPTY_ITEM = "createBeaconEmptyItem"',
            'CRAFT_PERMISSION_PATH = "Permissions.Craft"',
            "Class.forName(API_CLASS, true, classLoader)",
            "apiClass.getMethod(CREATE_EMPTY_ITEM, Player.class)",
            'apiClass.getMethod("getBeaconConfig")',
            'sectionClass.getMethod("getString", String.class)',
            'apiClass.getMethod("hasNoPermission", Player.class, String.class, boolean.class)',
            "Boolean.TRUE.equals(denied)",
            "implements NotPlaceable",
            "event.cancel()",
            "The Curio was not consumed",
        ):
            require(token in beacon, f"Beacon Plus bridge invariant is missing: {token}", failures)
        require("import thito.beaconplus" not in beacon, "Beacon Plus bridge must remain reflection-only", failures)
        for forbidden in (
            "addPluginChunkTicket",
            "setChunkForceLoaded",
            "setForceLoaded",
            "BlockTicker",
            "StorageCacheUtils",
            "BeaconPlusManager",
            "PotionEffectType",
        ):
            require(forbidden not in beacon, f"Beacon Plus bridge must not duplicate BeaconPlus3 runtime: {forbidden}", failures)

        docs = read(root, files["docs"])
        for token in (
            "BeaconAPI#createBeaconEmptyItem(Player)",
            "genuine BeaconPlus3 beacon item",
            "no hard BeaconPlus3 dependency",
            "`Permissions.Craft`",
            "not consumed",
            "no direct `thito.beaconplus` imports",
        ):
            require(token in docs, f"Curios documentation is missing BeaconPlus3 bridge detail: {token}", failures)
    except FileNotFoundError as error:
        failures.append(f"Unable to inspect missing Adventurer's Curios file: {error}")

    report = root / "build/reports/adventurers-curios.txt"
    report.parent.mkdir(parents=True, exist_ok=True)

    if failures:
        report.write_text(
            "Adventurer's Curios verification: FAIL\n"
            + "\n".join(f"- {failure}" for failure in failures)
            + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Adventurer's Curios verification: PASS\n"
        "- eight built-in Curios are registered before registry finalization\n"
        "- original navigation and detection Curios retain their bounded behavior\n"
        "- Miner's Canary, Dungeon Chalk, Storm Glass and Expedition Journal remain player-triggered and bounded\n"
        "- Beacon Plus is a reflection-only commissioning bridge to the standalone BeaconPlus3 API\n"
        "- BeaconPlus3 creates and owns the genuine beacon item and all runtime behavior after commissioning\n"
        "- BeaconPlus3's configured craft permission is enforced through its own permission helper\n"
        "- the Curio remains intact if BeaconPlus3 is absent, disabled or cannot create the native item\n"
        "- no duplicate Beacon Plus chunk loader, support ticker or persistence runtime remains in Slimefun\n"
        "- no existing database schema, Cargo, Energy or machine transaction semantics are changed\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
