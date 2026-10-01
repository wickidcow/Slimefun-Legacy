# Coordinated addon preservation checkpoint — September 30, 2026

## Scope

Continue core PR #289 (`62595cf11496b39d9b9857e9b304f48957b7ec99`) through the full maintained addon set. PR #292 pins the existing Networks #49, InfinityExpansion2 #23, Supreme #19, FinalTECH #52 and JustEnoughGuide #15 candidates, retaining the GeneticChickEngineering pin and all 45 inclusions/exclusions. Candidate manifest commit: `2bf5485cabd3c460ea1d68bc8ea9f529d8a54610`; tested merge: `a84755a935aa7a094cb8affde46628cad01eee77`.

This checkpoint changes validation and candidate source selection, not production Java/Kotlin, IDs, recipes, inventories, storage formats, migration defaults or the Slimefun version. United-to-Legacy forward upgrades remain relevant; United reverse conversion is not a release gate. Preserve Gugu compatibility and old data independently of the Minecraft 1.21.11 runtime floor. Rebar/Pylon remain outside this workstream.

## Complete candidate build: passed, not yet release-approved

[Canonical bundle run 36788293778](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36788293778) built and packaged all 45 maintained addons. The downloaded artifact `11131565448` matched SHA-256 `dfd20bdbd6e203945949ebf4001dde7f21c9ba32b1aa982faff4f61fecb4a527`. Its inner `SF_Addons_1.21.11-26.3.zip` matched `65fb50d7908ebc0d50be946187c50d3cea784fa996e88b8350189259f6059156`.

Local inspection verified all 45 manifest identities, explicit source pins, JAR descriptors, ZIP integrity, manifest hashes and SHA256SUMS entries. The manifest records the actual tested core merge `a84755a9`, not a stable release tag.

This bundle path uses the existing compile/assemble probe and does not run every addon's complete test suite. A successful compile does not certify gameplay, old-world upgrades or complete deprecation cleanup.

## Corrected priority-addon restart evidence

[Run 36789324560](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36789324560) used the verified core artifact for `62595cf1`, Networks `cda26048`, IE2 `2aa423f8`, Supreme `24ef8251` and FinalTECH `6a6adbe0`. Every input artifact source and digest was checked. This was a four-addon subset, not the complete 45-addon archive; JEG was not part of this subset.

All four addons were required to enable, with zero dependency-gated omissions, across two boots each:

| Server | Observed build/channel | Result |
| --- | --- | --- |
| Paper 1.21.11 / Java 21 | 132 / STABLE | Both boots passed |
| Paper 26.2 / Java 25 | 129 / STABLE | Both boots passed |
| Paper 26.3 / Java 25 | 140 / BETA | Both boots passed |

Actual logs confirmed no detected linkage/enable or configuration-load errors and a clean prior Slimefun shutdown on the second boot. These are startup/restart checks, not machine-throughput or historical inventory-content tests.

The original second-boot log from run `36783227345`, artifact `11128084713`, was retained as a negative control. The old linkage-only check accepted that log despite FinalTECH's `InvalidConfigurationException` and failed en-US.yml load. The new checker accepts its first boot and rejects the faulty second boot. Corrected FinalTECH logs pass the same checker.

Downloaded restart artifacts were independently digest-checked: `11131071030` (1.21.11), `11130844950` (26.2), and `11131295792` (26.3). Their logs, required-addon lists, source SHA-256 records and negative-control results were inspected. The first validation attempt stopped at an omitted Java setup before server execution; selecting Java 25 fixed the harness without weakening any source guard.

## Permanent gates and actual test results

- `verify_runtime_configuration.py` supplements both full-stack boots with configuration-error detection and fails on missing/blank/unreadable evidence. Nine tests protect failure detection, separate boot logs, bounded diagnostics and source immutability.
- `verify_addon_bytecode.py` checks all base classes, including shaded classes, plus multi-release overlays applicable to the requested Java floor. It rejects newer or preview-required classes, invalid/empty archives and ambiguous duplicate entries. Higher multi-release overlays are reported separately, not mistaken for classes loaded by Java 21. Seventeen tests cover these rules and stale-report replacement.
- Both suites are registered in `verify_legacy.py`. The canonical bundle invokes the bytecode checker on each final distributable JAR before uploading it; failures cannot reach the package job. Reports stay outside the one-record-per-addon metadata directory.

[Gate validation run 36789857251](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36789857251) passed all source invariants, all 26 new Python tests and an actual-JAR differential check over the complete archive. Evidence artifact `11131216458` matched SHA-256 `b13520f07027337c4b878ae616c9da237881a92b954f79f7d7787c6f5fa03261`. Reviewed Git blob IDs were checked against local source. Temporary validation/export workflows and transport data are excluded from promotion.

## Actual remaining blocker and release sequence

The candidate archive's `SF_SlimeEasy1.0.5.jar`, source `072be6eda4ddcfd9d4df1c210979fe735fdb1fa3`, contains 389 Java 25 base classes (major 69). The other 44 candidate JARs pass the Java 21 header ceiling. The new checker deliberately rejects this SlimeEasy build; its successful negative-control validation is not approval of the failing bundle.

A separate SlimeEasy native-floor workstream is already active. Do not overwrite it, merely lower class headers, remove the addon, or widen the floor to make packaging green. Adopt its exact reviewed source only after its own floor/native behavior tests pass, then refresh the pin and rebuild.

Next gates: reconcile concurrent addon work, complete the addon updates and forward-upgrade/content tests, rerun the whole candidate set through supported-version checks, and verify the final bundle. Only then bump the Slimefun release version once and publish the exact tested core JAR with its matching full addon ZIP. Individual plugins remain raw installable JARs. No stable release, version bump, master merge, production install or automatic data conversion was performed by this checkpoint.
