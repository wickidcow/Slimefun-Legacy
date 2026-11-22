# Existing-item compatibility is a release requirement

The server support floor is Minecraft 1.21.11. It is **not** an age limit on items, backpacks, machine records, or inventory data. Items created by older Slimefun and maintained-addon versions must continue to be recognized and usable in updated Legacy builds when the corresponding item/addon is available. Historical readers are compatibility code, not disposable pre-1.21.11 server support.

## Preserved behavior and identity

Keep existing Slimefun and research identifiers, NamespacedKeys and typed PDC values, exact stack counts, item charge, soulbound flags, durability, enchantments, owner/backpack UUIDs, custom names/lore, recipe ingredients/outputs and production behavior. Keep machine identities, stored custom fields, inventories, Networks/quantum storage and unknown-addon payloads. Do not replace existing player items with fresh templates as a modernization shortcut. Do not automatically regenerate identities or rewrite resource-pack/model mappings.

A missing addon or unreadable legacy item is not evidence that its stored record is empty. Unresolved identifiers and original payloads must remain available for recovery; any corrective migration needs explicit, bounded validation and must not silently discard data. The normal empty-slot contract remains distinct from serialization/deserialization failure.

## Two separate validation directions

1. **Older item/plugin data to updated Legacy:** this is the primary requirement. Retain existing legacy readers and add regression fixtures for the representations actually supported by Legacy.
2. **Legacy data to another fork:** pin the destination build and validate its readers and addon registrations separately. Gugu and United do not necessarily accept the same representations. Do not downgrade every live-world write to an older format in order to claim reverse compatibility.

Switching plugin implementations on the same Minecraft version is different from downgrading Minecraft itself. Neither same-format recognition nor a successful compile certifies an entire world-switch.

## Permanent constructor regression gates

`SlimefunItemStackLegacyTextTest` freezes representative full component trees
from the v4.1.81 constructor at `d728bf04ebd738dd5779e27b2ea83c68f0edbd10`.
It checks root decorations, nested inherited italics, reset/color boundaries,
mixed section-sign/ampersand input, literal invalid codes and separate empty
and whitespace-only lore lines. Expected trees are written independently of
the parser under test, so a serializer or deprecation refactor cannot silently
change them while retaining the same plain text and Slimefun ID.

These tests protect current constructor output; they do not establish that
v4.1.81 matches historical Bukkit output. In particular, the existing forced
root `italic=false` is a recorded compatibility baseline, not proof of the
right representation for every older item. An intentional baseline change
must include old/new native-byte evidence and the historical migration
acceptance below. Do not automatically regenerate expectations from changed
constructor code or relax item matching to make the tests pass.

## Native historical presentation regression for v4.1.82

The native `ItemIdentityProbe.verifyOldBukkitTextApiMatrix` checks **26 representative
core item IDs** from the actual Slimefun v4.1.55 static declarations. It
recreates only the old Bukkit display-name/lore *setter path* on detached
copies of current registered item templates, classifies the result against
the Adventure-based template, saves original bytes and (where strictly
eligible) exact-source repair receipts, and verifies native equality,
stack quantities and backup integrity after a separate JVM/server restart.
The matrix includes dusts, alloys, gold grades, circuits and historical
Carbonado resources. Gold grades, iron-based ingots and sugar-based dusts
also have negative checks ensuring different Slimefun IDs sharing a
Minecraft material **never stack together**.

The same native tests also compare two modern Adventure reconstructions
against the historical Bukkit String-setter result: the default parsed
component decorations, and the current explicitly disabled-italics root
decoration. Each test records only the redacted MATCH/DIFFERENT outcomes
and verifies they remain stable after a server restart. This investigates
whether a source-construction adjustment can prevent future mismatches
without changing global item matchers. Neither outcome alone authorizes
changing already-persisted item bytes.

This is a **source-derived synthetic compatibility matrix**, not an export
from an older AlbionMC chest or QuickShop shop database. It must never
be used to justify automatic replacement of custom lore, unknown
metadata, owned storage, or historical shop sale-item snapshots. Those
still require genuine old-world item bytes, full staging backups, typed-PDC
checks and restart/transaction/quantity-conservation acceptance. The
live inventory candidate remains strictly presentation-only, opt-in,
bounded and backed up; the chest/export commands are read-only.

The private container-export fixture uses a synthetic native 54-slot inventory;
its second boot reopens original item/reference receipt files, not a persisted
physical world chest. The receipt-audit gate invokes the production verifier on
both boots, requiring exact unchanged receipts and current registration, refusing
changed/missing registration and another operator's receipt. It covers style-only
and typed-functional differences without creating a repair. Physical container
persistence and QuickShop transactions remain separate acceptance requirements.

