#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1A source invariants."""

from __future__ import annotations

import json
import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "CORE_PLATFORM_PHASE1A.md",
        "SLIMEFUN_LEGACY_4.1.19.md",
        "docs/PLATFORM_COMPATIBILITY_API.md",
        "docs/adr/0001-capability-based-platform-compatibility.md",
        "docs/adr/0002-reviewed-multi-fork-upstream-intake.md",
        "compatibility/upstream-sources.json",
        "compatibility/core-feature-backlog.json",
        "scripts/check_upstream_candidates.py",
        ".github/workflows/upstream-candidate-radar.yml",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/MinecraftVersionNumber.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformCapability.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformCompatibilityService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformFamily.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformProfile.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformSupportLevel.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultPlatformCompatibilityService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/RuntimePlatformDetector.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/platform/TestMinecraftVersionNumber.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/platform/TestPlatformProfile.java",
    )
    for relative in required_files:
        require((root / relative).is_file(), f"Missing Phase 1A file: {relative}", failures)

    try:
        version_number = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/MinecraftVersionNumber.java",
        )
        for token in (
            "@SlimefunAPI",
            "implements Comparable<MinecraftVersionNumber>",
            "Optional<MinecraftVersionNumber> parse",
            "Snapshot identifiers",
            "boolean isAtLeast",
        ):
            require(token in version_number, f"MinecraftVersionNumber invariant is missing: {token}", failures)

        platform_service = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformCompatibilityService.java",
        )
        for token in (
            "@SlimefunAPI",
            "PlatformProfile getProfile()",
            "boolean supports",
            "isMinecraftVersionAtLeast",
        ):
            require(token in platform_service, f"Platform API invariant is missing: {token}", failures)

        implementation = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultPlatformCompatibilityService.java",
        )
        detector = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/RuntimePlatformDetector.java",
        )
        require("@SlimefunInternal" in implementation, "Platform service must remain internal", failures)
        for token in (
            "PlatformFamily.PURPUR",
            "PlatformFamily.FOLIA",
            "PlatformFamily.PAPER_DERIVATIVE",
            "PlatformSupportLevel.BEST_EFFORT",
            "REGION_OWNED_EXECUTION",
            "DATA_COMPONENT_API",
            "getChunkAtAsync",
        ):
            require(token in detector, f"Platform detector invariant is missing: {token}", failures)

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "new DefaultPlatformCompatibilityService()",
            "platformCompatibilityService.initialize(getServer())",
            "getPlatformCompatibilityService()",
            "MinecraftVersionNumber serverVersion",
            "private static final int RECOMMENDED_JAVA_VERSION = 21;",
        ):
            require(token in slimefun, f"Slimefun integration invariant is missing: {token}", failures)
        initialize = slimefun.find("platformCompatibilityService.initialize")
        version_check = slimefun.find("if (isVersionUnsupported())")
        require(
            initialize >= 0 and version_check >= 0 and initialize < version_check,
            "Platform compatibility service must initialize before the Minecraft support check",
            failures,
        )

        extended = read(root, "src/main/java/city/norain/slimefun4/SlimefunExtended.java")
        require(
            "MinecraftVersionNumber.parse(server.getMinecraftVersion())" in extended,
            "SlimefunExtended still duplicates Minecraft version parsing",
            failures,
        )

        versions = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java",
        )
        for token in (
            "Compatibility profile",
            "Detected capabilities",
            "getPlatformCompatibilityService()",
            "PlatformCapability::getDisplayName",
        ):
            require(token in versions, f"/sf versions diagnostic invariant is missing: {token}", failures)

        workflow = read(root, ".github/workflows/upstream-candidate-radar.yml")
        for token in (
            "schedule:",
            "workflow_dispatch:",
            "scripts/check_upstream_candidates.py",
            "actions/upload-artifact@v4",
            "permissions:\n  contents: read",
        ):
            require(token in workflow, f"Upstream radar workflow invariant is missing: {token}", failures)

        checker = read(root, "scripts/check_upstream_candidates.py")
        for token in (
            "never merges",
            "--offline",
            "api.github.com/repos",
            "UPDATE_AVAILABLE",
            "This report is advisory",
        ):
            require(token in checker, f"Upstream checker invariant is missing: {token}", failures)
    except FileNotFoundError as error:
        failures.append(f"Unable to inspect missing file: {error}")

    try:
        upstream = json.loads(read(root, "compatibility/upstream-sources.json"))
        source_ids = {entry["id"] for entry in upstream["sources"]}
        require(upstream.get("schema_version") == 1, "Unexpected upstream source schema", failures)
        require(
            source_ids
            == {
                "slimefun-original",
                "slimefun-gugu",
                "slimefun5",
                "slimefun-united",
                "slimefun4core",
            },
            "Upstream source registry is incomplete",
            failures,
        )
        require(
            upstream["policy"].get("automatic_merges") is False,
            "Upstream registry must forbid automatic merges",
            failures,
        )
        require(
            upstream["policy"].get("preserve_saved_data") is True,
            "Upstream registry must preserve saved data",
            failures,
        )

        backlog = json.loads(read(root, "compatibility/core-feature-backlog.json"))
        features = {entry["id"]: entry for entry in backlog["features"]}
        for feature_id in (
            "capability-platform-api",
            "upstream-candidate-radar",
            "api-deprecation-lifecycle",
            "scheduler-capability-routing",
            "module-dependency-graph",
            "unified-localization-keys",
            "storage-integrity-audit",
            "staff-multitool-modes",
        ):
            require(feature_id in features, f"Feature backlog entry is missing: {feature_id}", failures)
        require(
            backlog["rules"].get("no_feature_is_enabled_by_manifest_alone") is True,
            "Feature manifest must not enable code by itself",
            failures,
        )
    except (FileNotFoundError, KeyError, TypeError, json.JSONDecodeError) as error:
        failures.append(f"Unable to validate Phase 1A manifests: {error}")

    support_contract = json.loads(read(root, "compatibility/support-contract.json"))
    require(support_contract.get("release") in {"4.1.19", "4.1.20"}, "Support contract release must retain the Phase 1A foundation", failures)
    require(
        support_contract.get("compatibility_policy", {}).get("capability_based_platform_api") is True,
        "Support contract does not declare the platform API",
        failures,
    )
    require(any(version in read(root, "gradle.properties") for version in ("projectVersion=4.1.19", "projectVersion=4.1.20")), "Gradle release must retain the Phase 1A foundation", failures)
    require(
        read(root, "CHANGELOG.md").startswith(("# Slimefun Legacy 4.1.19", "# Slimefun Legacy 4.1.20")),
        "Changelog must start with a release containing the Phase 1A foundation",
        failures,
    )

    report = root / "build/reports/core-platform-phase1a.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text("Core Platform Phase 1A failures:\n" + "\n".join(f"- {item}" for item in failures) + "\n", encoding="utf-8")
        print("Core Platform Phase 1A verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    report.write_text(
        "Slimefun Legacy Core Platform Phase 1A foundation\n"
        "Capability API: PASS\n"
        "Central version parser: PASS\n"
        "Upstream registry: PASS\n"
        "Feature backlog: PASS\n"
        "Advisory radar: PASS\n",
        encoding="utf-8",
    )
    print("Slimefun Legacy Core Platform Phase 1A foundation verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
