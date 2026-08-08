#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing required file: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


guide = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java")
doctor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java")
runtime = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/EnchantmentMachineRuntime.java")
enchanter = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoEnchanter.java")
disenchanter = read("src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoDisenchanter.java")
changelog = read("EVERYTHING_THAT_CHANGED.md")
readme = read("README.md")
gradle = read("gradle.properties")

current_version = ""
for line in gradle.splitlines():
    if line.startswith("projectVersion="):
        current_version = line.split("=", 1)[1].strip()
        break

for marker in (
    "TOTAL_CALLS",
    "FAILED_CALLS",
    "RECURSIVE_CALLS",
    "SLOW_CALLS",
    "FALLBACKS_USED",
    "SUPPRESSED_WARNINGS",
    "suppressedSinceLast=",
    "Slimefun Guide runtime summary",
):
    require(marker in guide, f"guide diagnostics marker missing: {marker}")
require("RuntimeException | LinkageError | StackOverflowError" in guide, "guide failure boundary regressed")

require("Slimefun Legacy 4.1.18 Item Doctor failure isolation." in doctor, "Item Doctor release guard is missing")
require("Item doctor skipped a failing stack" in doctor, "per-stack Item Doctor continuation is missing")
require("catch (RuntimeException | LinkageError ex)" in doctor, "Item Doctor linkage isolation is missing")
require("usesLeft = limitedUseItem.getMaxUseCount();" in doctor, "limited-use fallback is missing")
require("describeStack(item)" in doctor, "safe Item Doctor failure context is missing")

for marker in (
    "processingTicks",
    "consumeOneEach",
    "Inputs were left untouched",
    "FAILURE_COOLDOWN_MILLIS",
):
    require(marker in runtime, f"machine runtime marker missing: {marker}")

require("Slimefun Legacy 4.1.18 machine runtime hardening." in enchanter, "Auto Enchanter release marker is missing")
require("AdvancedEnchantmentsIntegration" in enchanter, "Auto Enchanter custom-enchant integration regressed")
require("resultingCount" in enchanter, "Auto Enchanter final enchant-count validation is missing")
require("EnchantmentMachineRuntime.consumeOneEach" in enchanter, "Auto Enchanter transactional consumption is missing")
require("Math.max(1" not in enchanter or "processingTicks" in enchanter, "Auto Enchanter minimum timing is missing")
require("menu.replaceExistingItem(targetSlot, null)" not in enchanter, "Auto Enchanter still moves cancelled inputs")

require("Slimefun Legacy 4.1.18 machine runtime hardening." in disenchanter, "Auto Disenchanter release marker is missing")
require("AdvancedEnchantmentsIntegration" in disenchanter, "Auto Disenchanter custom-enchant integration regressed")
require("transferWasComplete" in disenchanter, "Auto Disenchanter duplication verification is missing")
require("EnchantmentMachineRuntime.consumeOneEach" in disenchanter, "Auto Disenchanter transactional consumption is missing")
require("menu.replaceExistingItem(itemSlot, null)" not in disenchanter, "Auto Disenchanter still moves cancelled inputs")

require("# Slimefun Legacy 4.1.18" in changelog, "4.1.18 changelog section is missing")
require(bool(current_version) and f"Slimefun Legacy {current_version} is tested primarily" in readme, "README current version was not updated")

for name, text in (
    ("guide", guide),
    ("doctor", doctor),
    ("runtime", runtime),
    ("enchanter", enchanter),
    ("disenchanter", disenchanter),
):
    require(text.count("{") == text.count("}"), f"unbalanced braces in {name}")

if errors:
    print("Slimefun Legacy 4.1.18 final verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("PASS: Slimefun Legacy 4.1.18 Guide & Runtime Stability invariants")
