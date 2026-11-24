# Doctor v82 — operator-only live batch recovery after server restart

## Problem

The existing /sf doctor identity live scan/confirm writes up to eight strictly
style-only repaired stacks in the operator's 36 player storage slots. Before
writing, it retains exact old native bytes in private owner-scoped backups under
plugins/Slimefun/recovery/identity. Those bytes survive server restarts, but
there was no equivalent per-slot operator command to restore an old styling
after a restart. The held-item single-stack repair has a separate rollback
workflow; it does not cover the batch player-inventory slots.

## New opt-in operator commands

1. After an existing live batch update, retain each original backup UUID and
   its zero-based player storage slot, and independently back up the server.
2. With that updated item still in the *same* player storage slot:
   /sf doctor identity live rollback-preview <original-receipt> <slot>
3. With no changes to any ordinary storage, armor or offhand slot, within 60 seconds:
   /sf doctor identity live rollback-confirm
4. The completed restore reports a **second independently verified private
   receipt containing the *updated* stack**, which must also be kept. The
   original receipt remains unchanged; nothing is deleted automatically.

The preview and commit are operator-only, player-owned, region-thread
synchronous and never run when opening inventories or loading chunks.

## Required safety evidence

- The old receipt must pass owner-scoped verified SHA-256 original-byte readback,
  no symlinks, and strict byte limits.
- The current item and registered template must match the historical original's
  exact Slimefun ID and material, count, opaque native typed data and protected
  metadata. Doctor's existing strict style-only predecessor check must pass;
  meaningful lore, amount, custom names, unknown IDs and changed functional
  PDC fields are refused.
- Bind the preview to the entire player inventory, including armor and
  offhand, by a complete exact native-byte snapshot and checksum. An unrelated
  storage or equipment slot change invalidates the preview rather than being repaired.
- Before the slot write, reopen the original receipt and store a *second* durable,
  readback-verified native backup of the current updated stack. Repeat the
  full-storage checksum check after disk I/O.
- After the write, compare the exact original bytes and all unrelated storage
  slots. If an error occurs, revert the selected slot to the repaired version
  **only** if the observed slot still exactly contains the old bytes written
  by this operation. Never overwrite a foreign item inserted concurrently;
  both receipts are left on disk for manual recovery.
- Explicitly refuse any inventory whose ordinary storage area is not exactly
  36 slots. Snapshot the complete player inventory (up to 54 slots, including
  equipment), but allow writes only to ordinary positions 0-35. No physical-
  container snapshot APIs are used.

## Non-goals and release gate

This does **not** touch chest, barrel, shop, Cargo, machine, QuickShop SQL,
offline player, armor, offhand, Hopper, item template data or other player
inventories. It does not authorize broad native migrations or non-presentation
changes. It does not replace the server's whole-world backup.

Only merge to Doctor #616 after formatting, MockBukkit/real-Paper correctness,
compatibility, and mandatory exact-source addon bundle checks pass. Keep
the parent draft until its real-server restart and native-byte conservation
tests and safe release validation pass.