The native two-boot gate now also creates an actual **vanilla 27-slot physical
chest block** in the disposable test world and places synthetic historical
Carbonado, the modern template and a different typed-owner record in separate
slots. After normal shutdown and restart, it reopens the **same world chest**
and asserts byte-exact item persistence, slots, quantities (63/1/9), typed
owner metadata, registered-template immutability and saved private receipt
replay. The test uses the owning region scheduler on Folia. This validates
disposable native chest persistence, **not real old AlbionMC saved-world item
provenance**, QuickShop database snapshots, sale price, shop owner, or trades.
The separate `QuickShop-Hikari Public API Contract` GitHub Action pins
both the official QuickShop-Hikari **6.3.0.3** (seen in an older AlbionMC
startup log) and **6.3.0.4** JARs by their independent upstream published
SHA-256 digests, checks plugin.yml identities and uses `javap` to verify
every public API method signature and inherited interface used by the
optional reflective Doctor inspector. This catches upstream source-vs-distribution API drift
without adding a hard QuickShop dependency or trusting an unverified JAR.
The ABI workflow also stages that exact signed-and-hashed QuickShop JAR
with the current Slimefun candidate on a **new disposable Paper 26.2
server**, verifies its live Bukkit public service/API on **two JVM boots**,
and refuses an unexpected pre-existing shop at a test chest location. This
is actual plugin enablement and runtime service activation, not a created
shop, configured matcher, persisted sale record, real player trade or real
AlbionMC world evidence. The AlbionMC-installed QuickShop build still needs
confirmation.


The separate disposable native QuickShop-Hikari 6.3.0.4 two-boot
activation on **both SHA-256-pinned Paper 26.2 build 129 and
Paper 26.3 build 145** now calls the **real configured QuickShop
`ItemMatcher` API**,
not only an approximate `ItemStack.isSimilar` model. It evaluates
detached synthetic historical/current component variants, same-material
different Slimefun PDC IDs, and different typed owner PDC values; it
verifies source native bytes unchanged and requires the redacted matcher
verdicts and `matcher.work-type` to remain identical across both JVM boots.
The native test treats JavaPlugin.getConfig() returning null as an
unavailable optional configuration observation (work-type `-1`), **not**
as a failed matcher or license to assume QuickShop's default settings.
It still requires real matcher reflexivity, exact original native bytes,
and identical verdicts on both independent boots. This avoids a
fresh-bootstrap NPE observed on both Paper 26.2 and 26.3 without relaxing
item identity or source-validation gates.

The binary API gate also verifies `getItemMatcher()` and
`ItemMatcher.matches(ItemStack, ItemStack)` on official 6.3.0.3 and 6.3.0.4
releases. Any YES for different item IDs or functional owner metadata is
**evidence about the configured matcher, not permission for Doctor to
merge or rewrite data**. The actual AlbionMC shop, saved SQL row and active
matcher configuration remain untested in this synthetic fixture.

The production QuickShop read-only three-source **classifier** is now also
exercised on both native boots against that chest's actual persisted old,
current and typed-owner stock. The fixture models the default QuickShop
`ItemStack.isSimilar` matcher on detached Paper/Folia native stacks,
asserting stale shop-reference/old-stock vs stale-reference/current-stock,
typed-owner refusal, different Slimefun IDs sharing a material and a matcher
that mutates its input. It verifies source bytes unchanged on both boots.
This is **not a real QuickShop-Hikari plugin/database/shop transaction test**:
no QuickShop JAR or saved shop record is loaded by the synthetic model, and the
server's active matcher mode remains unknown. Real historical and QuickShop
acceptance remain required.

## QuickShop internal serialized-item evidence — 2026-11-20

Public QuickShop-Hikari 6.3.0.3 and 6.3.0.4 interfaces expose
`ShopMeta.itemSerializeString()`, the cached Bukkit YAML representation
of the shop's **internal in-memory item**. Unlike the possibly modified
`ShopItemEvent(RETRIEVE)` view from `getItem()`, the cache is produced by
`updateSerializeItem()` from the internal item field. This is a third
read-only source distinct from both the event-processed view and physical
chest stock, and **not an independently verified SQL database row**.

Doctor now parses the cached YAML under a 1 Mi-character budget, verifies
that it contains exactly one `item` root, native-roundtrips the decoded
item and requires matching Slimefun ID and material. It reports redacted
cache/reference, cache/view and cache/stock differences; a matching
configured QuickShop comparator never authorizes overwriting a mismatched
cache. `export-shop` saves a separate private operator-scoped pair of
native receipts for the cached item and current template, in addition to
the existing view/current and selected stock/current pairs. Pre/post
readback includes the original cached YAML string as well as owner,
price, sale view, stock and current registered template. No QuickShop
setter, DB query, matcher override, chest write, trade or automatic repair
is used.

