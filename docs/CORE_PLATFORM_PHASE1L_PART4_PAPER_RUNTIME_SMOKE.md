# Slimefun Legacy 4.1.30 — Core Platform Phase 1L Part 4

## Paper 26.2 runtime smoke verification

Phase 1L Part 4 adds an actual Paper boot test to the release-safety layer. Existing Slimefun Legacy verification already checks source invariants, protected addon/API compatibility, bytecode level, packaging boundaries, reproducibility, and runtime diagnostic wiring. Part 4 adds the missing question: **can the built candidate JAR actually enable, report its upgrade state, shut down cleanly, and start again on the supported Paper line?**

The workflow is `.github/workflows/runtime-smoke.yml`. It runs on pull requests, relevant pushes to `master`/`main`, manual dispatch, and a weekly schedule.

## Runtime used

The smoke harness targets the supported production line recorded by Slimefun Legacy:

- Minecraft/Paper: `26.2`
- Java: `25`
- Paper release channel: `STABLE`

The harness queries PaperMC's current downloads service for the latest stable Paper 26.2 build rather than hard-coding an old server build. This intentionally turns new stable Paper builds into compatibility signals while avoiding alpha/beta builds.

## Two-boot lifecycle test

`scripts/paper_runtime_smoke.sh` creates an isolated temporary server and performs two complete boot cycles with the candidate Slimefun JAR.

### First boot

The harness verifies that:

- Paper reaches its normal `Done` startup state;
- Slimefun Legacy 4.1.30 is observed enabling;
- no Slimefun enable/load failure is reported;
- `sf doctor upgrade` produces the Phase 1L upgrade-readiness report;
- the report does not classify the fresh supported runtime as `BLOCKED`;
- the server accepts a normal `stop` and exits successfully.

### Second boot

The same server directory is started again. In addition to the first-boot checks, the second cycle requires the upgrade diagnostics to observe the previous Slimefun shutdown as clean. This exercises the storage/lifecycle persistence path that a compile-only test cannot cover.

## Safety boundary

The runtime smoke test is CI-only verification. It does not:

- connect to or modify a production Minecraft server;
- migrate a real world or player database;
- publish or release the candidate JAR;
- enable automatic core or addon updates;
- change Cargo, Energy, machine, recipe, research, storage, or saved-world semantics;
- promote experimental Paper builds to the supported runtime line.

The temporary CI world and server files are disposable. Only concise console logs and smoke-test evidence are uploaded for diagnosis.

## Release interpretation

A passing smoke test proves that the exact candidate can complete this narrow two-boot scenario on the current stable Paper 26.2 build. It is stronger evidence than compilation alone, but it is not a guarantee that every gameplay feature or third-party addon works. Addon compatibility CI, protected API checks, reproducible release verification, and production staging remain separate gates.
