# Slimefun Doctor Command Reference

The **Slimefun Doctor** is Slimefun Legacy's operator/admin diagnostics, repair and migration toolkit. The in-game
**Slimefun Recovery Center** exposes the safest common workflows, while this page is the complete command reference.

Most commands on this page require:

`slimefun.command.doctor`

That permission defaults to operators. The related `/sf tick ...` performance commands use
`slimefun.command.tick`, which also defaults to operators.

> **Rule of thumb:** run a read-only status/scan first, review the output, make a current backup before any repair or
> migration, and use only the exact confirmation or fingerprint Doctor prints for that run.

## Start here

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor status` | No | Shows Doctor state, previous clean shutdown, pending database writes, backpack save state, machine isolation, integration failures and the current/last server-wide run. |
| `/sf doctor report` | No | Prints the public-safe support summary for issue reports. `/sf doctor support` is an alias. |
| `/sf doctor scan` | No | Runs the batched server-wide dry run across reachable inventories, loaded storage/machines, dropped/nested items and backpacks. Includes a bounded, read-only historical identity comparison against current registered item templates. Ends with **Doctor Next Steps**. |
| `/sf doctor repair confirm` | **Yes** | Applies the conservative server-wide item presentation repair after a reviewed scan and backup. It does not replace the specialist migration/resource-pack lanes below. |

Recommended general sequence:

1. `/sf doctor status`
2. `/sf doctor report`
3. `/sf doctor scan`
4. Follow the **Doctor Next Steps** command for the specific finding.
5. Re-run `/sf doctor scan` after any repair or migration.

### Historical item stacking and identity triage

The scan now reports how many registered Slimefun stacks it encountered, how many it could compare with the
current registered template, how many matched, and how many were **presentation-only diagnostic candidates**.
It separately counts other metadata differences, unavailable templates and checks deferred beyond a 20,000-stack
safety budget. A short list of candidate item **IDs only** is displayed, not private PDC, player or location data.

These are **not** registry-wide actual producer PASS results: intentional addon-specific lore, saved ownership or
machine state may differ from the template. A presentation-only candidate **is not eligible for automatic repair**.
For an actual historical item and newly generated item, hold the older item in the main hand, the new one in the
offhand, and run `/sf doctor identity compare` to inspect the real pair (including capacity). Do not infer
nonstacking from a full 64/64 stack. No historical stacking repair is performed by `/sf doctor scan`, `/sf doctor
repair confirm` or the general CJK presentation-repair path.

#### Experimental opt-in held-stack historical presentation repair (development candidate)

A separate, deliberately narrow command is available **only after this change has passed validation and been released**:

```text
/sf doctor identity compare
/sf doctor identity repair scan
/sf doctor identity repair confirm
/sf doctor identity repair rollback confirm
/sf doctor identity repair verify <backup-uuid>
/sf doctor identity repair rollback <backup-uuid> confirm
```

Keep the **old item in main hand** and a **current item matching the registered template in offhand**. Preview is read-only and expires in five minutes. Confirmation requires unchanged snapshots of both held stacks (including each separate amount and all metadata) and an unchanged registered template; it writes and verifies the exact original native item bytes under `plugins/Slimefun/recovery/identity/` before touching the main hand. Only the Adventure display name and lore are copied. The result must pass strict `ItemStack.isSimilar` against the new item while retaining the original quantity and all other data. The offhand is never modified.

The immediate in-session rollback is available for five minutes while still holding the unchanged repaired stack. Successful confirmation displays a private backup UUID. On an isolated backup clone after a clean restart, use `/sf doctor identity repair verify <backup-uuid>` to authenticate the original owner-scoped backup against the unchanged repaired main-hand stack and a fresh registered-template reference in the offhand. Verification is read-only. Only if it passes, `/sf doctor identity repair rollback <backup-uuid> confirm` explicitly restores the held item's original serialized bytes across restart; the backup remains on disk. These commands do not search inventories or recreate missing stacks. Keep the receipt and raw backup private. Make an offline backup first. Fail-closed cases include non-presentation metadata differences, intentional/dynamic addon lore, changed items between scan and confirm, mismatched IDs, model/profile/typed-PDC differences, loss during detachment, or any unsafe backup. This does **not** scan or repair backpacks, Fluffy Barrels, Cargo, offline inventories or placed storage; those require separate native conservation and restart evidence. It does not claim a repair for live #578 data until actual AlbionMC samples pass.

## Player and item repair

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor hand` | **Yes** | Repairs the safely recoverable presentation of the recognized Slimefun item in your main hand. |
| `/sf doctor inventory` | **Yes** | Repairs your online inventory and ender chest. |
| `/sf doctor inventory <player>` | **Yes** | Repairs an online player's inventory and ender chest. |

