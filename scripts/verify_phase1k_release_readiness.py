#!/usr/bin/env python3
"""Verify the retained Phase 1K release-hardening contract after 4.1.29."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HISTORICAL_RELEASED_VERSION = "4.1.29"
FULL_GIT_SHA_RE = re.compile(r"^[0-9a-fA-F]{40}$")


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


def version_tuple(version: str) -> tuple[int, int, int]:
    return tuple(map(int, version.split(".")))


# These reviewed commands must execute in this order in the primary Build step.
# This is deliberately a narrow contract for our workflow, not a shell/YAML parser.
BUILD_COMMANDS = (
    "set -euo pipefail",
    "./gradlew clean --no-daemon",
    "mkdir -p build/reports",
    "./gradlew build -PslimefunDeprecationReport=true --console=plain --no-daemon "
    "2>&1 | tee build/reports/deprecation-compile.log",
    "python3 scripts/summarize_deprecations.py build/reports/deprecation-compile.log "
    "--fail-on-warnings --require-successful-build",
)


def verify_primary_build_commands(workflow: str) -> list[str]:
    step = re.search(r"(?ms)^      - name: Build[ \t]*\n(.*?)(?=^      - |\Z)", workflow)
    if step is None:
        return ["Primary Build step is missing"]
    run = re.search(r"(?ms)^        run: \|[ \t]*\n(.*)", step.group(1))
    if run is None:
        return ["Primary Build step must retain its reviewed shell block"]
    failures = []
    if re.search(r"(?m)^        (?:continue-on-error|if):", step.group(1)):
        failures.append("Primary Build step must not suppress failures or conditionally skip validation")
    commands = [line.strip() for line in run.group(1).splitlines()
                if line.strip() and not line.lstrip().startswith("#")]
    positions = []
    for command in BUILD_COMMANDS:
        if commands.count(command) != 1:
            failures.append(f"Primary Build step must execute exactly once: {command}")
        else:
            positions.append(commands.index(command))
    if len(positions) == len(BUILD_COMMANDS) and positions != sorted(positions):
        failures.append("Primary Build must clean before opening the compiler log and validate evidence after building")
    for command in commands:
        if command.startswith("./gradlew ") and re.search(r"\bclean\b", command) and command != BUILD_COMMANDS[1]:
            failures.append("Primary Build must not combine clean with compiler-log capture")
    return failures


def self_test_primary_build_commands() -> None:
    def workflow(commands: list[str], settings: str = "") -> str:
        return ("      - name: Build\n" + settings + "        run: |\n"
                + "".join(f"          {command}\n" for command in commands)
                + "      - name: Next step\n        run: echo done\n")

    valid = list(BUILD_COMMANDS)
    if verify_primary_build_commands(workflow(valid)):
        raise AssertionError("Build-evidence guard rejects the reviewed safe sequence")
    invalid = ["", workflow(valid, "        continue-on-error: true\n"),
               workflow(valid, "        if: false\n")]
    for index in range(len(valid)):
        invalid.append(workflow(valid[:index] + valid[index + 1:]))
        invalid.append(workflow(valid[:index] + ["# " + valid[index]] + valid[index + 1:]))
        invalid.append(workflow(valid[:index] + [valid[index] + " || true"] + valid[index + 1:]))
        invalid.append(workflow(valid[:index] + [valid[index]] + valid[index:]))
    for index in range(len(valid) - 1):
        swapped = valid.copy()
        swapped[index], swapped[index + 1] = swapped[index + 1], swapped[index]
        invalid.append(workflow(swapped))
    invalid.append(workflow(valid + ["./gradlew clean build --no-daemon | tee build/reports/deprecation-compile.log"]))
    invalid.append(workflow([]) + "".join(f"# {command}\n" for command in valid))
    for case, candidate in enumerate(invalid, 1):
        if not verify_primary_build_commands(candidate):
            raise AssertionError(f"Build-evidence guard accepted invalid sequence {case}")
    print(f"Primary build-evidence guard self-test: PASS ({len(invalid) + 1} cases)")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    try:
        self_test_primary_build_commands()
        version = project_version(root)
        require(bool(version), "projectVersion is missing", failures)
        if version:
            require(version_tuple(version) >= (4, 1, 29), "Phase 1K release hardening requires 4.1.29 or newer", failures)

        support = load_json(root, "compatibility/support-contract.json")
        java = support.get("java", {})
        require(java.get("build_toolchain") == 25, "Build toolchain must remain Java 25", failures)
        require(java.get("supported_runtime") == 25, "Supported runtime must remain Java 25", failures)
        require(java.get("bytecode_target") == 21, "Bytecode target must remain Java 21", failures)

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
            require(policy.get(key) is True, f"Retained release-hardening policy must remain true: {key}", failures)

        for key in (
            "database_format_changed",
            "storage_schema_changed",
            "third_party_plugin_dependency_emulation",
            "gugu_runtime_core_target",
            "phase1k_changes_normal_cargo_energy_machine_semantics",
            "phase1k_part4_changes_normal_cargo_energy_machine_semantics",
            "phase1k_part4_changes_storage_or_gameplay_semantics",
        ):
            require(policy.get(key) is False, f"Retained release-hardening policy must remain false: {key}", failures)

        require(
            type(policy.get("gameplay_behavior_changed")) is bool,
            "Active release must explicitly declare whether gameplay behavior changed",
            failures,
        )

        baselines = load_json(root, "compatibility/release-baselines.json")
        candidate = baselines.get("candidate", {})
        previous = baselines.get("previous_stable", {})
        floor = baselines.get("legacy_floor", {})
        require(candidate.get("version") == version, "Baseline candidate must match projectVersion", failures)
        if version == HISTORICAL_RELEASED_VERSION:
            require(previous.get("version") == "4.1.21", "4.1.29 must compare against 4.1.21", failures)
        elif version:
            previous_version = previous.get("version")
            try:
                previous_is_older = version_tuple(str(previous_version)) < version_tuple(version)
            except (TypeError, ValueError):
                previous_is_older = False
            require(previous_is_older, "Later releases must compare against an older previous-stable version", failures)
            require(
                FULL_GIT_SHA_RE.fullmatch(str(previous.get("source", {}).get("ref", ""))) is not None,
                "Later releases must pin previous stable to a full Git commit SHA",
                failures,
            )
        require(previous.get("release_blocking") is True, "Previous stable baseline must remain release blocking", failures)
        require(floor.get("version") == "4.1.15", "Historical compatibility floor must remain 4.1.15", failures)
        require(floor.get("release_blocking") is False, "Historical compatibility floor must remain advisory", failures)

        for relative in (
            "compatibility/addon-compatibility-matrix.json",
            "compatibility/cross-fork-api-matrix.json",
            "compatibility/core-api-registry.json",
        ):
            data = load_json(root, relative)
            require(data.get("release") == version, f"{relative} release must match projectVersion", failures)

        build_workflow = read(root, ".github/workflows/build-ci.yml")
        for token in (
            "python3 scripts/verify_legacy.py .",
            "--expected-java 21",
            "OUTPUT_NAME=Slimefun-Legacy${VERSION}.jar",
            "dist/${OUTPUT_NAME}",
        ):
            require(token in build_workflow, f"Primary build workflow invariant missing: {token}", failures)

        failures.extend(verify_primary_build_commands(build_workflow))

        compatibility_workflow = read(root, ".github/workflows/compatibility-ci.yml")
        for token in (
            "Build Slimefun Legacy candidate",
            "Build previous stable",
            "Prepare addon compatibility matrix",
            "compare_addon_slimefun_compatibility.py",
        ):
            require(token in compatibility_workflow, f"Compatibility workflow invariant missing: {token}", failures)

        require((root / "docs/CORE_PLATFORM_PHASE1K_PART3.md").is_file(), "Phase 1K dependency-audit notes must remain under docs/", failures)
        require((root / "docs/CORE_PLATFORM_PHASE1K_PART4_RELEASE_HARDENING.md").is_file(), "Phase 1K release-hardening notes must remain under docs/", failures)
    except Exception as error:
        failures.append(f"Release-hardening verifier failed to inspect repository: {error}")

    report = root / "build/reports/phase1k-release-readiness.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text("Retained Phase 1K release hardening: FAIL\n" + "\n".join(f"- {failure}" for failure in failures) + "\n", encoding="utf-8")
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    gameplay_changed = support.get("compatibility_policy", {}).get("gameplay_behavior_changed")
    report.write_text(
        "Retained Phase 1K release hardening: PASS\n"
        "- the historical 4.1.29 dependency and release gates remain enforced\n"
        "- Java 25 runtime/toolchain and Java 21 bytecode contract remains intact\n"
        "- later development uses a pinned, rolling previous-stable release baseline\n"
        "- compatibility matrices remain aligned with the active candidate\n"
        "- Cargo/Energy compatibility boundaries, database, storage-schema and saved-world formats remain protected\n"
        f"- gameplay behavior changed is explicitly declared as {str(gameplay_changed).lower()} for the active release\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test_primary_build_commands()
    else:
        raise SystemExit(main())
