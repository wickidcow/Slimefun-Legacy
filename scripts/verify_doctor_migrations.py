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
provider_api = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemMigrationProvider.java")
provider_service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemMigrationService.java")
test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/TestSlimefunRegistryLegacyItemIds.java")

require("ConcurrentHashMap<>();" in registry, "legacy-id registry must remain concurrency-safe")
require("registerLegacySlimefunItemId" in registry, "legacy-id registration API is missing")
require("getLegacySlimefunItemIds" in registry, "legacy-id read-only mapping view is missing")
require("Collections.unmodifiableMap(legacySlimefunItemIds)" in registry, "legacy-id mapping view must remain immutable")
require("putIfAbsent(legacyId, currentId)" in registry, "legacy-id registration must remain collision-aware and idempotent")
require("!legacyId.equals(currentId)" in registry, "legacy-id self-map rejection is missing")
require("!legacyId.isBlank()" in registry, "blank legacy-id rejection is missing")
require("!currentId.isBlank()" in registry, "blank current-id rejection is missing")

require("interface LegacyItemMigrationProvider" in provider_api, "addon-owned migration provider API is missing")
require("getLegacyItemMappings()" in provider_api, "migration provider must declare owned legacy mappings")
require("runMigration(boolean repair)" in provider_api, "migration provider scan/repair operation is missing")
require("must not force-load chunks" in provider_api, "migration provider chunk-loading safety contract is missing")
require("getRegistrations(LegacyItemMigrationProvider.class)" in provider_service, "migration provider discovery is missing")
require("Collections.unmodifiableMap(copy)" in provider_service, "provider mapping snapshots must be defensive/read-only")
require("runMigration(repair)" in provider_service, "provider service must delegate migration to the addon")

require('super(plugin, cmd, "doctor", true);' in router, "Doctor migration router must retain the doctor command name")
require('equalsIgnoreCase("migrations")' in router, "Doctor migrations command route is missing")
require('case "status" -> sendMigrationStatus(sender);' in router, "Doctor migration status command is missing")
require('case "list" -> sendMigrationList(sender, parsePage(args));' in router, "Doctor migration list command is missing")
require('case "unknown", "unknowns" -> sendUnknownIds(sender);' in router, "Doctor migration unknown-id correlation is missing")
require('case "plan", "dryrun", "dry-run" -> sendMigrationPlan(sender);' in router, "Doctor migration dry-run plan is missing")
require('case "providers", "provider" -> sendMigrationProviders(sender);' in router, "migration provider listing is missing")
require('case "scan" -> runMigrationProvider(sender, args, false);' in router, "read-only provider migration scan is missing")
require('case "execute" -> runMigrationProvider(sender, args, true);' in router, "provider-owned migration execution route is missing")
require("getUnknownIdSamples()" in router, "Doctor migration correlation must use Item Doctor unknown-ID samples")
require("getLegacySlimefunItemIds()" in router, "Doctor migration command must use addon-declared mappings")
require("This plan is sample-based" in router, "Doctor migration dry-run must disclose sample-based limits")
require("Actual migration remains addon-owned" in router, "Doctor migration dry-run must preserve addon-owned repair boundary")
require("validateProviderMappings" in router, "provider mappings must be validated before execution")
require('!args[4].equalsIgnoreCase("confirm")' in router, "provider repair must require explicit confirmation")
require("Migration blocked: provider mappings are not safe to execute" in router, "provider mapping mismatch must block repair")
require("migrationService.run(provider, repair)" in router, "Doctor must delegate provider execution through the guarded service")
require("Slimefun core did not rewrite addon persistence" in router, "provider execution report must preserve core mutation boundary")
reject("registerLegacySlimefunItemId(" in router, "Doctor command must not register or rewrite migration mappings")
reject("setItemData(" in router, "Doctor router must not directly rewrite Slimefun item IDs")
reject("ChatColors" in router, "Doctor migration router must not expand the Dough dependency boundary")

require("new DoctorRouterCommand(plugin, cmd)" in subcommands, "Doctor migration router is not registered")
require('"migrations"' in tabs, "Doctor migrations tab completion is missing")
require('"providers", "scan", "execute"' in tabs, "migration provider actions are missing from tab completion")
require("getRegistrations(LegacyItemMigrationProvider.class)" in tabs, "provider plugin tab completion must use live service registrations")
require('List.of("confirm")' in tabs, "provider execution confirmation tab completion is missing")

require("registersLegacyIdsWithoutPollutingLiveItemRegistry" in test, "live-registry isolation regression test is missing")
require("allowsIdempotentRegistrationButRejectsConflictingTargets" in test, "legacy mapping collision regression test is missing")
require("rejectsBlankLegacyMappings" in test, "blank legacy mapping regression test is missing")
require("exposesLegacyMappingsAsReadOnly" in test, "legacy mapping immutability regression test is missing")

if ERRORS:
    print("Doctor migration verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor migration diagnostics verification passed.")
