#!/usr/bin/env python3
"""Verify Slimefun Doctor placed-block diagnosis remains conservative and isolated."""

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
upgrade = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorUpgradeWorkflow.java")
block_migration = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyBlockMigrationService.java")
output_chest = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/OutputChest.java")

require("legacyBlockIds" in report and "unknownBlockIds" in report,
        "Doctor report must retain separate legacy/unknown placed-block ID counters")
require("legacyBlockIdCounts" in report and "getLegacyBlockIdCounts()" in report,
        "Doctor report must expose exact per-ID legacy placed-block counts")
require("getLegacyBlockIdSamples()" in report and "getUnknownBlockIdSamples()" in report,
        "Doctor report must retain sampled placed-block identity findings")
require("legacyMigrationCandidateCounts" in report and "schemaMigrationCandidateCounts" in report,
        "placed-block reporting must not replace consolidated item/schema migration accounting")
require("itemLocalClaimPresent" in report,
        "placed-block reporting must preserve claim-aware same-ID schema accounting")

require("StorageCacheUtils.getDataContainer(location)" in block_doctor,
        "placed-block diagnosis must read the persisted Slimefun data container")
require("String storedId = data.getSfId()" in block_doctor,
        "placed-block diagnosis must inspect the actually persisted Slimefun ID")
require("getLegacySlimefunItemIdTarget(storedId)" in block_doctor,
        "placed-block diagnosis must consult addon-declared legacy mappings")
require("resolved != null && !storedId.equals(resolved.getId())" in block_doctor,
        "placed-block diagnosis must identify live compatibility aliases")
require("report.legacyBlockIdFound(storedId)" in block_doctor,
        "legacy/alias persisted block IDs must be reported")
require("report.unknownBlockIdFound(storedId)" in block_doctor,
        "unresolvable persisted block IDs must be reported")
require("state instanceof Nameable" in block_doctor and "ItemDoctorText.containsCjk(currentName)" in block_doctor,
        "placed-block presentation repair must remain limited to nameable blocks with CJK names")
require("ItemDoctorText.containsCjk(canonicalName)" in block_doctor,
        "placed-block repair must reject a CJK canonical replacement")
require("state.update(false, false)" in block_doctor,
        "placed-block presentation repair must persist without forcing physics")
reject("setSfId" in block_doctor or "setItemData" in block_doctor,
       "block presentation/diagnosis must never directly rewrite persisted Slimefun IDs")

server_scan_probe = "inspectSlimefunBlock(blockData.getLocation(), report.isRepairMode(), report);"
auto_probe = "inspectSlimefunBlock(blockData.getLocation(), true, report);"
require(auto_probe in service,
        "normal chunk-data load repair must inspect placed block presentation")
require(service.find(auto_probe) < service.find("BlockMenu menu = blockData.getBlockMenu();"),
        "automatic placed-block inspection must happen before BlockMenu filtering so menu-less blocks are covered")
require(server_scan_probe in service,
        "server-wide Doctor scan/repair must inspect loaded Slimefun block identity")
require("if (schemaExecutor == null)" in service and service.find("if (schemaExecutor == null)", service.find("private void collectSlimefunChunk")) < service.find(server_scan_probe),
        "placed-block presentation work must be gated out of explicit schema migration execution")
require("No presentation repair or schema-validation phase runs here" in service,
        "same-ID schema execution must retain its no-presentation-side-effect contract")

require("getLegacyBlockIds()" in scan and "getUnknownBlockIds()" in scan,
        "migration-aware Doctor scan must display placed-block identity counts")
require("getLegacyBlockIdSamples()" in scan and "getUnknownBlockIdSamples()" in scan,
        "migration-aware Doctor scan must display placed-block identity samples")
require("getLegacyMigrationCandidates()" in scan and "getSchemaMigrationCandidates()" in scan,
        "world diagnosis must preserve consolidated item/schema migration output")
require("[BLOCK LIVE ALIAS]" in correlation,
        "legacy correlation must distinguish compatibility-only live block aliases")
require("report.getLegacyMigrationCandidateCounts()" in correlation,
        "placed-block correlation must preserve exact declared legacy item counts")
require("this scan never rewrites stored block IDs" in correlation,
        "placed-block findings must retain an explicit non-authoritative migration boundary")

require("Placed block IDs: legacy/alias" in upgrade,
        "upgrade status must expose placed-block identity findings")
require("report.getLegacyBlockIdCounts()" in upgrade,
        "upgrade planning must use exact per-ID legacy placed-block counts")
require("Lane 3 - Legacy placed machines" in upgrade,
        "upgrade planning must expose a dedicated placed-machine migration lane")
require("safeBlockProviderCoveredIds" in upgrade and "actionableLegacyBlocks" in upgrade,
        "provider-owned placed machines must be classified as actionable only through safe exact providers")
require("needsProviderLegacyBlocks" in upgrade,
        "mapped legacy machines without an exact provider must remain NEEDS PROVIDER")
require("blockedLegacyBlocks" in upgrade and "+ report.getUnknownBlockIds()" in upgrade,
        "missing-target and unknown placed blocks must contribute to MANUAL/BLOCKED totals")
require("Unknown placed block IDs stay manual" in upgrade,
        "unknown placed-block identities must remain manual and never be guessed")
require("/sf doctor migrations blocks scan " in upgrade,
        "actionable placed machines must direct operators through exact fingerprint planning")
require("block-ID rewrite" in upgrade,
        "upgrade plan safety footer must explicitly reject unapproved direct block-ID rewriting")

require("getRegistrations(LegacyBlockMigrationProvider.class)" in block_migration,
        "exact placed-machine provider discovery is missing")
require("isCandidateStillValid(candidate)" in block_migration,
        "placed-machine execution must revalidate exact candidate state before mutation")
require("isStillLoaded(candidate)" in block_migration,
        "placed-machine migration must stay within loaded scope")
reject("getChunkAt(" in block_migration or "loadChunk(" in block_migration,
       "placed-machine migration service must not force-load chunks")

require("class OutputChest extends SlimefunItem" in output_chest,
        "Output Chest fixture changed unexpectedly")
reject("BlockMenu" in output_chest,
       "Output Chest unexpectedly gained a BlockMenu; review the menu-less-block Doctor regression assumptions")

if ERRORS:
    print("Doctor placed-block migration verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor placed-block migration verification passed.")
print("- menu-less placed Slimefun blocks remain included")
print("- CJK block-name recovery stays presentation-only")
print("- persisted legacy/live-alias/unknown IDs are diagnosed separately")
print("- schema migration execution remains isolated from presentation repair")
print("- exact provider-owned machines can be READY; unknown/unowned blocks remain fail-closed")
print("- exact item/schema migration accounting remains intact")
