#!/usr/bin/env python3
"""Verify shared MachineProcessor lifecycle and progress-bar invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Machine processor correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Machine processor correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Machine processor correctness failed: forbidden {label}: {needle}")


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Machine processor correctness failed: missing method {method_name}")

    start = match.end() - 1
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1 : index]

    raise SystemExit(f"Machine processor correctness failed: unterminated method {method_name}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineProcessor.java",
    )
    operation_source = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineOperation.java",
    )
    menu_utils = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/utils/ChestMenuUtils.java",
    )

    source_compact = compact(source)
    progress = compact(method_body(source, "updateProgressBar"))
    operation = compact(operation_source)
    menu_progress = compact(method_body(menu_utils, "updateProgressbar"))
    progress_text = compact(method_body(menu_utils, "getProgressBar"))
    durability = compact(method_body(menu_utils, "getDurability"))

    require(operation, "return getRemainingTicks() <= 0", "finished-operation contract")
    require(progress, "int remainingTicks = operation.getRemainingTicks()", "remaining-tick lookup")
    require(progress, "int totalTicks = operation.getTotalTicks()", "total-tick lookup")
    require(
        progress,
        "if (remainingTicks > 0 && totalTicks > 0)",
        "unfinished positive-duration progress guard",
    )
    require_absent(
        progress,
        "remainingTicks > 0 || totalTicks > 0",
        "finished-operation progress update",
    )
    require(progress, "ChestMenuUtils.updateProgressbar", "progress-bar update")

    # MachineProcessor exposes three endOperation overloads. Verify the concrete BlockPosition
    # implementation across the source instead of accidentally inspecting a delegating wrapper.
    require(source_compact, "T operation = machines.remove(pos)", "atomic operation removal before lifecycle callback")
    require(source_compact, "if (operation.isFinished())", "finished-operation event gate")
    require(
        source_compact,
        "new AsyncMachineOperationFinishEvent(pos, this, operation)",
        "finish event dispatch",
    )
    require(source_compact, "operation.onCancel(pos)", "premature-operation cancellation callback")

    # Progress helpers must remain safe when called outside MachineProcessor too. Clamp timing
    # inputs and use ratio math instead of integer division so long operations still animate.
    require(menu_progress, "inv.getViewers().isEmpty() || time <= 0", "invalid-duration/viewer guard")
    require(menu_progress, "int safeTimeLeft = Math.max(0, Math.min(timeLeft, time))", "remaining-time clamp")
    require(menu_progress, "getDurability(item, safeTimeLeft, time)", "bounded durability input")
    require(menu_progress, "getProgressBar(safeTimeLeft, time)", "bounded text progress input")
    require(progress_text, "int safeTotal = Math.max(1, total)", "positive text-progress total")
    require(progress_text, "int safeTime = Math.max(0, Math.min(time, safeTotal))", "text-progress remaining clamp")
    require(durability, "if (maxDurability <= 0 || max <= 0)", "non-damageable/invalid-duration durability guard")
    require(durability, "int safeTimeLeft = Math.max(0, Math.min(timeLeft, max))", "durability remaining clamp")
    require(
        durability,
        "Math.round((maxDurability * (double) safeTimeLeft) / max)",
        "precise durability ratio",
    )
    require_absent(durability, "getMaxDurability() / max", "integer-truncated durability ratio")

    print("Machine processor correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
