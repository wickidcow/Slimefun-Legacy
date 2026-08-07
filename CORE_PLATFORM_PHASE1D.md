# Core Platform Phase 1D

Phase 1D makes Slimefun Legacy compatibility checks follow the release lifecycle instead of remaining tied to an old hard-coded build. The runtime and addon APIs from Phases 1A-1C remain unchanged.

## Rolling regression baseline

- Added `compatibility/release-baselines.json` as the single source of truth for candidate, previous-stable, and historical-floor compatibility baselines.
- Slimefun Legacy 4.1.22 uses **4.1.21** as the release-blocking previous-stable baseline.
- The previous-stable baseline is pinned to a Git ref so CI tests a reproducible core rather than whichever branch happens to be current.
- API compatibility and addon source/binary compatibility now read the same baseline registry.

## Historical compatibility floor

- Slimefun Legacy **4.1.15** is retained as a separate historical floor.
- Historical-floor comparisons are advisory and never block a release by themselves.
- This distinguishes a new regression from long-term ecosystem drift.

## CI lifecycle hardening

- Required addon failures only block release when the addon builds against the previous stable core and fails against the candidate.
- Weekly/manual CI also compares the candidate with the historical floor for visibility.
- Expanded the advisory addon matrix with additional Gugu ecosystem projects.
- Candidate Paper API probing remains non-blocking and continues to expose future Paper breakage early.

## Forward-compatible verification

- Phase 1A, 1B, and 1C source verifiers no longer require an explicit allow-list entry for every future Legacy release.
- They now verify that the current release is at least the phase's introduction version and that the original phase release documentation remains present.
- Phase 1D adds a permanent lifecycle verifier that prevents workflow YAML from silently reintroducing a hard-coded active baseline.

## Compatibility promise

Phase 1D changes release engineering and compatibility verification only. It does not rename or remove addon APIs, item IDs, recipes, storage keys, database structures, saved-world formats, or gameplay behavior.

## Next platform work

The next compatibility layer can build on this lifecycle foundation with optional capability adapters for Pylon/Rebar machine, storage, cargo, and energy endpoints plus a unified runtime failure registry.
