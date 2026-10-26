# Slimefun Legacy 4.1.78 — Full Addon Refresh Diagnostic

This **unpublished** integration is a child of the successful combined Java core and 45-addon smoke preview in [PR #481](https://github.com/wickidcow/Slimefun-Legacy/pull/481). It keeps the already validated machine-ticker protections and 45-addon roster, then deliberately substitutes the two newly prepared Magic Expansion and JEG source revisions.

## Only source changes in this diagnostic

| Addon | Previous r151 source | New *unreleased* exact source | Candidate plugin version |
| --- | --- | --- | --- |
| SF_MagicExpansion | `7c2c1a927176ebd0aaab4481dcc21a51320f7f10` | `c9be11af6b32f3e0d517803b9362c7c88312b150` | 1.1.19 |
| SF_JustEnoughGuide | `9355dd512f2061be6258c54bc714673b179c6252` | `9107d23c2eaa34e6477f561fb1f42d93c7e50195` | 2.1.74 |

The candidate source is from draft [Magic Expansion #41](https://github.com/wickidcow/SF_MagicExpansion/pull/41) and [JEG #24](https://github.com/wickidcow/SF_JustEnoughGuide/pull/24). Both versions are **not published**. This manifest is limited to an exact-source test branch; it does not overwrite Slimefun Legacy's official r150 or held r151 candidate pins.

## Retained candidate protections

- Three Java-only core machine-load changes: idempotent ticker registration, optional future machine placement cap, optional fair runtime budget. Both limits remain disabled by default and no stored machine is deleted.
- Provisional Networks 1.0.62 and InfinityExpansion2 2.0.16 source-locked storage pair with preview-only receipts. Native storage and Fluffy conservation must still pass; no preview may be eligible for publication.
- BuildingStaff 1.0.36 native dependency projection is still a draft source, not an immutable published binary.
- HotbarPets 1.0.4 opt-in per-pet permission feature defaults disabled.
- No changes to the other 43 source pins, existing SlimeHUD behavior, persisted Black Market receipts, or attached resource-pack files. The disposable four-JVM Black Market probe's **diagnostic-only** r151 source/JAR lock now recognizes exact Magic 1.1.19 preview bytes; master and official candidate #470 retain the published 1.1.18 lock.

## Required before official release

1. Both standalone 1.1.19 and 2.1.74 candidates complete their **own exact-head CI** and merge only after their core feature branches pass.
2. Publish each standalone addon through its authorized release workflow, verifying final source SHA, plugin.yml version, Java 21 class floor, filename and SHA-256 digest. Repeat for Networks/IE2/HotbarPets/BuildingStaff.
3. Repin the official coordinated addon manifest to **final merged published SHAs**, not these draft PR commits; update its native receipts and release revision as part of the approved bundle.
4. Require reproducible identical core JARs, full public API 4.1.77 baseline, all 45 JAR hashes, Paper/Purpur native storage/full stack, and Folia/Leaf readiness for the exact final 4.1.78 head.
5. **Do not tag, publish, advertise a stable release, overwrite v4.1.77, ship a preview ZIP, or work on Bedrock/Geyser.**

This review isolates compatibility of newer maintained Java addon code. A failing publication gate remains a real blocker, not something to suppress.
