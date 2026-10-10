# Published-release upgrade fixture

This read-only CI evidence lane tests upgrades between explicitly pinned, **already-published** Slimefun Legacy plugin sets. It preserves the original 4.1.69 refresh to 4.1.70 case and adds the published 4.1.71 to 4.1.72 case. It does not create a new plugin release, repackage published assets, modify their tags, touch a live server, or substitute for a copied production-world test.

## Reviewed release cases and runtimes

| Scenario | Published baseline | Published candidate | Runtime lanes |
| --- | --- | --- | --- |
| `4.1.69-to-4.1.70` | 4.1.69 refresh, addon revision 112 | 4.1.70, addon revision 125 | Paper 1.21.11, 26.2 and 26.3 |
| `4.1.71-to-4.1.72` | 4.1.71, addon revision 127 | 4.1.72, addon revision 129 | Paper 1.21.11, 26.2 and 26.3; experimental Purpur 26.3 build 2646 |

Each plugin set contains its exact published core JAR and 45-addon ZIP. The new case uses public core sources `f68544e47d8489704d0e9d34adc65f0114b07153` and `504db79c4ce82358c8a74968d7fbab3f4af1d017`; its candidate is the clean-build JAR actually published as 4.1.72. The earlier release pair and its frozen downloads remain a separate case.

The historical 4.1.69 baseline has two distinct provenance pins: refreshed core source `d430eda74525b31328660bc3d3e36e13c9869353` and revision-112 bundle source `bcd8b3a6619541df82dc5858178ef888a090e3c5`. Validate each against its own reviewed identity; do not require the older published bundle to claim the later refreshed core source or rebuild it to make those sources agree.

Adding a case or runtime is prospective validation. Neither publication nor an earlier main-build or full-stack result establishes a pass for these new four-boot lanes. Actual completed reports and their source/runtime identities are required.

## Four boots on a single fixed server binary

1. **Seed / selected baseline:** create a new synthetic offline owner, representative registered core/addon stacks with varied amounts and additional opaque persistent data, named stored backpacks, a bound nested-backpack carrier, a shulker containing items and a backpack carrier, and native barrel inventories. Save through real Slimefun and Paper APIs, retaining baseline native item snapshots.
2. **Baseline control / same baseline:** reload the same world/database and require unchanged inventory sizes, empty slots, item components, amounts, original registered item IDs, backpack names/owners/numbers/UUIDs and profile backpack count. A baseline failure is a failed fixture, not evidence of a candidate regression.
3. **Upgrade / selected candidate:** replace only the core/addon JAR set with the exact published candidate bytes, preserving generated world/database/configuration. Require the same checks against the old snapshots. Then explicitly mark and acknowledge a new save of the stored stacks, rather than treating an unchanged-cache no-op as a write test.
4. **Restart / same candidate:** reload the newly saved state, require the candidate save marker, remove only that marker in a comparison clone, and require equality with the old snapshots again. Never rewrite the original baseline expectations to match a candidate failure.

Each runtime job keeps the same downloaded, checksum-verified server JAR for all four boots. Paper 1.21.11 and 26.2 require stable-channel builds. Paper 26.3 prefers stable but may explicitly test beta; alpha and silent fallback to another Minecraft generation are rejected. The Purpur lane requires the reviewed 26.3 build 2646 and is available only for the new release pair. Actual server/provider builds and channels are recorded. This is a **plugin-set upgrade on one server version**, not a Minecraft-version migration or downgrade test.

Within the disposable GitHub Actions context, the new Purpur case is selected explicitly:

```sh
python3 scripts/published_upgrade_smoke.py \
  --scenario 4.1.71-to-4.1.72 --platform purpur \
  --minecraft 26.3 --purpur-build 2646 \
  --work-dir build/published-upgrade-4.1.71-to-4.1.72-purpur-26.3
```

Paper cases use `--platform paper` and omit `--purpur-build`. The driver rejects unreviewed scenarios or server combinations; selecting a build does not certify Purpur for a production server.

## Evidence and safety

The driver independently checks all four published core/addon downloads against fixed sizes and SHA-256 hashes, verifies embedded core/source identities and exact 45-plugin manifests, and verifies every installed addon is enabled with its expected version on each boot. Sampling is capped at three registered items per item-owning plugin, plus named core representatives and nested-container cases. Non-item addons are enable/version checked, not claimed as item-persistence coverage. `fixture-coverage.tsv` records exactly which IDs were sampled.

Before the first boot, the driver writes `fixture-scenario.properties` once and checks its hash before and after every phase. The file contains exactly three UTF-8, LF-terminated properties: `scenario`, `old-core-version` and `new-core-version`. The Java fixture rejects missing, duplicate, unknown or malformed properties, unreviewed scenario names and version pairs that disagree with the selected scenario. It derives the expected running core version from that scenario and the authorized phase; there is no version fallback. Baseline snapshots record the same scenario and version pair, and each phase report includes `scenario`, `old_core_version`, `new_core_version`, `expected_core_version` and the actual `core` version. The driver requires those reported identities to match.

