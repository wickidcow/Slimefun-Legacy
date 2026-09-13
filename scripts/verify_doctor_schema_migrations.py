#!/usr/bin/env python3
"""Verify same-ID Doctor schema discovery/validation remains read-only and isolated."""

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


probe_api = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaProbe.java")
candidate = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaCandidate.java")
validator_api = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaValidator.java")
validation = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/LegacyItemSchemaValidation.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaProbeService.java")
runner = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaValidationRunner.java")
report = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorReport.java")
candidate_summary = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaCandidateSummary.java")
validation_summary = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/LegacyItemSchemaValidationSummary.java")
item_doctor = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java")
doctor_service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemDoctorService.java")
scan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorScanWithLegacyCorrelation.java")
correlation = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorSchemaMigrationCorrelation.java")

require("interface LegacyItemSchemaProbe" in probe_api, "schema probe API is missing")
require("getSupportedItemIds()" in probe_api, "schema probes must publish explicit current item IDs")
require("probeItem(@Nonnull ItemStack item" in probe_api, "schema probe ItemStack boundary is missing")
require("ItemStack} is a clone" in probe_api, "schema probe API must document cloned ItemStack isolation")
require("do not perform database, network or filesystem IO" in probe_api, "fast schema probe IO prohibition is missing")

require("MAX_VALIDATION_CLAIM_LENGTH = 2048" in candidate, "opaque validation claims must remain bounded")
require("getValidationClaim()" in candidate, "schema candidate opaque validation claim is missing")
require("VALIDATION_REQUIRED candidates must provide an opaque validation claim" in candidate,
        "validation-required candidates must require a claim")

require("interface LegacyItemSchemaValidator" in validator_api, "schema validator API is missing")
require("getSupportedCandidateTypes()" in validator_api, "schema validators must publish explicit candidate types")
require("CompletionStage<LegacyItemSchemaValidation>" in validator_api, "schema validator must remain asynchronous")
require("same Bukkit plugin" in validator_api, "same-addon validation ownership contract is missing")
require("must not mutate ItemStacks, databases, worlds, chunks or player state" in validator_api,
        "schema validation read-only contract is missing")
require("VERIFIED" in validation and "BACKING_DATA_MISSING" in validation and "STATE_MISMATCH" in validation,
        "schema validation result classifications are incomplete")

require("item.clone()" in service, "core must pass cloned ItemStacks to schema probes")
require("getRegistrations(LegacyItemSchemaProbe.class)" in service, "schema probe service discovery is missing")
require("getRegistrations(LegacyItemSchemaValidator.class)" in service, "schema validator service discovery is missing")
require("new ValidatorKey(providerId" in service, "validator ownership must remain bound to provider plugin")
require("ValidationRequestKey" in service and "ConcurrentHashMap" in service,
        "schema validation claims must be deduplicated in a concurrency-safe request map")
require("slimefunId" in service and "request.slimefunId()" in service,
        "schema validation evidence must remain bound to the current Slimefun item ID")
require("Claim contents were not logged" in service, "schema validation failure logging must protect claim contents")
require("validatePending" in service, "post-traversal schema validation phase is missing")
require("LegacyItemSchemaValidation.Status.MANUAL_ONLY" in service,
        "missing/failed schema validators must fail closed to manual-only")
reject("setItemData(" in service, "schema discovery/validation service must not rewrite Slimefun IDs")
reject("setItemPdc(" in service, "schema discovery/validation service must not mutate item persistent data")

require("getSchemaProbeSession()" in runner and "validatePending(report)" in runner,
        "schema validation runner must use the scan-scoped probe session")
require("schemaMigrationCandidateFound" in report, "schema candidate aggregation is missing")
require("schemaValidationFound" in report, "schema validation aggregation is missing")
require("getSchemaValidationSummaries" in report, "schema validation summaries are missing")
require("getSchemaValidatedCandidates" in report, "validated candidate stack count is missing")
require("String slimefunId" in report and "key.slimefunId" in report,
        "schema report aggregation must retain the current Slimefun item ID")
require("getSlimefunId()" in candidate_summary and "getSlimefunId()" in validation_summary,
        "schema summary objects must expose their current Slimefun item ID")

require("schemaProbes.inspect(item, itemId, report)" in item_doctor,
        "schema probes must run through the authoritative recursive Item Doctor traversal")
require("startMigrationAwareServerRun" in doctor_service, "dedicated migration-aware scan entry point is missing")
require("return startServerRun(false, true, null, completion);" in doctor_service,
        "migration-aware scan must remain read-only and isolated from schema execution")
require("new LegacyItemSchemaProbeService(plugin).createSession(report)" in doctor_service,
        "schema probe session must be created only for the explicit migration-aware run")

require("LegacyItemSchemaValidationRunner.validate(report)" in scan,
        "normal migration-aware Doctor scan must run persistent-state validation after traversal")
require("Schema probes receive cloned items; persistent-state validators are read-only" in scan,
        "operator output must disclose the schema validation safety boundary")
require("getSlimefunId()" in correlation,
        "operator schema output must identify the current Slimefun item family for every candidate/validation group")
require("Opaque validation claims are never displayed" in correlation,
        "schema correlation must explicitly protect opaque validation claims")
require("Read-only probe/validation pass" in correlation,
        "schema correlation must disclose read-only behavior")
require("repair remains addon-owned" in correlation,
        "schema correlation must preserve addon-owned repair boundary")
reject("getValidationClaim" in correlation, "operator correlation must never access opaque validation claims")
reject("setItemData(" in correlation, "schema correlation must not rewrite Slimefun IDs")
reject("setItemPdc(" in correlation, "schema correlation must not mutate item persistent data")

# Mutation is intentionally not part of this slice. A later executor must have its own fingerprinted-plan verifier.
reject("LegacyItemSchemaMigrator" in service, "read-only schema validation slice must not silently gain a migrator")

if ERRORS:
    print("Doctor schema migration verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor schema migration verification passed.")
