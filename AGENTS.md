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

## Compatibility-First Modernization

Minecraft 1.21.11 is the minimum supported version. Prioritize Paper and Purpur; retain Leaf and Folia support with explicit scheduler/region-safety validation. Do not confuse the minimum server version with the age of world data that must remain readable.

Preserve item/research IDs, persistent keys, machine/backpack identities, inventories, saved-world formats and addon-facing descriptors. Audit `BlockStorage`, legacy `Config`, menus, ticker/energy bridges and serialization before changing them. Deprecated compatibility bridges may remain when their removal would break data or addons; blanket warning suppression is not a modernization strategy.

Follow [the storage audit and execution order](docs/modernization-storage-audit.md). Keep batches coherent and isolate them from concurrent work. Report actual test evidence separately from planned validation; a green compile is not proof of old-world upgrades, Folia safety or cross-fork rollback. Cross-fork compatibility claims require exact build pairs and round-trip evidence, not merely successful reads of an older format.