These commands preserve recognized functional Slimefun metadata and skip cases Doctor cannot prove are safe.

## Resource pack and item textures

These commands deliberately separate **resource-pack delivery**, **server item-model mappings**, and **stored ItemStack
texture metadata**.

| Command | Changes data? | What it does |
| --- | --- | --- |

| `/sf doctor resource-pack status` | No | Shows saved install/uninstall progress, sender ownership and backup location. |
| `/sf doctor resource-pack install scan` | No | Previews ID-pack installation and known numeric-model cleanup. |
| `/sf doctor resource-pack install confirm` | **Yes** | Backs up settings/items, resets matching Legacy mappings, pins the ID-pack preview and resumes cleanup after any required restart. |
| `/sf doctor resource-pack uninstall scan` | No | Previews sender removal and known numeric-model cleanup. |
| `/sf doctor resource-pack uninstall confirm` | **Yes** | Removes Legacy delivery and matching model overrides; preserves external ownership and custom models. |
| `/sf doctor resource-pack resume` | **Yes** | Retries only an already confirmed operation; offline players and vanilla containers finish on join/load. |
| `/sf doctor item-models status` | No | Summarizes item-model repair/adoption state and candidate counts. |
| `/sf doctor item-models scan` | No | Finds stale exact Legacy bundled model values on reachable stored Slimefun items. |
| `/sf doctor item-models repair confirm` | **Yes** | Repairs only eligible stale exact bundled model data on reachable stored items. Custom/non-matching model data is preserved. |
| `/sf doctor item-models enable-pack scan` | No | Audits an established server before adopting Legacy's bundled item-model mappings. |
| `/sf doctor item-models enable-pack confirm` | **Yes** | Adopts currently-zero bundled mappings and updates eligible reachable stored items. Custom non-zero mappings are preserved. |
| `/sf doctor item-models remove-resourcepack-texture-ids` | No | Audits exact Legacy bundled mappings that could be removed. |
| `/sf doctor item-models remove-resourcepack-texture-ids confirm` | **Yes** | Resets only mappings that still exactly match Legacy's bundled values. It **does not unregister Slimefun items or machines**. |

The older `rollback-v52` spelling remains a compatibility alias for the resource-pack mapping-removal command, but
`remove-resourcepack-texture-ids` is the preferred name.

After confirmed mapping removal, stop normally, restart, run `/sf doctor item-models scan`, review the results, and
only then use `/sf doctor item-models repair confirm` if stale stored item data remains.

## Core, registry, chunk and compatibility diagnostics

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor core` | No | Core readiness, lifecycle, scheduler, registry, machine, storage and chunk/runtime health. `lifecycle` is an alias. |
| `/sf doctor registry` | No | Registered/enabled items, item groups, researches, tickers and addon registry ownership. |
| `/sf doctor chunks` | No | World/chunk/block-data lifecycle, loaded records, unknown IDs and machine/chunk coordination. `worlds` and `blocks` are aliases. |
| `/sf doctor compatibility` | No | Summarizes known addon compatibility/runtime evidence. |
| `/sf doctor compatibility <plugin>` | No | Shows compatibility details for one plugin/addon. |
| `/sf doctor compatibility api <plugin>` | No | Shows cross-fork/API compatibility evidence for a plugin. |
| `/sf doctor dependencies` | No | Lists missing, disabled, aliased or otherwise unhealthy declared dependencies. |
| `/sf doctor dependencies <plugin>` | No | Dependency resolution details for one plugin. |

## Runtime and integrations

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor runtime` | No | Lists isolated/failing machines and circuit-breaker state. |
| `/sf doctor runtime retry` | Runtime state | Clears the isolation state for the machine you are looking at so it can retry normally. |
| `/sf doctor runtime retry all` | Runtime state | Clears machine isolation state globally. This does not rewrite machine storage. |
| `/sf doctor integrations` | No | Shows optional external integration capabilities and failures. |
| `/sf doctor integrations probe` | No | Probes the nearby supported external block/integration capabilities. |
| `/sf doctor integrations retry <id>` | Runtime state | Re-enables one isolated external integration callback/provider. |
| `/sf doctor integrations retry all` | Runtime state | Re-enables all isolated external integration callbacks/providers. |
| `/sf doctor integrations reload` | Runtime state | Reloads optional integration discovery/registration state. |

