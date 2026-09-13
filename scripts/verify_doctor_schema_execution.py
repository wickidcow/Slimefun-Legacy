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


candidate = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaCandidate.java")
validation = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaValidation.java")
migrator = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaMigrator.java")
probe_service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaProbeService.java")
plan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationPlan.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationService.java")
executor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaMigrationExecutor.java")
item_doctor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorService.java")
command = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorSchemaMigrationCommand.java")
validation_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/TestLegacyItemSchemaValidation.java")
plan_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/services/stability/TestLegacyItemSchemaMigrationPlan.java")
router = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorRouterCommand.java")
tabs = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java")

require("candidates may also supply a deterministic claim" in candidate,
        "READY item-local candidates must document deterministic private claims")
require("READY candidate without a claim remains" in candidate,
        "claim-less READY candidates must remain diagnostic-only")
require("MAX_MIGRATION_PAYLOAD_LENGTH = 2048" in validation,
        "schema migration payloads must remain bounded")
require("Only VERIFIED schema validation may carry a migration payload" in validation,
        "non-verified schema validation must never authorize migration payloads")
require("interface LegacyItemSchemaMigrator" in migrator,
        "addon-owned schema migrator API is missing")
require("may mutate only the supplied" in migrator,
        "schema migrator must retain its ItemStack-only mutation boundary")

require("validation.getStatus() == LegacyItemSchemaValidation.Status.VERIFIED" in probe_service,
        "only VERIFIED external validation may become validator-backed authorization")
require("payload != null" in probe_service,
        "diagnostic-only VERIFIED results must not silently become executable")
require("readyRequests" in probe_service and "candidate.getReadiness() == LegacyItemSchemaCandidate.Readiness.READY && claim != null" in probe_service,
        "READY execution authorization must require an explicit item-local private claim")
require("getReadyAuthorizations()" in probe_service,
        "scan session must expose bounded READY authorizations separately from verified external state")

require('MessageDigest.getInstance("SHA-256")' in plan,
        "schema migration fingerprints must use SHA-256")
require('update(digest, "version", providerVersion)' in plan,
        "schema migration fingerprint must bind the addon version")
require('update(digest, "generation", Long.toString(generation))' in plan,
        "schema migration fingerprint must bind the scan generation")
require('update(digest, "claim", digest(authorization.validationClaim()))' in plan,
        "schema migration fingerprint must hash opaque claims")
require('update(digest, "payload", digest(authorization.migrationPayload()))' in plan,
        "schema migration fingerprint must hash private migration payloads")
require('update(digest, "external-validation", Boolean.toString(authorization.requiresExternalValidation()))' in plan,
        "schema fingerprint must bind READY versus externally validated authorization mode")
require('READY_ITEM_LOCAL_PAYLOAD = "doctor:item-local-ready"' in plan,
        "READY plans must use a core-owned marker instead of addon-supplied hidden state")
require("!requiresExternalValidation && !READY_ITEM_LOCAL_PAYLOAD.equals(migrationPayload)" in plan,
        "READY authorization must reject addon-supplied migration payloads")
require("one schema authorization claim produced conflicting migration evidence" in plan,
        "one private claim must never mix READY and externally validated evidence")
require("Math.addExact(previous.candidateCount(), authorization.candidateCount())" in plan,
        "duplicate authorization scan counts must be merged with overflow protection")

require("PLAN_TTL_MILLIS = 10L * 60L * 1000L" in service,
        "schema migration plans must expire after ten minutes")
require("report.getFailures() != 0L" in service,
        "Doctor scan failures must prevent schema execution plans")
require("session.getVerifiedAuthorizations()" in service and "session.getReadyAuthorizations()" in service,
        "plan creation must consider validator-backed and READY item-local evidence separately")
require("READY_ITEM_LOCAL_PAYLOAD" in service and "candidateCount(), false" in service,
        "READY item-local plan entries must be marked as not requiring external validation")
require("candidateCount(), true" in service,
        "validator-backed plan entries must retain mandatory external revalidation")
require("if (!authorization.requiresExternalValidation()) continue;" in service,
        "final validator recheck may be skipped only for READY item-local authorizations")
require("getRegistrations(LegacyItemSchemaValidator.class)" in service,
        "validator-backed execution must still use the owning addon's current validator")
