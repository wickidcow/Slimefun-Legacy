#!/usr/bin/env python3
"""Verify the guided Doctor item-upgrade workflow remains item-only and guarded."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def reject(condition: bool, message: str) -> None:
    if condition:
        ERRORS.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing required file: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


router = read(
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorItemUpgradeRouterCommand.java"
)
command = read(
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/ItemUpgradeDoctorCommand.java"
)
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemUpgradeService.java")
inspector = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemUpgradeInspector.java")
plan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemUpgradePlan.java")
subcommands = read(
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/SlimefunSubCommands.java"
)
tabs = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java")

require('equalsIgnoreCase("item-upgrade")' in router, "Doctor item-upgrade route is missing")
require(
    "new DoctorItemUpgradeRouterCommand(plugin, cmd)" in subcommands,
    "Doctor item-upgrade router is not registered",
)
require('"item-upgrade"' in tabs, "Doctor item-upgrade tab completion is missing")
require('List.of("status", "scan", "fix")' in tabs, "Doctor item-upgrade action completion is missing")

require("getLegacySlimefunItemIds()" in inspector, "item upgrade must use declared legacy-ID mappings")
require("SlimefunItem.getById(targetId)" in inspector, "item upgrade must require a registered target")
require("currentMaterial != targetMaterial" in inspector, "material-changing upgrades must fail closed")
require("setItemData(item, targetId)" in inspector, "safe item-ID rewrite is missing")
require("ItemPresentationDoctor" in inspector, "translated presentation repair must reuse Item Doctor safety")
reject("registerLegacySlimefunItemId(" in inspector, "item upgrade must not invent/register mappings")

require("ItemUpgradePlan.create(" in command, "scan must create a guarded fix plan")
require("matchesFingerprint" in command, "fix must validate the scan fingerprint")
require("matchesMappings" in command, "fix must invalidate mapping drift")
require("preparedPlan = null" in command, "fix authorization must be consumable/single-use")
require("DEFAULT_TTL_MILLIS" in plan, "item-upgrade plan TTL is missing")
require("10L * 60L * 1000L" in plan, "item-upgrade plan must remain short-lived")

require("saveBackpackInventory" in service, "upgraded backpack item stacks must be persisted")
require("saveBlockInventory" in service, "upgraded loaded machine inventory stacks must be persisted")
require("saveUniversalInventory" in service, "upgraded universal inventory stacks must be persisted")
reject("LegacyItemMigrationProvider" in service, "item-only upgrade must not invoke addon persistence migration providers")
reject("delete" in service.lower(), "item-only upgrade must not contain deletion logic")
reject("setBlockData" in service, "item-only upgrade must not rewrite placed block data")
reject("removeBlock" in service, "item-only upgrade must not remove placed block records")
reject("Cargo" in service, "item-only upgrade must not manipulate Cargo state")
reject("EnergyNet" in service, "item-only upgrade must not manipulate Energy state")

require("READ-ONLY" in command, "scan output must clearly say it is read-only")
require("ITEM-ONLY" in command, "fix output must clearly say it is item-only")
require("NO MAPPING" in command, "unknown IDs must be surfaced without guessing")
require("BLOCKED" in command, "unsafe mappings must be surfaced as blocked")
require("current backup" in command, "guided workflow must explicitly require a backup")

if ERRORS:
    print("Doctor item-upgrade verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor item-upgrade verification passed.")
