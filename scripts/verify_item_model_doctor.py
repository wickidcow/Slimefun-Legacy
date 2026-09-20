#!/usr/bin/env python3
"""Verify guarded Item Doctor cleanup for stale Slimefun Legacy item-model values."""

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ERRORS: list[str] = []


def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        ERRORS.append(f"missing required file: {path}")
        return ""
    return file.read_text(encoding="utf-8")


def require(value: bool, message: str) -> None:
    if not value:
        ERRORS.append(message)


def reject(value: bool, message: str) -> None:
    if value:
        ERRORS.append(message)


executor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemModelRepairExecutor.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorService.java")
report = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorReport.java")
command = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
tabs = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java")
docs = read("docs/wiki/Doctor-and-Diagnostics.md")
test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/services/stability/TestItemDoctorReportItemModels.java")

require("SlimefunItem.getById(slimefunId) == null" in executor,
        "item-model cleanup must require a currently registered Slimefun ID")
require("getModelData(slimefunId) != 0" in executor,
        "item-model cleanup must refuse IDs whose current server mapping is still non-zero")
require("Float.compare(floats.get(0), (float) bundledModel) != 0" in executor,
        "item-model cleanup must require an exact first-float match to the bundled mapping")
require("floats.remove(0)" in executor,
        "item-model cleanup must remove only Slimefun's first model float")
require("component.getFlags().isEmpty()" in executor
        and "component.getStrings().isEmpty()" in executor
        and "component.getColors().isEmpty()" in executor,
        "item-model cleanup must inspect all modern CustomModelData component lanes before clearing it")
require("currentMeta.setCustomModelDataComponent(null)" in executor,
        "single-value stale model data must be removed as a component, not left as empty metadata")
require("component.setFloats(floats)" in executor and "currentMeta.setCustomModelDataComponent(component)" in executor,
        "additional custom-model component values must be preserved")
require("ItemMeta originalMeta = currentMeta.clone()" in executor and "item.setItemMeta(originalMeta)" in executor,
        "failed item-model cleanup must restore original metadata")
require("MAX_CONTAINER_DEPTH = 4" in executor,
        "item-model cleanup nested-container traversal must remain bounded")

require("startItemModelRun(boolean repair" in service,
        "Item Doctor service must expose explicit item-model scan/repair traversal")
require("new ItemModelRepairExecutor(repair)" in service,
        "item-model traversal must use the guarded executor")
automatic_region = service[service.find("@EventHandler"):service.find("private final class ServerRun")]
reject("startItemModelRun(" in automatic_region,
       "automatic Item Doctor listeners must never invoke item-model cleanup")

require("itemModelCandidates" in report and "itemModelRepairs" in report,
        "Doctor report must retain dedicated item-model candidate/repair counters")
require('case "item-models", "itemmodels", "models"' in command,
        "Doctor command must expose the item-model compatibility group")
require('action.equals("scan")' in command,
        "item-model Doctor must expose a read-only scan")
require('args[3].equalsIgnoreCase("confirm")' in command,
        "item-model repair must require explicit confirm")
require("service.startItemModelRun(repair" in command,
        "item-model command must use the server-wide Doctor traversal")
require('"item-models"' in tabs and 'List.of("status", "scan", "repair")' in tabs,
        "item-model Doctor tab completion is missing")
require("/sf doctor item-models scan" in docs and "/sf doctor item-models repair confirm" in docs,
        "item-model Doctor documentation is missing")
require("testItemModelCandidateCountsAndRepairs" in test,
        "item-model Doctor report regression test is missing")

if ERRORS:
    print("Item-model Doctor verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Item-model Doctor verification passed.")
print("- cleanup is explicit and never automatic")
print("- only exact bundled first-float matches on IDs configured to 0 are eligible")
print("- unrelated modern CustomModelData lanes remain preserved")
print("- nested containers and server-wide Doctor storage traversal remain bounded")
