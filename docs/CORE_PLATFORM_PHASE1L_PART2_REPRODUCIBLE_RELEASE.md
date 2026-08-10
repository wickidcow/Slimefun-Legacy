# Slimefun Legacy 4.1.30 — Core Platform Phase 1L Part 2

## Reproducible Release Artifact & Source Verification

Phase 1L Part 2 makes the built Slimefun JAR itself part of the release contract. A successful source build is no longer enough: release CI also verifies that the artifact identifies the expected source, preserves the packaging boundary, and can be rebuilt byte-for-byte from the same commit.

## Reproducible archive foundation

Gradle archive tasks now disable file timestamp preservation and use reproducible file ordering. The generated `git.properties` build time is derived from `SOURCE_DATE_EPOCH` rather than the current wall clock. Release CI sets `SOURCE_DATE_EPOCH` from the exact Git commit timestamp and records the full source commit through `SOURCE_COMMIT`.

Local builds that do not provide `SOURCE_DATE_EPOCH` use the Unix epoch for build metadata. This keeps the archive deterministic instead of silently reintroducing wall-clock differences.

## Built-artifact verification

`scripts/verify_release_artifact.py` inspects the finished shaded JAR and verifies:

- embedded `plugin.yml` identifies Slimefun and the active 4.1.30 version;
- embedded `git.properties` identifies the active version and exact source commit when available;
- Slimefun-owned classes remain at or below the Java 21 bytecode target;
- compile-only server and optional plugin APIs are not accidentally bundled;
- private implementation libraries are not present under their original unrelocated package names;
- relocated private-library classes are present under Slimefun's private library namespace;
- the candidate and previous-stable compatibility baseline metadata still agree with the active development line.

The verifier writes machine-readable JSON and a human-readable Markdown summary including the artifact SHA-256, source commit, embedded commit, class count, bytecode target, and previous-stable baseline.

## Primary build gate

The normal `Build English Slimefun` workflow now sets deterministic source metadata and runs the release-artifact verifier after the existing Java 21 bytecode check. A JAR with incorrect embedded version information or a broken packaging boundary is therefore rejected before the normal artifact is uploaded.

## Reproducible Release Candidate workflow

A new manually triggered `Reproducible Release Candidate` workflow performs the stronger release check:

1. check out the exact source commit with full Git metadata;
2. pin `SOURCE_COMMIT` and `SOURCE_DATE_EPOCH` to that commit;
3. run the complete Slimefun Legacy invariant suite;
4. verify that Spotless does not need to modify committed source;
5. perform a first clean release build and inspect the resulting JAR;
6. perform a second clean build from the same source and environment contract;
7. inspect the second JAR;
8. require identical SHA-256 hashes and byte-for-byte equality;
9. upload the candidate JAR and verification evidence as `Slimefun-Reproducible-Release-Candidate`.

A hash mismatch is release-blocking. The workflow does not attempt to rewrite source, create a release, move a tag, or publish an artifact automatically.

## Compatibility and data boundary

Part 2 is build/release infrastructure only. It does not change Slimefun item IDs, recipes, research IDs, Cargo behavior, Energy behavior, machine processing, Guide behavior, database formats, storage schemas, saved-world formats, or addon runtime semantics.

The previous-stable release-blocking baseline remains Slimefun Legacy 4.1.29 at commit `9794baffdd4a96f71fa18ae45ced8bab30982fb0`; the 4.1.15 historical floor remains advisory.
