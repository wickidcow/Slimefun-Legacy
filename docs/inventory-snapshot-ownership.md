# Inventory snapshot ownership — October 2, 2026

## Defect and preservation scope

The inspected implementation at `a194ab1d0d5dd1cfc72caada225961ba0852ab87` copied only the list structure in `InvSnapshot(List)` and exposed its mutable pairs and ItemStacks through the generated getter. The public snapshot-building utility also returned one shared mutable empty pair for every empty slot.

A caller could change the acknowledged baseline to match an inventory modification that had not reached the database. The subsequent real controller save then detected no difference and submitted no item write. This is a reproduced API-level failure path, not evidence that a particular player's items were lost or that an ordinary client can exploit it.

The clean continuation is based on master `951c042c982aeb04b5a55163f3c94036b9ac68fa`, retaining the merged delayed-save/RecordKey fixes, revision-106 addon selection and JEG floor-build correction. No addon source, version, release asset, item/research ID, persisted key/type, codec, schema, recipe, capacity, menu, machine rate, transfer order or migration default changes.

## Correction and API behavior

The list constructor owns cloned pairs/items. Its public getter retains the same descriptor but returns a detached, editable list with detached pairs/items. Editing the export no longer edits the saved baseline. Keeping the returned list editable avoids introducing unsupported-operation failures for callers that edited the old array/inventory-backed list.

Ordinary core InvSnapshot instances compare against their private baseline without exporting or cloning it first. The static comparison overload retains the historical overridden-getter dispatch for addon-defined subclasses and does not newly invoke a subclass's instance-comparison override. Two permanent compatibility cases protect both hooks. This does not impose thread safety or private-baseline semantics on arbitrary custom subclasses.

Public snapshot-building results no longer expose the private shared empty-comparison sentinel. Independently recorded amounts are retained without recomputing or normalizing them; the exact item/null/empty/resize comparator is unchanged. Array/inventory constructors still clone items only once.

Copies are allocated on explicit snapshot export and list-constructor ownership; normal core comparisons do not add item clones. No measured TPS improvement is claimed. Consumers must change the actual inventory and use the normal save API; snapshot export mutation is not an acknowledgement operation.

## Actual regression and database evidence

The initial audit37000213850 and clean-head build37000790013 passed the first20 added cases and the then562-entry suite. The final API-preservation audit below supersedes their test totals while retaining their evidence as earlier checkpoints.

[Audit 37001461992](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/37001461992) tested the refined source and23 regression cases. With only the original production classes restored, it produced16 assertion failures, zero errors/skips; all six controller cases specifically failed because no changed item was submitted. Both historical subclass-hook cases passed as controls. The refined implementation passed all23 cases.

The complete126 XML reports contain565 entries, zero failures/errors and one existing historical-database-fixture skip. The full source invariants, Gradle build, explicit production deprecation/removal guard, Java21 bytecode and artifact packaging/provenance checks passed. Existing test/runtime notes and empty external-database factory entries remain distinct from production warning and actual database-coverage claims.

Seventeen snapshot tests cover constructor/export ownership, editable-list isolation, subclass dispatch, independent empty slots, old typed metadata, clone failures, recorded amounts, and1,000 randomized comparator layouts. Instrumentation performs2,000 unchanged core comparisons without additional ItemStack clones; explicitly exporting does clone.

Six parameterized controller regressions exercise block, universal and backpack inventory save/staging methods using the existing controlled submission-completion harness and a real SQLite test store. After an initial save, an exported baseline is modified and actual inventory quantity or metadata changes. The subsequent save must submit the change, and closing/reopening SQLite must read the corrected item bytes. This is MockBukkit with a test schema, not a captured historical world or a native server/database certification.

Downloaded final audit artifact11223719268 matched SHA256 `86d71445aa983b8fbbfbeceab9cfa5d015d7d709b6981c362d029742c9d51706`. Both XML sets, archive integrity and all four checked source hashes were independently inspected. Original audit11223161977 hash `743888fd6a82817ef6de76ac4e2d23c48af21c2a80ffc246ecb6878baf20b9f9` and first clean-head report11223099763 hash `3a9dc4189a375ee2bbc5a186f6f3b059d00c56d4d0e86315ebf2012d44f6551a` remain historical evidence, not final-head results.

## Initial native Paper evidence, separately attributed

Run37001006062 compared published4.1.64 core bytes with the initial clean candidatead07df9c on Paper1.21.11 stable132/Java21,26.2 stable129/Java25 and26.3 beta140/Java25. The same original and candidate binaries were used in all lanes. Actual InvSnapshot/InvStorageUtils methods and native Bukkit items were exercised without a player facade.

All three lanes reproduced four original alias defects and passed the corrected isolation checks, plus exact typed-item clone/native-byte round trips and normal live-inventory change detection. Old unregistered IDs, owner metadata, exact FLOAT bits, large LONG values, byte arrays, nested owner data, names/lore and quantities remained unchanged. Each version's serialized item bytes were identical between original and initial candidate. This probe is a snapshot/native-item test, not the controller/SQLite test or a full populated-machine test.

Downloaded runtime artifacts and all six logs were checked:11224305580 (1.21.11) hash `20c8eb1b107581be7bd37f8c9588701b33f28cea63f3458d70d42cba2491bd47`;11224136622 (26.2) hash `e1f5648dfbd97f14dd265b67247be62c0931dc61ced64fb066349c268d1995ea`;11223339118 (26.3) hash `871e194f9ed6ca43280c5fc90c2f3eaeaa54d242509335d7029e28d8ce16ebfe`. These initial results are not relabeled as the final API-refined binary. The final head requires its own normal and native artifact checks.

## Workflow and release limits

The first audit stopped before Gradle because a shallow checkout lacked the exact original commit. Explicitly fetching it corrected the setup. Parameterized test names were made explicit so all six controller cases are distinguishable in XML; no assertion was removed. Only two production files, two permanent test files and this checkpoint are promoted; temporary audit workflows/preparers/probes and unrelated formatter changes remain excluded.

The final clean head must pass its own API/platform/canonical-addon/full-stack checks. This fix cannot reconstruct already missed saves or guarantee arbitrary concurrent inventory mutation, custom subclasses, hostile addons, storage failure, crashes or Folia ownership patterns. No stable release or automatic data rewrite is performed by the patch. The independently published revision-106 addon ZIP and4.1.64 core remain unchanged.
