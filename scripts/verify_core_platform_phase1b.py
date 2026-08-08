#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1B source invariants."""

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


def release_tuple(value: object) -> tuple[int, int, int] | None:
    if not isinstance(value, str):
        return None
    parts = value.strip().split(".")
    if len(parts) != 3 or any(not part.isdigit() for part in parts):
        return None
    return tuple(int(part) for part in parts)


def release_at_least(value: object, minimum: tuple[int, int, int]) -> bool:
    parsed = release_tuple(value)
    return parsed is not None and parsed >= minimum


def project_version(root: Path) -> str:
    for line in read(root, "gradle.properties").splitlines():
        if line.startswith("projectVersion="):
            return line.split("=", 1)[1].strip()
    raise ValueError("projectVersion is missing from gradle.properties")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "docs/history/CORE_PLATFORM_PHASE1B.md",
        "docs/history/SLIMEFUN_LEGACY_4.1.20.md",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/annotations/SlimefunDeprecated.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformCompatibilityReport.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformRequirements.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/RuntimePlatformDetector.java",
        "compatibility/api-signatures-4.1.19.txt",
        "scripts/verify_api_compatibility.py",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/platform/TestPlatformRequirements.java",
    )
    for relative in required_files:
        require((root / relative).is_file(), f"Missing Phase 1B file: {relative}", failures)

    try:
        service = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformCompatibilityService.java",
        )
        for token in (
            "default @Nonnull Optional<MinecraftVersionNumber> getMinecraftVersion()",
            "default boolean isMinecraftVersionBefore",
            "default boolean isJavaVersionAtLeast",
            "default boolean isPaperCompatible()",
            "default boolean isRegionOwnedExecution()",
            "default @Nonnull PlatformCompatibilityReport check",
        ):
            require(token in service, f"Additive platform-service helper is missing: {token}", failures)

        requirements = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformRequirements.java",
        )
        for token in (
            "@SlimefunAPI",
            "minimumMinecraftVersion",
            "minimumJavaVersion",
            "requireCapability",
            "acceptFamily",
            "Collections.unmodifiableSet",
        ):
            require(token in requirements, f"Platform requirements invariant is missing: {token}", failures)

        report = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/platform/PlatformCompatibilityReport.java",
        )
        for token in ("List.copyOf", "isCompatible()", "getIncompatibilities()", "describe()"):
            require(token in report, f"Compatibility report invariant is missing: {token}", failures)

        deprecated = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/annotations/SlimefunDeprecated.java",
        )
        for token in ("String since()", "String replacement()", "String removalVersion()"):
            require(token in deprecated, f"Deprecation lifecycle field is missing: {token}", failures)

        folia = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/scheduling/FoliaSupport.java",
        )
        for token in (
            "@Deprecated(since = \"4.1.20\", forRemoval = false)",
            "@SlimefunDeprecated",
            "RuntimePlatformDetector.isRegionOwnedExecution()",
            "public static boolean isFolia()",
        ):
            require(token in folia, f"Folia compatibility bridge invariant is missing: {token}", failures)

        scheduler = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/scheduling/PaperScheduler.java",
        )
        for token in (
            "public PaperScheduler(@Nonnull Plugin plugin)",
            "PlatformCompatibilityService platformCompatibilityService",
            "usesRegionOwnedExecution()",
            "RuntimePlatformDetector.isRegionOwnedExecution()",
        ):
            require(token in scheduler, f"Scheduler compatibility invariant is missing: {token}", failures)
        require("private static final boolean FOLIA" not in scheduler, "Scheduler still freezes Folia detection", failures)

        implementation = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultPlatformCompatibilityService.java",
        )
        for token in (
            "public void initialize(@Nonnull Server server)",
            "public void initialize(@Nonnull Server server, boolean regionOwnedExecution)",
            "RuntimePlatformDetector.detectCapabilities(server)",
        ):
            require(token in implementation, f"Platform implementation invariant is missing: {token}", failures)

        detector = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/RuntimePlatformDetector.java",
        )
        for token in (
            "FOLIA_RUNTIME_CLASS",
            "PAPER_CONFIGURATION_CLASS",
            "DATA_COMPONENT_TYPE_CLASS",
            "PLAYER_PICK_BLOCK_EVENT_CLASS",
            "detectCapabilities",
            "detectFamily",
            "supportLevel",
        ):
            require(token in detector, f"Central detector invariant is missing: {token}", failures)

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "new PaperScheduler(this, platformCompatibilityService)",
            "platformCompatibilityService.initialize(getServer())",
            "platformCompatibilityService.isPaperCompatible()",
        ):
            require(token in slimefun, f"Slimefun platform routing invariant is missing: {token}", failures)
    except FileNotFoundError as error:
        failures.append(f"Unable to inspect missing file: {error}")

    source_root = root / "src/main/java"
    detector_path = Path("io/github/thebusybiscuit/slimefun4/core/services/compatibility/RuntimePlatformDetector.java")
    scheduler_path = Path("io/github/thebusybiscuit/slimefun4/implementation/scheduling/PaperScheduler.java")
    folia_path = Path("io/github/thebusybiscuit/slimefun4/core/services/scheduling/FoliaSupport.java")

    if source_root.is_dir():
        for source in source_root.rglob("*.java"):
            relative = source.relative_to(source_root)
            text = source.read_text(encoding="utf-8")

            if "@SlimefunDeprecated" in text and "@Deprecated" not in text:
                failures.append(f"Lifecycle deprecation is missing Java @Deprecated: {relative}")

            if "import io.papermc.lib.PaperLib;" in text or "PaperLib." in text:
                failures.append(f"Direct PaperLib platform check remains: {relative}")

            if relative != folia_path and "FoliaSupport" in text:
                failures.append(f"New code bypasses the platform service through FoliaSupport: {relative}")

            if relative != detector_path and 'Class.forName("io.papermc' in text:
                failures.append(f"Direct Paper implementation probe remains outside detector: {relative}")

            if relative != detector_path:
                for implementation_name in (
                    "io.papermc.paper.threadedregions.RegionizedServer",
                    "io.papermc.paper.configuration.Configuration",
                    "io.papermc.paper.datacomponent.DataComponentType",
                ):
                    if implementation_name in text:
                        failures.append(
                            f"Paper/Folia implementation identity remains outside detector: {relative}"
                        )

            for call in ("Bukkit.getName()", "Bukkit.getVersion()", "Bukkit.getBukkitVersion()"):
                if call in text:
                    failures.append(f"Direct Bukkit platform-version call remains: {relative}: {call}")

            if relative != scheduler_path:
                for call in (
                    "Bukkit.getRegionScheduler()",
                    "Bukkit.getGlobalRegionScheduler()",
                    "Bukkit.getAsyncScheduler()",
                ):
                    if call in text:
                        failures.append(f"Region scheduler call escaped scheduler boundary: {relative}: {call}")

    try:
        support_contract = json.loads(read(root, "compatibility/support-contract.json"))
        require(
            release_at_least(support_contract.get("release"), (4, 1, 20)),
            "Support contract release must retain the Phase 1B-or-later line",
            failures,
        )
        require(
            isinstance(support_contract.get("phase"), str) and bool(support_contract.get("phase")),
            "Support contract phase marker is missing",
            failures,
        )
        policy = support_contract.get("compatibility_policy", {})
        for key in (
            "capability_based_platform_api",
            "declarative_addon_platform_requirements",
            "documented_api_deprecation_lifecycle",
            "central_runtime_platform_detector",
            "api_signature_baseline",
        ):
            require(policy.get(key) is True, f"Support contract policy is missing: {key}", failures)

        backlog = json.loads(read(root, "compatibility/core-feature-backlog.json"))
        status = {entry["id"]: entry["status"] for entry in backlog["features"]}
        for feature_id in (
            "capability-platform-api",
            "api-deprecation-lifecycle",
            "scheduler-capability-routing",
            "api-signature-baseline",
        ):
            require(
                status.get(feature_id) == "implemented-phase-1b",
                f"Feature backlog is stale for {feature_id}",
                failures,
            )
    except (FileNotFoundError, KeyError, TypeError, json.JSONDecodeError) as error:
        failures.append(f"Unable to validate Phase 1B manifests: {error}")

    require(
        release_at_least(project_version(root), (4, 1, 20)),
        "Gradle release must be Phase 1B or later",
        failures,
    )
    require(
        "# Slimefun Legacy 4.1.20" in read(root, "CHANGELOG.md"),
        "Changelog no longer contains the 4.1.20 Phase 1B release",
        failures,
    )
    require(
        f"SLIMEFUN_LEGACY_{project_version(root)}.md" in read(root, "README.md"),
        "README release link does not match the current Legacy release",
        failures,
    )

    output = root / "build/reports/core-platform-phase1b.txt"
    output.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        output.write_text(
            "Core Platform Phase 1B failures:\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print("Core Platform Phase 1B verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    output.write_text(
        "Slimefun Legacy Core Platform Phase 1B foundation\n"
        "Existing API bridges: PASS\n"
        "Declarative addon requirements: PASS\n"
        "Central platform detector: PASS\n"
        "Scheduler capability routing: PASS\n"
        "Deprecation lifecycle: PASS\n"
        "Direct-check guardrails: PASS\n",
        encoding="utf-8",
    )
    print("Slimefun Legacy 4.1.20 Core Platform Phase 1B verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
