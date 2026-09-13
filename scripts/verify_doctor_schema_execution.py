#!/usr/bin/env python3
"""Verify fingerprinted same-ID Doctor schema migration remains explicit, private and addon-owned."""

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ERRORS: list[str] = []


def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        ERRORS.append(f"missing required file: {path}")
        return ""
    return file.read_text(encoding="utf-8")


def require(value: bool, message: str) -> None:
    if not value:
        ERRORS.append(message)


def reject(value: bool, message: str) -> None:
    if value:
        ERRORS.append(message)


validation = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaValidation.java")
migrator = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaMigrator.java")
probe_service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaProbeService.java")
plan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationPlan.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationService.java")
executor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationExecutor.java")
validation_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/TestLegacyItemSchemaValidation.java")
plan_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/services/stability/TestLegacyItemSchemaMigrationPlan.java")
router = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorRouterCommand.java")

require("MAX_MIGRATION_PAYLOAD_LENGTH = 2048" in validation,
        "schema migration payloads must remain bounded")
require("Only VERIFIED schema validation may carry a migration payload" in validation,
        "non-verified schema validation must never authorize migration payloads")
require("getMigrationPayload()" in validation,
        "verified schema validation private migration payload is missing")

require("interface LegacyItemSchemaMigrator" in migrator,
        "addon-owned schema migrator API is missing")
require("may mutate only the supplied" in migrator,
        "schema migrator must retain its ItemStack-only mutation boundary")
require("current Slimefun item ID, candidate type and opaque validation claim" in migrator,
        "schema migrator API must document exact live revalidation")

require("validation.getStatus() == LegacyItemSchemaValidation.Status.VERIFIED" in probe_service,
        "only VERIFIED schema validation may become executable authorization")
require("payload != null" in probe_service,
        "diagnostic-only VERIFIED results must not silently become executable")
require("verifiedAuthorizations" in probe_service and "ConcurrentHashMap" in probe_service,
        "verified schema authorizations must remain private and concurrency-safe")

require('MessageDigest.getInstance("SHA-256")' in plan,
        "schema migration fingerprints must use SHA-256")
require('update(digest, "version", providerVersion)' in plan,
        "schema migration fingerprint must bind the addon version")
require('update(digest, "generation", Long.toString(generation))' in plan,
        "schema migration fingerprint must bind the scan generation")
require('update(digest, "claim", digest(authorization.validationClaim()))' in plan,
        "schema migration fingerprint must hash opaque validation claims")
require('update(digest, "payload", digest(authorization.migrationPayload()))' in plan,
        "schema migration fingerprint must hash private migration payloads")
require("findAuthorization(" in plan,
        "schema migration plan must support exact live authorization matching")

require("PLAN_TTL_MILLIS = 10L * 60L * 1000L" in service,
        "schema migration plans must expire after ten minutes")
require("report.getFailures() != 0L" in service,
        "Doctor scan failures must prevent schema execution plans")
require("getRegistrations(LegacyItemSchemaMigrator.class)" in service,
        "schema migrators must be discovered through Bukkit services")
require("registration.getPlugin() != owner" in service,
        "schema executor must retain same-addon ownership checks")
require("matchesProviderVersion" in service,
        "addon version drift must invalidate schema execution")
require("ambiguous" in service.lower(),
        "ambiguous duplicate schema providers must remain non-executable")

require("probe.probeItem(item.clone(), slimefunId)" in executor,
        "schema execution must re-run the addon probe against a clone of the live item")
require("plan.findAuthorization(" in executor,
        "schema execution must require exact fingerprint-plan authorization")
require("migrator.migrateItem(" in executor,
        "core must delegate schema mutation to the owning addon migrator")
require("ItemStack original = item.clone()" in executor and "restore(item, original)" in executor,
        "failed addon schema mutation must roll back the live ItemStack")
reject("getValidationClaim()" in router,
       "Doctor operator commands must not expose private validation claims")
reject("getMigrationPayload()" in router,
       "Doctor operator commands must not expose private migration payloads")

# This slice deliberately stops before command/traversal activation. A later PR must add its own explicit execution gate.
reject("startSchemaMigrationRun" in router,
       "schema-plan foundation must not silently expose an execution command")
reject("PlayerJoinEvent" in service or "ChunkLoadEvent" in service or "InventoryOpenEvent" in service,
       "schema migration plans must not gain background event execution")

require("nonVerifiedValidationCannotCarryMigrationPayload" in validation_test,
        "schema validation payload authorization regression test is missing")
require("fingerprintDoesNotExposePrivateClaimOrPayload" in plan_test,
        "schema plan privacy regression test is missing")
require("providerVersionAndGenerationBindFingerprint" in plan_test,
        "schema plan provider/generation binding regression test is missing")
require("authorizationRequiresExactItemTypeAndClaim" in plan_test,
        "schema exact-authorization regression test is missing")
require("expiryBoundaryIsStrict" in plan_test,
        "schema plan expiry regression test is missing")

if ERRORS:
    print("Doctor schema execution verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor schema execution verification passed.")
