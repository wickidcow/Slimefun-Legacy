#!/usr/bin/env python3
"""Verify persisted backpack Item-ID migration remains explicit, cache-safe and fingerprint-gated."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
ERRORS: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        ERRORS.append(f"missing required file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


cache = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BackpackCache.java")
profile = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ProfileDataController.java")
storage = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/PersistedBackpackItemStorageMaintenance.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/PersistedBackpackItemIdMigrationService.java")
plan = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/PersistedBackpackItemIdMigrationPlan.java")
command = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorBackpackItemIdMigrationCommand.java")
stored_command = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorStoredItemMigrationCommand.java")
tabs = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/SlimefunTabCompleter.java")
upgrade = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorUpgradeWorkflow.java")
plan_test = read("src/test/java/io/github/thebusybiscuit/slimefun4/core/services/stability/TestPersistedBackpackItemIdMigrationPlan.java")
cache_test = read("src/test/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/TestBackpackCacheMaintenanceGuard.java")

require("DataScope.BACKPACK_INVENTORY" in storage, "backpack migration must target BACKPACK_INVENTORY only")
require("runIfAllWriteWorkIdle" in storage and "runIfReadExecutorIdle" in storage,
        "backpack storage scan/write must hold profile read/write idle gates")
require("BackpackCache.hasActiveControllerCache()" in storage,
        "backpack storage maintenance must fail closed without an authoritative cache")
require("runIfAllUncachedInActiveController" in storage,
        "backpack writes must hold the cache guard for the approved batch")
require("stale != 0 || missing != 0" in storage,
        "all stale/missing rows must be rejected before backpack mutation starts")
require("rollbackComplete" in storage and "for (int i = applied.size() - 1; i >= 0; i--)" in storage,
        "backpack batch write failures must retain reverse rollback")
apply_batch = storage.find("private RewriteSummary applyBatch")
applied_mark = storage.find("applied.add(current);", apply_batch)
write_mark = storage.find("writeValue(current, request.replacement());", apply_batch)
require(apply_batch >= 0 and 0 <= applied_mark < write_mark,
        "backpack rollback must conservatively include the current row before a potentially commit-then-throw write")
require("activeCache = null" in cache,
        "the authoritative backpack cache guard must clear during cache cleanup")
require("runIfAllUncached(Collection<String> uuids" in cache,
        "backpack cache must expose an atomic all-UUID maintenance guard")
require("ReentrantReadWriteLock" in cache
        and "maintenanceLock.readLock()" in cache
        and "maintenanceLock.writeLock()" in cache,
        "backpack cache must use a shared/exclusive maintenance gate so normal cache misses stay concurrent")
require("maintenance.tryLock()" in cache,
        "backpack storage maintenance must fail fast instead of blocking on in-flight gameplay loads")
require(cache.count("maintenanceLock.readLock()") >= 4,
        "normal and maintenance cache-install paths must share the read side of the Doctor maintenance gate")
require("getOrLoad(String pUuid, int num, Supplier<PlayerBackpack> loader)" in cache
        and "getOrLoad(String uuid, Supplier<PlayerBackpack> loader)" in cache,
        "normal cache misses must keep database load + cache install under the shared maintenance gate")
require("backpackCache.getOrLoad(uuid, num" in profile and "backpackCache.getOrLoad(uuid, () -> loadBackpackByUuid(uuid))" in profile,
        "both synchronous backpack load paths must use the cache-miss maintenance guard")

require("resolveLegacySlimefunItemId" in service,
        "backpack migration must use the live registered legacy-ID resolver")
require("SlimefunItem.getById(canonicalId)" in service,
        "backpack migration must require the canonical target to be registered")
require("setItemData(replacement, canonicalId)" in service,
        "backpack migration must change the Slimefun Item ID through ItemDataService")
require("setItemData(comparison, legacyId)" in service and "original.equals(comparison)" in service,
        "backpack migration must prove the Item ID is the only semantic ItemStack change")
require("preparedPlan = null;" in service,
        "backpack execution plan must be consumed before storage mutation")
require("snapshot.cachedRecords()" in service and "snapshot.unreadableRecords()" in service,
        "cached and malformed persisted backpack rows must remain visible to the audit")

require("MessageDigest.getInstance(\"SHA-256\")" in plan,
        "backpack migration fingerprint must use SHA-256")
require('update(digest, "backpack", entry.backpackId())' in plan,
        "backpack UUID must be bound into the execution fingerprint")
require('update(digest, "slot", entry.slotKey())' in plan,
        "backpack slot must be bound into the execution fingerprint")
require('update(digest, "stored", entry.expectedValueHash())' in plan,
        "exact stored payload hash must be bound into the execution fingerprint")
require("isExpired" in plan and "matchesFingerprint" in plan,
        "backpack migration plan must retain expiry and fingerprint validation")

require("backpackItemIdMigrations.execute(sender, args)" in stored_command,
        "persisted storage router must expose the backpack Item-ID lane")
require("schemas storage backpacks" in command,
        "backpack migration command must remain under the persisted storage namespace")
require("Cached backpacks were not inspected" in command,
        "operator output must disclose cached backpack deferral")
require('"ids", "backpacks"' in tabs,
        "tab completion must expose machine and backpack Item-ID lanes")
require("PersistedBackpackItemIdMigrationService" in upgrade,
        "aggregate upgrade planning must audit persisted backpack Item IDs")
require("CACHED/DEFERRED" in upgrade,
        "aggregate upgrade summary must distinguish cached backpack deferral")

require("cachedDeferredRowsAreBoundIntoFingerprint" in plan_test,
        "backpack fingerprint regression test must bind deferred-cache count")
require("executesUncachedBatchExactlyOnce" in cache_test,
        "backpack cache batch-guard regression test is missing")
require("inFlightCacheMissDefersMaintenanceWithoutBlocking" in cache_test,
        "backpack cache must regression-test fail-fast deferral for in-flight synchronous loads")
require("independentCacheMissLoadsMayOverlap" in cache_test,
        "backpack cache must regression-test that unrelated gameplay loads are not serialized by Doctor safety")
require("maintenanceDoesNotBlockUnrelatedCacheReads" in cache_test,
        "Doctor storage rewrites must regression-test that unrelated cache reads remain responsive")

if ERRORS:
    print("Doctor persisted backpack Item-ID verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Doctor persisted backpack Item-ID verification passed.")
