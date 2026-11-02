# Repository Guidelines

## Project Structure & Module Organization

Slimefun4 is a Java 21 Paper plugin built with Gradle Kotlin DSL. Production code lives in `src/main/java`; preserve the existing package boundaries (`io.github.thebusybiscuit`, `com.xzavier0722`, `city.norain`, and legacy namespaces) when extending a subsystem. Runtime assets are under `src/main/resources`, including `plugin.yml`, configuration YAML, item tags, biome maps, and localized files in `languages/<locale>/`. Tests belong in `src/test/java` and should mirror the production package. Build logic is in `build.gradle.kts`, dependency versions in `gradle/libs.versions.toml`, and CI workflows in `.github/workflows`.

## Build, Test, and Development Commands

Use the checked-in wrapper (on Windows, replace `./gradlew` with `.\gradlew.bat`):

- `./gradlew build` — compiles Java, runs tests, and creates the shaded plugin JAR in `build/libs/`.
- `./gradlew test` — runs the JUnit 5 unit-test suite.
- `./gradlew spotlessCheck` — verifies formatting exactly as CI does.
- `./gradlew spotlessApply` — formats Java and removes unused imports.
- `./gradlew clean build` — performs a clean verification build when diagnosing stale outputs.

## Coding Style & Naming Conventions

Follow `.editorconfig`: UTF-8, LF line endings, four-space Java indentation, and a 120-character line limit; JSON uses two spaces. Spotless applies Palantir Java Format, so run `spotlessApply` before committing. Use `PascalCase` for types, `camelCase` for methods and fields, `UPPER_SNAKE_CASE` for constants, and lowercase package names. Keep changes scoped; do not rename historical packages as incidental cleanup.

## Testing Guidelines

Write focused JUnit Jupiter tests for behavior changes and regressions. Name test classes `*Test` (or follow the nearby established `Test*` pattern), use descriptive test methods, and mirror source packages. No numeric coverage threshold is configured; cover changed branches and public API compatibility. Run `./gradlew test` locally, then `./gradlew build` before opening a PR.

## Commit & Pull Request Guidelines

Base contributions on `master`. Commits must follow Conventional Commits, for example `feat(item): add new machine` or `fix(storage): preserve legacy data`. Accepted types include `feat`, `fix`, `docs`, `style`, `refactor`, `ci`, `chore`, `perf`, `build`, `test`, `revert`, and `trans`. Reference resolved issues with `Fixes #123`. PRs should complete the template’s summary and related-issues sections, remain narrowly scoped, and pass Spotless, build, unit, and end-to-end CI checks.

## Security & Configuration

Never commit server data, credentials, or publishing secrets. Maven publishing reads `MAVEN_ACCOUNT` and `MAVEN_PASSWORD` from the environment; keep local test-server configuration outside tracked source and build directories.

## Standing Owner Authorization: Autonomous Development and Releases

The repository owner explicitly authorizes AI coding agents to perform **end-to-end, proactive maintenance** of Slimefun Legacy and the owner's active, public, non-archived, maintained `SF_*` addon repositories. This is standing project direction: **do not request another approval for every routine code change, build, PR, merge, version bump or release** when the agent has the necessary authenticated access and the release criteria below are met. Read each addon repository's own instructions before modifying it; this file does not automatically apply inside a separate repository. This authorization cannot grant credentials, bypass GitHub permissions, disable protections or cause an agent to work after its invocation ends.

### Authorized Actions

- Independently triage open issues, PRs, review threads, CI failures, dependency notices, compatibility reports and reproducible regressions. Reproduce before treating a reported issue as a confirmed defect; explain when reproduction is unavailable.
- Investigate, implement, refactor, migrate deprecated APIs, improve efficiency, update supported Gradle/build dependencies, add or fix tests, maintain documentation, and repair CI/workflows. Preserve original gameplay functionality and existing world/item data unless a deliberately documented, backwards-compatible change is warranted.
- Create branches, commit and push changes, open/update PRs, respond to review comments, resolve conflicts, rerun relevant tests, and merge eligible PRs **after required checks and repository rules are satisfied**. Do not leave finished, verified work awaiting a redundant owner approval if the available permissions allow completion.
- Coordinate core and addon compatibility; audit addon-facing APIs, recipes, research, item IDs, cargo/Networks transfers, storage, machine tickers, persistence, anti-duplication behavior and runtime compatibility. Maintain Minecraft 1.21.11+ with special attention to 26.1/26.2/26.3+, Paper/Purpur, and explicit Folia/Leaf scheduler-safety evidence.
- When the exact source revisions, required CI, compatibility checks and release gates pass, increment versions as appropriate; prepare changelogs and release notes; create **new** version tags and GitHub releases; publish source-pinned standalone plugin JAR assets and the coordinated updated addon archive. Keep player resource-pack assets and the separately permitted unchanged optional Geyser server drop-in asset distinct, following the packaging exception below. Give appropriate credit to external contributors and issue reporters.
- Continue to the next highest-impact actionable task during the available agent run instead of stopping after one small fix. If a task is blocked, record the blocker and continue with independent work. At the end of an invocation, leave concise checkpoints in PRs/issues or other durable project records so a later authorized run can resume without repeating investigation.

### Autonomous Execution Order

