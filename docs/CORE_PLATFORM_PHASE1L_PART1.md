# Core Platform Phase 1L — Part 1

## Release lifecycle and stable-baseline rollover

Slimefun Legacy 4.1.30 begins Core Platform Phase 1L by making the just-validated 4.1.29 release the new release-blocking compatibility baseline.

### Development line

- Candidate: **4.1.30**
- Phase: **Core Platform Phase 1L**
- Previous stable baseline: **4.1.29**
- Previous stable commit: `9794baffdd4a96f71fa18ae45ced8bab30982fb0`
- Historical compatibility floor: **4.1.15** (advisory)
- Build/runtime toolchain: **Java 25**
- Slimefun-owned bytecode target: **Java 21**
- Primary platform remains **Paper 26.2 / Minecraft 1.21.11**

## Why this matters

Future required-addon compatibility checks now compare 4.1.30 against the completed 4.1.29 release instead of the older 4.1.21 baseline. A required addon that works against 4.1.29 but fails against the 4.1.30 candidate is therefore classified as a new candidate regression and blocks release.

The 4.1.15 baseline remains available only as a long-term drift signal. Historical-floor failures remain advisory and do not redefine the supported production line.

## Preserved Phase 1K boundaries

Phase 1L keeps the 4.1.29 release-hardening contract intact:

- no hard external plugin dependency is introduced for Slimefun itself;
- optional plugin APIs remain isolated from the runtime package contract;
- third-party plugins are not installed, enabled, replaced, impersonated, or emulated by Slimefun;
- Gugu, Original, and United remain advisory/reference compatibility probes rather than alternate runtime cores;
- provider aliases remain descriptor-level evidence and are not treated as Java/API compatibility proof;
- full Legacy verification and required-addon regression checks remain release gates.

## Runtime and data safety boundary

Part 1 is a release-lifecycle and CI metadata change. It intentionally does **not** alter normal Slimefun Cargo, Energy, machine, recipe, guide, database, storage-schema, item-ID, or saved-world behavior.

## Planned Phase 1L direction

Part 1 establishes the baseline rollover foundation. Later Phase 1L work can build on it with reproducible release verification, operator-facing upgrade diagnostics, and safer reviewed upstream intake without weakening Legacy compatibility guarantees.
