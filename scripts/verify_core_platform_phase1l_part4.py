#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.31 Core Platform Phase 1L Part 4 Paper runtime smoke coverage."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

CURRENT_VERSION = "4.1.31"


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


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
        require(project_version(root) == CURRENT_VERSION, "Part 4 requires projectVersion 4.1.31", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        primary = support.get("primary_platform", {})
        require(primary.get("release_line") == "26.2", "Primary Paper release line must be 26.2", failures)
        require(primary.get("minecraft") == "26.2", "Primary Minecraft version must be 26.2", failures)
        require(primary.get("paper_api") == "26.2.build.+", "Primary Paper API must be 26.2.build.+", failures)

        policy = support.get("compatibility_policy", {})
        for key in (
            "paper_26_2_runtime_smoke",
            "paper_runtime_smoke_uses_latest_stable_build",
            "paper_runtime_smoke_two_boot_lifecycle",
            "paper_runtime_smoke_runs_upgrade_diagnostics",
            "paper_runtime_smoke_requires_clean_restart_evidence",
        ):
            require(policy.get(key) is True, f"Phase 1L Part 4 policy must remain true: {key}", failures)
        for key in (
            "phase1l_part4_changes_normal_cargo_energy_machine_semantics",
            "phase1l_part4_changes_storage_or_gameplay_semantics",
        ):
            require(policy.get(key) is False, f"Phase 1L Part 4 policy must remain false: {key}", failures)

        versions = read(root, "gradle/libs.versions.toml")
        require('paperApi = "26.2.build.+"' in versions, "Production Paper API catalog target must be 26.2.build.+", failures)

        workflow = read(root, ".github/workflows/runtime-smoke.yml")
        for token in (
            "name: Paper 26.2 Runtime Smoke",
            'PAPER_MINECRAFT_VERSION: "26.2"',
            "distribution: temurin",
            "java-version: '25'",
            "python3 scripts/verify_legacy.py .",
            'bash scripts/paper_runtime_smoke.sh "${{ steps.candidate.outputs.jar }}"',
            "paper-26.2-runtime-smoke",
        ):
            require(token in workflow, f"Runtime-smoke workflow invariant missing: {token}", failures)

        harness = read(root, "scripts/paper_runtime_smoke.sh")
        for token in (
            'MC_VERSION="${PAPER_MINECRAFT_VERSION:-26.2}"',
            "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds",
            'select(.channel == "STABLE")',
            "sf doctor upgrade",
            'run_cycle "first" false',
            'run_cycle "second" true',
            "Slimefun Upgrade Readiness",
            "Overall status:",
            "BLOCKED",
            "previous shutdown",
            "Stopping server",
            "Slimefun Legacy Paper runtime smoke: PASS",
        ):
            require(token in harness, f"Paper smoke harness invariant missing: {token}", failures)

        for forbidden in (
            "force-upgrade",
            "--forceUpgrade",
            "git push",
            "gh release",
            "disablePlugin(",
            "enablePlugin(",
        ):
            require(forbidden not in harness, f"Paper smoke harness crossed safety boundary: {forbidden}", failures)

        verifier = read(root, "scripts/verify_legacy.py")
        part3_position = verifier.find('"verify_core_platform_phase1l_part3.py"')
        part4_position = verifier.find('"verify_core_platform_phase1l_part4.py"')
        require(part3_position >= 0, "Full Legacy verifier must retain Phase 1L Part 3", failures)
        require(part4_position >= 0, "Full Legacy verifier must run Phase 1L Part 4", failures)
        if part3_position >= 0 and part4_position >= 0:
            require(part3_position < part4_position, "Phase 1L Part 4 verifier must run after Part 3", failures)

        require(
            (root / "docs/CORE_PLATFORM_PHASE1L_PART4_PAPER_RUNTIME_SMOKE.md").is_file(),
            "Phase 1L Part 4 documentation is missing",
            failures,
        )
    except Exception as error:
        failures.append(f"Phase 1L Part 4 verifier failed to inspect repository: {error}")

    report = root / "build/reports/phase1l-paper-runtime-smoke.txt"
    report.parent.mkdir(parents=True, exist_ok=True)

    if failures:
        report.write_text(
            "Core Platform Phase 1L Part 4 Paper runtime smoke verification: FAIL\n"
            + "\n".join(f"- {failure}" for failure in failures)
            + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1L Part 4 Paper runtime smoke verification: PASS\n"
        "- production source compiles against Paper API 26.2.build.+\n"
        "- the support contract identifies Minecraft/Paper 26.2 consistently\n"
        "- stable Paper 26.2 is selected through PaperMC's downloads service\n"
        "- the candidate JAR is boot-tested with Java 25\n"
        "- /sf doctor upgrade is executed during the runtime smoke\n"
        "- two boot cycles exercise enable, clean shutdown and restart persistence\n"
        "- BLOCKED upgrade diagnostics fail the smoke test\n"
        "- no production data, release publishing or force-upgrade path is used\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
