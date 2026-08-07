#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1D lifecycle invariants."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path


FULL_GIT_SHA_RE = re.compile(r"^[0-9a-fA-F]{40}$")


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def version_tuple(value: object) -> tuple[int, int, int] | None:
    if not isinstance(value, str):
        return None
    parts = value.strip().split(".")
    if len(parts) != 3 or any(not part.isdigit() for part in parts):
        return None
    return tuple(int(part) for part in parts)


def project_version(root: Path) -> str:
    for line in read(root, "gradle.properties").splitlines():
        if line.startswith("projectVersion="):
            return line.split("=", 1)[1].strip()
    raise ValueError("projectVersion is missing")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "CORE_PLATFORM_PHASE1D.md",
        "SLIMEFUN_LEGACY_4.1.22.md",
        "compatibility/release-baselines.json",
        "compatibility/addon-compatibility-matrix.json",
        "compatibility/support-contract.json",
        "scripts/read_release_baselines.py",
        ".github/workflows/compatibility-ci.yml",
        ".github/workflows/api-compatibility.yml",
    )
    for relative in required_files:
        require((root / relative).is_file(), f"Missing Phase 1D file: {relative}", failures)

    try:
        current = project_version(root)
        current_tuple = version_tuple(current)
        require(current_tuple is not None and current_tuple >= (4, 1, 22), "Phase 1D requires Legacy 4.1.22 or newer", failures)

        baselines = json.loads(read(root, "compatibility/release-baselines.json"))
        candidate = baselines.get("candidate", {})
        previous = baselines.get("previous_stable", {})
        floor = baselines.get("legacy_floor", {})
        policy = baselines.get("policy", {})
        require(baselines.get("schema") == 1, "Release baseline registry schema must be 1", failures)
        require(candidate.get("version") == current, "Baseline candidate must match projectVersion", failures)
        require(previous.get("version") == "4.1.21", "Phase 1D previous stable baseline must be 4.1.21", failures)
        require(previous.get("release_blocking") is True, "Previous stable baseline must block candidate regressions", failures)
        require(floor.get("version") == "4.1.15", "Legacy compatibility floor must remain 4.1.15", failures)
        require(floor.get("release_blocking") is False, "Legacy compatibility floor must remain advisory", failures)
        require(version_tuple(floor.get("version")) < version_tuple(previous.get("version")) < current_tuple, "Baseline versions must be ordered floor < previous stable < candidate", failures)
        for label, entry in (("previous stable", previous), ("legacy floor", floor)):
            source = entry.get("source", {})
            require(source.get("mode") == "git-ref", f"{label} source must use a pinned git ref", failures)
            ref = source.get("ref")
            require(bool(ref), f"{label} source ref is missing", failures)
            require(
                isinstance(ref, str) and FULL_GIT_SHA_RE.fullmatch(ref) is not None,
                f"{label} source must use a full 40-character Git commit SHA",
                failures,
            )
        for key in (
            "single_source_for_ci_baselines",
            "previous_stable_advances_each_release",
            "legacy_floor_is_advisory",
            "required_addon_candidate_regressions_block_release",
            "historical_floor_regressions_do_not_block_release",
            "baseline_source_builds_are_pinned",
        ):
            require(policy.get(key) is True, f"Baseline lifecycle policy is missing: {key}", failures)

        matrix = json.loads(read(root, "compatibility/addon-compatibility-matrix.json"))
        require(matrix.get("release") == current, "Addon matrix release must match projectVersion", failures)
        require(matrix.get("baseline_registry") == "compatibility/release-baselines.json", "Addon matrix must reference the shared baseline registry", failures)
        require("baseline" not in matrix, "Addon matrix must not duplicate an embedded baseline definition", failures)
        matrix_policy = matrix.get("policy", {})
        require(matrix_policy.get("previous_stable_regressions_block_release") is True, "Addon matrix must block previous-stable regressions", failures)
        require(matrix_policy.get("legacy_floor_comparison_is_advisory") is True, "Legacy floor addon comparison must remain advisory", failures)
        addons = [entry for entry in matrix.get("addons", []) if isinstance(entry, dict) and entry.get("enabled", True)]
        require(len(addons) >= 19, "Phase 1D addon matrix must retain at least 19 enabled targets", failures)
        extended = {"FoxyMachines", "FlowerPower", "IDreamOfEasy", "Gastronomicon", "Bump", "SlimeCustomizer", "EMCTech"}
        by_name = {entry.get("name"): entry for entry in addons}
        require(extended <= set(by_name), "Expanded historical addon probes are incomplete", failures)
        require(all(by_name[name].get("advisory") is True for name in extended if name in by_name), "Expanded historical addon probes must remain advisory", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        require(support.get("release") == current, "Support contract release must match projectVersion", failures)
        require(support.get("phase") == "Core Platform Phase 1D", "Support contract must identify Core Platform Phase 1D", failures)
        support_policy = support.get("compatibility_policy", {})
        for key in (
            "rolling_previous_stable_baseline",
            "historical_legacy_floor_baseline",
            "baseline_registry_single_source",
            "previous_stable_regressions_block_release",
            "legacy_floor_failures_are_advisory",
            "api_and_addon_workflows_share_baseline_registry",
            "forward_compatible_phase_verifiers",
        ):
            require(support_policy.get(key) is True, f"Support contract Phase 1D policy is missing: {key}", failures)

        workflow = read(root, ".github/workflows/compatibility-ci.yml")
        for token in (
            "prepare-release-baselines:",
            "read_release_baselines.py",
            "needs.prepare-release-baselines.outputs.previous_ref",
            "needs.prepare-release-baselines.outputs.floor_ref",
            "build-legacy-floor-slimefun:",
            "legacy-floor-addons:",
            "previous stable $PREVIOUS_VERSION",
            "This probe is advisory and never blocks release.",
        ):
            require(token in workflow, f"Compatibility workflow lifecycle invariant is missing: {token}", failures)
        require("Build 4.1.15 compatibility baseline" not in workflow, "Compatibility workflow still names 4.1.15 as the active baseline", failures)
        require("493587431dc831d4b8bc38649af6e22df74a15b0" not in workflow, "Legacy floor commit must live only in the baseline registry", failures)

        api_workflow = read(root, ".github/workflows/api-compatibility.yml")
        for token in (
            "read_release_baselines.py",
            "steps.baseline.outputs.previous_ref",
            "build/api-baseline-source",
            "check_api_compatibility.py",
        ):
            require(token in api_workflow, f"API workflow shared-baseline invariant is missing: {token}", failures)
        require("gh release download" not in api_workflow, "API workflow must not discover an arbitrary latest GitHub release", failures)
        require("API_BASELINE_TAG" not in api_workflow, "API workflow must use the shared baseline registry", failures)

        for script_name in (
            "verify_core_platform_phase1a.py",
            "verify_core_platform_phase1b.py",
            "verify_core_platform_phase1c.py",
        ):
            source = read(root, f"scripts/{script_name}")
            require("release_at_least" in source, f"{script_name} must accept later Legacy releases", failures)

        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "github-output.txt"
            result = subprocess.run(
                [sys.executable, str(root / "scripts/read_release_baselines.py"), str(root / "compatibility/release-baselines.json"), "--github-output", str(output)],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )
            require(result.returncode == 0, f"Release baseline reader failed: {result.stderr.strip()}", failures)
            if output.is_file():
                values = dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines() if "=" in line)
                require(values.get("candidate_version") == current, "Baseline reader candidate output is incorrect", failures)
                require(values.get("previous_version") == "4.1.21", "Baseline reader previous stable output is incorrect", failures)
                require(values.get("floor_version") == "4.1.15", "Baseline reader legacy floor output is incorrect", failures)
            else:
                failures.append("Release baseline reader did not create GitHub output")

        readme = read(root, "README.md")
        changelog = read(root, "CHANGELOG.md")
        require("[Release Notes](SLIMEFUN_LEGACY_4.1.22.md)" in readme, "README release notes link is not current", failures)
        require("Slimefun Legacy 4.1.22 is tested primarily against" in readme, "README compatibility release text is stale", failures)
        require(changelog.startswith("# Slimefun Legacy 4.1.22 — Core Platform Phase 1D"), "Changelog must start with Phase 1D", failures)
    except (FileNotFoundError, json.JSONDecodeError, TypeError, ValueError) as error:
        failures.append(f"Phase 1D verifier could not inspect repository state: {error}")

    report = root / "build/reports/core-platform-phase1d.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text("Core Platform Phase 1D verification: FAIL\n" + "\n".join(f"- {failure}" for failure in failures) + "\n", encoding="utf-8")
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1D verification: PASS\n"
        "- rolling previous-stable baseline registry validated\n"
        "- 4.1.15 historical floor remains advisory\n"
        "- addon and public API workflows share the registry\n"
        "- expanded addon matrix and forward-compatible phase verifiers validated\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
