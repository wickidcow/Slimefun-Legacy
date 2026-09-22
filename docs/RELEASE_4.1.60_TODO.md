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

- [ ] Merge PR #246 — pin the Paper 26.2/26.3 full-stack smoke to the exact addon bundle artifact and source commit under test.
  - Ordinary PR/push/scheduled runs may still validate the latest released canonical bundle.
  - Addon-bundle-triggered runs must consume the exact artifact produced by the triggering workflow.
  - Preserve bundle provenance in CI evidence.
- [ ] After #246 merges, prove the cross-workflow artifact path on master using a fresh maintained-addon bundle build.
  - Require the triggering bundle run ID/head SHA to appear in provenance.
  - Require the exact bundle to boot twice on Paper 26.2 and Paper 26.3.
- [ ] Merge PR #250 — give exact addon-bundle `workflow_run` smokes their own concurrency key so unrelated master pushes cannot cancel the provenance validation.
- [ ] Merge PR #251 — retry only recognized transient Maven repository/network failures in addon compatibility checks so HTTP 429/5xx infrastructure does not masquerade as a required-addon regression.
- [ ] Merge PR #247 — include the active Guide provider in `/sf doctor report`.
  - Match Recovery Center Support & Diagnostics wording.
  - Show `Classic Slimefun Guide` when JEG is absent.
  - Show JEG version plus enabled/disabled state when installed.
- [ ] Merge PR #248 — correct Paper 26.3 maturity wording.
  - Matrix state: `alpha-awaiting-stable`.
  - Generated addon-bundle notes: alpha/pre-release compatibility target.
  - No compatibility-policy or build-logic change.

## Doctor / Recovery Center final pass

- [ ] Run the full Item Doctor static verification after #247 merges.
- [ ] Confirm `/sf doctor report` remains public-safe and contains no paths, IPs, credentials, player data, coordinates, raw config dumps, or raw exception messages.
- [ ] Confirm the Recovery Center remains OP/admin-only at every entry point.
- [ ] Confirm resource-pack actions remain separated:
  - Legacy sender enable/disable;
  - resource-pack item adoption/upgrade;
  - stored-item texture/model repair;
  - exact bundled mapping removal.
- [ ] Confirm destructive/mutating lanes still require explicit confirmation or fingerprint gates.
- [ ] Confirm the Slimefun Doctor wiki link and maintained command list remain reachable from the Recovery Center.
- [ ] Confirm Doctor Next Steps routes unresolved findings to specialist lanes instead of implying the generic repair fixes everything.

## Maintained addon bundle validation

- [ ] Freeze canonical release-bundle source identity before publishing 4.1.60.
  - The current matrix contains 45 maintained addons, but only 7 have explicit `source_commit` pins and 38 still build from repository HEAD.
  - Keep normal compatibility discovery flexible if desired, but the **release bundle** must have an exact source commit recorded and enforced for every shipped addon.
  - Continue embedding resolved commit + JAR SHA-256 metadata inside the addon bundle manifest.
- [ ] Rebuild the canonical `SF_Addons_1.21.11-26.3.zip` after stabilization PRs merge.
- [ ] Require every maintained addon in `compatibility/sfl-addon-release-matrix.json` to compile against the detected Paper 26.3 API.
- [ ] Run required-addon runtime smoke for JEG, BetterChests, FastMachines, Networks, and SlimeTinker.
- [ ] Confirm current pinned JEG and SlimeHUD revisions are the versions actually present in the generated bundle.
- [ ] Confirm no archived/duplicate addons are accidentally shipped.
  - Magic 8 Ball remains core-integrated and excluded as a standalone addon.
  - Historical DracFun remains replaced by DracFun Reborn.
- [ ] Verify canonical addon JAR naming and reject stale/qualified filenames where the bundle policy forbids them.

## Platform and runtime gates

- [ ] Paper 26.2 runtime smoke passes.
- [ ] Paper/Purpur 1.21.11 compatibility passes.
- [ ] Purpur/Folia/Leaf 26.2 runtime smoke passes.
- [ ] Paper 26.3 advisory compile passes against the latest detected official API artifact.
- [ ] Paper 26.3 pre-release runtime smoke boots twice.
- [ ] Paper 26.2 / 26.3 full-stack smoke passes with exact bundle provenance.
- [ ] Velocity modern-forwarding smoke remains green.
- [ ] Public API compatibility remains green against the previous stable release.
- [ ] Runtime gameplay correctness remains green.
- [ ] Required addon runtime smoke remains green.
- [ ] Reproducible release build produces byte-identical JARs.
- [ ] Verify Java 21 bytecode target.

## Release preparation

- [ ] Intentionally roll the development line from 4.1.59 to 4.1.60 in one coordinated version-alignment change.
  - `gradle.properties` project version;
  - `compatibility/release-baselines.json` candidate → 4.1.60 and previous stable → released 4.1.59;
  - `compatibility/core-api-registry.json`;
  - `compatibility/cross-fork-api-matrix.json`;
  - `compatibility/support-contract.json`;
  - `compatibility/addon-compatibility-matrix.json`;
  - README development/release-lifecycle wording where it describes the active candidate.
- [ ] Pin released 4.1.59 commit `3169bb8c67973b16c46316fdc4fe875df88351d1` as the release-blocking previous-stable compatibility baseline for 4.1.60.
- [ ] After the rollover, require compatibility CI job names/summaries to say previous stable 4.1.59 rather than 4.1.58.
- [ ] Prepare `docs/releases/4.1.60.md` only after stabilization PRs and release gates are green.
- [ ] Update `EVERYTHING_THAT_CHANGED.md` with the final 4.1.60 stabilization changes.
- [ ] Require the addon bundle selected by `.github/workflows/reproducible-release.yml` to come from the **same exact source commit** as the core release.
  - The current workflow prefers the newest successful master bundle but does not yet require its `headSha` to equal the release `GITHUB_SHA`.
  - 4.1.60 must not publish a freshly built core JAR beside a bundle validated against an older core commit.
  - If exact-SHA automation is not implemented before release cut, run/validate the canonical addon bundle at the final release commit before invoking release publication.
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
