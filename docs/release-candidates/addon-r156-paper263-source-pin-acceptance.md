# Addon r156: Paper 26.3 source-pin acceptance (test candidate)

## Why this candidate exists

On 2026-11-14, Slimefun Legacy Doctor draft PR #607 failed its canonical 44-addon bundle on
the older pinned CrystamaeHistoria and Galactifun `setRemoveWhenFarAway` calls.
A retry of the exact same core branch on 2026-11-14 evening (bundle run
[43636523541, attempt 2](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/43636523541))
**failed again** on pinned CrystamaeHistoria `ArmourStandUtils.java:24` under
Paper 26.3 `-Werror`. The run explicitly checks out the matrix's fixed
`SOURCE_COMMIT`: merely merging the addon master branch or rerunning an unchanged
source matrix does **not** update the pinned source.

## Exact source selection

Only these two entries change relative to canonical r155:

| Addon | r155 source | Candidate r156 merged source | Evidence |
| --- | --- | --- | --- |
| SF_CrystamaeHistoria | `584db74db1dd9d108cf658bdaf2a09c9132b0d47` | `d04f6b147580bc31426a96e894d65a72c5088f48` | [postmerge 43644942571](https://github.com/wickidcow/SF_CrystamaeHistoria/actions/runs/43644942571) |
| SF_Galactifun | `82bdf5254b42a14f38c98590a8b6ebf708c5be22` | `c52a1a2c705e58ba3adafb0e8cab6171358efd80` | [postmerge 43644986896](https://github.com/wickidcow/SF_Galactifun/actions/runs/43644986896) |

Both referenced addon postmerge workflows completed successfully, including two-process
native Paper 26.3 fixture/probe coverage recorded in their respective PRs (#11 and #13).
This is evidence for those particular tested behaviors, **not** universal historical
world, every addon, Folia, or production player-data coverage.

## Strict boundary

This is a **source-only test candidate**: the updated matrix causes CI to compile and
test the exact two merged source revisions. The remaining 42 addon pins, released
Slimefun Legacy `v4.1.80`, the public addon ZIP, resource packs, and historical
storage release/fixture locks are **unchanged**. A candidate CI artifact must not be
called or published as a stable addon release.

The two addons have **not** yet published separate, new immutable versioned raw JAR
releases for these fixes. Do not treat raw rebuilt JARs retaining older plugin
version numbers as already published distributables or overwrite the existing
release ZIP. Their official release workflows and checksum/provenance checks must
produce verifiable, distinct versions first, and any later production bundle must
be sourced from and validated against those exact releases.

## Acceptance

1. Run exact-head main build, addon bundle and downstream Paper 26.2/26.3 full-stack;
   verify all 44 addon source SHAs and byte manifests. No old-release fallback.
2. Validate independent static source/pin tests and that there is no new
   Paper 26.3 deprecation or compatibility failure in either addon.
3. Verify native entity-distance/despawn and restart parity against the
   specific source, with no existing world/item change or data-loss signal.
4. Separately complete both addons' official immutable versioned releases.
   Only then consider promoting these pins and packaging a new stable addon ZIP;
   retain `v4.1.80` release assets and their backups.
5. Revalidate Doctor #607 on its own **new exact branch head** after any base change.
   Its historical opt-in repair's real saved-item rollback/restart review remains
   independent and is not satisfied merely by an addon compile fix.

This document is an explicit gating record, not authorization to bypass checks.
