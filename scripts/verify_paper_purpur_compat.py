#!/usr/bin/env python3
"""Static invariants for Paper/Purpur-first compatibility maintenance."""

from __future__ import annotations

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
    required_addons = (
        "wickidcow/SF_FastMachines",
        "Sefiraat/Networks",
        "wickidcow/SF_SlimeTinkerIE2",
        "wickidcow/SF_BetterChests",
    )
    gugu_advisory_addons = (
        "SlimefunGuguProject/FluffyMachines",
        "SlimefunGuguProject/FoxyMachines",
        "SlimefunGuguProject/Networks",
        "SlimefunGuguProject/SlimeTinker",
        "SlimefunGuguProject/FlowerPower",
        "SlimefunGuguProject/IDreamOfEasy",
        "SlimefunGuguProject/Gastronomicon",
        "SlimefunGuguProject/Bump",
        "SlimefunGuguProject/SlimeCustomizer",
        "SlimefunGuguProject/EMCTech",
    )
    for token in (*required_addons, *gugu_advisory_addons):
        if token not in workflow:
            failures.append(f"Addon compatibility matrix entry is missing: {token}")
    for token in (
        "continue-on-error: ${{ matrix.advisory }}",
        "max-parallel: 4",
        "GIT_TERMINAL_PROMPT: '0'",
        "addon-compatibility-${{ matrix.slug }}",
        "- name: Make Gradle wrapper executable",
        "run: chmod +x gradlew",
    ):
        if token not in workflow:
            failures.append(f"Addon compatibility workflow safety control is missing: {token}")

    if failures:
        print("Paper/Purpur compatibility verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    print("Paper/Purpur-first compatibility verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
