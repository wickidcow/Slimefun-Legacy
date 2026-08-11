#!/usr/bin/env python3
"""Verify Slimefun Legacy guide-runtime correctness invariants.

These checks intentionally preserve the stronger core-native behavior already present in
Slimefun Legacy while incorporating the useful lessons from JustEnoughGuide's
470c736fdee429b8d49789775716774eafb3d978 update:

* guide failures must be observable rather than silently swallowed;
* GuideHistory must exist before consumers can observe a PlayerProfile;
* core must not depend on addon-style reflective GuideHistory replacement;
* public guide entry points must stay behind the runtime guard.
"""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Guide runtime correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Guide runtime correctness failed: missing {label}: {needle}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Guide runtime correctness failed: found forbidden {label}: {needle}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    guard = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java",
    )
    require(guard, "RuntimeException | LinkageError | StackOverflowError", "guide failure boundary")
    require(guard, "Slimefun.logger().log(Level.SEVERE", "full guide exception logging")
    require(guard, "describeContext(", "guide failure context")
    require(guard, "addon=", "addon ownership diagnostics")
    require(guard, "activeChain=", "nested guide call diagnostics")

    guide = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/SlimefunGuide.java",
    )
    for operation in (
        "restore guide history",
        "open main menu page ",
        "open item group page ",
        "open guide search",
        "display item stack",
        "display Slimefun item ",
    ):
        require(guide, f'"{operation}', f"guarded guide operation {operation!r}")
    require(guide, "GuideRuntimeGuard.run(", "guide runtime guard routing")

    profile = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/player/PlayerProfile.java",
    )
    require(
        profile,
        "private final GuideHistory guideHistory = new GuideHistory(this);",
        "eager core-owned GuideHistory initialization",
    )
    require(profile, "public @Nonnull GuideHistory getGuideHistory()", "nonnull GuideHistory API")
    reject(profile, 'ReflectionUtil.setValue', "reflective PlayerProfile mutation")
    reject(profile, '"guideHistory"', "string-based GuideHistory field mutation")

    enhanced = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java",
    )
    require(enhanced, "GuideRuntimeGuard.run(", "enhanced guide page guard")
    require(enhanced, "catch (Exception | LinkageError exception)", "enhanced item-open failure boundary")
    require(enhanced, "item.error(", "enhanced item-open exception reporting")

    print("Guide runtime correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
