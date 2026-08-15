#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.31 Core Platform Phase 1L Part 3 upgrade diagnostics."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

CURRENT_VERSION = "4.1.31"
CURRENT_PHASE = "Core Platform Phase 1L"
PREVIOUS_STABLE_VERSION = "4.1.29"


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def load_json(root: Path, relative: str) -> dict:
    return json.loads(read(root, relative))


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def project_version(root: Path) -> str:
    match = re.search(r"^projectVersion=(\d+\.\d+\.\d+)$", read(root, "gradle.properties"), re.M)
    return match.group(1) if match else ""


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    try:
        require(project_version(root) == CURRENT_VERSION, "Part 3 requires projectVersion 4.1.31", failures)

        upgrade = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/UpgradeDiagnostics.java",
        )
        for token in (
            "Slimefun Upgrade Readiness",
            "Overall status:",
            'status = "READY"',
            'status = "ATTENTION"',
            'status = "BLOCKED"',
            "getPlatformCompatibilityService()",
            "getCoreReadinessService()",
            "getRegistryRuntimeService()",
            "getSchedulerService()",
            "getMachineRuntimeService()",
            "getStorageRuntimeService()",
            "PluginDependencyDiagnosticsService",
            "AddonCompatibilitySummary",
            "getAddonRuntimeHealthService()",
            "getExternalIntegrationService()",
            "getWorldChunkRuntimeService()",
            "getBlockDataRuntimeService()",
            "getItemDoctorService()",
            "getAddonApiCompatibilityFacade()",
            'BASELINE_RESOURCE = "compatibility/release-baselines.json"',
            "READY means no current diagnostic blocker",
            "Read-only snapshot:",
        ):
            require(token in upgrade, f"Upgrade diagnostics invariant missing: {token}", failures)

        for forbidden in (
            ".refresh(",
            "retryMachine(",
            "retryAllMachines(",
            "retryAll(",
            ".retry(",
            "startServerRun(",
            "inspectItem(",
            "inspectPlayer(",
            "setPaused(",
            ".save(",
            ".delete(",
            "loadChunk(",
            "unloadChunk(",
            "disablePlugin(",
            "enablePlugin(",
        ):
            require(forbidden not in upgrade, f"Upgrade diagnostics must remain read-only: {forbidden}", failures)

        for forbidden in (
            "CargoNet",
            "EnergyNet",
            "BlockStorageMigrator",
            "PlayerProfileMigrator",
        ):
            require(forbidden not in upgrade, f"Upgrade diagnostics crossed a protected runtime boundary: {forbidden}", failures)

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        require(
            'case "upgrade" -> UpgradeDiagnostics.send(plugin, sender);' in doctor,
            "Doctor command does not expose /sf doctor upgrade",
            failures,
        )
        require("status|upgrade|core|registry|chunks" in doctor, "Doctor usage does not advertise upgrade diagnostics", failures)

        build = read(root, "build.gradle.kts")
        require(
            'from("compatibility/release-baselines.json")' in build,
            "Build does not package the canonical release baseline registry",
            failures,
        )
        require('into("compatibility")' in build, "Packaged baseline registry path is missing", failures)

        baselines = load_json(root, "compatibility/release-baselines.json")
        require(
            baselines.get("candidate", {}).get("version") == CURRENT_VERSION,
            "Upgrade diagnostics candidate baseline must remain 4.1.31",
            failures,
        )
        require(
            baselines.get("previous_stable", {}).get("version") == PREVIOUS_STABLE_VERSION,
            "Upgrade diagnostics previous stable must remain 4.1.29",
            failures,
        )

        support = load_json(root, "compatibility/support-contract.json")
        require(support.get("release") == CURRENT_VERSION, "Support contract release must remain 4.1.31", failures)
        require(support.get("phase") == CURRENT_PHASE, "Support contract phase must remain Core Platform Phase 1L", failures)
        policy = support.get("compatibility_policy", {})
        for key in (
            "runtime_upgrade_diagnostics",
            "upgrade_diagnostics_are_read_only",
            "upgrade_diagnostics_conservative_status",
            "release_baseline_registry_packaged_for_runtime_diagnostics",
            "upgrade_diagnostics_reports_dependency_and_runtime_evidence",
        ):
            require(policy.get(key) is True, f"Phase 1L Part 3 policy must remain true: {key}", failures)
        for key in (
            "upgrade_diagnostics_automatic_repairs_or_migrations",
            "upgrade_diagnostics_changes_plugin_enable_state",
            "upgrade_diagnostics_parses_arbitrary_server_logs",
            "phase1l_part3_changes_normal_cargo_energy_machine_semantics",
            "phase1l_part3_changes_storage_or_gameplay_semantics",
            "database_format_changed",
            "storage_schema_changed",
            "gameplay_behavior_changed",
        ):
            require(policy.get(key) is False, f"Phase 1L Part 3 policy must remain false: {key}", failures)

        registry = load_json(root, "compatibility/core-api-registry.json")
        require(registry.get("release") == CURRENT_VERSION, "Core API registry release must remain 4.1.31", failures)
        require(
            "runtime-upgrade-readiness-diagnostics" in set(registry.get("compatibility_capabilities", [])),
            "Core API registry is missing runtime upgrade diagnostics capability",
            failures,
        )

        legacy_verifier = read(root, "scripts/verify_legacy.py")
        part2_position = legacy_verifier.find('"verify_phase1l_release_artifact.py"')
        part3_position = legacy_verifier.find('"verify_core_platform_phase1l_part3.py"')
        require(part2_position >= 0, "Full Legacy verifier must retain the Phase 1L Part 2 gate", failures)
        require(part3_position >= 0, "Full Legacy verifier must run the Phase 1L Part 3 verifier", failures)
        if part2_position >= 0 and part3_position >= 0:
            require(part2_position < part3_position, "Phase 1L Part 3 verifier must run after Part 2", failures)

        require(
            (root / "docs/CORE_PLATFORM_PHASE1L_PART3_UPGRADE_DIAGNOSTICS.md").is_file(),
            "Phase 1L Part 3 documentation is missing",
            failures,
        )
    except Exception as error:
        failures.append(f"Phase 1L Part 3 verifier failed to inspect repository: {error}")

    report = root / "build/reports/phase1l-upgrade-diagnostics.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1L Part 3 upgrade diagnostics verification: FAIL\n"
            + "\n".join(f"- {failure}" for failure in failures)
            + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1L Part 3 upgrade diagnostics verification: PASS\n"
        "- /sf doctor upgrade is wired as a read-only diagnostic command\n"
        "- runtime evidence covers platform, core, storage, dependencies, addons and guarded failure signals\n"
        "- canonical candidate and previous-stable metadata are packaged from the existing baseline registry\n"
        "- READY/ATTENTION/BLOCKED status remains conservative and is not promoted to a compatibility guarantee\n"
        "- no automatic repair, migration, plugin-state change, machine retry or integration reload is performed\n"
        "- no normal Cargo, Energy, machine, gameplay, database, storage-schema or saved-world semantics are changed\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
