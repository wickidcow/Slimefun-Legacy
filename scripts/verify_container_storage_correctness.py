#!/usr/bin/env python3
"""Verify AContainer loaded-storage energy and addon-hook invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Container storage correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Container storage correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Container storage correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Container storage correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source = compact(
        read(
            root,
            "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java",
        )
    )

    # Keep the legacy protected tick/takeCharge dispatch points intact for addons while carrying
    # the already-resolved ticker data through the current region thread only.
    require(source, "private final ThreadLocal<TickContext> tickContext = new ThreadLocal<>()", "thread-local tick context")
    require(source, "private record TickContext(Location location, SlimefunBlockData data)", "tick context payload")
    require(source, "TickContext previous = tickContext.get()", "nested tick-context preservation")
    require(source, "tickContext.set(new TickContext(b.getLocation(), data))", "loaded ticker-data capture")
    require(source, "try { AContainer.this.tick(b); } finally", "legacy virtual tick dispatch with cleanup")
    require(source, "tickContext.remove()", "thread-local cleanup")
    require(source, "tickContext.set(previous)", "nested tick-context restoration")
    require(source, "protected void tick(Block b)", "legacy protected tick hook")
    require(source, "if (takeCharge(b.getLocation()))", "legacy virtual takeCharge dispatch")
    require(source, "protected boolean takeCharge(@Nonnull Location l)", "legacy protected takeCharge hook")

    # The current block may use the ticker's loaded SlimefunBlockData, but arbitrary locations
    # must fall back to a fresh shared storage resolution rather than reusing data from the wrong block.
    require(source, "context != null && context.location().equals(l)", "tick-location identity guard")
    require(source, "return takeCharge(l, context.data())", "loaded ticker-data charge path")
    require(source, "ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(l)", "non-ticker storage fallback")
    require(source, "private boolean takeCharge(@Nonnull Location l, @Nonnull ASlimefunDataContainer data)", "shared storage helper")
    require(source, "data == null || data.isPendingRemove()", "missing/pending storage guard")
    require(source, "if (!data.isDataLoaded())", "unloaded storage guard")
    require(source, "StorageCacheUtils.requestLoad(data)", "storage load request")

    # Reads and writes for one energy transaction must share the same validated storage object.
    require(source, "long charge = getChargeLong(l, data)", "loaded-data energy read")
    require(source, "setCharge(l, charge - getEnergyConsumption(), data)", "loaded-data energy write")
    require_before(
        source,
        "long charge = getChargeLong(l, data)",
        "setCharge(l, charge - getEnergyConsumption(), data)",
        "energy read before write",
    )
    require_absent(source, "long charge = getChargeLong(l);", "duplicate location-based energy read")
    require_absent(source, "setCharge(l, (long) charge - getEnergyConsumption());", "duplicate location-based energy write")

    print("AContainer loaded-storage correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