Use retry/reload only after reviewing the first exception that caused the isolation.

## Addon Doctor providers

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor addons status` | No | Lists registered addon Doctor providers and current availability. |
| `/sf doctor addons scan` | No | Runs registered addon Doctor providers in dry-run mode. |
| `/sf doctor addons repair confirm` | **Yes** | Requests each registered addon Doctor provider to perform its own guarded loaded-scope repair. |

Slimefun core does not guess addon-specific persistence formats. Addon Doctor providers own their own repair logic.

## Storage integrity

The storage integrity lane is intentionally stricter than ordinary Doctor repair.

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor storage status` | No | Shows the last scan, write boundary, two-pass confirmation state, preflight and last repair result. |
| `/sf doctor storage recovery [page]` | No | Reports current block, universal and backpack load holds plus pending universal migration identities. |
| `/sf doctor storage backpacks scan <backpack-uuid>` | No | Inspects one held backpack row-by-row, reports only unreadable stored rows and prints a whole-backpack SHA-256 fingerprint. It does not load, cache, rewrite or clear the backpack. |
| `/sf doctor storage backpacks quarantine <backpack-uuid> <full-fingerprint>` | **Yes — destructive** | Revalidates the exact backpack state, writes every unreadable raw row to a recovery ZIP, then deletes only those unreadable rows. Requires an existing failed-load hold and refuses cached/live or actively-saving backpacks. |
| `/sf doctor storage scan` | No | Starts a read-only backend ownership/integrity scan. |
| `/sf doctor storage plan [page]` | No | Shows the exact scope-qualified orphan-owner plan produced by confirmed scans. |
| `/sf doctor storage verify <full-fingerprint>` | No | Revalidates the exact SHA-256 plan and quiet-write boundary before repair. |
| `/sf doctor storage repair <full-fingerprint>` | **Yes — destructive** | Executes the currently verified exact storage repair plan. |

Do not shorten or reuse a storage repair fingerprint. If state changes, scan and verify again.

For a backpack that cannot load because Minecraft no longer recognizes a removed datapack/mod/plugin item component or enchantment, prefer restoring the missing dependency. If that is impossible and the owner explicitly accepts losing only the unreadable item rows, use the backpack scan/quarantine lane above. The quarantine command archives the exact removed payloads under `data-storage/Slimefun/recovery/backpacks/` before deletion. It intentionally leaves the incomplete-load hold in place; a later normal complete backpack load must succeed before the hold is released.

## Upgrade readiness

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor upgrade` | No | Shows the core/runtime upgrade-readiness snapshot. |
| `/sf doctor upgrade status` | No | Summarizes upgrade/migration readiness. |
| `/sf doctor upgrade scan` | No | Runs the read-only upgrade discovery traversal. |
| `/sf doctor upgrade plan` | No | Builds the combined read-only migration plan and routes each finding to its native authorization lane. |
| `/sf doctor upgrade providers` | No | Lists migration/schema providers relevant to an upgrade. |

The aggregate upgrade plan is **not** an execution token. Every mutation still requires its own native scan and
fingerprint/confirmation.

## Legacy item-ID migrations

Canonical route: `/sf doctor migrations ...` (`migrate` and `migration` are accepted route aliases).

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor migrations status` | No | Summarizes declared legacy ID mappings, registered targets and migration providers. |
| `/sf doctor migrations list [page]` | No | Lists declared old-ID → current-ID replacements. |
| `/sf doctor migrations unknown` | No | Correlates unknown IDs from the latest Doctor scan with declared mappings. |
| `/sf doctor migrations recovery [page]` | No | Groups observed legacy IDs by source addon, distinguishes exact declared counts from sampled unknown IDs, and suggests safe recovery steps without rewriting data. |
| `/sf doctor migrations plan` | No | Builds a dry-run plan from the latest Doctor scan. |
| `/sf doctor migrations providers` | No | Lists enabled legacy-ID migration providers and validation state. |
| `/sf doctor migrations scan <plugin>` | No | Runs one provider's read-only migration scan and, when clean, creates a short-lived fingerprint. |
| `/sf doctor migrations execute <plugin> <fingerprint>` | **Yes** | Executes only the prepared provider plan whose mappings and fingerprint still match. |

