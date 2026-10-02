# Addon bundle revision 103

Revision 103 reconciles PR 297 and PR 298 without discarding either history. Both independently used revision 102. All 45 members and exclusions remain; only BetterChests, BuildingStaff, FluffyMachines and Networks advance from revision 101. The optional compile-only Rebar API bootstrap is retained. No core production source, core version, item/research IDs, persisted keys, recipes or migration defaults are changed by this integration.

## Completed addon sources

| Addon | Version | Selected source | Merged addon PR |
| --- | --- | --- | --- |
| BetterChests | 1.0.3 | 5c830e845d2849e0d8aea53414be8cba80af6775 | wickidcow/SF_BetterChests#8 |
| BuildingStaff | 1.0.35 | 9f017f4ca4cec5997c14ccd1e564a21b21ed16a0 | wickidcow/SF_BuildingStaff#1 |
| FluffyMachines | 26.2.13 | 91dc76b4e6d2024faeb1cbecace18c0a99b99aee | wickidcow/SF_FluffyMachines#5 |
| Networks | 1.0.47 | ba79e6e36bd4798d37f7b8988efb414e7f041927 | wickidcow/SF_NetworksExp#53 |

BetterChests refuses missing/unreadable block-data writes while retaining the read-only Doctor snapshot and exact legacy Cargo mutation. FluffyMachines retains first-item registration across output buffers and protects deferred item-frame interactions. BuildingStaff supports optional provider-owned custom placement/breaking with exact-item reservation and verified rollback; its real-provider coverage is specifically Rebar 0.43.0-26.2 and Pylon 0.41.1-26.2, not all future provider versions. Networks fixes grid filter/sort cache invalidation and sort cycling without changing storage/transfer order.

## Evidence attribution

The completed addon PRs record their actual builds and generated-server probes: BetterChests 30 project tests and runtime run 36935404300; BuildingStaff 21 transaction tests and real-provider run 36939131638; FluffyMachines 20 project tests and runtime run 36939285435; Networks 98 project tests in normal run 36938589126. These are separately attributed checks, not a single combined suite or exhaustive historical-world, client, power-failure or Folia concurrency certification.

The retained maintained-addon-test-ledger.json is historical evidence from the Networks-only revision-102 branch. Its 402 entries are not relabeled as revision-103 tests. Earlier documentation is a dated checkpoint, not current publication status. The three newer addon sources and the five pre-existing source/evidence differences require this explicit attribution rather than invented green ledger rows.

## Publication gate

The combined source must pass its own canonical 45-addon packaging and full-stack checks. Before replacing the ZIP on v4.1.63, test the exact resulting archive with the actual published core JAR, SHA-256 993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42. Preserve the existing core asset and tag. Record the refreshed ZIP hash and independent addon-source provenance in release notes; do not claim the newer master core is already in the published core JAR.

Source preparation alone does not publish anything. Do not overwrite a newer bundle or a concurrently changed release. Retain the previous ZIP as recovery evidence and verify the uploaded bytes after publication. Production server files are outside this task.
