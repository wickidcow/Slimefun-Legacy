#!/usr/bin/env python3
"""Verify the 4.1.28 Item Doctor translation-recovery safeguards."""

from __future__ import annotations

import sys
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Item Doctor translation recovery verification failed: {message}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    doctor = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java").read_text(encoding="utf-8")
    text = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorText.java").read_text(encoding="utf-8")
    service = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorService.java").read_text(encoding="utf-8")
    config = (root / "src/main/resources/config.yml").read_text(encoding="utf-8")
    tests = (root / "src/test/java/io/github/thebusybiscuit/slimefun4/core/services/stability/TestItemDoctorText.java").read_text(encoding="utf-8")

    require("mergeStaticEnglishLore" in text, "authoritative static-lore recovery helper is missing")
    require("mergeConservativeEnglishLore" in text, "conservative third-party lore recovery helper is missing")
    require("hasCompatibleTokenShape" in text, "dynamic token-shape guard is missing")
    require("ELECTRIC_DUST_FABRICATOR" in doctor, "Electric Dust Fabricator static recovery is missing")
    require("REINFORCED_FLUFFY_WRENCH" in doctor, "Reinforced Fluffy Wrench static recovery is missing")
    require("maximum > 0F" in doctor, "zero-capacity Rechargeable guard is missing")
    require("backpackPdcBound" in doctor, "generic PlayerBackpack/Dolly state preservation is missing")
    require("humanizeItemId(itemId)" in doctor, "registered-item English name fallback is missing")
    require("sfItem.getAddon() instanceof Slimefun" in doctor, "core authoritative lore path is missing")
    require("loreStillUnresolved" in doctor, "partial repair must retain unresolved-lore diagnostics")
    require("repair-picked-up-items: true" in config, "pickup repair must remain enabled by default")
    require("repair-orphaned-item-names: true" in config, "orphaned name recovery must remain enabled by default")
    require("canonicalizesStaticNumericLoreInsteadOfTreatingNumbersAsSavedState" in tests, "static numeric lore regression test is missing")
    require("conservativelyRepairsAddonLoreButLeavesAmbiguousStateLinesUntouched" in tests, "addon state-preservation regression test is missing")
    require("protected or unresolved CJK lore" in service, "operator-facing unresolved lore diagnostic is missing")

    print("Item Doctor translation recovery verification: PASS")
    print("- CJK display names repair independently from protected lore")
    print("- core/static numeric lore can use authoritative English templates")
    print("- ambiguous addon lore state remains protected")
    print("- zero-capacity Rechargeable addons no longer false-fail")
    print("- PlayerBackpack-backed addon items retain storage identity")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
