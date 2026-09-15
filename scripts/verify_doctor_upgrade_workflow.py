#!/usr/bin/env python3
"""Verify that Doctor upgrade orchestration remains read-only and delegates authorization to native gates."""

from __future__ import annotations

import sys
from pathlib import Path

ERRORS: list[str] = []


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        ERRORS.append(f"missing required file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(value: bool, message: str) -> None:
    if not value:
        ERRORS.append(message)


def reject(value: bool, message: str) -> None:
    if value:
        ERRORS.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    workflow = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorUpgradeWorkflow.java",
    )
    block_service = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/PersistedBlockIdMigrationService.java",
    )
    item_service = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/PersistedItemFormatMigrationService.java",
    )
    router = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorRouterCommand.java",
    )
    doctor = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java",
    )

    require('case "status" -> sendStatus(sender);' in workflow, "upgrade status action is missing")
    require('case "scan" -> runScan(sender);' in workflow, "upgrade scan action is missing")
    require(
        'case "plan", "dryrun", "dry-run" -> sendPlan(sender);' in workflow,
        "upgrade read-only plan action is missing",
    )
    require('case "providers", "provider" -> sendProviders(sender);' in workflow, "upgrade providers action is missing")
    require(
        "DoctorScanWithLegacyCorrelation.run(plugin, sender);" in workflow,
        "upgrade scan must reuse the migration-aware Doctor traversal",
    )
    require(
        "report == null || !report.isComplete() || report.isRepairMode()" in workflow,
        "upgrade planning must require a completed read-only scan",
    )
    require(
        "/sf doctor migrations scan " in workflow,
        "legacy-ID lane must direct operators through the native provider fingerprint scan",
    )
    require(
        "/sf doctor migrations schemas scan" in workflow,
        "same-ID lane must direct operators through the native schema fingerprint scan",
    )
    require(
        "/sf doctor migrations blocks scan " in workflow,
        "exact-machine lane must direct operators through the native placed-machine fingerprint scan",
    )
    require(
        "/sf doctor migrations schemas blocks scan" in workflow,
        "persisted block-ID lane must direct operators through the native storage fingerprint scan",
    )
    require(
        "/sf doctor migrations schemas storage scan" in workflow,
        "persisted item-payload lane must direct operators through the native storage fingerprint scan",
    )
    require(
        "new PersistedBlockIdMigrationService().audit()" in workflow,
        "upgrade planning must use the read-only persisted block-ID audit",
    )
    require(
        "new PersistedItemFormatMigrationService().audit()" in workflow,
        "upgrade planning must use the read-only persisted item-payload audit",
    )
    require(
        "public @Nonnull AuditResult audit()" in block_service,
        "persisted block-ID service must expose a read-only audit path",
    )
    require(
        "public @Nonnull AuditResult audit()" in item_service,
        "persisted item-payload service must expose a read-only audit path",
    )
    require(
        "fingerprints remain separate and single-use" in workflow,
        "operator output must explicitly preserve separate migration authorization lanes",
    )
    require(
        "Do not guess-convert these entries" in workflow,
        "manual/unresolved evidence must remain fail-closed",
    )
    reject(
        "Doctor has no block-ID migration executor" in workflow,
        "upgrade workflow must not claim the persisted block-ID executor is missing",
    )
    require(
        "Core diagnostics never rewrite addon persistence directly." in router,
        "existing legacy migration safety wording must remain intact",
    )
    require(
        'args.length > 2 && args[1].equalsIgnoreCase("upgrade")' in router,
        "router must intercept only upgrade subcommands",
    )
    require(
        'case "upgrade" -> UpgradeDiagnostics.send(plugin, sender);' in doctor,
        "plain /sf doctor upgrade must retain the existing readiness snapshot",
    )

    forbidden_calls = (
        "migrationService.run(",
        "migrationService.preparePlan(",
        "migrationService.invalidatePreparedPlan(",
        "migrationService.invalidateAllPreparedPlans(",
        "PersistedBlockIdMigrationService().preparePlan(",
        "PersistedItemFormatMigrationService().preparePlan(",
        "startSchemaMigrationRun(",
        ".migrateItem(",
        "LegacyItemSchemaMigrationService",
        "LegacyItemMigrationPlan",
        "LegacyItemSchemaMigrationPlan",
        "MessageDigest",
    )
    for call in forbidden_calls:
        reject(call in workflow, f"upgrade orchestration must not gain mutation/authorization primitive: {call}")

    reject("PlayerJoinEvent" in workflow, "upgrade orchestration must not run from player join events")
    reject("ChunkLoadEvent" in workflow, "upgrade orchestration must not run from chunk load events")
    reject("InventoryOpenEvent" in workflow, "upgrade orchestration must not run from inventory open events")

    if ERRORS:
        print("Doctor upgrade workflow verification failed:")
        for error in ERRORS:
            print(f" - {error}")
        return 1

    print("Doctor upgrade workflow verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
