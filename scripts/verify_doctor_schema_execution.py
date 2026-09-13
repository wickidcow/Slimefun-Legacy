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
item_doctor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorService.java")
command = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorSchemaMigrationCommand.java")
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
require("List<Authorization> authorizations()" in plan,
        "schema executor must receive the private bounded authorization set without exposing it publicly")

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
require("CompletionStage<Boolean> revalidatePlan" in service,
        "schema plans must revalidate backing state immediately before execution")
require("getRegistrations(LegacyItemSchemaValidator.class)" in service,
        "execution revalidation must use the owning addon's current validator registration")
require("result.getStatus() == LegacyItemSchemaValidation.Status.VERIFIED" in service,
        "execution revalidation must require VERIFIED backing state")
require("Objects.equals(result.getMigrationPayload(), authorization.migrationPayload())" in service,
        "execution revalidation must reproduce the exact private migration payload")

require("probe.probeItem(item.clone(), slimefunId)" in executor,
        "schema execution must re-run the addon probe against a clone of the live item")
require("plan.findAuthorization(" in executor,
        "schema execution must require exact fingerprint-plan authorization")
require("migrator.migrateItem(" in executor,
        "core must delegate schema mutation to the owning addon migrator")
require("ItemStack original = item.clone()" in executor and "restore(item, original)" in executor,
        "failed addon schema mutation must roll back the live ItemStack")
require("!slimefunId.equals(resultingId.get())" in executor,
        "same-ID schema execution must reject addon attempts to rewrite the Slimefun item ID")
require("item.getAmount() != original.getAmount()" in executor,
        "schema execution must reject addon attempts to change stack amount")
require("!changed && !item.equals(original)" in executor,
        "schema execution must reject mutation when an addon reports no change")
require("violated the same-ID ItemStack contract" in executor,
        "schema postcondition failures must restore and report the original item")
require("MAX_CONTAINER_DEPTH = 4" in executor,
        "schema execution nested-container recursion must remain bounded")
require("meta instanceof BundleMeta" in executor and "bundleMeta.setItems(contents)" in executor,
        "schema execution must preserve recursive Bundle migration support")
require("meta instanceof BlockStateMeta" in executor and "blockStateMeta.setBlockState(container)" in executor,
        "schema execution must preserve recursive container-item migration support")
require("inspectInventory(@Nonnull Inventory inventory" in executor,
        "schema executor inventory traversal entry point is missing")
require("remainingAuthorizations" in executor and "consumeAuthorization(authorization)" in executor,
        "schema execution must enforce the exact scanned count for every private authorization")
require("new AtomicLong(authorization.candidateCount())" in executor,
        "schema execution count limits must originate from the fingerprinted scan count")

require("startSchemaMigrationRun(" in item_doctor,
        "Item Doctor must expose the explicit schema execution traversal entry point")
require("return startServerRun(true, false, executor, completion);" in item_doctor,
        "schema execution must reuse ServerRun without enabling schema discovery probes")
require(item_doctor.count("schemaExecutor == null") >= 3,
        "schema execution must switch all inventory, dropped-item and backpack mutation points")
require(item_doctor.count("schemaExecutor.inspectInventory") >= 2,
        "schema execution must cover queued inventories and maintenance backpacks")
require("schemaExecutor.inspectItem(item, report)" in item_doctor,
        "schema execution must cover dropped ItemStacks")
reject("startSchemaMigrationRun(" in item_doctor[item_doctor.find("@EventHandler"):item_doctor.find("private final class ServerRun")],
       "automatic Doctor listeners must never invoke schema execution")

require('case "schemas", "schema" -> schemaMigrations.execute(sender, args);' in router,
        "Doctor migrations router must expose the explicit same-ID schema command group")
reject("getValidationClaim()" in router or "getMigrationPayload()" in router,
       "Doctor router must not expose private schema claims or payloads")
require("LegacyItemSchemaValidationRunner.validate(report)" in command,
        "schema plan creation must complete addon-owned read-only validation")
require("migrationService.preparePlans(report)" in command,
        "schema scan must create plans only after validation")
require("plan.matchesFingerprint(args[5])" in command,
        "schema execution must require the operator-supplied plan fingerprint")
require("migrationService.invalidateAllPreparedPlans()" in command,
        "schema execution must invalidate the selected fingerprint and sibling plans before mutation")
require("migrationService.revalidatePlan(plan)" in command,
        "schema execution must revalidate backing state after consuming the fingerprint")
require("migrationService.createExecutor(plan)" in command,
        "schema execution must re-check live addon ownership/version/registrations after revalidation")
require("doctor.startSchemaMigrationRun(executor" in command,
        "schema execution command must use the existing Doctor traversal")
consume_pos = command.rfind("migrationService.invalidateAllPreparedPlans()")
revalidate_pos = command.find("migrationService.revalidatePlan(plan)")
executor_pos = command.find("migrationService.createExecutor(plan)")
start_pos = command.find("doctor.startSchemaMigrationRun(executor")
require(-1 not in (consume_pos, revalidate_pos, executor_pos, start_pos)
        and consume_pos < revalidate_pos < executor_pos < start_pos,
        "schema execution order must be consume all plans -> revalidate backing state -> refresh executor -> mutate")
require("sibling schema plans are now consumed" in command,
        "operator output must state that sibling plans are invalidated by execution")
require("Backing state was revalidated immediately before traversal" in command,
        "operator output must confirm the final backing-state validation gate")
require("Automatic Doctor listeners do not participate" in command,
        "operator output must state that automatic listeners are outside schema execution")
reject("getValidationClaim()" in command or "getMigrationPayload()" in command,
       "schema operator command must never expose private claims or payloads")
reject("PlayerJoinEvent" in service or "ChunkLoadEvent" in service or "InventoryOpenEvent" in service,
       "schema migration plan service must not gain background event execution")

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