Prepared migration plans are short-lived and single-use.

## Same-ID schema and persisted-data migrations

These lanes exist because a Slimefun ID can stay the same while an addon's stored schema changes.

| Command family | Changes data? | Purpose |
| --- | --- | --- |
| `/sf doctor migrations schemas status` | No | Schema migration provider/status summary. |
| `/sf doctor migrations schemas scan` | No | Read-only same-ID item-schema scan. |
| `/sf doctor migrations schemas execute <plugin> <fingerprint>` | **Yes** | Executes one prepared same-ID schema migration. |
| `/sf doctor migrations blocks status` | No | Exact placed-machine migration-provider status. |
| `/sf doctor migrations blocks scan <plugin>` | No | Scans currently loaded supported addon-owned machines without force-loading chunks. |
| `/sf doctor migrations blocks execute <plugin> <fingerprint>` | **Yes** | Executes the exact prepared placed-machine plan. |
| `/sf doctor migrations schemas blocks status` | No | Persisted block/universal ID migration state. |
| `/sf doctor migrations schemas blocks scan` | No | Storage-level read-only persisted block-ID scan without loading chunks. |
| `/sf doctor migrations schemas blocks execute <fingerprint>` | **Yes** | Executes the prepared persisted block-ID plan. |
| `/sf doctor migrations schemas storage status` | No | Persisted item-payload migration state. |
| `/sf doctor migrations schemas storage scan` | No | Read-only persisted inventory payload/schema scan. |
| `/sf doctor migrations schemas storage execute <fingerprint>` | **Yes** | Executes the prepared persisted item-payload migration. |
| `/sf doctor migrations schemas storage ids status` | No | Persisted machine Item-ID migration state. |
| `/sf doctor migrations schemas storage ids scan` | No | Read-only persisted machine Item-ID audit. |
| `/sf doctor migrations schemas storage ids execute <fingerprint>` | **Yes** | Executes the exact prepared persisted machine Item-ID plan. |
| `/sf doctor migrations schemas storage backpacks status` | No | Persisted backpack Item-ID migration state. |
| `/sf doctor migrations schemas storage backpacks scan` | No | Read-only backpack Item-ID audit; live/cached backpacks are deferred. |
| `/sf doctor migrations schemas storage backpacks execute <fingerprint>` | **Yes** | Executes the exact prepared persisted backpack Item-ID plan. |

Always use the execute command Doctor itself prints after the corresponding scan. Provider IDs and fingerprint
positions vary by migration lane; do not guess them.

## Proxy and player identity

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor proxy` | No | Checks Paper/backend forwarding evidence for Velocity modern, Bungee-compatible forwarding, conflicts and unsafe offline-backend configuration. |
| `/sf doctor proxy player <name>` | No | Compares the online player's Bukkit UUID with the Slimefun profile owner, research count and backpack count. |
| `/sf doctor proxy player <name> <research-key>` | No | Adds the exact research locked/unlocked state to the identity report. |

Doctor never prints the Velocity forwarding secret.

## SmartSpawner replacement

This one-time compatibility lane replaces registered SmartSpawner blocks with InfinityExpansion2 Mob Simulation
Chambers while SmartSpawner is still installed.

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor smartspawners status` | No | Verifies SmartSpawner's public API and the IE2 Mob Simulation Chamber target, then summarizes registered spawners. |
| `/sf doctor smartspawners scan` | No | Reads all SmartSpawner records without force-loading chunks, resolves matching Mob Data Cards, and creates a short-lived fingerprint. |
| `/sf doctor smartspawners replace <fingerprint>` | **Yes** | Revalidates each exact SmartSpawner record, removes it through SmartSpawner's public API, and installs the corresponding Mob Simulation Chamber. |

