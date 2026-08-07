# Slimefun Legacy 4.1.22 — Core Platform Phase 1D

## Stability and compatibility lifecycle

- Replaced the stale release-blocking 4.1.15 addon baseline with **4.1.21**, the previous stable Legacy release.
- Added a central `compatibility/release-baselines.json` registry so the active baseline is no longer duplicated in workflow YAML.
- Kept **4.1.15** as a separate advisory historical compatibility floor instead of deleting it.
- Unified the public API workflow and addon compatibility workflow around the same pinned previous-stable baseline.

## Better regression classification

- Required addons now block a candidate only when they build against 4.1.21 and fail against 4.1.22.
- A failure against the historical 4.1.15 floor is reported as compatibility drift and remains advisory.
- Baseline source refs are pinned for reproducible CI behavior.
- Added lifecycle summaries showing the candidate, previous stable baseline, and historical floor used by each run.

## Wider addon coverage

- Retains required Legacy probes for FastMachines, Networks Expansion, SlimeTinker IE2, and BetterChests.
- Retains representative checks for Networks, Infinity Expansion 2, DynaTech, Supreme, Magic Expansion, FluffyMachines, FastMachines, and SlimeTinker.
- Adds advisory Gugu checks for FoxyMachines, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, and EMCTech.

## Future-proof verification

- Phase 1A-1C verifiers now accept future releases without manually adding every new version number.
- Added a permanent Phase 1D verifier for baseline lifecycle consistency.
- Candidate Paper API compilation stays available as an early-warning, non-blocking compatibility probe.

## Compatibility

- No existing addon API signatures were removed.
- No item IDs, research IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.
- Paper 26.2 / Minecraft 1.21.11 remains the primary production target, Purpur remains supported, and Folia remains experimental.
