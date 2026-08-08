# Slimefun Legacy 4.1.18 — Guide & Runtime Stability

## Guide stability

- Guards classic and enhanced guide entry points, nested item groups, history restoration, search, bookmarks, pagination, item clicks, and addon `FlexItemGroup` menus.
- Blocks recursive calls and isolates addon runtime/linkage failures.
- Uses safe fallback icons and names for broken addon categories.
- Reports slow guide calls with player, mode, category key, category class, addon owner, nesting depth, and active call chain.
- Counts failures, recursion blocks, slow calls, fallbacks, and suppressed duplicate warnings, with periodic runtime summaries.

## Item Doctor stability

- A malformed stack can no longer terminate the complete scan or repair run.
- Runtime and addon linkage failures are counted, logged with safe item context, skipped, and scanning continues.
- Nested container failures are isolated the same way.
- Limited-use items without stored-use data or readable old lore fall back to their registered maximum instead of failing dynamic-state capture.
- Unknown IDs remain report-only and are never guessed or replaced.

## Machine reliability

- Auto Enchanter and Auto Disenchanter keep inputs untouched when another plugin cancels an event or a compatibility operation fails.
- Input stacks are validated before one item is consumed from each slot.
- Output capacity is checked before committing inputs.
- Processing time is never allowed to become zero ticks.
- The Auto Enchanter validates the final enchantment count, not only the incoming book.
- The Auto Disenchanter verifies every vanilla enchantment was removed and stored before accepting the operation.
- Visible status icons explain missing inputs, incompatible enchantments, full outputs, event cancellation, or blocked integration failures.
- The existing optional AdvancedEnchantments bridge remains supported without a hard dependency.

## Compatibility

- Primary: Paper 26.2 / Minecraft 1.21.11
- Secondary: Purpur based on Paper 26.2
- Runtime: Java 25
- Slimefun-owned bytecode target: Java 21
- Folia: experimental under the existing Phase 1 limitations

No item IDs, storage schemas, block data, backpack formats, or database formats are changed by this release.
