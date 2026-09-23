# Slimefun Legacy 4.1.60 — Stabilization & Validation Integrity

4.1.60 is a focused stabilization release after 4.1.59. The priority is making the current Recovery Center/Doctor work, maintained addon bundle, and Paper 26.3 compatibility evidence trustworthy before adding more gameplay features.

## Scope rules

- Keep Paper 26.2 as the production baseline until Paper 26.3 has a stable server build and an explicit baseline-promotion decision is made.
- Continue testing Paper 26.3 as the pre-release/candidate compatibility target.
- Do not change existing Slimefun item IDs, research IDs, storage keys, databases, or saved-world formats.
- Do not add automatic data migrations or automatic item-model cleanup.
- Preserve external/combined resource-pack support; Legacy sender state and Slimefun item-model mappings remain independent.
- Avoid unrelated Cargo, Energy, machine recipe, or gameplay behavior changes unless fixing a confirmed regression.
- Keep Java 21 bytecode output while using the maintained Java 25 build/runtime toolchain.

## Stabilization work in progress

- [x] Merge PR #246 — pin the Paper 26.2/26.3 full-stack smoke to the exact addon bundle artifact and source commit under test.
  - Ordinary PR/push/scheduled runs may still validate the latest released canonical bundle.
  - Addon-bundle-triggered runs must consume the exact artifact produced by the triggering workflow.
  - Preserve bundle provenance in CI evidence.
- [x] After #246 merges, prove the cross-workflow artifact path on master using a fresh maintained-addon bundle build.
  - Proven by full-stack workflow run `35733620964`, triggered from addon-bundle run `35729428058`.
  - Trigger source SHA: `d58bc92c153f113b9da8246acb0438497c0679f1`.
  - The exact bundle artifact was downloaded and both Paper 26.2 and Paper 26.3 full-stack boots passed.
- [x] Merge PR #250 — give exact addon-bundle `workflow_run` smokes their own concurrency key so unrelated master pushes cannot cancel the provenance validation.
- [x] Merge PR #251 — retry only recognized transient Maven repository/network failures in addon compatibility checks so HTTP 429/5xx infrastructure does not masquerade as a required-addon regression.
- [x] Merge PR #252 — retry only recognized transient Paper Maven repository failures in the 1.21.11 API compile lane; preserve immediate failure for real compiler/API errors.
- [x] Merge PR #257 — ignore cancelled/failed addon-bundle `workflow_run` completions so missing artifacts from unsuccessful source runs do not create false full-stack failures.
- [x] Merge PR #247 — include the active Guide provider in `/sf doctor report`.
  - Match Recovery Center Support & Diagnostics wording.
  - Show `Classic Slimefun Guide` when JEG is absent.
  - Show JEG version plus enabled/disabled state when installed.
- [x] Merge PR #248 — correct Paper 26.3 maturity wording.
  - Matrix state: `alpha-awaiting-stable`.
  - Generated addon-bundle notes: alpha/pre-release compatibility target.
  - No compatibility-policy or build-logic change.

## Doctor / Recovery Center final pass

- [x] Run the full Item Doctor static verification after #247 merges.
- [x] Confirm `/sf doctor report` remains public-safe and contains no paths, IPs, credentials, player data, coordinates, raw config dumps, or raw exception messages.
- [x] Confirm the Recovery Center remains OP/admin-only at every entry point.
- [x] Confirm resource-pack actions remain separated:
  - Legacy sender enable/disable;
  - resource-pack item adoption/upgrade;
  - stored-item texture/model repair;
  - exact bundled mapping removal.
- [x] Confirm destructive/mutating lanes still require explicit confirmation or fingerprint gates.
- [x] Confirm the Slimefun Doctor wiki link and maintained command list remain reachable from the Recovery Center.
- [x] Confirm Doctor Next Steps routes unresolved findings to specialist lanes instead of implying the generic repair fixes everything.

## Maintained addon bundle validation

- [x] Freeze canonical release-bundle source identity before publishing 4.1.60.
  - The current matrix contains 45 maintained addons, but only 7 have explicit `source_commit` pins and 38 still build from repository HEAD.
  - Keep normal compatibility discovery flexible if desired, but the **release bundle** must have an exact source commit recorded and enforced for every shipped addon.
  - Continue embedding resolved commit + JAR SHA-256 metadata inside the addon bundle manifest.
