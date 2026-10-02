# Inventory snapshot ownership — October 2, 2026

## Scope and defect

Continue the storage-safety work after the merged delayed-save and RecordKey fixes. The inspected implementation at `a194ab1d0d5dd1cfc72caada225961ba0852ab87` copied only the list structure in `InvSnapshot(List)` and exposed its mutable pairs and ItemStacks through the generated public getter. Its public snapshot-building utility also returned one shared mutable empty pair for every empty slot.

A caller could change the saved baseline to match an inventory modification that had not reached the database. The subsequent real controller save then observed no difference and submitted no item write. This is a reproduced API-level failure path, not evidence that a specific player's items were lost or that the bug was exploitable through an ordinary client.

The clean change is based on current master `951c042c982aeb04b5a55163f3c94036b9ac68fa`, preserving the concurrent merged revision-106 addon selection and JEG floor-build correction. Comparison from the audit baseline confirms no intervening change to these production/test files. No addon source selection, release asset, version or migration default changes.

## Correction

- Own the list-constructor pairs and cloned items instead of retaining caller-owned objects.
- Keep the public getter's descriptor while returning detached pairs/items in an unmodifiable list. Mutating an exported pair or item no longer changes what the controller considers saved. Consumers must explicitly change an inventory and use its normal save API; an export is not an acknowledgement operation.
- Keep instance comparisons on the private baseline. The static InvSnapshot comparison overload delegates to that path instead of deep-copying the public export for every save check.
- Do not expose the private shared empty-comparison sentinel through public snapshot-building results.

The existing exact item comparator, independently recorded amounts, null/empty/resize behavior and array/inventory constructor cloning remain. No IDs, item metadata normalization, persisted keys/types, database schemas, item codec, recipes, menus, machine rates, capacities or transfer order change. The defensive getter and list constructor may allocate copies; the ordinary comparison loop does not add item clones. No measured TPS improvement is claimed.

## Actual isolated validation

[Run 37000213850](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/37000213850) ran the original and corrected implementations against the same added tests, then the complete existing build and source/artifact guards.

The original control has 20 reported cases, 15 assertion failures, no errors/skips. All six block/universal/backpack controller cases specifically failed because no changed item was submitted. The corrected code passes all 20 new cases. Its complete 126 XML reports contain 562 entries, zero failures/errors and one existing historical-database-fixture skip; that skip and empty external-database factory entries are not executed customer-world/external-database coverage.

Fourteen snapshot cases cover constructor/export ownership, separate pairs and empty slots, exact old typed metadata, clone failure propagation, preserved recorded amounts, 1,000 randomized comparison layouts and clone-count instrumentation. The latter performs 2,000 unchanged comparisons without extra ItemStack clones, then checks that explicitly exporting a snapshot does clone.

Two parameterized controller regressions run for block, universal and backpack inventories (six executions). They use the established real controller staging/acknowledgement methods with controlled submission completion and a real SQLite test store. An initial item is saved, its exported baseline is mutated, the actual inventory changes, and a subsequent save must submit the new quantity or metadata. Closing and reopening the SQLite connection confirms the correct item bytes. This is a test schema and MockBukkit ItemStack environment, not a real Paper-server process or a captured historical world.

The complete validation also passed the existing source invariants before formatting, full Gradle build, explicit production deprecation/removal guard, Java 21 bytecode and artifact metadata/packaging checks. Existing test/runtime deprecation notes are not represented as a globally warning-free build.

Downloaded evidence artifact `11223161977` matched SHA-256 `743888fd6a82817ef6de76ac4e2d23c48af21c2a80ffc246ecb6878baf20b9f9`. Archive integrity, both original/corrected XML sets and all four promoted Git blob hashes were independently checked. Only these formatted source files and this checkpoint are promoted; temporary audit workflows and preparers are excluded.

The first audit attempt stopped before Gradle because the shallow checkout lacked the pinned original commit. Fetching that exact commit corrected the setup. Parameterized test names were then made explicit so all six cases can be identified in XML; no assertion or production guard was removed.

## Integration and limits

The clean PR must pass its own normal public API, supported-server, canonical-addon and full-stack checks. The audit's green results are not substituted for the new head's results. Keep the published-core/addon-only release work independent and preserve all revision-106 addon selections.

This snapshot fix cannot reconstruct already missed saves and does not make arbitrary concurrent inventory mutation, filesystem/database failure, hostile addons or every Folia ownership pattern safe. No stable release, automatic data rewrite or live-server installation is performed by this change.
