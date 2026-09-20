#!/usr/bin/env python3
"""Verify AContainer loaded-storage energy and addon-hook invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Container storage correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Container storage correctness failed: missing {label}: {needle}")


def require_absent(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Container storage correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Container storage correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    source = compact(
        read(
            root,
            "src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java",
        )
    )

    # Keep the legacy protected tick/takeCharge dispatch points intact for addons while carrying
    # the already-resolved ticker data through the current region thread only.
    require(source, "private final ThreadLocal<TickContext> tickContext = new ThreadLocal<>()", "thread-local tick context")
    require(source, "private record TickContext(Location location, SlimefunBlockData data)", "tick context payload")
    require(source, "TickContext previous = tickContext.get()", "nested tick-context preservation")
    require(source, "tickContext.set(new TickContext(b.getLocation(), data))", "loaded ticker-data capture")
    require(source, "try { AContainer.this.tick(b); } finally", "legacy virtual tick dispatch with cleanup")
    require(source, "tickContext.remove()", "thread-local cleanup")
    require(source, "tickContext.set(previous)", "nested tick-context restoration")
    require(source, "protected void tick(Block b)", "legacy protected tick hook")
    require(source, "if (takeCharge(b.getLocation()))", "legacy virtual takeCharge dispatch")
    require(source, "protected boolean takeCharge(@Nonnull Location l)", "legacy protected takeCharge hook")

    # The current block may use the ticker's loaded SlimefunBlockData, but arbitrary locations
    # must fall back to a fresh shared storage resolution rather than reusing data from the wrong block.
    require(source, "context != null && context.location().equals(l)", "tick-location identity guard")
    require(source, "return takeCharge(l, context.data())", "loaded ticker-data charge path")
    require(source, "ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(l)", "non-ticker storage fallback")
    require(source, "private boolean takeCharge(@Nonnull Location l, @Nonnull ASlimefunDataContainer data)", "shared storage helper")
    require(source, "data == null || data.isPendingRemove()", "missing/pending storage guard")
    require(source, "if (!data.isDataLoaded())", "unloaded storage guard")
    require(source, "StorageCacheUtils.requestLoad(data)", "storage load request")

    # Reads and writes for one energy transaction must share the same validated storage object.
    require(source, "long charge = getChargeLong(l, data)", "loaded-data energy read")
    require(source, "setCharge(l, charge - getEnergyConsumption(), data)", "loaded-data energy write")
    require_before(
        source,
        "long charge = getChargeLong(l, data)",
        "setCharge(l, charge - getEnergyConsumption(), data)",
        "energy read before write",
    )
    require_absent(source, "long charge = getChargeLong(l);", "duplicate location-based energy read")
    require_absent(source, "setCharge(l, (long) charge - getEnergyConsumption());", "duplicate location-based energy write")

    # Backpack snapshots acknowledge completed queue batches, not individual
    # runnables that may be compacted/replaced inside QueuedWriteTask.
    controller = compact(
        read(
            root,
            "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ADataController.java",
        )
    )
    require(
        controller,
        "protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(",
        "completion-returning scoped write submission",
    )
    require(
        controller,
        "return queuedTask.getCompletionFuture()",
        "existing accepting queue completion",
    )
    require_before(
        controller,
        "CompletableFuture<Void> completion = queuedTask.getCompletionFuture()",
        "writeExecutor.submit(queuedTask)",
        "new queue completion captured before executor submission",
    )

    profile = compact(
        read(
            root,
            "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ProfileDataController.java",
        )
    )
    require(profile, "public CompletableFuture<Void> saveBackpackInventoryAsync", "async backpack save API")
    require(
        profile,
        "private final Map<String, CompletableFuture<Void>> backpackSaveChains",
        "per-backpack save chains",
    )
    require(
        profile,
        "private final Set<String> uncertainBackpackBaselines",
        "partial-write recovery marker",
    )
    require(profile, "stagedSnapshot = new InvSnapshot(contents)", "immutable staged backpack snapshot")
    require(profile, "if (slot < 0 || slot >= re.length)", "stale resized-slot load guard")
    require(profile, "boolean repairRequired = false", "backpack load repair tracking")
    require(profile, "repairRequired = true", "stale/corrupt row repair marker")
    require(
        profile,
        "uncertainBackpackBaselines.add(uuid)",
        "recovered stale rows force a full reconciliation on next save",
    )
    require(profile, "stageBackpackWrites(backpackId, contents)", "fully staged backpack writes")
    require(profile, "for (int slot = 0; slot < 54; slot++)", "full legal backpack recovery range")
    require(
        profile,
        "CompletableFuture<Void> next = start.thenCompose(ignored -> saveAttempt.get())",
        "serialized save attempts per backpack UUID",
    )
    require(
        profile,
        "changed = new HashSet<>(stagedWrites.keySet())",
        "full rewrite after uncertain partial persistence",
    )
    require(
        profile,
        "scheduleDeleteTaskWithCompletion(ownerScope, write.key(), false)",
        "tracked backpack delete completion",
    )
    require(
        profile,
        "scheduleWriteTaskWithCompletion(ownerScope, write.key(), write.data(), false)",
        "tracked backpack write completion",
    )
    require(profile, "CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new))", "backpack write batch barrier")
    require(
        profile,
        "backpack.acknowledgeSnapshot(stagedSnapshot)",
        "snapshot acknowledgement after successful batch",
    )
    require(
        profile,
        "uncertainBackpackBaselines.add(backpackId)",
        "failed batch marks persistence baseline uncertain",
    )
    require_before(
        profile,
        "awaitBackpackSaveChains()",
        "super.shutdown()",
        "backpack save chains drain before controller shutdown",
    )
    require_absent(
        profile,
        "CompletableFuture<Void> completion = new CompletableFuture<>()",
        "per-runnable completion future vulnerable to queue compaction",
    )
    require_absent(
        profile,
        "if (allChangesStaged) { bp.refreshSnapshot(); }",
        "queue-acceptance snapshot acknowledgement",
    )
    require(
        profile,
        "public void invalidateCacheAfterBackpackPersistence(@Nonnull String pUuid)",
        "persistence-aware logout cache invalidation",
    )
    require(
        profile,
        "Set<String> backpackIds = backpackCache.getOwnerBackpackIds(pUuid)",
        "owner backpack snapshot before logout eviction",
    )
    require(
        profile,
        "CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))",
        "logout eviction waits for registered backpack persistence",
    )
    require(
        profile,
        "failure != null || hasUncertainBackpackBaseline(backpackIds)",
        "failed or uncertain persistence retains canonical cache",
    )
    require(
        profile,
        "backpackCache.invalidateAfterPersistence(pUuid)",
        "successful persistence-only cache eviction",
    )

    cache = compact(
        read(
            root,
            "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BackpackCache.java",
        )
    )
    require(cache, "synchronized Set<String> getOwnerBackpackIds(String pUuid)", "owner backpack cache snapshot")
    require(
        cache,
        "synchronized void invalidateAfterPersistence(String pUuid)",
        "persistence-safe backpack cache eviction",
    )
    require(
        cache,
        "backpack.markInvalidAfterPersistence()",
        "cache eviction without off-thread Bukkit inventory access",
    )

    profile_listener = compact(
        read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/PlayerProfileListener.java",
        )
    )
    require(
        profile_listener,
        "@EventHandler(priority = EventPriority.HIGHEST)",
        "profile cache cleanup ordered after normal-priority backpack quit save",
    )
    require(
        profile_listener,
        ".invalidateCacheAfterBackpackPersistence(e.getPlayer().getUniqueId().toString())",
        "quit path uses persistence-aware cache invalidation",
    )

    backpack = compact(
        read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/player/PlayerBackpack.java",
        )
    )
    require(backpack, "private volatile InvSnapshot snapshot", "cross-thread snapshot visibility")
    require(backpack, "private volatile boolean isInvalid", "cross-thread invalidation visibility")
    require(
        backpack,
        "public void markInvalidAfterPersistence()",
        "no-Bukkit persistence-complete invalidation marker",
    )
    require(
        backpack,
        "public void acknowledgeSnapshot(@Nonnull InvSnapshot persistedSnapshot)",
        "exact persisted-snapshot acknowledgement API",
    )
    require(backpack, "this.snapshot = persistedSnapshot", "immutable staged snapshot assignment")

    listener = compact(
        read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BackpackListener.java",
        )
    )
    require(
        listener,
        "private final Map<UUID, CompletableFuture<Void>> pendingSaves",
        "pending backpack save registry",
    )
    require(
        listener,
        "if (!backpack.getUniqueId().equals(backpacks.get(playerId))) { saveDetachedBackpack(backpack, context); return; }",
        "detached backpack saves are kept out of tracked player-session state",
    )
    require(
        listener,
        "private void saveDetachedBackpack(@Nonnull PlayerBackpack backpack, @Nonnull String context)",
        "detached backpack persistence path",
    )
    require(listener, "saveBackpackInventoryAsync(backpack)", "listener async backpack save")
    require(
        listener,
        "if (!pendingSaves.containsKey(playerId)) { finishBackpackSession(playerId, null); }",
        "quit keeps reservation while save is pending",
    )
    require_before(
        listener,
        "pendingSaves.put(playerId, save)",
        "save.whenComplete",
        "pending save registered before completion callback",
    )
    require(
        listener,
        "pendingSaves.remove(playerId, save); finishBackpackSession(playerId, backpack.getUniqueId());",
        "pending save cleared before session reservation release",
    )
    require(
        listener,
        "pendingSaves.put(playerId, save)",
        "pending backpack save registration",
    )
    require(
        listener,
        "backpacks.remove(playerId, backpack.getUniqueId())",
        "closed backpack UUID removed from active GUI map",
    )
    require_before(
        listener,
        "pendingSaves.put(playerId, save)",
        "backpacks.remove(playerId, backpack.getUniqueId())",
        "save reservation registered before active GUI state is cleared",
    )
    require(
        listener,
        "private void finishBackpackSession(@Nonnull UUID playerId, @Nullable UUID backpackId)",
        "central backpack session release helper",
    )
    require(
        listener,
        "openRegistry.release(playerId)",
        "canonical reservation release in session cleanup helper",
    )

    print("Container and backpack storage correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
