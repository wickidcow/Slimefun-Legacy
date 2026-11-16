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
| SF_CrystamaeHistoria | `584db74db1dd9d108cf658bdaf2a09c9132b0d47` | `0eb00ea7516be822a3b50b9872400d9892996b8e` | [official v1.0.3](https://github.com/wickidcow/SF_CrystamaeHistoria/releases/tag/v1.0.3); [postmerge native source CI](https://github.com/wickidcow/SF_CrystamaeHistoria/actions/runs/43644942571) |
| SF_Galactifun | `82bdf5254b42a14f38c98590a8b6ebf708c5be22` | `1fff7080cb8bcd00eeb1555f433cc0bf8e99c172` | [official v1.0.8](https://github.com/wickidcow/SF_Galactifun/releases/tag/v1.0.8); [postmerge native source CI](https://github.com/wickidcow/SF_Galactifun/actions/runs/43644986896) |

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

Both addons now have verified **distinct official published versioned release assets**
from the exact Git tag commits above (GitHub release/tag APIs confirmed on
2026-11-16 UTC):

| Addon | Official raw JAR | Published SHA-256 | Size |
| --- | --- | --- | --- |
| CrystamaeHistoria v1.0.3 | `SF_CrystamaeHistoria1.0.3.jar` | `f3da97dc760853f0cd166dd154b977a58f0f404a302087d878ef562bac72a470` | 1,200,147 bytes |
| Galactifun v1.0.8 | `SF_Galactifun1.0.8.jar` | `ee6a5d3816dc228a4b1db02c3961f58df8633166e519b238d3c8a4f366db9d1e` | 1,522,385 bytes |

The asset checksums are **GitHub release metadata** and require independent
hashing of downloaded raw bytes in release provenance acceptance. Building the
same source locally is not proof of a byte-identical official uploaded JAR.
Only an exact-head successful canonical 44-addon build/native full-stack and
postmerge verification can authorize the new core release ZIP. Never overwrite
existing v4.1.80 assets.

## Acceptance

1. Run exact-head main build, addon bundle and downstream Paper 26.2/26.3 full-stack;
   verify all 44 addon source SHAs and byte manifests. No old-release fallback.
2. Validate independent static source/pin tests and that there is no new
   Paper 26.3 deprecation or compatibility failure in either addon.
3. Verify native entity-distance/despawn and restart parity against the
   specific source, with no existing world/item change or data-loss signal.
4. Both addon releases now exist; independently hash their released raw JAR bytes,
   verify official tag ancestry/commit and the matching staged ZIP JAR versions.
   This proof is separate from local source-based rebuild checks and must fail
   closed for missing or mismatched assets. Retain `v4.1.80` and backups.
5. Revalidate Doctor #607 on its own **new exact branch head** after any base change.
   Its historical opt-in repair's real saved-item rollback/restart review remains
   independent and is not satisfied merely by an addon compile fix.

This document is an explicit gating record, not authorization to bypass checks.