Matching mob types receive their registered `IE_MOB_DATA_CARD_*` card. Unsupported mobs and SmartSpawner item-spawners
become **empty Mob Simulation Chambers**. The scan lists **every mob/item type in alphabetical order**, including its
block count and the exact card ID. Missing cards show `not registered`; cards registered but disabled in Slimefun show
`disabled`. An IE2 card disabled in its own mob-simulation configuration may never register, so `not registered` can
also mean that the installed addon configuration skipped the card. The report includes the installed card provider
name/version to help identify an older addon build. Install or enable the needed cards, restart, and run a **fresh
scan** before replacing spawners; an existing plan retains its original card choices.

If the block immediately above a converted chamber is air, Doctor places a
tagged sign with these four lines:

```text
Spawner Migrated
Replaced with a
Mob Simulation
Chamber
```

The scan reports stacked SmartSpawner blocks, and the migration manifest records each original stack size. One
SmartSpawner block becomes one chamber; stack size is not silently turned into hidden chamber throughput.

Before replacement, collect any loot and XP currently stored inside SmartSpawner and make an offline backup.
SmartSpawner's public API exposes the registered spawner identity/type/location/stack size but not the current
stored-loot/XP balances, so Doctor does not pretend it can migrate those balances. Keep SmartSpawner installed until
the follow-up scan reports no remaining records.

#### Server-wide blank-lore historical-item tally (v4.1.82 candidate)

`/sf doctor scan` continues to inspect only a **bounded number of loaded,
reachable** registered stacks. It now counts **blank-lore layout variants**
separately from fully matching items, strict styling-only differences, other
metadata differences, and unavailable registered templates. This category
requires a matching item material/plain name and nonblank lore text (in the
same order), with exact non-presentation **native bytes** after amount
normalization. It shows bounded Slimefun **item-ID samples only**, not
private item text, PDC, player names, coordinates, or owner metadata.
The scan does not force-load chunks or certify offline chests and is a
**read-only triage**, NEVER an automatic bulk migration or permission to
change shop definitions. If variants are found, compare a genuine old
shop/chest item and a fresh reference in a private staging clone and
capture the original native bytes using the command below.

#### Private old-versus-new evidence export (v4.1.82 candidate)

To investigate **real, old chest/shop item bytes** instead of relying on synthetic
fixtures, an operator on a **disposable full-backup clone** can hold a fresh
`/sf cheat` item in the main hand and the corresponding old chest/shop item
in the offhand, verify both resolve to the same registered Slimefun ID, and run:

```text
/sf doctor identity compare
/sf doctor identity export
```

Export is **operator-only, explicit and non-destructive**: it serializes both
actual held stacks into two new, exact-native-byte backup files under
`plugins/Slimefun/recovery/identity/` and rereads each file to verify its
contents. Receipts identify the two filenames using the executing owner's
UUID and the two independent UUID receipts. It does **not** export automatically,
send data online, change hands or inventories, update shops, or rewrite worlds.
Existing local backup files are never overwritten; symlinked directories fail.
The `.bin` contents can contain personal/owner IDs and typed PDC; keep them
**private**, never post them to a public GitHub issue, or include them in a
public commit. Contact project maintainers privately for old-world acceptance
if needed. The original old item should remain saved in an untouched full
offline backup even after evidence capture.

#### Live player-inventory compatibility batches (v4.1.82 candidate)

The initial **live update** mechanism operates on a currently online operator's
36 player **storage** slots. It does **not** silently rewrite chests, machines,
backpacks, QuickShop AdminShop sale-item snapshots, offline players, or dropped
items. Future chest/shop support requires separate tests of original saved
world/shop records and their transaction/restart behavior.

This path is **manual and disabled unless an operator explicitly confirms each
batch**. On a full-backup staging server:

```text
/sf doctor identity live scan
/sf doctor identity live confirm
/sf doctor identity live cancel
```