The MockBukkit-only cached-YAML fixture conservatively reports
`FUNCTIONAL_METADATA` when its synthetic YAML codec changes non-presentation
item data, rather than converting the result into a false
`STYLE_ONLY_CANDIDATE` or `CURRENT`. Only real native Paper/Folia
round-trip tests can establish whether production cache decoding retains
all item metadata; a different outcome is never silently normalized.

The existing disposable two-JVM physical-world-chest probe additionally
calls the **production cached YAML decoder** on both boots, using synthetic
old/current/typed-owner `ItemStack` objects actually reloaded from the
same native chest. It asserts the cache's old/current/typed functional
classifications, the distinction between cached-internal and
event-processed runtime view, rejection of same-material different
Slimefun IDs, exact untouched serialized stock and receipt persistence.
This still does not instantiate a QuickShop shop or authenticate its
original SQL row.

The official 6.3.0.3/6.3.0.4 binary ABI contract explicitly verifies the
cached getter. The two-JVM official plugin activation test verifies its
public method exists, but does NOT create a real QuickShop shop or validate
an actual SQL row. Real old-world staging DB backups, restart and trade
checks remain required before any persisted shop update.

## Prospective QuickShop save projection — 2026-11-20

Official QuickShop-Hikari 6.3.0.3 and 6.3.0.4 expose
`Shop.saveToInfoStorage()`, a **read-only in-memory export projection**
used for persistence. In official 6.3.0.4, `ContainerShop` builds the
`ShopInfoStorage` DTO from its internal item, price and owner. Calling
this method does **not** persist to SQL, and **its output is not an
authenticated database row**. The projection's opaque encoded item may
differ from the cached YAML and the event-processed runtime sale view.

Doctor's operator-only `inspect-shop` / `export-shop` now inspect this
prospective encoding under a 1 Mi-character budget, require the
projected owner and exact price to agree with the live shop, then
reproduce the entire opaque encoded item, owner and price projection
after any private receipt I/O. It fails closed on any difference. Only
the redacted fact **STABLE, NOT SAVED SQL** appears in the operator
report. No encoded item string, owner, price or digest is logged,
sent to a public endpoint or added to a new backup file. Tests cover
changed serialization, changed owner, fractional price differences,
non-finite values, missing content and oversized item strings. The
official SHA-pinned QuickShop binaries and both real Paper runtime
boots verify the new API signatures; they still do not construct
a shop record or authenticate real AlbionMC persisted SQL data.

**Release boundary:** real historical QuickShop row data, verified
stock/owner/price before and after a staging restart, and controlled
trade/economy conservation remain required. This diagnostic does not
authorize a bulk player, chest, machine or shop migration.

## Real QuickShop in-memory prospective encoding fixture — 2026-11-20

