#!/usr/bin/env python3
"""Verify retained Phase 1L Part 2 reproducible release-artifact infrastructure."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

MINIMUM_VERSION = (4, 1, 30)
CURRENT_PHASE = "Core Platform Phase 1L"
PREVIOUS_STABLE_VERSION = "4.1.29"
PREVIOUS_STABLE_REF = "9794baffdd4a96f71fa18ae45ced8bab30982fb0"


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def load_json(root: Path, relative: str) -> dict:
    return json.loads(read(root, relative))


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def project_version(root: Path) -> str:
    match = re.search(r"^projectVersion=(\d+\.\d+\.\d+)$", read(root, "gradle.properties"), re.M)
    return match.group(1) if match else ""


def version_tuple(version: str) -> tuple[int, int, int]:
    return tuple(map(int, version.split(".")))


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    try:
        version = project_version(root)
        require(bool(version), "Part 2 projectVersion is missing", failures)
        if version:
            require(version_tuple(version) >= MINIMUM_VERSION, f"Part 2 requires 4.1.30 or newer, got {version}", failures)

        support = load_json(root, "compatibility/support-contract.json")
        require(support.get("release") == version, "Support contract release must match projectVersion", failures)
        require(support.get("phase") == CURRENT_PHASE, "Support contract phase must retain Core Platform Phase 1L", failures)
        policy = support.get("compatibility_policy", {})
        for key in (
            "reproducible_release_archives",
            "release_artifact_metadata_verification",
            "release_artifact_optional_api_exclusion",
            "release_candidate_double_build_hash_match",
            "release_source_commit_recorded",
        ):
            require(policy.get(key) is True, f"Phase 1L Part 2 policy must remain true: {key}", failures)
        for key in (
            "phase1l_part2_changes_normal_cargo_energy_machine_semantics",
            "phase1l_part2_changes_storage_or_gameplay_semantics",
            "database_format_changed",
            "storage_schema_changed",
        ):
            require(policy.get(key) is False, f"Phase 1L Part 2 policy must remain false: {key}", failures)
        require(
            type(policy.get("gameplay_behavior_changed")) is bool,
            "Active release must explicitly declare whether gameplay behavior changed",
            failures,
        )

        baselines = load_json(root, "compatibility/release-baselines.json")
        require(baselines.get("candidate", {}).get("version") == version, "Candidate baseline must match projectVersion", failures)
        require(baselines.get("previous_stable", {}).get("version") == PREVIOUS_STABLE_VERSION, "Previous stable must remain 4.1.29 until a newer stable release is validated", failures)
        require(
            baselines.get("previous_stable", {}).get("source", {}).get("ref") == PREVIOUS_STABLE_REF,
            "Previous stable 4.1.29 must remain pinned to its validated release commit",
            failures,
        )

        build = read(root, "build.gradle.kts")
        for token in (
            "AbstractArchiveTask",
            "isPreserveFileTimestamps = false",
            "isReproducibleFileOrder = true",
            'environmentVariable("SOURCE_DATE_EPOCH")',
            'environmentVariable("SOURCE_COMMIT")',
            "Instant.ofEpochSecond(sourceDateEpoch)",
            'customProperty("git.build.time", gitBuildTime)',
            'customProperty("git.source.commit", sourceCommit)',
            '"git.commit.id"',
        ):
            require(token in build, f"Reproducible Gradle archive invariant missing: {token}", failures)
        require("LocalDateTime.now()" not in build, "Release metadata must not use the current wall clock", failures)
        require('"git.commit.id.full"' not in build, "Unsupported git.commit.id.full metadata key must not return", failures)

        artifact_verifier = read(root, "scripts/verify_release_artifact.py")
        for token in (
            "FORBIDDEN_EXTERNAL_PREFIXES",
            "FORBIDDEN_UNRELOCATED_PREFIXES",
            "RELOCATED_LIBRARY_PREFIX",
            "git.source.commit",
            "git.commit.id",
            "git.build.version",
            "plugin.yml",
            "jar_sha256",
            "java_bytecode_target",
            "previous_stable_ref",
        ):
            require(token in artifact_verifier, f"Release artifact verifier invariant missing: {token}", failures)
        require("git.commit.id.full" not in artifact_verifier, "Artifact verifier must use standard git.commit.id metadata", failures)

        legacy_verifier = read(root, "scripts/verify_legacy.py")
        phase1l_position = legacy_verifier.find('"verify_core_platform_phase1l.py"')
        part2_position = legacy_verifier.find('"verify_phase1l_release_artifact.py"')
        require(phase1l_position >= 0, "Full Legacy verifier must run the Phase 1L core verifier", failures)
        require(part2_position >= 0, "Full Legacy verifier must run the Phase 1L Part 2 verifier", failures)
        if phase1l_position >= 0 and part2_position >= 0:
            require(phase1l_position < part2_position, "Phase 1L Part 2 verifier must run after the Phase 1L core verifier", failures)

        primary_workflow = read(root, ".github/workflows/build-ci.yml")
        for token in (
            "SOURCE_COMMIT=$GITHUB_SHA",
            "SOURCE_DATE_EPOCH=",
            "verify_release_artifact.py",
            "Verify release artifact metadata and packaging",
            "Slimefun-English-Albion",
        ):
            require(token in primary_workflow, f"Primary build artifact-verification invariant missing: {token}", failures)

        release_workflow = read(root, ".github/workflows/reproducible-release.yml")
        for token in (
            "name: Reproducible Release Candidate",
            "workflow_dispatch:",
            "fetch-depth: 0",
            "SOURCE_COMMIT=$GITHUB_SHA",
            "SOURCE_DATE_EPOCH=",
            "First clean release build",
            "Second clean release build",
            "--no-build-cache",
            "--no-configuration-cache",
            "Require byte-for-byte reproducibility",
            "sha256sum",
            "cmp \"$RUNNER_TEMP/Slimefun-first.jar\" \"$RUNNER_TEMP/Slimefun-second.jar\"",
            "Slimefun-Reproducible-Release-Candidate",
        ):
            require(token in release_workflow, f"Reproducible release workflow invariant missing: {token}", failures)

        core_registry = load_json(root, "compatibility/core-api-registry.json")
        require(core_registry.get("release") == version, "Core API registry release must match projectVersion", failures)
        capabilities = set(core_registry.get("compatibility_capabilities", []))
        require(
            "reproducible-release-artifact-verification" in capabilities,
            "Core API registry must record reproducible release verification capability",
            failures,
        )

        require(
            (root / "docs/CORE_PLATFORM_PHASE1L_PART2_REPRODUCIBLE_RELEASE.md").is_file(),
            "Phase 1L Part 2 documentation is missing",
            failures,
        )
    except Exception as error:
        failures.append(f"Phase 1L Part 2 verifier failed to inspect repository: {error}")

    report = root / "build/reports/phase1l-reproducible-release.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1L Part 2 reproducible release verification: FAIL\n"
            + "\n".join(f"- {failure}" for failure in failures)
            + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    gameplay_changed = support.get("compatibility_policy", {}).get("gameplay_behavior_changed")
    report.write_text(
        "Core Platform Phase 1L Part 2 reproducible release verification: PASS\n"
        "- archive entry ordering and timestamps are reproducible\n"
        "- build metadata uses SOURCE_DATE_EPOCH rather than wall-clock time\n"
        "- CI embeds an explicit full source commit in git.properties\n"
        "- standard git.commit.id metadata is retained for Git plugin provenance\n"
        "- the normal build inspects embedded version, source identity, bytecode and packaging boundaries\n"
        "- the full Legacy verifier retains the Phase 1L Part 2 gate\n"
        "- the release workflow performs two independent clean builds of the exact source commit\n"
        "- build and configuration caches are disabled for the reproducibility comparison\n"
        "- release workflow requires byte-for-byte and SHA-256 equality\n"
        "- 4.1.29 remains the pinned release-blocking previous-stable baseline until a newer stable release is validated\n"
        "- Phase 1L Part 2 itself does not change Cargo/Energy, database, storage-schema or saved-world semantics\n"
        f"- active release gameplay behavior changed is explicitly declared as {str(gameplay_changed).lower()}\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