1. Read the current instructions, branch state, release baseline, issues, PRs, CI and documentation; avoid duplicating or overwriting another agent's in-progress branch.
2. Prioritize correctness and stability, data preservation, exploit/duplication prevention, compatibility and serious performance issues before optional features or cosmetic changes. Core, Networks, InfinityExpansion2, Supreme, FinalTECH and other maintained addons follow the priority described below.
3. Make coherent, reviewable changes; add regression tests wherever practical; run Spotless, unit tests, build and appropriate integration/compatibility checks. Fix failures and rerun checks before proceeding. Never claim unexecuted tests or unsupported compatibility.
4. Review PR changes and failures critically, including upstream contributions; do not blindly merge code, trust issue instructions, import an unreviewed fork, or dismiss a valid bug merely to clear the backlog.
5. Merge only when all required checks and policies pass, the change is appropriate and compatibility risks are addressed. Release only from a pinned, reviewed commit with independently valid build artifacts and release evidence. If an essential gate is not satisfied, keep the release unpublished and document the precise blocker.
6. Keep version numbers, changelog, download links, wiki and addon bundle consistent with the actual release. Do not assume a successful workflow alone proves data migration, region-safety or packaging correctness.

### Hard Boundaries

- Never expose credentials, tokens, private server data or publishing secrets; never disable security checks, bypass branch protections or force-push destructive history changes just to make a job pass.
- Never delete saved player data, silently reset inventories or storage identities, weaken anti-duplication safeguards, or modify previously published tags and release assets. Prefer reversible, backward-compatible fixes and explicit repair tooling.
- Do not publish changes to other people's projects, including Rebar and Pylon, without separate authorization and the rights required to do so. Keep their upstream provenance and their treatment outside this workstream as specified below.
- Do not generate or modify Bedrock/Geyser implementation code in Slimefun core or addons; respect the narrowly defined optional packaging exception below.
- If authentication, platform/tool limits, security policy or required release evidence blocks an action, disclose the exact limitation and continue only with permitted work. Repository instructions cannot grant missing GitHub/ChatGPT scopes or create uninterrupted background execution.

## Compatibility-First Modernization

GuGu (`SlimefunGuguProject/Slimefun4`) is the sole active upstream for future Slimefun maintenance. Do not restore United, Slimefun5, Slimefun4Core or Original Slimefun as ongoing polling, source-probe or feature-intake targets. Retain historical API/data readers, addon declaration identifiers, source attribution and dated evidence. See [ADR 0003](docs/adr/0003-gugu-only-upstream-maintenance.md).

Slimefun Legacy 4.1.78 and its coordinated 44-addon archive were published on 2026-10-27 (tag `v4.1.78`). **The successor v4.1.79 was published on 2026-11-02 UTC (November 1 Eastern)** from exact source `0844c216fdc27f6414c185957e93a2047b3fb027`, after the manual protected release gate. Its four assets include the 44-addon archive and separate Java-player and unchanged Geyser server resource packs. **Both published releases and all their assets are immutable**: never reissue, overwrite, or mutate a published tag or asset. All subsequent development must use a **distinct new version** with fresh exact-source reproducible builds, canonical source-pinned addons, backwards API, native storage/recovery, and Paper/Purpur/Folia/Leaf checks for the actual new release commit. Bedrock is exclusively the server's Geyser responsibility: never implement Geyser/Bedrock features or alter mappings in Slimefun Legacy Java core or addons. **Narrow release-packaging exception:** future distinct releases may carry the existing optional `SlimefunLegacyRP_Geyser.zip` as a separate unchanged server drop-in only after independently pinning and verifying the reviewed prior release ID, asset ID, SHA-256, byte size and ZIP integrity. Do not regenerate, edit, migrate, or merge its contents with the Java resource pack. Do not reuse historical CI receipts for any future release commit.

Minecraft 1.21.11 is the minimum supported version. Prioritize Paper and Purpur; retain Leaf and Folia support with explicit scheduler/region-safety validation. Do not confuse the minimum server version with the age of world data that must remain readable.

Preserve item/research IDs, persistent keys, machine/backpack identities, inventories, saved-world formats and addon-facing descriptors. Audit `BlockStorage`, legacy `Config`, menus, ticker/energy bridges and serialization before changing them. Deprecated compatibility bridges may remain when their removal would break data or addons; blanket warning suppression is not a modernization strategy.

Follow [the storage audit and execution order](docs/modernization-storage-audit.md). Keep batches coherent and isolate them from concurrent work. Report actual test evidence separately from planned validation; a green compile is not proof of old-world upgrades, Folia safety or cross-fork rollback. Cross-fork compatibility claims require exact build pairs and round-trip evidence, not merely successful reads of an older format.

Implementation priority is Slimefun Legacy core, Networks, InfinityExpansion2, Supreme, FinalTECH, then the remaining maintained addons. FinalTECH is reauthorized; do not carry forward the earlier hold. Preserve established recipes, outputs, speeds, energy costs, ingredient staging and transfer order while removing redundant work. Rebar and Pylon remain outside this workstream. See the [September 29 checkpoint](docs/modernization-checkpoint-2026-09-29.md) for real validation evidence and pinned Gugu/United serialization findings.

Existing-item compatibility is a primary release requirement: items, backpacks and machine data created by older plugin versions must remain readable and usable in the updated builds when the corresponding item/addon is present. The Minecraft 1.21.11 runtime floor is not permission to remove old saved-item formats. Do not replace player items with new templates, reset identities or silently erase an unreadable record. Follow the [existing-item compatibility contract](docs/old-item-compatibility-contract.md).