Each successful phase also records the running server's observed `minecraft`, `server_name` and `server_version` values from Bukkit. The driver requires the reported Minecraft version and server name to match the requested lane and retains the full server version in its evidence. Download metadata alone does not substitute for this runtime identity check.

The test plugin compiles against the real Paper 1.21.11 API and the selected baseline's published Slimefun core. It never uses hand-written Bukkit doubles. The driver refuses non-CI execution, work paths outside a fresh workspace/build child, or any existing work directory. The plugin additionally requires a disposable-only JVM flag, a synthetic UUID marker, loopback binding, a dedicated fixture world, no online players and an authorized console phase. The scenario declaration must remain unchanged through the final plugin guard. Production scripts, player data, storage formats, migration defaults and runtime Java are not changed.

The offline Python tests exercise driver failure detection and archive/download checks using deliberately synthetic ZIPs and network doubles. Those are tooling tests, not server test evidence. The test plugin JAR is never a public addon or an input to the canonical release ZIP.

## Honest result interpretation

Only a completed `status: PASS` report with all four phases and the correct scenario is a pass. Failed/missing phases and missing reports fail the job. Existing published release checks do not count as a pass for a newly introduced case. Full console/compile logs and compact JSON/count/coverage evidence are retained, not synthetic worlds or any owner data.

This lane does **not** complete the optional `slimefun.realDatabase` owner-database test; no such database was supplied. It does not exercise all item IDs, every dynamic addon schema, placed-machine execution, Cargo/Networks transfer conservation, player GUI actions, Folia or cross-fork rollback. It supplements the earlier full-stack startup/restart and unit-test evidence, without erasing those limitations.

Inventory writes are explicitly acknowledged before each normal shutdown. This tests stored-item upgrade and re-save behavior, not an immediate shutdown with pending delayed writes or crash durability. It does not resolve the historical run02 actor/cause or run03 actor PID/executable described in the [4.1.72 storage assessment](release-candidates/4.1.72.md). The accepted metadata assessment and its main-build binding remain distinct from this prospective published-clean-build inventory fixture. No storage adapter, schema, serialization or save-order correction is introduced.

Purpur 26.3 remains experimental. Even a passing synthetic fixture would not certify a populated AlbionMC world, its other plugins, a Minecraft downgrade or Folia 26.3.

## Initial harness failure and correction

The first run (`37635153412`) compiled the fixture against the genuine APIs but stopped during the old-release seed phase. The inspected Paper 26.2 console log reported `Synthetic usercache owner was not resolved`: the UUID-only lookup creates a nameless never-joined offline player even when a separate name cache was seeded. This is test initialization failure, not a demonstrated 4.1.70 upgrade regression.

The fixture now uses the public name-based offline-player lookup on its explicitly offline-mode, loopback server and verifies the deterministic offline UUID derived from `OfflinePlayer:SFLTestFixture`. It no longer pre-populates the internal usercache file. The synthetic identity and all later owner/item/persistence assertions remain mandatory; the owner-database skip remains untouched. The added offline tooling check verifies the cross-language owner identity and guard wiring, not Paper runtime behavior. The later historical run below supplies server evidence for that earlier source; it does not validate this extension.

## Historical results and JEG qualification

[PR #348](https://github.com/wickidcow/Slimefun-Legacy/pull/348) records the subsequent [fixture run 37639406527](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/37639406527) at source `97afb3b74668775f1b36386d48a727cf00b8faec`. All four historical-case phases passed on Paper 1.21.11 build 132 STABLE, 26.2 build 132 STABLE and 26.3 build 159 BETA. Its reports checked 125 sampled registered IDs from 41 item-owning groups, four stored backpacks, five barrels, 324 slots and 256 occupied stacks per verification boot. All 45 addons enabled at their expected versions. Initial synthetic-owner and template-copy setup failures remain part of that recorded history.

That persistence PASS does **not** mean the historical published addon set had error-free event handling: published JustEnoughGuide 2.1.71 logged an offline-profile listener NPE. PR #348 records the separate fix and real-server validation in [SF_JustEnoughGuide PR #19](https://github.com/wickidcow/SF_JustEnoughGuide/pull/19). This historical case keeps its original published JARs and the JEG qualification; do not inject a later addon, skip its enable/version checks or relabel the old logs as error-free. The new 4.1.71 to 4.1.72 case uses its own published plugin sets and requires its own reports. The historical counts and results above are not results for the new source, release pair or Purpur lane.