require("result.getStatus() == LegacyItemSchemaValidation.Status.VERIFIED" in service,
        "validator-backed execution must still require VERIFIED backing state")
require("Objects.equals(result.getMigrationPayload(), authorization.migrationPayload())" in service,
        "validator-backed execution must reproduce the exact private migration payload")
require("!probes.containsKey(authorization.slimefunId())" in service,
        "executor creation must preflight every authorized item ID against a live addon probe")
require("!migrators.containsKey(authorization.candidateType())" in service,
        "executor creation must preflight every candidate type against a live addon migrator")

require("probe.probeItem(item.clone(), slimefunId)" in executor,
        "execution must re-run the addon probe against a clone of the live item")
require("plan.findAuthorization(" in executor,
        "execution must require exact fingerprint-plan claim authorization")
require("migrator.migrateItem(" in executor,
        "core must delegate schema mutation to the owning addon migrator")
require("ItemStack original = item.clone()" in executor and "restore(item, original)" in executor,
        "failed addon schema mutation must roll back the live ItemStack")
require("!slimefunId.equals(resultingId.get())" in executor,
        "same-ID execution must reject addon attempts to rewrite the Slimefun item ID")
require("item.getAmount() != original.getAmount()" in executor,
        "schema execution must reject addon attempts to change stack amount")
require("remainingAuthorizations" in executor and "consumeAuthorization(authorization)" in executor,
        "schema execution must enforce the exact scanned count for every private authorization")
require("MAX_CONTAINER_DEPTH = 4" in executor,
        "schema execution nested-container recursion must remain bounded")

require("startSchemaMigrationRun(" in item_doctor,
        "Item Doctor must expose the explicit schema execution traversal entry point")
require("return startServerRun(true, false, executor, completion);" in item_doctor,
        "schema execution must reuse ServerRun without enabling discovery probes")
reject("startSchemaMigrationRun(" in item_doctor[item_doctor.find("@EventHandler"):item_doctor.find("private final class ServerRun")],
       "automatic Doctor listeners must never invoke schema execution")

require('case "schemas", "schema" -> schemaMigrations.execute(sender, args);' in router,
        "Doctor migrations router must expose the explicit same-ID schema command group")
reject("getValidationClaim()" in router or "getMigrationPayload()" in router,
       "Doctor router must not expose private schema claims or payloads")
require("LegacyItemSchemaValidationRunner.validate(report)" in command,
        "schema plan creation must complete addon-owned read-only validation phase")
require("migrationService.preparePlans(report)" in command,
        "schema scan must create plans only after traversal/validation completion")
require("plan.matchesFingerprint(args[5])" in command,
        "schema execution must require the operator-supplied plan fingerprint")
require("migrationService.invalidateAllPreparedPlans()" in command,
        "schema execution must invalidate selected and sibling plans before mutation")
require("migrationService.revalidatePlan(plan)" in command,
        "schema execution must pass the final authorization revalidation gate")
require("doctor.startSchemaMigrationRun(executor" in command,
        "schema execution command must use the existing Doctor traversal")
reject("getValidationClaim()" in command or "getMigrationPayload()" in command,
       "schema operator command must never expose private claims or payloads")

require('"execute", "schemas"' in tabs,
        "Doctor migration tab completion must expose the same-ID schema command group")
require("Same-ID schema fingerprints are private short-lived command state" in tabs,
        "schema tab completion must never suggest cached execution fingerprints")

require("nonVerifiedValidationCannotCarryMigrationPayload" in validation_test,
        "schema validation payload authorization regression test is missing")
require("readyAuthorizationUsesOnlyCoreMarkerAndBindsMode" in plan_test,
        "READY authorization mode/marker regression test is missing")
require("mixedReadyAndValidatedEvidenceForSameClaimIsRejected" in plan_test,
        "READY/validated claim collision regression test is missing")
require("fingerprintDoesNotExposePrivateClaimOrPayload" in plan_test,
        "schema plan privacy regression test is missing")
require("duplicateEquivalentAuthorizationsAreCanonicalized" in plan_test,
        "duplicate schema authorization canonicalization regression test is missing")
require("conflictingPayloadsForOnePrivateClaimAreRejected" in plan_test,
        "conflicting schema migration payload regression test is missing")
require("expiryBoundaryIsStrict" in plan_test,
        "schema plan expiry regression test is missing")

if ERRORS:
    print("Doctor schema execution verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor schema execution verification passed.")
