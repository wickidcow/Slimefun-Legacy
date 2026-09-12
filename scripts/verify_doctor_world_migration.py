#!/usr/bin/env python3
"""Verify Slimefun Doctor old-world block diagnosis remains conservative and wired."""

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


report = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorReport.java")
block_doctor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/BlockPresentationDoctor.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorService.java")
scan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorScanWithLegacyCorrelation.java")
correlation = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorLegacyIdCorrelation.java")
output_chest = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/OutputChest.java")

require("legacyBlockIds" in report and "unknownBlockIds" in report,
        "Doctor report must retain separate legacy/unknown placed-block ID counters")
require("getLegacyBlockIdSamples()" in report and "getUnknownBlockIdSamples()" in report,
        "Doctor report must expose sampled placed-block migration IDs")
require("legacyBlockIdFound" in report and "unknownBlockIdFound" in report,
        "Doctor report must retain placed-block identity recording methods")

require("StorageCacheUtils.getDataContainer(location)" in block_doctor,
        "placed-block diagnosis must read the persisted Slimefun data container")
require("String storedId = data.getSfId()" in block_doctor,
        "placed-block diagnosis must inspect the actually persisted Slimefun ID")
require("getLegacySlimefunItemIdTarget(storedId)" in block_doctor,
        "placed-block diagnosis must consult addon-declared legacy mappings")
require("resolved != null && !storedId.equals(resolved.getId())" in block_doctor,
        "placed-block diagnosis must identify live compatibility aliases")
require("report.legacyBlockIdFound(storedId)" in block_doctor,
        "legacy persisted block IDs must be reported")
require("report.unknownBlockIdFound(storedId)" in block_doctor,
        "unresolvable persisted block IDs must be reported")
require("state instanceof Nameable" in block_doctor and "ItemDoctorText.containsCjk(currentName)" in block_doctor,
        "placed-block presentation repair must remain limited to nameable blocks with CJK names")
require("state.update(false, false)" in block_doctor,
        "placed-block presentation repair must persist without forcing physics")
reject("setSfId" in block_doctor or "setItemData" in block_doctor,
       "block presentation/diagnosis must never directly rewrite persisted Slimefun IDs")

server_scan_probe = "inspectSlimefunBlock(blockData.getLocation(), report.isRepairMode(), report);"
auto_probe = "inspectSlimefunBlock(blockData.getLocation(), true, report);"
require(server_scan_probe in service,
        "server-wide Doctor scan/repair must inspect every loaded Slimefun block identity")
require(auto_probe in service,
        "normal chunk-data load repair must inspect placed block presentation")
require(service.find(auto_probe) < service.find("BlockMenu menu = blockData.getBlockMenu();"),
        "placed block inspection must happen before BlockMenu filtering so menu-less blocks are covered")

require("getLegacyBlockIds()" in scan and "getUnknownBlockIds()" in scan,
        "normal Doctor scan must display legacy and unknown placed-block ID counts")
require("getLegacyBlockIdSamples()" in scan and "getUnknownBlockIdSamples()" in scan,
        "normal Doctor scan must display placed-block ID samples")
require("[LIVE ALIAS]" in correlation,
        "legacy correlation must distinguish compatibility-only live aliases")
require("report.getLegacyBlockIdSamples()" in correlation and "report.getUnknownBlockIdSamples()" in correlation,
        "legacy correlation must include placed-world block findings")
require("they do not authorize a migration" in correlation,
        "legacy/live-alias findings must retain an explicit non-authoritative migration boundary")

require("class OutputChest extends SlimefunItem" in output_chest,
        "Output Chest fixture changed unexpectedly")
reject("BlockMenu" in output_chest,
       "Output Chest unexpectedly gained a BlockMenu; review the menu-less-block Doctor regression assumptions")

if ERRORS:
    print("Doctor old-world migration verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor old-world migration verification passed.")
print("- menu-less placed Slimefun blocks remain included")
print("- CJK block-name recovery stays presentation-only")
print("- persisted legacy/live-alias/unknown IDs are diagnosed separately")
print("- core does not directly rewrite addon-owned persisted block IDs")