The companion operator command `/sf doctor identity live export-shop <slot>`
uses the **same loaded single QuickShop chest and the same nonmutating
Hikari public API** to capture private, byte-verified, owner-scoped native
receipts for the actual in-memory sale-item reference and current registered
Slimefun template, plus the selected physical stock item and its reference
when nonempty. An unlimited AdminShop with empty chest stock still provides
its sale/template pair; no item is assumed to have been sold from a chest.
The report prints only the private receipt UUIDs, no item PDC, private names
or owner/price. It independently rechecks the sale item, chest slot, owner,
price and registered template *after* disk I/O; racing items cause refusal
while existing original evidence files remain recoverable. The command
does not write shops, databases, chests or inventories, and does not read
or certify QuickShop's durable SQL row or perform a transaction. Original
`.bin` receipts can contain private owner/PDC and must stay off public
issues; use a full-backup staging clone. Evidence is not repair authorization.

A further read-only operator command, `/sf doctor identity live inspect-shop
<slot>`, looks at **a loaded, single physical chest** within six blocks and
compares its **runtime QuickShop-Hikari sale-item reference**, the selected
physical stock slot (0–26), and today's registered Slimefun template. It
uses only QuickShop's public `QuickShopAPI` shop lookup, `Shop.getItem()`,
and configured `Shop.matches(ItemStack)` methods, loaded as an optional
dependency. It reports redacted independent sale-reference and stock
difference classes, native sale/stock parity, and the actual matcher verdict
for both a fresh template and stock (on detached copies). A matcher accepting
an item **never overrides** typed-PDC/Slimefun-ID safety classification and
never creates an update plan. It checks the original shop-item bytes, stock
bytes, registered template, shop owner identity and price again before
reporting. Different IDs, unstable serialization, concurrent changes, missing
or incompatible QuickShop, and double chests are refused. No price/owner,
shop DB, inventory, or transaction mutation is performed; neither native
stock nor private owner/PDC bytes are printed. The API's live sale-item
reference is not by itself evidence that the persisted SQL row is identical.
The command requires integration verification on the actual installed
QuickShop-Hikari build and a staging restart before interpreting live results.

The two shop commands additionally inspect QuickShop-Hikari's public
`ShopMeta.itemSerializeString()`, a separately serialized **internal
in-memory item cache**. This cached Bukkit-YAML item is parsed with bounded
size, checked against the registered Slimefun material/ID, and classified
independently of the potentially event-modified `getItem()` view. Doctor
reports whether cache, runtime view and physical stock agree while ignoring
stack quantity; it refuses unexpected YAML fields, missing items, unknown
identities and changes in the cached string during inspection/export. Private
`export-shop` also produces an **additional cached-item/current-template
receipt pair** (two extra receipt UUIDs), preserving the source's typed
metadata separately from the runtime view. These are read-only observations;
QuickShop's cached in-memory YAML **must not be described as a verified SQL
row**. Compare the durable original QuickShop DB backup independently after a
staging restart, before even considering shop migration.

A separate `/sf doctor identity live scan-container` command lets an
operator **look at a loaded physical chest/barrel/container within six blocks**
and inspect up to 54 slots **without modifying the container or generating
any update plan**. It also verifies an exact native-byte, bounded, complete
inventory preimage twice, including empty slots, to refuse a changing chest.
Native item bytes and the private snapshot hash are never printed. It dispatches the inspection to the container's owning
region on Folia and returns redacted counts plus the **first 12 zero-based
slots with a registered item/template mismatch** (no private item lore/PDC
values) through the player's scheduler. These are evidence-export hints, not
repair approvals; unknown IDs and malformed items can be refused without
appearing in that slot list. The chest may change after inspection, so the
export command independently validates the selected slot again. This does not update any QuickShop sale
snapshot, even when the target chest is attached to a shop. A later
`live confirm` can only act on the operator's own player inventory, never
on the scanned chest.

