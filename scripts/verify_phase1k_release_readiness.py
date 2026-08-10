#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.29 Phase 1K release-readiness invariants."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


CURRENT_VERSION = "4.1.29"
CURRENT_PHASE = "Core Platform Phase 1K"


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
        require(version == CURRENT_VERSION, f"projectVersion must be {CURRENT_VERSION}, got {version or '<missing>'}", failures)

        support = load_json(root, "compatibility/support-contract.json")
        require(support.get("release") == CURRENT_VERSION, "Support contract release does not match 4.1.29", failures)
        require(support.get("phase") == CURRENT_PHASE, "Support contract phase must remain Core Platform Phase 1K", failures)

        java = support.get("java", {})
        require(java.get("build_toolchain") == 25, "Release build toolchain must remain Java 25", failures)
        require(java.get("supported_runtime") == 25, "Supported runtime must remain Java 25", failures)
        require(java.get("bytecode_target") == 21, "Release bytecode target must remain Java 21", failures)

        policy = support.get("compatibility_policy", {})
        for key in (
            "public_api_removals_require_allowlist",
            "source_and_binary_addon_matrix",
            "previous_stable_regressions_block_release",
            "api_and_addon_workflows_share_baseline_registry",
            "normal_slimefun_core_hash_guard",
            "phase1k_dependency_contract_release_gate",
            "phase1k_release_readiness_gate",
            "release_candidate_requires_full_legacy_verifier",
            "release_workflow_badges_reference_existing_workflows",
        ):
            require(policy.get(key) is True, f"Release readiness policy must remain true: {key}", failures)

        for key in (
            "database_format_changed",
            "storage_schema_changed",
            "gameplay_behavior_changed",
            "third_party_plugin_dependency_emulation",
            "gugu_runtime_core_target",
            "phase1k_changes_normal_cargo_energy_machine_semantics",
            "phase1k_part4_changes_normal_cargo_energy_machine_semantics",
            "phase1k_part4_changes_storage_or_gameplay_semantics",
        ):
            require(policy.get(key) is False, f"Release readiness policy must remain false: {key}", failures)

        baselines = load_json(root, "compatibility/release-baselines.json")
        candidate = baselines.get("candidate", {})
        previous = baselines.get("previous_stable", {})
        floor = baselines.get("legacy_floor", {})
        require(candidate.get("version") == CURRENT_VERSION, "Release baseline candidate must be 4.1.29", failures)
        require(previous.get("version") == "4.1.21", "Previous stable baseline must remain 4.1.21 for 4.1.29", failures)
        require(previous.get("release_blocking") is True, "Previous stable baseline must remain release blocking", failures)
        require(floor.get("version") == "4.1.15", "Historical compatibility floor must remain 4.1.15", failures)
        require(floor.get("release_blocking") is False, "Historical compatibility floor must remain advisory", failures)

        for relative in (
            "compatibility/addon-compatibility-matrix.json",
            "compatibility/cross-fork-api-matrix.json",
            "compatibility/core-api-registry.json",
        ):
            data = load_json(root, relative)
            require(data.get("release") == CURRENT_VERSION, f"{relative} release must match 4.1.29", failures)

        readme = read(root, "README.md")
        require(
            "Current development release: **4.1.29 — Core Platform Phase 1K (Dependency & Addon Boundary Hardening)**" in readme,
            "README current development release is not 4.1.29 Phase 1K",
            failures,
        )
        require(
            "Slimefun Legacy 4.1.29 is tested primarily against **Paper 26.2 / Minecraft 1.21.11 on Java 25**" in readme,
            "README primary support line is not the 4.1.29 Paper 26.2 / Java 25 contract",
            failures,
        )
        require("[Release History](EVERYTHING_THAT_CHANGED.md)" in readme, "README must link consolidated release history", failures)
        require("actions/workflows/build-ci.yml" in readme, "README build badge must point at build-ci.yml", failures)
        require("actions/workflows/compatibility-ci.yml" in readme, "README compatibility badge must point at compatibility-ci.yml", failures)

        build_workflow = read(root, ".github/workflows/build-ci.yml")
        for token in (
            "name: Build English Slimefun",
            "branches: [ master, main ]",
            "pull_request:",
            "workflow_dispatch:",
            "java-version: '25'",
            "python3 scripts/verify_legacy.py .",
            "./gradlew spotlessApply --no-daemon",
            "./gradlew clean build --no-daemon",
            "scripts/check_bytecode_target.py \"$JAR\" --expected-java 21",
            "actions/upload-artifact@v7",
            "name: Slimefun-English-Albion",
        ):
            require(token in build_workflow, f"Primary build workflow invariant missing: {token}", failures)
        require(not (root / ".github/workflows/build.yml").exists(), "Legacy duplicate build.yml workflow path must remain removed", failures)

        compatibility_workflow = read(root, ".github/workflows/compatibility-ci.yml")
        for token in (
            "name: Slimefun Compatibility",
            "workflow_dispatch:",
            "schedule:",
            "python3 scripts/verify_legacy.py .",
            "Build Slimefun Legacy candidate",
            "Build previous stable",
            "Prepare addon compatibility matrix",
            "Prepare cross-fork API probes",
            "compare_addon_slimefun_compatibility.py",
        ):
            require(token in compatibility_workflow, f"Compatibility workflow invariant missing: {token}", failures)

        legacy_verifier = read(root, "scripts/verify_legacy.py")
        dependency_position = legacy_verifier.find('"verify_phase1k_dependency_contract.py"')
        release_position = legacy_verifier.find('"verify_phase1k_release_readiness.py"')
        require(dependency_position >= 0, "Full Legacy verifier must run the Phase 1K dependency contract", failures)
        require(release_position >= 0, "Full Legacy verifier must run the Phase 1K release-readiness gate", failures)
        if dependency_position >= 0 and release_position >= 0:
            require(
                dependency_position < release_position,
                "Dependency contract must run before the final release-readiness gate",
                failures,
            )

        require(
            (root / "docs/CORE_PLATFORM_PHASE1K_PART3.md").is_file(),
            "Phase 1K Part 3 dependency-audit notes must remain under docs/",
            failures,
        )
        require(
            not (root / "CORE_PLATFORM_PHASE1K_PART3.md").exists(),
            "Phase 1K Part 3 notes must not return to the repository root",
            failures,
        )
    except Exception as error:
        failures.append(f"Release-readiness verifier failed to inspect repository: {error}")

    report = root / "build/reports/phase1k-release-readiness.txt"
    report.parent.mkdir(parents=True, exist_ok=True)

    if failures:
        report.write_text(
            "Phase 1K Part 4 release readiness: FAIL\n" + "\n".join(f"- {failure}" for failure in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Phase 1K Part 4 release readiness: PASS\n"
        "- 4.1.29 version, support contract and compatibility matrices are aligned\n"
        "- Java 25 runtime/toolchain and Java 21 bytecode release contract is intact\n"
        "- previous-stable and historical-floor compatibility baselines are pinned correctly\n"
        "- dependency, API and addon regression gates remain release blocking where intended\n"
        "- README workflow badges point at existing primary and compatibility workflows\n"
        "- the primary workflow verifies invariants, formats, builds, checks bytecode and uploads the JAR\n"
        "- gameplay, Cargo/Energy, database, storage-schema and saved-world semantics are not changed by Part 4\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
