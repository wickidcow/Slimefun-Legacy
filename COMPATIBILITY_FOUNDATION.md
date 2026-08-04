# Slimefun Legacy 4.1.16 — Compatibility Foundation

> **4.1.17 inheritance:** Slimefun Legacy 4.1.17 retains this complete platform, bytecode, data-format, and gameplay compatibility contract. It adds only the optional addon-facing Doctor service API and does not change Slimefun storage formats.

Slimefun Legacy 4.1.16 is a maintenance-only release that formalizes the project's compatibility boundaries. It intentionally does not change gameplay, machine behavior, item IDs, saved data, or the database format.

## Supported platform contract

| Area | 4.1.16 contract |
| --- | --- |
| Primary server | Paper 26.2 / Minecraft 1.21.11 |
| Secondary server | Purpur based on Paper 26.2 |
| Experimental server | Folia based on Paper 26.2 |
| Build toolchain | Java 25 |
| Supported production runtime | Java 25 |
| Slimefun-owned bytecode | Java 21 maximum |
| Paper API compile baseline | `1.21.11-R0.1-SNAPSHOT` |
| Public plugin identity | `Slimefun` |
| Data format | Unchanged |
| Gameplay behavior | Unchanged |

The machine-readable source of truth is [`compatibility/support-contract.json`](compatibility/support-contract.json).

### About `api-version: '1.16'`

The Bukkit descriptor value remains `1.16` to preserve historical material handling and addon behavior. It is not a promise that Minecraft 1.16 is supported. The tested platform contract above and CI are the support boundary.

## Compatibility gates

### Public API surface

The public API workflow now:

- builds the candidate JAR;
- records every compatibility-protected public JVM signature;
- downloads a released baseline JAR;
- reports added and removed signatures;
- fails on an unapproved removal;
- fails if `javap` cannot inspect an API class instead of silently skipping it;
- publishes the candidate surface even when no released baseline exists.

Intentional removals require an exact entry in `scripts/api-removal-allowlist.txt` and should be accompanied by migration documentation.

### Java bytecode target

`scripts/check_bytecode_target.py` inspects the class-file headers inside the shaded JAR. Slimefun-owned classes must remain at Java 21 bytecode or lower even though CI and the supported server runtime use Java 25.

This protects addon tooling and prevents an accidental compiler configuration change from silently producing Java 25-only Slimefun classes.

### Sensitive dependency boundaries

`scripts/check_dependency_boundaries.py` prevents sensitive direct dependencies from spreading to new source files. The 4.1.16 baseline covers:

- Dough;
- GuizhanLib;
- WorldEdit;
- HikariCP;
- bStats;
- Unirest;
- CraftBukkit and Minecraft server internals.

Removing imports is always allowed. Adding a new importing file or increasing an existing file's sensitive import count fails verification until the architecture change is reviewed and the baseline is deliberately updated.

Direct CraftBukkit and NMS imports have a zero-import budget.

### Deprecation visibility

Compatibility CI compiles with `-Xlint:deprecation` and publishes:

- the complete compiler log;
- a normalized Markdown report grouped by source file.

The report is informational in 4.1.16. This distinction matters because deprecated public compatibility bridges may need to remain available for addons even after Legacy stops using them internally.

### Candidate Paper API compile

The Gradle build accepts an optional Paper API override:

```bash
./gradlew clean compileJava -PpaperApiVersion=1.21.11-R0.1-SNAPSHOT
```

Repository administrators can define the GitHub Actions variable `PAPER_API_CANDIDATE` to test a future Paper API. That job is intentionally non-blocking while the candidate API is unstable, but it gives early notice of source incompatibilities.

An optional `API_BASELINE_TAG` repository variable can pin the public API comparison to a specific GitHub release tag. Without it, the latest release is used.

### Addon compatibility matrix

The weekly and manually triggered compatibility workflow now performs a controlled two-JAR comparison for every addon:

1. check out the pinned 4.1.15 source, run its own `spotlessApply`, and build the known-good baseline JAR from commit `493587431dc831d4b8bc38649af6e22df74a15b0`;
2. build the addon in a fresh checkout copy against that known-good baseline JAR;
3. only after that succeeds, build a second fresh copy against the candidate Slimefun Legacy JAR produced by the current workflow;
4. publish the baseline log, candidate log, machine-readable JSON result and Markdown summary separately.

Only the core dependencies whose artifact name is exactly `Slimefun` or `Slimefun4` are replaced. Dependencies such as SlimefunTranslation, InfinityExpansion, InfinityLib and other Gugu addons remain untouched even when their Maven group contains the word `Slimefun`.

Results are classified as:

- `PASS` — the addon builds against both the known-good baseline and candidate;
- `BASELINE_BUILD_FAILED` — the addon also fails against 4.1.15, indicating an addon dependency, repository or build-environment problem rather than a new Legacy regression;
- `LEGACY_COMPATIBILITY_FAILED` — the addon builds against 4.1.15 but fails against the candidate, indicating a genuine candidate API regression;
- `INSTRUMENTATION_ERROR` — checkout or dependency-replacement infrastructure could not complete the comparison.

Required targets are release-blocking:

- `wickidcow/SF_FastMachines`;
- `wickidcow/SF_NetworksExp` targeting `2.1.112-Legacy-Alpha1`;
- `wickidcow/SF_SlimeTinkerIE2`;
- `wickidcow/SF_BetterChests`.

A curated set of public `SlimefunGuguProject` addons is also compiled as an advisory compatibility probe. Gugu failures remain visible in the GitHub Actions matrix and publish individual build logs, but archived or independently changing Gugu projects do not block a Slimefun Legacy release.

The required Networks target is the maintained Slimefun Legacy fork `wickidcow/SF_NetworksExp`. CI tracks its active `master` branch but requires the declared Gradle version to remain `2.1.112-Legacy-Alpha1`, so fixes within Alpha 1 can advance without silently switching the compatibility contract to a different release.

The Gugu advisory set includes FluffyMachines, FoxyMachines, SlimeTinker, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, and EMCTech. Matrix concurrency is limited to four addon builds at a time to reduce remote-service and runner pressure.

## Verification

Run the complete source checks with:

```bash
python3 scripts/verify_legacy.py .
```

Build and verify the JAR with:

```bash
./gradlew spotlessApply --no-daemon
./gradlew clean build --no-daemon
python3 scripts/check_bytecode_target.py build/libs/Slimefun-4.1.16.jar --expected-java 21
```

Generate the deprecation report with:

```bash
mkdir -p build/reports
./gradlew clean compileJava -PslimefunDeprecationReport=true --no-daemon 2>&1 \
  | tee build/reports/deprecation-compile.log
python3 scripts/summarize_deprecations.py build/reports/deprecation-compile.log
```

## Deliberately excluded from 4.1.16

This release does not add:

- automatic plugin or addon downloads;
- automatic JAR replacement;
- database migrations;
- guide changes;
- machine logic changes;
- new storage backends;
- Cargo or energy behavior changes;
- new Folia cross-region transactions.

Those areas remain separate, reviewable projects built on top of this compatibility foundation.
