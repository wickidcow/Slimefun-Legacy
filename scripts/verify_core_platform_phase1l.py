#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.30 Core Platform Phase 1L release-lifecycle invariants."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

CURRENT_VERSION = "4.1.30"
CURRENT_PHASE = "Core Platform Phase 1L"
PREVIOUS_STABLE_VERSION = "4.1.29"
PREVIOUS_STABLE_REF = "9794baffdd4a96f71fa18ae45ced8bab30982fb0"
LEGACY_FLOOR_VERSION = "4.1.15"


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
        version = project_version(root)
        require(version == CURRENT_VERSION, f"Phase 1L projectVersion must be {CURRENT_VERSION}, got {version or '<missing>'}", failures)

        support = load_json(root, "compatibility/support-contract.json")
        require(support.get("release") == CURRENT_VERSION, "Support contract release must match 4.1.30", failures)
        require(support.get("phase") == CURRENT_PHASE, "Support contract phase must be Core Platform Phase 1L", failures)

        policy = support.get("compatibility_policy", {})
        for key in (
            "rolling_previous_stable_baseline",
            "baseline_registry_single_source",
            "previous_stable_regressions_block_release",
            "api_and_addon_workflows_share_baseline_registry",
            "release_lifecycle_baseline_rollover",
            "previous_stable_is_last_released_candidate",
            "release_metadata_is_single_version_line",
            "phase1k_dependency_contract_release_gate",
            "phase1k_release_readiness_gate",
        ):
            require(policy.get(key) is True, f"Phase 1L policy must remain true: {key}", failures)

        for key in (
            "database_format_changed",
            "storage_schema_changed",
            "gameplay_behavior_changed",
            "third_party_plugin_dependency_emulation",
            "gugu_runtime_core_target",
            "phase1l_changes_normal_cargo_energy_machine_semantics",
            "phase1l_changes_storage_or_gameplay_semantics",
        ):
            require(policy.get(key) is False, f"Phase 1L policy must remain false: {key}", failures)

        java = support.get("java", {})
        require(java.get("build_toolchain") == 25, "Phase 1L build toolchain must remain Java 25", failures)
        require(java.get("supported_runtime") == 25, "Phase 1L runtime must remain Java 25", failures)
        require(java.get("bytecode_target") == 21, "Phase 1L bytecode target must remain Java 21", failures)

        baselines = load_json(root, "compatibility/release-baselines.json")
        candidate = baselines.get("candidate", {})
        previous = baselines.get("previous_stable", {})
        floor = baselines.get("legacy_floor", {})
        require(candidate.get("version") == CURRENT_VERSION, "Candidate baseline must be 4.1.30", failures)
        require(previous.get("version") == PREVIOUS_STABLE_VERSION, "Previous stable baseline must be 4.1.29", failures)
        require(previous.get("source", {}).get("mode") == "git-ref", "Previous stable baseline must use a pinned git ref", failures)
        require(previous.get("source", {}).get("ref") == PREVIOUS_STABLE_REF, "Previous stable 4.1.29 baseline must be pinned to the validated release commit", failures)
        require(previous.get("release_blocking") is True, "Previous stable 4.1.29 must be release blocking", failures)
        require(floor.get("version") == LEGACY_FLOOR_VERSION, "Historical compatibility floor must remain 4.1.15", failures)
        require(floor.get("release_blocking") is False, "Historical compatibility floor must remain advisory", failures)

        for relative in (
            "compatibility/addon-compatibility-matrix.json",
            "compatibility/cross-fork-api-matrix.json",
            "compatibility/core-api-registry.json",
        ):
            data = load_json(root, relative)
            require(data.get("release") == CURRENT_VERSION, f"{relative} release must be 4.1.30", failures)

        addon_matrix = load_json(root, "compatibility/addon-compatibility-matrix.json")
        required = [addon for addon in addon_matrix.get("addons", []) if addon.get("enabled") and not addon.get("advisory")]
        require(bool(required), "Phase 1L requires at least one release-blocking addon target", failures)
        for addon in required:
            require(addon.get("tier") == "required", f"Release-blocking addon must remain required: {addon.get('repository')}", failures)
            require("legacy" in addon.get("tested_core_variants", []), f"Required addon must test Legacy: {addon.get('repository')}", failures)

        require((root / "docs/CORE_PLATFORM_PHASE1L_PART1.md").is_file(), "Phase 1L Part 1 notes are missing", failures)
    except Exception as error:
        failures.append(f"Phase 1L verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1l.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text("Core Platform Phase 1L verification: FAIL\n" + "\n".join(f"- {failure}" for failure in failures) + "\n", encoding="utf-8")
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1L verification: PASS\n"
        "- 4.1.30 development metadata is aligned across the support contract and compatibility registries\n"
        "- validated 4.1.29 is now the release-blocking previous-stable baseline\n"
        "- the 4.1.15 historical compatibility floor remains advisory\n"
        "- required Legacy addon regressions continue to block release\n"
        "- Phase 1K dependency and release-hardening gates remain active\n"
        "- Java 25 runtime/toolchain and Java 21 bytecode targeting remain unchanged\n"
        "- no normal Cargo, Energy, machine, database, storage-schema or saved-world semantics are changed\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
