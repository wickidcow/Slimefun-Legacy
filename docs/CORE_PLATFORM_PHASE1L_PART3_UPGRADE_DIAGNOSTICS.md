# Slimefun Legacy 4.1.30 — Core Platform Phase 1L Part 3

## Runtime upgrade diagnostics

Phase 1L Part 3 adds a conservative, read-only runtime upgrade report for server operators:

```text
/sf doctor upgrade
```

The command is intended to answer one operational question after installing or testing a Slimefun Legacy build: **does the currently running server show evidence that should stop or delay an upgrade?**

It does not claim that every possible addon feature has been tested. A `READY` result means that the runtime evidence available to Slimefun contains no current blocker; it is not a blanket compatibility guarantee.

## Evidence reported

The upgrade report combines existing read-only runtime services rather than creating a second source of truth. It reports:

- the running Slimefun version, packaged candidate baseline, and previous-stable baseline;
- detected server software, Minecraft version, Java feature version, and platform support level;
- core readiness, registry finalization, scheduler acceptance, storage readiness, previous shutdown state, and pending database writes;
- missing or disabled required plugin dependencies and provider-alias resolutions;
- Slimefun addon compatibility results;
- active or paused machine failure isolation;
- guarded addon callback failures;
- external integration failures and isolation;
- runtime/provider linkage signals visible through Slimefun's guarded boundaries;
- failed or unsafe chunk lifecycle evidence, block-data load failures, and unknown Slimefun IDs;
- addon API compatibility/registration state and pending post-registration callbacks;
- unresolved evidence from the most recent completed Item Doctor run, when one exists.

The release baseline metadata comes from the existing `compatibility/release-baselines.json` registry. The build copies that same registry into the runtime JAR as `compatibility/release-baselines.json`; Part 3 does not maintain a second version list.

## Status levels

`READY` means no current blocker or warning was observed by the upgrade diagnostic snapshot.

`ATTENTION` means the core can still be running, but one or more conditions deserve operator review before treating the upgrade as clean. Examples include provider aliases, compatibility warnings, an unclean prior shutdown, pending writes, runtime failures, unresolved Item Doctor evidence, or an experimental/best-effort platform.

`BLOCKED` means the current runtime exposes a stronger reason not to treat the upgrade as ready. Examples include an unsupported platform, failed/stopping core state, a scheduler that is no longer accepting tasks, storage that is not ready, missing/disabled required dependencies, an addon that explicitly reports incompatibility, or a running version that does not match the packaged candidate baseline.

## Safety boundary

`/sf doctor upgrade` is observational. It does **not**:

- enable or disable plugins;
- emulate third-party dependencies;
- retry machine circuits or external integrations;
- reload adapters;
- run Item Doctor repairs;
- migrate storage or rewrite database records;
- load or unload chunks;
- parse arbitrary historical server logs and infer causes that Slimefun did not observe;
- change Cargo, Energy, machine processing, recipes, item IDs, research IDs, storage keys, database schemas, or saved-world formats.

Existing focused Doctor commands remain available when an operator intentionally wants a repair, retry, or deeper diagnostic action.

## Compatibility scope

Slimefun Legacy remains the runtime and release target. Paper is the primary supported platform for this release line, with the support levels recorded in the support contract. Cross-fork information, including Gugu, remains compatibility/reference evidence rather than a reason for this command to emulate another core at runtime.
