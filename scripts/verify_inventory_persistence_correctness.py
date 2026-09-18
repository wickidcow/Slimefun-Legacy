#!/usr/bin/env python3
"""Verify acknowledgement-aware block and universal inventory persistence."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Inventory persistence correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Inventory persistence correctness failed: missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Inventory persistence correctness failed: forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Inventory persistence correctness failed: ordering violation for {label}: "
            f"expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    menu = compact(
        read(root, "src/main/java/me/mrCookieSlime/Slimefun/api/inventory/DirtyChestMenu.java")
    )
    require(menu, "private long changeSequence = 1", "monotonic menu change sequence")
    require(menu, "private long acknowledgedChangeSequence", "menu acknowledgement sequence")
    require(menu, "public synchronized long captureChangeSequence()", "captured menu save token")
    require(menu, "public synchronized void acknowledgeChanges(long persistedSequence)", "menu persistence acknowledgement")
    require(menu, "changes = (int) Math.max(0L, (long) changes - newlyAcknowledged)", "later mutations remain dirty")

    block_menu = compact(
        read(root, "src/main/java/me/mrCookieSlime/Slimefun/api/inventory/BlockMenu.java")
    )
    require(block_menu, "saveBlockInventory(blockData)", "BlockMenu controller persistence handoff")
    forbid(block_menu, "changes = 0", "pre-persistence BlockMenu clean acknowledgement")

    controller = compact(
        read(root, "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ADataController.java")
    )
    require(
        controller,
        "protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(",
        "completion-returning scoped write submission",
    )
    require(
        controller,
        "return queuedTask.getCompletionFuture()",
        "existing queue completion returned while submission lock is held",
    )
    require_before(
        controller,
        "CompletableFuture<Void> completion = queuedTask.getCompletionFuture()",
        "writeExecutor.submit(queuedTask)",
        "new queue completion captured before executor submission",
    )

    storage = compact(
        read(root, "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataController.java")
    )
    require(storage, "private final Map<String, CompletableFuture<Void>> inventorySaveChains", "per-inventory save chains")
    require(storage, "private final Set<String> uncertainInventoryBaselines", "failed inventory baseline marker")
    require(storage, "public CompletableFuture<Void> saveBlockInventoryAsync", "async block inventory save")
    require(storage, "public CompletableFuture<Void> saveUniversalInventoryAsync", "async universal inventory save")
    require(storage, "ItemStack[] contents = copyInventoryContents(", "immutable inventory content copy")
    require_before(
        storage,
        "long changeSequence = menu == null ? 0L : menu.captureChangeSequence()",
        "ItemStack[] contents = copyInventoryContents(",
        "dirty token captured before inventory staging",
    )
    require(
        storage,
        "Map<Integer, InventoryWrite> stagedWrites = stageInventoryWrites(",
        "caller-thread inventory serialization staging",
    )
    require(
        storage,
        "data.put(FieldKey.INVENTORY_ITEM, item)",
        "ItemStack serialization before async save chain",
    )
    require(
        storage,
        "CompletableFuture<Void> next = start.thenCompose(ignored -> saveAttempt.get())",
        "serialized per-inventory save attempts",
    )
    require(storage, "InvSnapshot acknowledged = invSnapshots.get(snapshotKey)", "last acknowledged snapshot baseline")
    require(
        storage,
        "scheduleDeleteTaskWithCompletion(scopeKey, write.key(), true)",
        "delete write completion tracking",
    )
    require(
        storage,
        "scheduleWriteTaskWithCompletion(scopeKey, write.key(), write.data(), true)",
        "set write completion tracking",
    )
    require(
        storage,
        "CompletableFuture<Void> batch = CompletableFuture.allOf(",
        "exact inventory write batch barrier",
    )
    require(
        storage,
        "if (failure == null) { acknowledgeInventoryStage(",
        "inventory acknowledgement only on successful batch",
    )
    require(
        storage,
        "uncertainInventoryBaselines.contains(snapshotKey) ? new HashSet<>(stagedWrites.keySet())",
        "failed inventory baseline forces every staged slot to reconcile",
    )
    require(storage, "int size = 54", "full legal chest inventory staging range")
    require(
        storage,
        "contents == null || slot >= contents.length ? null : contents[slot]",
        "higher slots staged as explicit deletes after menu shrink",
    )
    require(
        storage,
        "if (slot < 0 || slot >= inv.length)",
        "stored inventory slot bounds guards",
    )
    require(
        storage,
        "uncertainInventoryBaselines.add(blockData.getKey())",
        "block load corruption forces reconciliation",
    )
    require(
        storage,
        "uncertainInventoryBaselines.add(uniData.getKey())",
        "universal load corruption forces reconciliation",
    )
    require(
        storage,
        "invSnapshots.remove(snapshotKey)",
        "failed inventory batch invalidates acknowledged snapshot",
    )
    require(
        storage,
        "uncertainInventoryBaselines.add(snapshotKey)",
        "failed inventory batch marks persisted baseline uncertain",
    )
    require(
        storage,
        "uncertainInventoryBaselines.remove(snapshotKey)",
        "successful inventory batch clears uncertain baseline",
    )
    require(
        storage,
        "menu.acknowledgeChanges(changeSequence)",
        "menu dirty token acknowledged after persistence",
    )
    require_before(
        storage,
        "awaitInventorySaveChains()",
        "super.shutdown()",
        "inventory save chains drain before controller shutdown gate",
    )
    forbid(
        storage,
        "lastSave = invSnapshots.put(",
        "snapshot advancement before database completion",
    )
    forbid(
        storage,
        "changed.forEach(slot -> scheduleDelayedBlockInvUpdate",
        "mutable delayed block-inventory save handoff",
    )
    forbid(
        storage,
        "changed.forEach(slot -> scheduleDelayedUniversalInvUpdate",
        "mutable delayed universal-inventory save handoff",
    )

    print("Inventory persistence correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
