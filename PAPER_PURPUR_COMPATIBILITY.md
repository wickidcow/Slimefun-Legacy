# Paper and Purpur Compatibility Maintenance

Slimefun Legacy treats **Paper** and **Purpur** as its primary server platforms. Folia remains a supported secondary target, but compatibility changes must preserve normal Paper behavior first.

## Current maintenance layer

This maintenance layer adds three low-risk fixes inspired by active Slimefun 5 work while retaining Slimefun Legacy's existing API packages and addon behavior:

- Defensive reading of the `doLimitedCrafting` gamerule so a Paper/Purpur API transition cannot crash Auto-Crafter interaction handling.
- A plain-text fallback for `/sf versions` if rich Adventure component delivery fails.
- A profiler cycle guard that prevents empty reports when a new profiling cycle starts before the previous report finishes.

It also adds `scripts/verify_legacy.py`, which runs every English, API, storage, Folia, Enhanced Guide, Gugu sync, upstream-health, and Paper/Purpur compatibility invariant from one command.

The Gugu sync workflow now proves upstream health before importing code. Failed, pending, missing, or unavailable upstream checks are blocked by default, and any manual draft-only override requires a written reason.

## Slimefun 5 review policy

Slimefun 5 is monitored as a source of modern fixes, not as a replacement codebase. Changes should be ported only when they:

1. Solve a reproducible Paper/Purpur, addon, storage, guide, or performance problem.
2. Can be adapted without relocating Legacy's public API packages.
3. Preserve existing addon binary compatibility.
4. Pass the full Legacy verification suite and Gradle tests.
5. Do not rewrite stable systems merely to match another fork's architecture.

## Core-correctness audit

The focused core audit also ports recipe-amount-correct multiblock consumption, all-match multiblock dispatch, synchronized Energy Regulator ticks, Multi Tool ID-mode migration and backpack identity diagnostics. See [`CORE_CORRECTNESS_AUDIT.md`](CORE_CORRECTNESS_AUDIT.md) for the full disposition of reviewed Slimefun 5, United and Gugu changes.

## Compatibility Maintenance Round 2

Legacy now uses supported Paper `DamageSource` calls for internally generated combat damage and current WorldEdit vector accessors. The deprecated CS-CoreLib `Config` type remains available only as an addon compatibility surface and is no longer marked for removal from this fork. Reflection tests lock the historical ticker, energy and BlockStorage signatures in place while the core continues using modern storage containers.

Gradle storage tests now opt into Java 25 native access for SQLite JDBC, avoiding the restricted-native-access warning during CI. See [`COMPATIBILITY_MAINTENANCE_ROUND2.md`](COMPATIBILITY_MAINTENANCE_ROUND2.md).

## Compatibility Foundation (4.1.16)

The primary tested line is **Paper 26.2 / Minecraft 1.21.11 on Java 25**, with Purpur based on that Paper line supported and Folia remaining experimental. Slimefun-owned classes continue to target Java 21 bytecode.

The build now publishes public API surfaces, blocks unapproved signature removals, verifies bytecode class versions, prevents sensitive direct dependency imports from spreading, records deprecation warnings, and optionally compiles against a future Paper API supplied through the `PAPER_API_CANDIDATE` repository variable. See [`COMPATIBILITY_FOUNDATION.md`](COMPATIBILITY_FOUNDATION.md).

## Validation

Run:

```bash
python3 scripts/verify_legacy.py .
./gradlew spotlessCheck test build --no-daemon
```

The Gugu upstream synchronization workflow runs the same complete verifier before it opens or updates its draft pull request.
