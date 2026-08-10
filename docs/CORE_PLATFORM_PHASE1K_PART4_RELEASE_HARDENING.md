# Slimefun Legacy 4.1.29 — Core Platform Phase 1K Part 4

## Release Hardening & Readiness Gate

Phase 1K Part 4 turns the completed dependency and addon-boundary work into a release-readiness contract for 4.1.29.

### Release checks

The final `scripts/verify_phase1k_release_readiness.py` gate verifies that:

- the project, support contract, addon matrix, cross-fork matrix, and core API registry all identify 4.1.29;
- the supported build/runtime contract remains Java 25 with Java 21 bytecode;
- the previous stable 4.1.21 baseline remains release blocking;
- the 4.1.15 historical compatibility floor remains advisory;
- the full Legacy verifier still includes API, addon, dependency, storage, documentation, platform, and compatibility checks;
- the primary build workflow verifies source invariants, runs formatting, builds the shaded JAR, validates Java 21 bytecode, and uploads the artifact;
- the compatibility workflow retains candidate/baseline builds, addon source/binary comparison, and advisory cross-fork probes;
- README build and compatibility badges point to workflow files that actually exist.

### Workflow cleanup

The primary workflow file is now `.github/workflows/build-ci.yml`, matching the existing README build badge. The previous `.github/workflows/build.yml` path was removed so there is one primary build workflow rather than two copies.

### Compatibility boundary

Part 4 is CI/release hardening only. It does not change Slimefun item IDs, recipes, research IDs, Cargo behavior, Energy behavior, machine processing, storage keys, database schemas, saved-world formats, or addon loading semantics.

GuizhanLib remains an internal relocated Slimefun library only. Slimefun does not provide, replace, or emulate `GuizhanLibPlugin`; addons that require that external plugin remain responsible for using the real dependency.
