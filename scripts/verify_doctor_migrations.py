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
item_upgrade_router = read(
    "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorItemUpgradeRouterCommand.java"
)
subcommands = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/SlimefunSubCommands.java")
tabs = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java")
provider_api = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemMigrationProvider.java")
provider_service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemMigrationService.java")
provider_plan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemMigrationPlan.java")
plan_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/services/stability/TestLegacyItemMigrationPlan.java")
registry_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/TestSlimefunRegistryLegacyItemIds.java")

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
require("PLAN_TTL_MILLIS = 10L * 60L * 1000L" in provider_service, "migration execution plans must expire after ten minutes")
require("ConcurrentHashMap<>()" in provider_service, "migration execution plan store must be concurrency-safe")
require("preparePlan(" in provider_service, "migration execution plan creation is missing")
require("getPreparedPlan(" in provider_service, "migration execution plan lookup is missing")
require("invalidatePreparedPlan(" in provider_service, "migration execution plan invalidation is missing")
require("plan.isExpired(System.currentTimeMillis())" in provider_service, "expired migration plans must be rejected")

require("MessageDigest.getInstance(\"SHA-256\")" in provider_plan, "migration plan fingerprint must use SHA-256")
require("Collections.unmodifiableMap(sorted)" in provider_plan, "migration plan mapping snapshot must remain ordered/read-only")
require("entries.sort(Map.Entry.comparingByKey())" in provider_plan, "migration plan mappings must be sorted before hashing")
require("matchesMappings" in provider_plan, "migration plan mapping drift detection is missing")
require("matchesFingerprint" in provider_plan, "migration plan fingerprint validation is missing")
require("isExpired" in provider_plan, "migration plan expiry check is missing")
require("getShortFingerprint" in provider_plan, "migration plan short fingerprint is missing")

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
require("report.getFailures() == 0L" in router, "provider scan failures must prevent execution-plan creation")
require("migrationService.preparePlan(provider, mappings)" in router, "clean provider scan must create an execution plan")
require("plan.matchesFingerprint(args[4])" in router, "provider repair must require the scan fingerprint")
require("plan.matchesMappings(mappings)" in router, "provider mapping drift must block execution")
require("provider mappings changed after the approved scan" in router, "provider mapping drift warning is missing")
require("single-use migration plan" in router, "provider repair must disclose single-use execution plan")
require("migrationService.invalidatePreparedPlan(providerId);\n        send(sender, \"&eUsing a single-use migration plan" in router,
        "migration plan must be consumed before the provider repair call")
require("migrationService.run(provider, true)" in router, "Doctor must delegate repair only after plan validation")
require("Slimefun core did not rewrite addon persistence" in router, "provider execution report must preserve core mutation boundary")
reject('equalsIgnoreCase("confirm")' in router, "generic confirm token must not authorize provider migration anymore")
reject('List.of("confirm")' in tabs, "tab completion must not suggest the retired generic confirm token")
reject("registerLegacySlimefunItemId(" in router, "Doctor command must not register or rewrite migration mappings")
reject("setItemData(" in router, "Doctor router must not directly rewrite Slimefun item IDs")
reject("ChatColors" in router, "Doctor migration router must not expand the Dough dependency boundary")

router_registered_directly = "new DoctorRouterCommand(plugin, cmd)" in subcommands
router_registered_through_wrapper = "new DoctorItemUpgradeRouterCommand(plugin, cmd)" in subcommands
require(
    router_registered_directly or router_registered_through_wrapper,
    "Doctor migration router is not registered",
)
if router_registered_through_wrapper:
    require(
        "new DoctorRouterCommand(plugin, cmd)" in item_upgrade_router,
        "Doctor item-upgrade wrapper must delegate all existing migration routes to DoctorRouterCommand",
    )
require('"migrations"' in tabs, "Doctor migrations tab completion is missing")
require('"providers", "scan", "execute"' in tabs, "migration provider actions are missing from tab completion")
require("getRegistrations(LegacyItemMigrationProvider.class)" in tabs, "provider plugin tab completion must use live service registrations")
require("Execution fingerprints are short-lived" in tabs, "migration fingerprint tab completion safety notice is missing")

require("fingerprintIsStableAcrossMappingInsertionOrder" in plan_test, "migration plan ordering regression test is missing")
require("mappingSnapshotIsReadOnlyAndDetectsDrift" in plan_test, "migration plan immutability/drift regression test is missing")
require("fingerprintIsBoundToProviderAndGeneration" in plan_test, "migration plan provider/generation binding test is missing")
require("acceptsFullOrShortFingerprintAndHonorsExpiryBoundary" in plan_test, "migration plan expiry/fingerprint test is missing")

require("registersLegacyIdsWithoutPollutingLiveItemRegistry" in registry_test, "live-registry isolation regression test is missing")
require("allowsIdempotentRegistrationButRejectsConflictingTargets" in registry_test, "legacy mapping collision regression test is missing")
require("rejectsBlankLegacyMappings" in registry_test, "blank legacy mapping regression test is missing")
require("exposesLegacyMappingsAsReadOnly" in registry_test, "legacy mapping immutability regression test is missing")

if ERRORS:
    print("Doctor migration verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor migration diagnostics verification passed.")
