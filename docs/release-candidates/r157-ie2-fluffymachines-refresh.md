# Addon bundle r157 — InfinityExpansion2 and FluffyMachines successor releases

This candidate extends the **unpublished** 45-addon r157 branch in PR #617. It does **not** replace or retag published Slimefun Legacy v4.1.81, whose four release assets and 44-addon archive stay byte-identical.

| Addon | v4.1.81 source/release | r157 successor | Tagged commit | Published raw asset SHA-256 |
| --- | --- | --- | --- | --- |
| FluffyMachines | 26.2.21 | [26.2.22](https://github.com/wickidcow/SF_FluffyMachines/releases/tag/v26.2.22) | `274275331f0d3087d96998aa4576ef1cc2b3a1ff` | `ae3eafe860523ec02b0ba435a790aa38c2dc509352eaf2cf5f7890507ec9e54e` |
| InfinityExpansion2 | 2.0.19 | [2.0.21](https://github.com/wickidcow/SF_InfinityExpansion2/releases/tag/v2.0.21) | `94e64236312a8c0b3fb18812143c6a02a806405b` | `7c307269b0e3afe9d32a0c0a5f7f742d8f50d4f55a31b0dfd3befdd55debb633` |

## Scope and safety

- Source pin changes are **only** the two addon commits, applied over PR #617's pending r157 45-addon manifest. The other 43 addon commits, exclusions and existing r156/r157 official raw-release receipts stay unchanged.
- InfinityExpansion2's published-distribution lock and separate native *release-mode* pair lock advance together, including exact tag, commit/tree, official JAR size/hash, URL and version. The immutable historical paired-storage integration fixture is untouched.
- The FluffyMachines source pin selects the tagged 26.2.22 release; the canonical bundle pipeline must build and verify its Java 21 source output. Do not claim exact raw GitHub release-byte identity unless the SHA is compared in CI.
- Release publication remains gated on reproducible core/ZIP, full 45-addon provenance, native paired storage/restart, API floor and Paper/Purpur checks. A PR-only bundle is a test artifact, **not** a published replacement for v4.1.81.
- Next distinct core release should include the validated 45-addon r157 ZIP; optionally produce a separately labeled compatible-addon-only ZIP for the v4.1.81 runtime after exact-source checks, without modifying existing v4.1.81 release assets.

## Published release verification

- FluffyMachines v26.2.22: asset `SF_FluffyMachines26.2.22.jar`, 389,763 bytes.
- InfinityExpansion2 v2.0.21: asset `SF_InfinityExpansion2.0.21.jar`, 1,795,834 bytes.
- Release checks and game-server runtime claims remain **pending** for this exact coordinated r157 candidate; no claim of AlbionMC production verification or automatic historical item migration.
