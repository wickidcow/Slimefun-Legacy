#!/usr/bin/env python3
"""Verify ThreadUtils avoids Paper-internal main-executor reflection."""

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
THREAD_UTILS = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ThreadUtils.java"

source = THREAD_UTILS.read_text(encoding="utf-8")

checks = {
    "Paper MCUtil internal class is not referenced": "io.papermc.paper.util.MCUtil" not in source,
    "Paper MAIN_EXECUTOR internal field is not referenced": "MAIN_EXECUTOR" not in source,
    "ThreadUtils does not reflect private fields": "getDeclaredField" not in source and "setAccessible" not in source,
    "Paper primary-thread fast path remains": "if (Bukkit.isPrimaryThread())" in source and "task.run();" in source,
    "Paper off-thread dispatch uses Slimefun scheduler": "Slimefun.runSync(task);" in source,
    "Folia global-region dispatch remains": "RuntimePlatformDetector.isRegionOwnedExecution()" in source
    and "Slimefun.getSchedulerService().run(task);" in source,
    "delayed dispatch remains scheduler-backed": "Slimefun.getSchedulerService().runLater(task, 1L)" in source,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("ThreadUtils dispatcher invariant failed: " + "; ".join(failed))

print("ThreadUtils scheduler dispatcher invariants verified.")