- [x] Rebuild the canonical `SF_Addons_1.21.11-26.3.zip` after stabilization PRs merge.
- [x] Require every maintained addon in `compatibility/sfl-addon-release-matrix.json` to compile against the detected Paper 26.3 API.
- [x] Run required-addon runtime smoke for JEG, BetterChests, FastMachines, Networks, and SlimeTinker.
- [x] Confirm current pinned JEG and SlimeHUD revisions are the versions actually present in the generated bundle.
- [x] Confirm no archived/duplicate addons are accidentally shipped.
  - Magic 8 Ball remains core-integrated and excluded as a standalone addon.
  - Historical DracFun remains replaced by DracFun Reborn.
- [x] Verify canonical addon JAR naming and reject stale/qualified filenames where the bundle policy forbids them.

## Platform and runtime gates

- [x] Paper 26.2 runtime smoke passes.
- [x] Paper/Purpur 1.21.11 compatibility passes.
- [x] Purpur/Folia/Leaf 26.2 runtime smoke passes.
- [x] Paper 26.3 advisory compile passes against the latest detected official API artifact.
- [x] Paper 26.3 pre-release runtime smoke boots twice.
- [x] Paper 26.2 / 26.3 full-stack smoke passes with exact bundle provenance.
- [x] Velocity modern-forwarding smoke remains green.
- [x] Public API compatibility remains green against the previous stable release.
- [ ] Runtime gameplay correctness remains green.
- [x] Required addon runtime smoke remains green.
- [x] Reproducible release build produces byte-identical JARs at the final release-prep source commit.
  - Proven by reproducible-release run `35767534943` at `3ec34185df90d8fb2a7176bb90b75d3c1fa7a47d`; both clean builds matched byte-for-byte.
- [x] Verify Java 21 bytecode target at the final release-prep source commit.
  - Proven by build run `35767535163` at the same source commit; the explicit `Verify Java 21 bytecode target` step passed.

## Release preparation

- [x] Merge PR #253 — coordinated 4.1.60 candidate rollover after merged-state master validation and the PR's compatibility gates are green.
- [x] Intentionally roll the development line from 4.1.59 to 4.1.60 in one coordinated version-alignment change.
  - `gradle.properties` project version;
  - `compatibility/release-baselines.json` candidate → 4.1.60 and previous stable → released 4.1.59;
  - `compatibility/core-api-registry.json`;
  - `compatibility/cross-fork-api-matrix.json`;
  - `compatibility/support-contract.json`;
  - `compatibility/addon-compatibility-matrix.json`;
  - README development/release-lifecycle wording where it describes the active candidate.
- [x] Pin released 4.1.59 commit `3169bb8c67973b16c46316fdc4fe875df88351d1` as the release-blocking previous-stable compatibility baseline for 4.1.60.
- [ ] After the rollover, require compatibility CI job names/summaries to say previous stable 4.1.59 rather than 4.1.58.
- [x] Prepare `docs/releases/4.1.60.md` only after stabilization PRs and release gates are green.
- [x] Update `EVERYTHING_THAT_CHANGED.md` with the final 4.1.60 stabilization changes.
- [x] Merge PR #254 — ordinary addon-bundle builds no longer mutate already-published Slimefun Legacy releases; release asset publication is owned only by the reproducible-release workflow.
- [x] Merge PR #255 — require the addon bundle selected by `.github/workflows/reproducible-release.yml` to come from the **same exact source commit** as the core release, lock every shipped addon to an exact per-run source SHA, and require explicit manual dispatch for publication after candidate validation.
- [x] Require the addon bundle selected by `.github/workflows/reproducible-release.yml` to come from the **same exact source commit** as the core release.
  - Implemented by PR #255: per-run addon SHA locks, bundle `core_source_commit`, exact `headSha == GITHUB_SHA` selection, and manual-only release publication.
  - The workflow now requires the selected bundle run `headSha` to equal the release `GITHUB_SHA` and verifies the bundle manifest `core_source_commit` against that same SHA.
  - Release-note changes trigger both the canonical addon-bundle build and reproducible candidate validation so the final release-prep commit receives exact-source evidence before manual publication.
- [ ] Publish only the canonical raw core JAR and validated addon bundle artifacts expected by the release workflow.
- [ ] Do not promote Paper 26.3 to the production baseline as part of 4.1.60 unless Paper publishes a stable build and the full promotion checklist is rerun explicitly.

## After 4.1.60

Once this stabilization release is complete, the next roadmap can return to feature work. Preferred order:

1. Doctor/Recovery Center quality-of-life improvements that remain read-only by default.
2. Maintained-addon integration gaps discovered by real server testing.
3. Performance work backed by profiler evidence.
4. New gameplay/content only after compatibility and persistence safety remain green.

## Release title

**Slimefun Legacy 4.1.60 — Stabilization & Validation Integrity**