The isolated native QuickShop-Hikari 6.3.0.4 two-JVM Paper 26.2/26.3 matrix
now constructs four real upstream \`ContainerShop\` instances **in memory**
using synthetic old/current Carbonado-like items, a different Slimefun ID,
and a changed typed owner PDC. The upstream constructor explicitly forbids
persistent data writes; the fixture does **not** call registration, load,
createShop, SQL or trade. It checks that \`saveToInfoStorage()\` emits the same
opaque serialized item for repeated reads, preserves exact owner/price,
does not collapse stylistic, ID or PDC-type differences, leaves original
native item bytes intact and does not register a live shop. The pinned
official PaperPlatform 6.3.0.4 uses Base64 of native ItemStack bytes;
the fixture now decodes the actual prospective save encoding and requires
**byte-for-byte equality to each original item**, followed by a native
`ItemStack.deserializeBytes` round-trip with identical bytes. This detects
any loss of text styling, typed PDC, Slimefun identity or other private
native data even when two distinct encoded strings are produced. A redacted
result must be identical across both server boots; no raw encoded item,
owner identity or stock data is included in artifacts.

**Scope:** this validates official in-memory Hikari save projection, NOT
a persisted SQL row, reloaded trading shop or actual AlbionMC legacy item.
A backed-up real-world restart, transaction and balance/stock conservation
test remains necessary before any automatic repair, migration or release.

## Disposable physical chest style migration and restart regression — 2026-11-20

The native Doctor test plugin now exercises the existing **production
player-inventory repair engine** against one separately created physical
single-chest inventory in a fresh disposable world. It seeds a synthetic
historical-style CARBONADO stack (63), a fresh registered stack (1), and
a typed owner-PDC negative control (9). The strict preview must select
only the style-only stack; the exact-original native-byte receipt is
written and reread before the test changes that one slot. After a clean
shutdown and independent JVM restart the test reopens the physical chest,
requires byte-exact repaired stock and unchanged adjacent/typed stock,
verifies the original operator-scoped receipt and rollback eligibility,
and physically merges 63+1 into 64 without loss or duplication.

The restarted chest is also rescanned using the production preview engine,
which must find zero remaining eligible candidates (idempotence), while still
refusing the functional typed-PDC negative control. The Python native runner
now requires exactly one redacted physical-chest migration PASS marker on
each of the first and second JVM boots; a missing or skipped fixture fails CI
even if unrelated Doctor probes passed.

This is an **isolated test-only call** of the existing batch engine and
does not add a production chest mutation command or grant permission to
rewrite existing AlbionMC containers, machines, QuickShop records or
offline inventories. The independent read-only old-chest fixture remains
unchanged. Native pass markers are emitted on seed and restart, so a
real Paper/Purpur/Folia regression fails closed. The historical old
item is still synthetic; real-world data and transaction proof remain
required before any live chest/shop migration or v4.1.82 release.

## Native disposable QuickShop SQL persistence gate — 2026-11-20

The official 6.3.0.4 two-JVM native Paper 26.2/26.3 matrix now also
creates **one synthetic shop and physical chest in a newly created
throwaway server directory**. Unlike the read-only in-memory DTO
projection above, this *test-only fixture* deliberately calls the real
upstream \`ShopManager.registerShop(..., true)\` to write a disposable
SQLite shop record with a synthetic historical-looking CARBONADO item
and a **real-player-format synthetic UUID owner** (never a real AlbionMC
account). An earlier verified revision exercised QuickShop's virtual-owner
serialization; the current fixture tests the more representative persisted
player UUID and checks that both the SQL record and the reloaded runtime
shop preserve the exact UUID on both boots. On the first boot, it queries the **actual QuickShop DatabaseHelper
\`listShops(world, false)\` SQL row**, verifies location, owner, exact
fractional price, Base64/native item bytes, live shop lookup and exact
physical stock count + metadata. It cleanly shuts the server down,
boots the **same disposable database and world in a new JVM**, and
repeats the same SQL, loaded-shop and physical-stock invariants with
no duplicate row or lost metadata. The runner requires a fixed redacted
observation for both boots; no raw item/owner encoding is exported.

**Strict boundary:** Only the ephemeral test world/database are
written. The installed production Slimefun Doctor remains read-only
for QuickShop and does not access, edit, migrate, create or trade any
live shop. Synthetic SQL round-trip is not genuine AlbionMC data, and
does not prove economic conservation under real trades. A backed-up
AlbionMC historical SQL row, actual trade balances, owner and stock
preservation checks are still required before enabling bulk repair or
shipping a migration feature.

## Reader-envelope regression

The retained String serializer can produce Base64-wrapped SF2/native item bytes. Previously the String read path recognized this envelope, but the codec's byte[] path decoded Base64 and unconditionally treated the decoded bytes as an old Bukkit object stream. This rejected a valid SF2 header as a Java object-stream header when an adapter returned the text representation as bytes.

The correction checks the decoded SF2 header before invoking the legacy object-stream reader. It accepts exactly one Base64 envelope, including MIME line breaks, while retaining native bytes and historical Bukkit object streams. It changes neither the serializer method nor schema, IDs, migration defaults or record-writing behavior.

`ItemStackDataCodecCompatibilityTest` covers all supported representations, exact metadata/typed PDC, amounts, existing backpack identity, unknown-addon data, repeated item round trips, malformed envelopes, and restoration of the temporary ItemMeta deserializer. `ItemStackEnvelopeMigrationTest` exercises the existing DatabasePatchV3 on disposable SQLite tables for backpacks, blocks and universal inventories; checks 12 mixed-format records and exact owner/slot keys; reopens the file and checks idempotence; and verifies malformed-row preservation and caller-transaction rollback.

These fixtures use the historical object-stream encoding generated by the current test harness. They are not presented as captured player items from every historical Minecraft version. No player world or production database is used by these tests. Real old-world, all-platform and cross-fork round trips remain separate evidence.

## Read-only full physical-container provenance guard — 2026-11-21

Doctor's operator-only `live scan-container` and `live export-container <slot>`
now capture a bounded **complete** native preimage of the already-loaded
physical inventory, including empty slots, physical order and typed-PDC
bytes. Each stack must survive an exact native deserialize/reserialize;
the combined read budget is 2 MiB (256 KiB per item, maximum 54 slots).
An independent second read must match before a read-only scan result or
original/template evidence is trusted. During private receipt export,
the same complete preimage must match again **after** receipt I/O.
Even an unrelated slot changed by a hopper, Cargo or an addon refuses the
operation. No public hash or owner/item bytes are reported.

This is a read-only provenance check only, not a complete durable
container recovery journal or a production chest update command.
Future chest migration still needs a block/location-bound durable
all-slot recovery envelope and exact no-loss rollback proofs on genuine
old-world data. QuickShop SQL and shop templates remain untouched.

## Immutable physical-container evidence envelope — 2026-11-21

The opt-in, operator-only read-only commands `identity live export-container-full`
and `identity live verify-container-full <receipt>` add a first **durable**
all-slot native source snapshot for later safe-migration architecture.
Each private, CREATE_NEW/fsync record under `recovery/containers/` is
owner-scoped and exact-byte authenticated with SHA-256. It binds the
world UUID, block coordinates, block-data string, slot count/indices
(including empty slots) and up to 2 MiB of original exact-native items.
The writer validates a complete preimage both before and after I/O and
rereads the full record. The independent read path refuses corruption,
wrong operator, world, location, block state and unsafe native roundtrips.
The operator sees only a receipt UUID and redacted same/different status.

This is an original-data provenance **envelope**, NOT a prepared transaction
journal, automatic rollback or authorization to migrate physical inventory,
QuickShop, Cargo, offline players or machines. No full-container restore
code exists; a changed chest is never overwritten from a receipt.
Only backed-up staging with genuine old-world pairs can establish eligible
historical transformations and final transaction/quantity/restart acceptance.

## Native physical chest all-slot private evidence — two-process regression

The test-only `ResourcePackDoctorProbe` now creates a real disposable world
single chest at `(10,180,3)` in its brand-new Paper/Purpur/Folia CI server.
Boot one places independent old-format CARBONADO x63, fresh CARBONADO x1,
different typed-owner PDC x9, and ordinary stone x17, with all other slots
empty. It calls production `DoctorContainerEvidenceEnvelope.capture`
against the actual **block inventory** and confirms the original receipt
immediately. Boot two reopens the SAME saved world/chest on a different
JVM and uses the production `verify` method to prove exact world location,
container block-state, full slot order, native item data, PDC and quantities
survived both disk and server restarts. A controlled unrelated stone change
from 17 to 16 must be diagnosed as different; after restoring 17, the
original receipt must again verify. It leaves the synthetic world chest and
Slimefun registered template unchanged.

The native runner requires both distinct redacted markers
`SFL_PHYSICAL_CHEST_FULL_ENVELOPE_SEED_PASS` and
`SFL_PHYSICAL_CHEST_FULL_ENVELOPE_RESTART_PASS` exactly once on the
corresponding JVM boots. This does **not** permit production chest repairs,
QuickShop SQL writes, automatic world scans or restoration, and does not
substitute for a genuine backed-up AlbionMC old/new item pair.

## Receipt-bound physical-container assessment (2026-11-21)

The new read-only `identity live preview-container-full <receipt>`
requires a valid unchanged private all-slot origin receipt before it
even counts any style-only migration hints. Entire-slot bytes (including
vanilla, PDC-owned and empty slots) must match the original physical
world/block binding. The same complete snapshot must still match after
template/classifier reads, or the operation refuses. The existing strict
Doctor classifier caps its reported candidate slot list at eight; it
refuses meaningful names/lore, typed ownership/ledger changes and
unknown IDs. Native disposable Paper/Purpur/Folia CI on separate JVM
boots now tests one style-only candidate, a typed-owner negative
control, full receipt replay and refusal after unrelated stock drift.

No plan survives the command and no production chest mutation or
QuickShop SQL writer is introduced. This proves **read-only, stale
original rejection**, not atomic chests/shops, genuine historical
migration eligibility or full live transaction/recovery safety.

## Read-only whole-container transaction after-image preflight — 2026-11-21

`DoctorContainerMigrationPreflight` is an **internal, unexposed** draft
engine. It starts only with the private, owner-scoped, location-bound
complete original receipt; performs the strict Doctor historical style
classifier against the already-loaded 27/54-slot block inventory; and
builds a hash of a **hypothetical** after-image containing only proven
style-only replacements. It verifies registered-template fingerprints,
exact-native round trips, item quantities, complete original slot
metadata (including empty slots and typed-PDC negative controls), and
rechecks the full original receipt after planning. More than eight
eligible historical stacks refuses the entire plan rather than silently
attempting a partial rewrite. It returns only redacted slot indices,
before/after hashes, stock totals and number of changed stacks. There
is **no chest setter, durable write-ahead transaction status, automated
rollback or operator `confirm` command** in this class.

Unit tests exercise exact before/after hashes on a **separate disposable
inventory**, 63+1+9+17 item conservation, refusal of altered unrelated
vanilla stock, wrong owner, racing addon writes, meaningful text changes,
typed-PDC mismatch, and over-eight candidate refusal. The native CI
two-JVM disposable physical world chest now tests the same preflight
against a restarted block and saved all-slot receipt and requires
one bounded cosmetic candidate, zero loss of all original items and
refusal after unrelated stock drift. Existing full-envelope PASS
markers remain mandatory. These **synthetic** tests do not prove
actual historical AlbionMC data or QuickShop trade safety.

## Durable diagnostic-only physical-container transaction journal — 2026-11-22

The new internal `DoctorContainerTransactionJournal` records the
**proposed** transaction from `DoctorContainerMigrationPreflight` in
an owner-scoped, CREATE_NEW/fsync private record under
`plugins/Slimefun/recovery/container-journals/`. It records source
receipt ID, world UUID, block coordinate/state/slot count, exact
original and **hypothetical** updated all-slot SHA-256 hashes, a sorted
list of up to eight eligible changed slots, and conserved item totals.
Each journal has a versioned bounded format, independent SHA-256
integrity check and exact readback. Before reporting a created
journal, it reopens the original native all-slot evidence and
reconfirms the actual physical chest still matches that origin.

After a distinct JVM restart, its TEST-ONLY inspection distinguishes
`ORIGINAL` (full chest still matches original native bytes),
`PROPOSED` (all-slot current bytes match only the hypothetical
after-image), and `DIVERGED` (unexpected partial or unrelated
changes). An invalid source receipt, owner/world/block mismatch,
tampered journal or malformed/oversized record refuses inspection.
The disposable physical chest native fixture creates the journal
before its first clean shutdown, then reopens it on the second JVM and
requires ORIGINAL; an unrelated 17-to-16 stone stock change must yield
DIVERGED, and restoring the original stone count must yield ORIGINAL.

**Critical:** This is an immutable, read-only **transaction proposal**,
NOT a crash-safe write-ahead commit protocol, two-phase durable
transaction log, rollback implementation, or a live repair feature.
No physical container update or QuickShop SQL writer uses it.
There is no operator-facing `journal confirm` command. A real
migration still needs a separate crash-consistent protocol,
concurrency-safe isolation from Cargo/hoppers/shop trades, verified
original and after states, real-world item+shop SQL acceptance and
recovery semantics before any production write is enabled.

## Exact-native proposed-slot witness in the private transaction journal — 2026-11-22

Version 2 of the **unexposed diagnostic** `DoctorContainerTransactionJournal`
now persists a bounded complete proposed after-image: exact native bytes
for every planned occupied slot, plus explicit empty-slot positions. The
source all-slot original receipt remains unchanged and mandatory. The
journal independently validates that its native round-trips, item totals,
slot hash and operator/world/block binding agree after fsync/readback.
A separate-JVM replay reports PROPOSED only if the *actual current chest*
matches both the expected whole-container fingerprint **and each
individually stored proposed native slot**, never because a candidate
identifier or item display name happens to match. Otherwise it reports
DIVERGED, without touching the chest or shop.

All records remain private under `recovery/container-journals/`, bounded
to 2 MiB native item data plus metadata, and can contain owner IDs,
machine data and arbitrary personal item metadata. Version-1 staging
proposal files do not automatically upgrade; recapture a fresh proposal
from the original receipt on a backed-up test clone. The new unit
regression checks truncated after-image refusal without writing or
altering original typed-PDC stock. There is still **no production commit,
rollback, journal replay writer or QuickShop migration command**. Genuine
historical AlbionMC source bytes and economic trade conservation remain
unverified.

## Disposable physical proposed-state persistence, independent JVM — 2026-11-22

The native test plugin now creates a **second separate disposable physical
single chest**, at (8,180,6) in an exclusively test-only world. The first
server boot seeds the source-derived synthetic 63-stack old-style
CARBONADO, fresh 1-stack, typed-owner PDC 9-stack and unrelated stone
17-stack. It captures an original whole-container private receipt,
creates/fsyncs the version-2 complete proposed-slot journal, and asserts
the journal initially classifies the physical chest ORIGINAL. It then
**simulates** the style-only replacement in this disposable chest by
calling `Inventory.setItem` directly *inside the native test fixture*
and verifies the journal identifies the exact PROPOSED state; this
does not touch or enable any Slimefun production writer.

After stopping and restarting the **same saved test world in an
independent JVM**, the fixture requires byte-for-byte persistence of
the proposed 63-stack and unchanged fresh 1-stack, typed owner-PDC
9-stack, stone 17-stack, and all other empty slots. The original private
receipt must remain recoverable but report the chest as changed;
the durable complete after-slot journal must report PROPOSED. A
disposable 63+1 inventory merge must produce exactly 64, and unrelated
stone 17->16 drift must produce DIVERGED until restored. The native
runner requires unique
`SFL_PHYSICAL_CHEST_PROPOSED_JOURNAL_SEED_PASS` and
`SFL_PHYSICAL_CHEST_PROPOSED_JOURNAL_RESTART_PASS` markers on the
correct first/second boots across Paper 1.21.11/26.2/26.3,
Purpur 1.21.11/26.2, and Folia 26.2.

**Important:** The only physical setter here is in the test harness on a
throwaway chest. No player or production chest is rewritten, and this
does not prove crash-consistent live transaction isolation,
Cargo/hopper/QuickShop trade safety or compatibility with genuine
historical AlbionMC source records. All production chest and shop
migration commands remain absent.

## Independently rebooted interrupted two-stack migration fixture — 2026-11-22

The native Doctor test plugin now separately creates a third **disposable
world chest** at (12,180,6). It saves two distinct style-eligible old
CARBONADO stacks (63 and 8) alongside a fresh stack (1), functionally
owned-PDC negative control (9) and vanilla stone (17). The private original
all-slot receipt and complete proposed after-image journal must identify
**exactly two eligible slots** before the test writes anything.

On the first JVM boot the native test, not production Doctor, changes **only
the first** of those two eligible slots and stops without completing the
second. The private journal must refuse to call this mixed physical chest
either ORIGINAL or PROPOSED; it must report **DIVERGED**. After a clean
server shutdown and restart in an independent second JVM, the exact mixed
native source/after bytes and owned metadata must survive, and journal
inspection must remain DIVERGED. The test then deliberately finishes the
second slot *only in the disposable test fixture*, and the journal must
switch to PROPOSED, with 98 items conserved, normal 63+1 stacking, and
the typed-owner/vanilla stock unchanged. Any unrelated stone drift must
return to DIVERGED, then PROPOSED again only after restoring the same
original 17 stone.

The native Python runner independently requires one first-boot and
one second-boot partial-journal PASS marker for all six supported CI
runtime lanes. No live physical repair, rollback, shop transaction,
machine/Cargo sync or production writer has been added; **DIVERGED is
diagnostic, not permission to restore or complete a live migration**.

## Bind proposed journal to exact native source receipt — 2026-11-22

Crash-replay `DoctorContainerTransactionJournal` now validates more than
the independent checksums on the saved after-image and original evidence.
The owner-scoped private source receipt must still match the **same
before-state SHA-256 and total item count** recorded at journal creation.
For every slot, the exact saved original and proposed bytes must be
identical unless the slot was one of the at-most-eight original
style-only candidates; every listed candidate must actually differ.
If a different, internally valid owner-and-location-matching original
receipt is substituted, the journal refuses instead of reporting
PROPOSED. Internal `DoctorContainerEvidenceEnvelope.openVerifiedOriginal`
reopens verified original native bytes without touching live stock and
does not expose those bytes to player commands.

The new unit regression stages two otherwise valid receipts owned by
the same operator and bound to the same physical block with **different
unrelated stone counts**. It substitutes the second sealed source under
the first receipt's path while retaining the exact journal and synthetic
after-inventory, then requires checked refusal. A digest is an integrity
check, not authentication against a filesystem adversary; no live
restore, chest writer, Cargo lock, shop transaction or automatic repair
is enabled by this diagnostic safeguard.

## Operator read-only physical-container journal commands — 2026-11-22

Doctor's OP-only `identity live` handler now exposes a **read-only-to-
world** proposal file and replay inspection:
`journal-container-full <receipt-uuid>` and
`inspect-container-journal <journal-uuid>`.
Both require a loaded one-block chest/barrel the player is looking at
within six blocks, run via `Slimefun.getSchedulerService().runAt(location)`
on the physical block's owning region, refuse unsupported or
chunk-spanning containers, and send only redacted source/candidate counts,
UUID and classification results back to the player's region. They do not
make an inventory repair plan or use the existing player-only confirm.

Journal creation requires a matching private whole-container receipt
and the sealed original/proposed native-slot proof; only the
**private diagnostic file** is written. Inspection identifies ORIGINAL,
PROPOSED or DIVERGED, or refuses malformed/mismatched evidence. Even a
PROPOSED journal is **not a transaction commit record**, and DIVERGED
does not authorize rollback. The real-world migration writer, hopper/Cargo
isolation, machine storage and QuickShop SQL conservation tests remain
unimplemented. New commands must pass the full CI compatibility suite
before use on live AlbionMC.

## Redacted interrupted-candidate attribution (read-only) — 2026-11-22

The private v2 journal's read-only `inspect` now distinguishes a
**strictly expected subset** of staged style-only changes from
unrelated stock mutations without making partial progress actionable.
For every physical slot, it independently compares actual native bytes
against the **verified original receipt** and the **sealed exact proposed
slot witness**. It reports the number of candidate slots exactly equal
to the proposed after-image, the number still exactly equal to their
original bytes, and the number of slots whose native bytes match
neither the allowed original nor proposed state (including changed
vanilla stock, empties and typed-owner PDC).

A `DIVERGED` state gets an `expectedOnlyPartial` diagnostic flag
**only** when at least one eligible stack is still original, at least
one eligible stack exactly matches its planned update, and every
other slot is byte-for-byte unchanged. It remains DIVERGED regardless
of that flag, and neither the inspector nor the command resumes,
completes, restores, commits, locks or writes any inventory data.
The redacted command output shows only counts; never item bytes,
owner metadata or sale information. Native two-JVM tests and the
separate mocked-journal regression require this classification to
remain accurate across a persisted first-of-two simulated write,
detect unrelated stone drift, and protect typed-PDC owner changes.

## Native published historical-core item preservation — November 22, 2026

`Published Historical Core Item Handoff` is a new independent native
workflow that uses exactly pinned, published Slimefun Legacy **v4.1.55**
and **v4.1.81** plugin JARs, rather than synthetic old-style names/lore.
It first boots pinned Paper 1.21.11 with published v4.1.55 and an
independent Bukkit-only fixture, mints the real old release's registered
CARBONADO and CARBON_CHUNK (and RAW_CARBONADO/STEEL_INGOT if registered),
places them in a fresh disposable physical chest, saves exact native
bytes and item counts, then stops the server normally. A **new JVM**
boots that exact saved test world with published v4.1.81 and refuses
native byte loss, missing source stacks, changed amounts, new occupied
slots or unrelated vanilla stock mutation.

The second JVM records redacted per-ID source/fresh SHA-256 and normal
Bukkit `isSimilar` and material-match results. A mismatch is **an
observation, not permission to rewrite old stock**: the test does not
change stored chest items, convert metadata, alter `isSimilar`, or
touch any production plugin data. This is historical core provenance
evidence missing from source-derived synthetic fixtures, but it is not
an exact copy of **AlbionMC's** original QuickShop snapshot, addon
owner PDC, custom names/lore or old persisted world data. Live
restoration/update requires further backed-up staging proof and
transactional protection for machine/Cargo/hopper/QuickShop storage.

The new gate pins old JAR SHA-256
`353c530f9369ebce1d08ddfdbd52b69a2a3b97172cb27210105285068845c6e0`,
new JAR SHA-256
`9d0e436658cb426ecf3468ab55f698fb7e41e23c46e26f9153ffe19a7fa901d1`,
and Paper 1.21.11 build 132 SHA-256
`5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba`.
All native receipts remain inside the throwaway workflow directory;
only redacted comparisons and runtime logs are uploaded.

## Historical mismatch attribution (follow-on diagnostic branch) — 2026-11-22

The real v4.1.55 -> v4.1.81 native world handoff **passed** and
verified that genuinely persisted CARBONADO (63), CARBON_CHUNK (8),
RAW_CARBONADO (7) and STEEL_INGOT (12) retain their exact original
bytes and unchanged material/quantities after restart, **but all four
were incompatible with newly registered v4.1.81 items under Bukkit
`isSimilar`**. This proves the reproducible problem exists without
QuickShop, and must not be reduced to a QuickShop bug.

A separately staged follow-on test extends only the read-only TSV
comparison (the chest and source receipts remain unchanged) to report
**boolean equality only** for Adventure display-name components, lore
components, plain display names, plain lore lines, legacy Bukkit text,
PDC key sets, custom model data, item model and enchantments, as well
as lore line counts. It deliberately does **not** print or upload
names, raw lore, owner values or other stored PDC data. Equal plain
text and PDC keys alone do not prove functional equivalence and never
authorize mass migration. This attribution is needed to distinguish
safe presentation-only compatibility repairs from functional or
meaningful-text changes before any production auto-updater is enabled.
