#!/usr/bin/env python3
"""Static invariants for Paper/Purpur-first compatibility maintenance."""

from __future__ import annotations

import sys
from pathlib import Path


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
    if marker != "ece7368e1d0b40bc95c63d2796117794fcaf190e":
        failures.append(".gugu-upstream-base does not contain the audited Gugu baseline")

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
    for token in ("wickidcow/FastMachines", "wickidcow/Networks", "wickidcow/SlimeTinker2", "wickidcow/BetterChests"):
        if token not in workflow:
            failures.append(f"Addon compatibility matrix entry is missing: {token}")

    if failures:
        print("Paper/Purpur compatibility verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    print("Paper/Purpur-first compatibility verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
