#!/usr/bin/env python3
"""Guard the SmartSpawner -> IE2 Mob Simulation Doctor migration lane."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMAND = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorSmartSpawnerCommand.java"
ROUTER = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorMachineRouterCommand.java"
TABS = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java"

command = COMMAND.read_text()
router = ROUTER.read_text()
tabs = TABS.read_text()

checks = {
    "SmartSpawner integration remains reflection-only": "github.nighter.smartspawner" not in command,
    "scan uses documented whole-server API": 'getMethod("getAllSpawners")' in command,
    "execution revalidates exact IDs": 'getMethod("getSpawnerById", String.class)' in command,
    "removal uses documented SmartSpawner API": 'getMethod("removeSpawner", String.class)' in command,
    "scan fingerprint expires": "PLAN_TTL_MILLIS" in command and "currentPlan()" in command,
    "fingerprint is single-use before mutation": "preparedPlan = null;" in command and "Plan consumed." in command,
    "pre-migration manifest is required": "writeManifest(plan)" in command and "migration manifest" in command,
    "stored loot and XP limitation is disclosed": "stored-loot/XP" in command,
    "changed SmartSpawner records are skipped": "sameIdentity(current)" in command,
    "existing Slimefun data blocks replacement": "StorageCacheUtils.hasSlimefunBlock(location)" in command,
    "replacement uses IE2 chamber ID": 'CHAMBER_ID = "IE_MOB_SIMULATION_CHAMBER"' in command,
    "matching cards are resolved dynamically": 'CARD_PREFIX = "IE_MOB_DATA_CARD_"' in command,
    "unsupported spawners become empty chambers": "migratedEmpty++" in command,
    "card is inserted in the chamber input slot": "CARD_INPUT_SLOT = 1" in command and "menu.replaceExistingItem(CARD_INPUT_SLOT, card)" in command,
    "replacement uses native block-data controller": ".createBlock(location, CHAMBER_ID)" in command and "BlockStorage" not in command,
    "source stack size is retained in migration metadata": '"smartspawner-migration-stack-size"' in command,
    "sign does not overwrite occupied blocks": "if (!signBlock.getType().isAir())" in command,
    "sign line one is exact": 'front.line(0, Component.text("Spawner Migrated"))' in command,
    "sign line two is exact": 'front.line(1, Component.text("Replaced with a"))' in command,
    "sign line three is exact": 'front.line(2, Component.text("Mob Simulation"))' in command,
    "sign line four is exact": 'front.line(3, Component.text("Chamber"))' in command,
    "generated signs are tagged": '"smartspawner_migration_sign"' in command and "PersistentDataType.BYTE" in command,
    "Doctor router exposes SmartSpawner lane": "new DoctorSmartSpawnerCommand(plugin)" in router and 'equalsIgnoreCase("smartspawners")' in router,
    "Doctor tab completion exposes lane": '"smartspawners"' in tabs and 'List.of("status", "scan", "replace")' in tabs,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("SmartSpawner Doctor invariant failed: " + "; ".join(failed))

print("SmartSpawner Doctor migration invariants verified.")