The operator can inspect a reported zero-based slot without saving or changing
anything using `/sf doctor identity live inspect-container <slot>`. It reads
only that slot and the current registered template, on the container's owning
region; the redacted category is `CURRENT`, `STYLE_ONLY_CANDIDATE`,
`BLANK_LORE_LAYOUT`, `MEANINGFUL_TEXT`, `FUNCTIONAL_METADATA`,
`UNKNOWN_ID`, `NOT_SLIMEFUN`, `EMPTY`, or `UNSAFE`. This is diagnostic
evidence, **not permission to repair**. `UNSAFE` also includes serialization
errors, oversized native items, or a slot/template change during comparison.
No lore, PDC values, owner data, native bytes, or recovery files are exposed
by this inspection. On all server types, the read-only scan, targeted inspection
and evidence export **refuse any double chest at a chunk boundary**, rather than
risk accessing the other half from a different Folia region. The guard is
intentionally conservative and may also refuse a same-chunk double chest
touching an edge. Single chests and barrels on chunk boundaries are unaffected;
move neither chest nor shop stock simply to bypass this refusal.

After a chest export, `/sf doctor identity live verify-evidence
<old-receipt-uuid> <template-receipt-uuid>` reads the two already-saved
operator-owned exact-native receipts again, including **after a server
restart**. It reports only redacted differences between the historical
source and the saved reference, whether the saved reference still matches
today's registered item, and whether a strict style-only candidate would
be possible against that unchanged registration. It does not modify the
receipts, current inventory, original chest, QuickShop, or any live item,
and cannot read another operator's receipts. A missing addon or changed
current template is reported as nonmatching, never migrated or assumed
empty. This is a test aid: a candidate classification is **not approval**
to enable chest or shop migration. Keep both receipt UUIDs and their
private original files together in the staging server's Doctor recovery
folder.

The separate operator command `/sf doctor identity live export-container <slot>`
captures **one 0-based slot (0–53)** from a loaded physical chest/barrel that
the operator looks at. The selected item must have a registered Slimefun ID
and an actual mismatch with its current registered template. Both before and
after writing private evidence, the full physical container snapshot
(including unrelated slots) must be identical; any hopper/Cargo/plugin
mutation causes a fail-closed refusal. The complete snapshot is bounded to
2 MiB of exact native item data and never permits a chest repair. It writes two
verified exact-byte **private** receipts into
`plugins/Slimefun/recovery/identity/`: one for the original stored stack
(including its amount and opaque metadata), and one for the current registered
template. It refuses unknown or mismatched IDs, empty/out-of-range slots,
item data over 256 KiB and unsupported serialization. Inspection and capture
run on the container-owning region under Folia. It leaves the chest item,
registered template, QuickShop sale-item snapshot and transaction data
unchanged. Receipts can contain owner IDs/private PDC: keep them off public
GitHub issues and only use them with a full-backup staging world.

The scan is read-only, bounded to 36 storage slots, and reports the count of
registered mismatches, strictly safe **style-only** candidates and refused
items. Both player and container previews also provide **redacted diagnostic
counts** for mismatches attributable to blank lore-line layout, meaningful
name/lore text differences, changed non-presentation/typed data, and
unrecognized registered IDs. These are investigative categories only, not
proof that an item is safe to rewrite; malformed/oversized items may remain
refused without any further classification. A scan can prepare up to **eight**
item stacks. It refuses serialized
stacks over **256 KiB**, and confirmation refuses batches requiring over
**512 KiB** of original-byte backup writes, to protect the live server thread.
Confirmation consumes
an exact-slot, exact-registered-template, exact-item-fingerprint plan expiring
after **60 seconds**. Every original native stack must first be backed up to
`plugins/Slimefun/recovery/identity/` and independently reloaded byte-for-byte
*before any slot is changed*. It checks that all original slots are still
identical after disk I/O and checks each replacement after writing. A write
failure attempts rollback only if the slot still contains the expected
replacement; the original private receipts remain available for recovery.

A candidate must retain the exact registered Slimefun ID, Minecraft material,
all non-presentation native bytes, typed PDC/owner values, model data and
quantity; the **plain name and all plain lore text must already match**.
Only safe Adventure formatting/component differences can be normalized.
Different meaningful text, unrecognized item IDs, changed typed data, and
`CARBONADO` blank-line variants are **not** eligible for this bulk path.
Specialized blank-line recovery remains single-held-stack only.

