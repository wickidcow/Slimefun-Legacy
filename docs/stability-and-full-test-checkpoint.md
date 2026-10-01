# Stability and full-test checkpoint — bundle revision 96

This candidate preserves the entire revision-95 source set, including concurrent component-preservation fixes, and changes only four addon pins: DankTech2, Cultivation, CrystamaeHistoria and RykenSlimeCustomizer. All 45 maintained sources remain pinned; inclusions/exclusions and version 4.1.62 are unchanged.

## Three actual persisted-data failure paths corrected

DankTech2 PR4 refuses unreadable pack registries before registering handlers. Cultivation PR5 refuses unreadable experience, codex and owner configuration. CrystamaeHistoria PR4 strictly loads five managed files and explicitly stops because InfinityLib otherwise catches its startup exception while leaving it enabled. These candidates serialize completely before staged file replacement and preserve existing file symlinks/POSIX modes. No IDs, typed data, inventories, recipes, rates or gameplay formulas are changed.

All 38 new permanent YAML/filesystem tests passed. Actual old-addon controls and corrected runtimes completed 72 server cycles across Paper1.21.11/Java21, Paper26.2/Java25 and Paper26.3beta140/Java25: DankTech2 15, Cultivation27 and CrystamaeHistoria30. Corrupt original files remained unchanged under the corrected loaders. Valid old pack contents/progress/settings were byte-identical after upgrade and restart within each server line. These are generated old-addon fixtures, not every historical world, third-party item subclass or absolute power-loss guarantees. See the addon PRs and their retained evidence documents for exact logs and hashes.

## Complete build/test coverage ledger

`compatibility/maintained-addon-test-ledger.json` records all 45 sources and the exact evidence behind each result. All full-build outcomes are resolved successfully. The existing suites report 227 entries across 12 addons, with zero failures/errors/skips. The other 33 addons report no automated test entries; their build success is not a gameplay certificate. Results combine exact-source runs and verified promoted source blobs, not one single 45-job run.

The five original audit failures were resolved without weakening gameplay assertions: DracFun uses its declared Gradle9 toolchain, MobDrops uses its declared Paper26.2 test API and unchanged MockBukkit4.116.1, and the exact-core build comparator now recognizes only the canonical maintained Legacy coordinate. Ryken PR8 replaces an unavailable upstream guide dependency with the published maintained compile-only API; normal repository and exact-core builds pass, and the guide implementation is not shaded. Ten new comparator regressions reject unrelated addon names and preserve existing fork/property/scope behavior. This is build tooling, not an item or storage conversion.

## Full-stack and release boundary

The prior revision-93 bundle passed two real boots of all45 maintained addons on each server line when supplied with the proper external WorldEdit provider:7.3.19 for the Java21 floor and7.4.6-beta-02 for newer Java25 lines. That provider is not added to the maintained release ZIP. Later component updates and the four new preservation/build-source pins require a new exact-source bundle and three-version runtime validation; the old archive is not substituted for this revision.

Remaining work includes deprecated-API cleanup, wider machine behavior/load/concurrency tests, and captured historical-world validation. No master merge, version bump, stable release, production installation or automatic migration is performed by this checkpoint. Individual plugin artifacts remain raw JARs; the aggregate bundle remains the existing45-addon ZIP.
