#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
GUIDE = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/SlimefunGuide.java"
GUARD = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java"

failures: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


require(GUIDE.is_file(), f"missing replacement file: {GUIDE}")
require(GUARD.is_file(), f"missing new file: {GUARD}")

if GUIDE.is_file() and GUARD.is_file():
    guide = GUIDE.read_text(encoding="utf-8")
    guard = GUARD.read_text(encoding="utf-8")

    require("GuideRuntimeGuard.run(" in guide, "SlimefunGuide does not route through the runtime guard")
    require(guide.count("GuideRuntimeGuard.run(") >= 6, "not all public guide entry points are guarded")
    require("stack.contains(call)" in guard, "recursive guide-call detection is missing")
    require("MAX_NESTED_GUIDE_CALLS" in guard, "guide depth limit is missing")
    require("StackOverflowError" in guard, "stack-overflow recovery is missing")
    require("LinkageError" in guard, "addon linkage-error recovery is missing")
    require("SLOW_GUIDE_CALL_NANOS" in guard, "slow guide-call diagnostics are missing")
    require("WARNING_COOLDOWN_MILLIS" in guard, "diagnostic rate limiting is missing")
    require("player.closeInventory()" in guard, "broken guide inventory cleanup is missing")
    require("group=" in guard and "addon=" in guard, "group/addon ownership diagnostics are missing")
    require("catch (Throwable" not in guard, "runtime guard must not swallow every JVM error")
    require("OutOfMemoryError" not in guard, "runtime guard must not catch fatal memory errors")

    for path, text in ((GUIDE, guide), (GUARD, guard)):
        require(text.count("{") == text.count("}"), f"unbalanced braces: {path}")
        require("\t" not in text, f"tab characters found: {path}")

if failures:
    print("Slimefun Legacy 4.1.18 guide runtime stability verification failed:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("Slimefun Legacy 4.1.18 guide runtime stability Phase 1A verification passed.")
