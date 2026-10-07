# Published-release upgrade fixture

This is a new, read-only CI evidence lane for the **already-published** Slimefun Legacy 4.1.69 refresh and 4.1.70 release, with their published 45-addon ZIPs (revisions 112 and 125). It does not create a new plugin release, repackage published assets, modify their tags, touch a live server, or substitute for a copied production-world test.

## Four boots on a single fixed Paper binary

1. **Seed / 4.1.69:** create a new synthetic offline owner, representative registered core/addon stacks with varied amounts and additional opaque persistent data, named stored backpacks, a bound nested-backpack carrier, a shulker containing items and a backpack carrier, and native barrel inventories. Save through real Slimefun and Paper APIs, retaining baseline native item snapshots.
2. **Baseline control / 4.1.69:** reload the same world/database and require unchanged inventory sizes, empty slots, item components, amounts, original registered item IDs, backpack names/owners/numbers/UUIDs and profile backpack count. A baseline failure is a failed fixture, not evidence of a 4.1.70 regression.
3. **Upgrade / 4.1.70:** replace only the core/addon JAR set with the exact published candidate bytes, preserving generated world/database/configuration. Require the same checks against the old snapshots. Then explicitly mark and acknowledge a new save of the stored stacks, rather than treating an unchanged-cache no-op as a write test.
4. **Restart / 4.1.70:** reload the newly saved state, require the candidate save marker, remove only that marker in a comparison clone, and require equality with the old snapshots again. Never rewrite the original baseline expectations to match a candidate failure.

Each runtime job keeps the same downloaded, checksum-verified Paper JAR for all four boots. Paper 1.21.11 and 26.2 require stable-channel builds. Paper 26.3 prefers stable but may explicitly test beta; alpha and silent fallback to another Minecraft generation are rejected. Actual server/provider builds and channels are recorded. This is a **plugin-set upgrade on one server version**, not a Minecraft-version migration or downgrade test.

## Evidence and safety

The driver independently checks all four published core/addon downloads against fixed sizes and SHA-256 hashes, verifies embedded core/source identities and exact 45-plugin manifests, and verifies every installed addon is enabled with its expected version on each boot. Sampling is capped at three registered items per item-owning plugin, plus named core representatives and nested-container cases. Non-item addons are enable/version checked, not claimed as item-persistence coverage. `fixture-coverage.tsv` records exactly which IDs were sampled.

The test plugin compiles against the real Paper 1.21.11 API and old published Slimefun core. It never uses hand-written Bukkit doubles. The driver refuses non-CI execution, work paths outside a fresh workspace/build child, or any existing work directory. The plugin additionally requires a disposable-only JVM flag, a synthetic UUID marker, loopback binding, a dedicated fixture world, no online players and an authorized console phase. Production scripts, player data, storage formats, migration defaults and runtime Java are not changed.

The offline Python tests exercise driver failure detection and archive/download checks using deliberately synthetic ZIPs and network doubles. Those are tooling tests, not server test evidence. The test plugin JAR is never a public addon or an input to the canonical release ZIP.

## Honest result interpretation

Only a completed `status: PASS` report with all four phases is a pass. Failed/missing phases and missing reports fail the job. Existing published release checks do not count as a pass for this new lane. Full console/compile logs and compact JSON/count/coverage evidence are retained, not synthetic worlds or any owner data.

This lane does **not** complete the optional `slimefun.realDatabase` owner-database test; no such database was supplied. It does not exercise all item IDs, every dynamic addon schema, placed-machine execution, Cargo/Networks transfer conservation, player GUI actions, Folia or cross-fork rollback. It supplements the earlier full-stack startup/restart and unit-test evidence, without erasing those limitations.

## Initial harness failure and correction

The first run (`37635153412`) compiled the fixture against the genuine APIs but stopped during the old-release seed phase. The inspected Paper 26.2 console log reported `Synthetic usercache owner was not resolved`: the UUID-only lookup creates a nameless never-joined offline player even when a separate name cache was seeded. This is test initialization failure, not a demonstrated 4.1.70 upgrade regression.

The fixture now uses the public name-based offline-player lookup on its explicitly offline-mode, loopback server and verifies the deterministic offline UUID derived from `OfflinePlayer:SFLTestFixture`. It no longer pre-populates the internal usercache file. The synthetic identity and all later owner/item/persistence assertions remain mandatory; the owner-database skip remains untouched. The added offline tooling check verifies the cross-language owner identity and guard wiring, not Paper runtime behavior. New server results are still required.