**Do not** enable server-wide automatic item updates, transfer an unverified
item between shops, or change QuickShop's default matcher as a workaround.
The existence of a candidate is not proof that historical customizations are
unimportant; capture real old/current examples first, and verify exact backups
and restart on a separate clone before production use. This is a bounded
live migration building block, not yet the complete old-world fix tracked by
[issue #615](https://github.com/wickidcow/Slimefun-Legacy/issues/615).

#### Operator-only historical Carbonado lore recovery (v4.1.82 candidate)

Some saved shop/chest copies of `CARBONADO` have the registered Slimefun ID, model,
and non-presentation native data of the current core item but carry a different
**plain-text lore**. A previous/current template change is not proof every old
item is safe to rewrite. `/sf doctor identity compare` now reports **plain
display name versus plain lore** differences separately, plus the number of
lore lines, without exposing their text.

On a **full-backup staging server**, an operator may test one held `CARBONADO`
stack after identifying the original as an older static material (not a
custom player-renamed object). Hold **old in main hand** and a current
`/sf cheat` `CARBONADO` in offhand:

```text
/sf doctor identity compare
/sf doctor identity repair scan static-lore
/sf doctor identity repair confirm static-lore
```

This **separate explicit opt-in** is permitted only when the plain display name
matches and **every nonblank plain-text lore line remains identical and in the
same order**: differences can only be completely blank/whitespace lore lines.
Any genuinely different text (including custom lore) is rejected. The Slimefun
ID must be `CARBONADO` in both stacks and the registered template, and all
other native item components must match byte-for-byte after removing
display/lore and normalizing the count. The
offhand must match the registered template exactly. This mode is **OP-only**;
the preview/confirm are bound to exact fingerprints, expire after five minutes,
and require the extra `static-lore` confirmation argument. Confirmation
writes and rereads an exact native-item backup **before** replacing the held
stack's name/lore; quantities and all typed data must survive. Save the private
receipt and test native restart verification/rollback as documented above.

**Warning:** Although meaningful nonblank lore text is never changed by
this specialized recovery, intentionally customized blank lines and text
styling can be replaced. It cannot automatically determine every old item's
historical meaning and is not an approval for player custom lore or other
Slimefun IDs. This command **does not** scan, migrate, or modify any chest,
QuickShop sale-item snapshot, QuickShop price, stored stock, machine, Fluffy
Barrel or other inventory. No server-wide/automatic migration is enabled.
A current QuickShop AdminShop can retain an old persisted sale-item snapshot
independent of its chest: update paths for those shops must be separately
validated on a backup clone with their actual QuickShop build before use.
See [issue #615](https://github.com/wickidcow/Slimefun-Legacy/issues/615).

## InfinityExpansion2 convenience bridge

When InfinityExpansion2 is installed, Slimefun Legacy can route to its Doctor migration bridge:

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor ie2 status` | No | Shows IE1 → IE2 migration status. |
| `/sf doctor ie2 scan` | No | Runs the IE2 migration dry run. |
| `/sf doctor ie2 migrate` | Addon-owned repair | Runs the installed IE2 migration path. |
| `/sf doctor ie2 refresh` | Runtime/provider state | Refreshes the IE2 bridge/provider state. |

The installed addon remains authoritative for its migration engine.

## Related performance commands

These are not `/sf doctor` commands. They use `slimefun.command.tick`.

| Command | What it does |
| --- | --- |
| `/sf tick query` | Full Slimefun ticker state. |
| `/sf tick top` | Collects a live profiler sample and ranks current ticker cost. |
| `/sf tick at` | Inspects the Slimefun ticker/machine at the block you are looking at. |
| `/sf tick frozen` | Lists targeted pauses/frozen ticker state. |
| `/sf tick rate` | Shows/works with the current Slimefun ticker rate controls. |

The Recovery Center's **Performance Health** screen links to these existing commands rather than maintaining a second
profiler.

## Safe operating pattern

For a problem you do not yet understand:

```text
/sf doctor status
/sf doctor report
/sf doctor scan
```

Then follow the specific **Doctor Next Steps** output. For performance problems, add:

```text
/sf tick top
```

For any command containing `confirm`, `execute`, `repair <fingerprint>`, or an addon migration action, make a
current backup first and use the exact command from the immediately preceding scan/plan.

## Related pages

- [Doctor & Diagnostics](Doctor-and-Diagnostics.md)
- [Commands & Permissions](Commands-and-Permissions.md)
- [Server Owner Guide](Server-Owner-Guide.md)
- [Troubleshooting](Troubleshooting.md)
