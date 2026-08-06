#!/usr/bin/env python3
"""Static invariants for Paper/Purpur-first compatibility maintenance."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

MINIMUM_GUGU_BASELINE = "ece7368e1d0b40bc95c63d2796117794fcaf190e"


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []
    def read(relative: str) -> str:
        path = root / relative
        if not path.is_file():
            failures.append(f"missing required file: {relative}")
            return ""
        return path.read_text(encoding="utf-8")
    marker = read(".gugu-upstream-base").strip()
    if len(marker) != 40 or any(character not in "0123456789abcdefABCDEF" for character in marker):
        failures.append(".gugu-upstream-base must contain one full 40-character commit SHA")
    elif (root / ".git").exists():
        # The marker advances after each accepted upstream sync. It must never
        # move behind the storage baseline that Legacy already integrated.
        for commit in (MINIMUM_GUGU_BASELINE, marker):
            result = subprocess.run(
                ["git", "cat-file", "-e", f"{commit}^{{commit}}"],
                cwd=root,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            if result.returncode != 0:
                break
        else:
            result = subprocess.run(
                ["git", "merge-base", "--is-ancestor", MINIMUM_GUGU_BASELINE, marker],
                cwd=root,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            if result.returncode != 0:
                failures.append(".gugu-upstream-base moved behind or away from the audited storage baseline")
    crafter = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/AutoCrafterListener.java")
    for token in (
        "isLimitedCrafting",
        "GameRules.LIMITED_CRAFTING",
        "catch (RuntimeException | LinkageError ignored)",
    ):
        if token not in crafter:
            failures.append(f"Auto-Crafter Paper/Purpur guard is missing: {token}")
    versions = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java")
    for token in (
        "sendVersionReport",
        "PlainTextComponentSerializer.plainText().serialize(report)",
        "catch (RuntimeException | LinkageError ignored)",
    ):
        if token not in versions:
            failures.append(f"/sf versions fallback is missing: {token}")
    profiler = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/profiler/SlimefunProfiler.java")
    if "if (isProfiling)" not in profiler or "mixed-cycle summary" not in profiler:
        failures.append("Profiler superseded-cycle guard is missing")
    if "if (isProfiling && queued.get() > 0)" in profiler:
        failures.append("Profiler still permits an empty report after queued is reset")

    workflow = read(".github/workflows/compatibility-ci.yml")
    baseline_start = workflow.find("  build-baseline-slimefun:")
    baseline_end = workflow.find("\n  paper-candidate-api:", baseline_start)
    if baseline_start < 0 or baseline_end < 0:
        failures.append("4.1.15 compatibility baseline job is missing or malformed")
    else:
        baseline_job = workflow[baseline_start:baseline_end]
        spotless = baseline_job.find("./gradlew spotlessApply --no-daemon")
        build = baseline_job.find("./gradlew clean build --no-daemon")
        if spotless < 0:
            failures.append("4.1.15 compatibility baseline must run Spotless before compiling")
        if build < 0:
            failures.append("4.1.15 compatibility baseline build command is missing")
        if spotless >= 0 and build >= 0 and spotless > build:
            failures.append("4.1.15 compatibility baseline runs Spotless after the build")
    comparator = read("scripts/compare_addon_slimefun_compatibility.py")
    legacy_builder = read("scripts/build_addon_against_local_slimefun.py")
    required_addons = {
        "wickidcow/SF_FastMachines",
        "wickidcow/SF_NetworksExp",
        "wickidcow/SF_SlimeTinkerIE2",
        "wickidcow/SF_BetterChests",
    }
    representative_addons = {
        "Sefiraat/Networks",
        "GuizhanCraft/InfinityExpansion2",
        "ProfElements/DynaTech",
        "Slimefun-Addon-Community/Supreme",
        "Yomicer/MagicExpansion",
        "SlimefunGuguProject/FluffyMachines",
        "GuizhanCraft/FastMachines",
        "Sefiraat/SlimeTinker",
    }
    try:
        matrix_payload = json.loads(read("compatibility/addon-compatibility-matrix.json"))
        matrix_entries = matrix_payload.get("addons", [])
        repositories = {entry.get("repository") for entry in matrix_entries if isinstance(entry, dict)}
        for repository in sorted(required_addons | representative_addons):
            if repository not in repositories:
                failures.append(f"Addon compatibility registry entry is missing: {repository}")
        for entry in matrix_entries:
            if entry.get("repository") in required_addons and entry.get("advisory") is not False:
                failures.append(f"Required addon target became advisory: {entry.get('repository')}")
            if entry.get("repository") in representative_addons and entry.get("advisory") is not True:
                failures.append(f"Independent representative target must remain advisory: {entry.get('repository')}")
    except (json.JSONDecodeError, TypeError) as error:
        failures.append(f"Addon compatibility registry is invalid: {error}")

    for token in (
        "prepare-addon-matrix:",
        "generate_addon_compatibility_matrix.py",
        "fromJSON(needs.prepare-addon-matrix.outputs.matrix)",
        "TARGET_VERSION: ${{ matrix.target_version }}",
        "Declared addon version:",
        "Expected addon version $TARGET_VERSION",
        "continue-on-error: ${{ matrix.advisory }}",
        "max-parallel: 4",
        "GIT_TERMINAL_PROMPT: '0'",
        "addon-compatibility-${{ matrix.slug }}",
        "- name: Make Gradle wrapper executable",
        "run: chmod +x gradlew",
        "Build 4.1.15 compatibility baseline",
        "ref: 493587431dc831d4b8bc38649af6e22df74a15b0",
        "name: slimefun-baseline-jar",
        "needs: [prepare-addon-matrix, build-baseline-slimefun, build-slimefun]",
        "Compare known-good baseline with candidate Legacy JAR",
        "compare_addon_slimefun_compatibility.py",
        "Publish classified compatibility summary",
        "Enforce classified compatibility result",
        "BASELINE_BUILD_FAILED",
        "LEGACY_COMPATIBILITY_FAILED",
        "result.json",
        "baseline.log",
        "candidate.log",
        "binary-linkage/result.json",
        "binary-linkage/summary.md",
    ):
        if token not in workflow:
            failures.append(f"Addon compatibility workflow safety control is missing: {token}")
    for token in (
        'BASELINE_BUILD_FAILED = "BASELINE_BUILD_FAILED"',
        'LEGACY_COMPATIBILITY_FAILED = "LEGACY_COMPATIBILITY_FAILED"',
        'INSTRUMENTATION_ERROR = "INSTRUMENTATION_ERROR"',
        'CORE_ARTIFACT_NAMES = {"slimefun", "slimefun4"}',
        "copy_project(source, baseline_project)",
        "copy_project(source, candidate_project)",
        "artifact == 'slimefun' || artifact == 'slimefun4'",
        "--no-build-cache",
        'report_dir / "result.json"',
        'report_dir / "summary.md"',
        "analyze_linkage",
        "write_linkage_report",
        "find_built_addon_jar",
    ):
        if token not in comparator:
            failures.append(f"Two-stage addon comparator invariant is missing: {token}")
    for unsafe in (
        "id.contains('slimefun')",
        '"slimefun" not in identity',
    ):
        if unsafe in comparator or unsafe in legacy_builder:
            failures.append(f"Addon dependency replacement is still over-broad: {unsafe}")
    for token in (
        'normalized_artifact in {"slimefun", "slimefun4"}',
        "coreArtifact = artifact == 'slimefun' || artifact == 'slimefun4'",
    ):
        if token not in legacy_builder:
            failures.append(f"Legacy addon helper exact dependency matching is missing: {token}")

    if failures:
        print("Paper/Purpur compatibility verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1
    print("Paper/Purpur-first compatibility verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
