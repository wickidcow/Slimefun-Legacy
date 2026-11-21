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
