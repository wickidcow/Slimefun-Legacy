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

The official 6.3.0.3/6.3.0.4 binary ABI contract explicitly verifies the
cached getter. The two-JVM official plugin activation test verifies its
public method exists, but does NOT create a real QuickShop shop or validate
an actual SQL row. Real old-world staging DB backups, restart and trade
checks remain required before any persisted shop update.

## Reader-envelope regression

The retained String serializer can produce Base64-wrapped SF2/native item bytes. Previously the String read path recognized this envelope, but the codec's byte[] path decoded Base64 and unconditionally treated the decoded bytes as an old Bukkit object stream. This rejected a valid SF2 header as a Java object-stream header when an adapter returned the text representation as bytes.

The correction checks the decoded SF2 header before invoking the legacy object-stream reader. It accepts exactly one Base64 envelope, including MIME line breaks, while retaining native bytes and historical Bukkit object streams. It changes neither the serializer method nor schema, IDs, migration defaults or record-writing behavior.

`ItemStackDataCodecCompatibilityTest` covers all supported representations, exact metadata/typed PDC, amounts, existing backpack identity, unknown-addon data, repeated item round trips, malformed envelopes, and restoration of the temporary ItemMeta deserializer. `ItemStackEnvelopeMigrationTest` exercises the existing DatabasePatchV3 on disposable SQLite tables for backpacks, blocks and universal inventories; checks 12 mixed-format records and exact owner/slot keys; reopens the file and checks idempotence; and verifies malformed-row preservation and caller-transaction rollback.

These fixtures use the historical object-stream encoding generated by the current test harness. They are not presented as captured player items from every historical Minecraft version. No player world or production database is used by these tests. Real old-world, all-platform and cross-fork round trips remain separate evidence.
