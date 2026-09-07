#!/usr/bin/env python3
"""Verify Slimefun Doctor legacy-id migration diagnostics remain safe and wired."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def reject(condition: bool, message: str) -> None:
    if condition:
        ERRORS.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing required file: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


registry = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/SlimefunRegistry.java")
router = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorRouterCommand.java")
subcommands = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/SlimefunSubCommands.java")
tabs = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java")
test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/TestSlimefunRegistryLegacyItemIds.java")

require("ConcurrentHashMap<>();" in registry, "legacy-id registry must remain concurrency-safe")
require("registerLegacySlimefunItemId" in registry, "legacy-id registration API is missing")
require("getLegacySlimefunItemIds" in registry, "legacy-id read-only mapping view is missing")
require("Collections.unmodifiableMap(legacySlimefunItemIds)" in registry, "legacy-id mapping view must remain immutable")
require("putIfAbsent(legacyId, currentId)" in registry, "legacy-id registration must remain collision-aware and idempotent")
require("!legacyId.equals(currentId)" in registry, "legacy-id self-map rejection is missing")

require('super(plugin, cmd, "doctor", true);' in router, "Doctor migration router must retain the doctor command name")
require('equalsIgnoreCase("migrations")' in router, "Doctor migrations command route is missing")
require('case "status" -> sendMigrationStatus(sender);' in router, "Doctor migration status command is missing")
require('case "list" -> sendMigrationList(sender, parsePage(args));' in router, "Doctor migration list command is missing")
require('case "unknown", "unknowns" -> sendUnknownIds(sender);' in router, "Doctor migration unknown-id correlation is missing")
require("getUnknownIdSamples()" in router, "Doctor migration correlation must use Item Doctor unknown-ID samples")
require("getLegacySlimefunItemIds()" in router, "Doctor migration command must use addon-declared mappings")
require("Generic migration/repair remains disabled" in router, "Doctor migration read-only safety notice is missing")
reject("registerLegacySlimefunItemId(" in router, "Doctor diagnostic command must not register or rewrite migration mappings")
reject("ChatColors" in router, "Doctor migration router must not expand the Dough dependency boundary")

require("new DoctorRouterCommand(plugin, cmd)" in subcommands, "Doctor migration router is not registered")
require('"migrations"' in tabs, "Doctor migrations tab completion is missing")
require('List.of("status", "list", "unknown")' in tabs, "Doctor migration action tab completion is missing")

require("registersLegacyIdsWithoutPollutingLiveItemRegistry" in test, "live-registry isolation regression test is missing")
require("allowsIdempotentRegistrationButRejectsConflictingTargets" in test, "legacy mapping collision regression test is missing")
require("exposesLegacyMappingsAsReadOnly" in test, "legacy mapping immutability regression test is missing")

if ERRORS:
    print("Doctor migration verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor migration diagnostics verification passed.")
