# Doctor v82 — registry-wide /sf cheat vs /sf give item-copy identity

## Why this matters

The public `/sf cheat` indexed guide's click handler gives
`slimefunItem.getItem().clone()`. The separate `/sf give` command constructs
`new CustomItemStack(slimefunItem.getItem(), amount)`. These are not identical
source paths: `SlimefunItemStack.clone()` invokes its constructor, which
reapplies Slimefun item data and textures. If a deprecated API replacement
changes text components or metadata in one path but not the other, freshly
generated items could stop stacking without their Slimefun item ID changing.

## Native, read-only acceptance

The existing registered-clone audit inside the real Paper resource-pack Doctor
fixture now iterates **every item in the loaded Slimefun registry** in stable
registered-ID order, including any enabled addons, and performs two source
paths directly:

1. `getItem().clone()`, exactly as the cheat indexed guide click handler.
2. `new CustomItemStack(getItem(), 1)`, exactly as the give command factory.

On detached native byte snapshots normalized to quantity one, compare two-way
Bukkit `isSimilar`, exact native bytes, item count, and the original registered
template's immutability. For items whose effective max stack size is at least
two, add one detached item from each path to an empty 9-slot inventory and
require one combined stack of two. For legitimate max-stack-size-one equipment,
require identity without falsely expecting a physical merge.

Keep the existing first/second clone byte-exact checks. Emit a redacted
`identity-registry-clone.json` schema 2 report containing public item IDs,
addon names, PASS/FAIL and categorical flags only. No player item changes,
private PDC, display text, SQL, coordinates or serialized item bytes appear in
public CI artifacts. The native fixture fails closed and prints public item IDs
when a copy path is incompatible.

## Boundaries

This reproduces both **factory/copy implementations**, not the complete
interactive player /sf cheat click or give command, and does not prove genuine
old saved-world item identity, crafting handlers, every machine output, Cargo,
FluffyMachines barrels, QuickShop persistence or cross-version migrations.
Those require their own source-specific native and restart fixtures.

If raw serialized NBT ordering differs but the typed Bukkit items remain
identical and actually stack, retain a diagnostic `give_cheat_native_exact:
false` and do **not** rewrite storage. If Bukkit similarity/merge fails,
prevent release of the changed source until its real producer path is
investigated, and only migrate old-world data after verified backups and
source-preserving tests.

Every test uses a disposable inventory and retains the registered template
byte-for-byte. No new in-game repair command or automatic item rewrite.
