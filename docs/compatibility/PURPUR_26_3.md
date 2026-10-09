# Purpur 26.3 experimental compatibility milestone

**Status: candidate / CI validation. Not yet production certified.**

Purpur publishes Minecraft 26.3 as **experimental**. Slimefun Legacy continues to support Minecraft **1.21.11 and newer**, retaining existing Paper/Purpur 1.21.11 and Paper/Purpur/Folia/Leaf 26.2 checks. This milestone adds a **Purpur-only 26.3** validation lane, without assuming Folia or Leaf 26.3 support.

## Official baseline

- Purpur official 26.3 **build 2646** (experimental), from [Purpur Downloads](https://purpurmc.org/download/purpur/26.3).
- The test harness queries the official `https://api.purpurmc.org/v2/purpur/26.3` metadata and refuses a pinned build absent from its published build list.
- The server binary is downloaded directly from the exact official version/build endpoint; the build number and Java runtime are written to the CI artifacts.
- Java 25 is used for the build and server runtime. Slimefun Legacy's distributable remains Java 21-compatible bytecode, with compilation targeting the latest official Paper 26.3 API.

## Acceptance criteria

The [Purpur 26.3 Experimental Runtime Milestone](../../.github/workflows/purpur-26.3-runtime-smoke.yml) workflow must:

1. Verify the supported version matrix; reject Folia/Leaf 26.3 until real builds and dedicated tests exist.
2. Run Slimefun Legacy compatibility invariants and build a production JAR against the real Paper 26.3 API.
3. Verify its produced JAR targets Java 21 bytecode and passes the release artifact integrity checker.
4. Download the **pinned official Purpur 26.3 build 2646**, boot a disposable test server twice, and observe Slimefun enabling.
5. Run `/sf doctor upgrade` during both boots, reject a `BLOCKED` readiness result, verify a clean prior shutdown on the second boot, and reject abnormal enable or shutdown failures.
6. Publish runtime build metadata, console logs and smoke results as GitHub Actions artifacts.

Green checks indicate an initial **core-only** Purpur 26.3 runtime compatibility milestone, not validation of every Slimefun addon or the complete AlbionMC plugin stack. Canonical addon-bundle runtime verification on Purpur 26.3 is a separate follow-on milestone.

## Production safety

Do **not** run an experimental 26.3 build directly against the only copy of a production world. Back up all world folders, plugin data and configs; test on a clone first. Minecraft 26.3 upgrades are not safely reversible to earlier world formats. Keep the Paper/Purpur 26.2 and 1.21.11 compatibility lanes in place.

Official status: [Purpur 26.3 downloads](https://purpurmc.org/download/purpur/26.3) and [PaperMC 26.3 upgrade notice](https://papermc.io/news/26-3/).
