# Manual addon release migration

Slimefun Legacy's `SFL_AddonsNDependencies.zip` is moving toward a fully release-backed, digest-verifiable inventory. Maintained `wickidcow/SF_*` releases are refreshed from GitHub Releases and checked against GitHub's recorded SHA-256 asset digest when one is available.

## Goal

Reduce the manual/pinned allowlist to zero without replacing custom server-tested behavior with an unrelated upstream build. A manual JAR leaves this list only after its source provenance is understood, its maintained build passes the Slimefun Legacy compatibility gates, and its GitHub Release contains the exact distributable JAR that the bundle consumes.

## Completed migration

### SF_SlimyTreeTaps

- Former bundle JAR: `SF_SlimyTreeTaps-1.0.0-SNAPSHOT.jar`
- Maintained repository: `wickidcow/SF_SlimyTreeTaps`
- Maintained release: `v1.0.1`
- Release asset: `SF_SlimyTreeTaps1.0.1.jar`
- GitHub SHA-256: `05a04f65d955e0d1aeca2ad076c49ea87260d431f5199a973cc4bdf495ceb183`
- Release source commit: `470b9431b2aea267572f8398db8c68ab1024f010`
- Status: **release-backed / no longer a manual bundle exception**

## Remaining manual/pinned JARs

| Bundle JAR | Current SHA-256 | Source provenance | Migration status |
| --- | --- | --- | --- |
| `SF_ExtraTools vMODIFIED v0.1.jar` | `06854a23fe211cf3d7a1dc6fbf957b2495ba6f73bc00ad7d66b3a5de2ecab710` | Current upstream resolves to `Alessio-Colombo/ExtraTools`; embedded metadata originally references `Sfiguz7/ExtraTools` | **Source exists, custom modifications must be reproduced before replacement** |
| `SF_HotbarPets-MODIFIED.jar` | `49f937b577fe9162807f6dede030f17ac8471f58270b9f67b43ebc736be70af9` | `Slimefun-Addon-Community/HotbarPets` | **Source exists, custom modifications must be reproduced before replacement** |
| `SF_SMG vMODIFIED v0.3.jar` | `f4531af5b20e5448db13a75658bad043329f1d4d95ac6b1894b46b1746102d40` | `waleks647/SMG` | **Source exists, custom modifications must be reproduced before replacement** |
| `SF_betterfarming-Build 1 (git d8b712f).jar` | `da1f90d7118ceab0cf030c1347b23f50ad6665afe0aea0648559e3fe4dbc4f3e` | Embedded metadata references `Gavin296/better-farming`, which currently does not resolve on GitHub | **Source recovery required** |
| `SF_Magic8Ball-Dev.jar` | `ff8b577fd90c5f25319a438f7c96dc9c31f887383b7f311c58b8a84c59fe39f3` | `xMoonGames/Magic-8-Ball`; upstream GitHub release `1.20.6` points to external Build 5 | **Source exists, but upstream GitHub release has no JAR asset/digest** |
| `SF_SoulJars-MODIFIED.jar` | `9c50ed9f5c1f7cb7e2212e9818f0bf1786cdc1c9dfe185eb31d6f0c6198d5464` | `Slimefun-Addon-Community/SoulJars` | **Source exists, custom modifications must be reproduced before replacement** |

## Release criteria for each migration

A manual JAR can be removed from `refresh-addon-bundle.yml` only when all of the following are true:

1. A maintained source repository exists under the `wickidcow` account and retains upstream authorship/license attribution.
2. The custom behavior present in the current pinned JAR is either reproduced from source or intentionally superseded with documented equivalent behavior.
3. The addon builds against current Slimefun Legacy and the Paper 26.2 production baseline using Java 25 tooling while retaining the intended runtime bytecode target.
4. The distributable JAR is structurally validated and does not shade Slimefun core classes accidentally.
5. A stable GitHub Release is created from a known source commit and contains the exact raw JAR asset used by the bundle.
6. The release asset exposes a GitHub SHA-256 digest and the bundle refresher verifies the downloaded bytes against it.
7. The refreshed bundle contains exactly one JAR for that addon and no stale predecessor.

## Recommended migration order

1. **ExtraTools** — small project and upstream source is available; recover the `MODIFIED v0.1` delta first.
2. **HotbarPets** — upstream source is available and the current custom JAR has clear provenance.
3. **SoulJars** — same upstream community lineage as HotbarPets and should be straightforward once the modification-recovery workflow is proven.
4. **SMG / SimpleMaterialGenerators** — upstream exists, then reproduce the `MODIFIED v0.3` behavior.
5. **Magic8Ball** — source exists, but create a maintained GitHub-hosted release asset rather than relying on the external Build 5 host.
6. **BetterFarming** — last because the embedded source repository no longer resolves and requires source recovery before an authoritative rebuild can be claimed.

## Enhanced Guide 4.2 follow-up

The current 4.1.49 candidate work caches guide bookmarks per online player and evicts that cache on quit, eliminating repeated YAML-list-to-`LinkedHashSet` reconstruction during category/search/bookmark rendering.

The next low-risk Enhanced Guide performance candidate is `LegacyRecipeUsageBrowser`: sort and freeze each reverse-usage list once when a per-world index finishes building, then reuse the pre-sorted immutable list when players reopen usage pages. This avoids repeated list copying, item-name resolution, and sorting while preserving the existing opt-in index build and TPS budgets.

## Paper 26.3 preparation

`paper-26.3-advisory.yml` checks the official Paper Maven metadata daily. It remains non-blocking and does not change the production baseline. Once an official `26.3.build.*` Paper API appears, the lane will compile Slimefun Legacy against that API and preserve the log for review before 26.3 is promoted to supported production status.
