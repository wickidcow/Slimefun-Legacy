#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1F compatibility-intelligence invariants."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def read(root: Path, rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def req(ok: bool, msg: str, failures: list[str]) -> None:
    if not ok:
        failures.append(msg)


def project_version(root: Path) -> str:
    match = re.search(r"^projectVersion=(\d+\.\d+\.\d+)$", read(root, "gradle.properties"), re.M)
    return match.group(1) if match else ""


def load_runtime_registry(root: Path) -> dict[str, tuple[str, str]]:
    entries: dict[str, tuple[str, str]] = {}
    path = root / "src/main/resources/compatibility/addon-support-registry.txt"
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|", 3)]
        if len(parts) != 4:
            continue
        slug, tier, display, _aliases = parts
        entries[slug] = (tier, display)
    return entries


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "CORE_PLATFORM_PHASE1F.md",
        "SLIMEFUN_LEGACY_4.1.24.md",
        "src/main/resources/compatibility/addon-support-registry.txt",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/KnownAddonCompatibilityRegistry.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/TestKnownAddonCompatibilityRegistry.java",
    )
    for rel in required_files:
        req((root / rel).is_file(), f"Missing Phase 1F file: {rel}", failures)

    try:
        current = project_version(root)
        req(tuple(map(int, current.split("."))) >= (4, 1, 24), "Phase 1F requires 4.1.24 or newer", failures)

        matrix = json.loads(read(root, "compatibility/addon-compatibility-matrix.json"))
        req(matrix.get("release") == current, "Addon matrix release must match projectVersion", failures)
        enabled_slugs = {
            entry.get("slug")
            for entry in matrix.get("addons", [])
            if isinstance(entry, dict) and entry.get("enabled", True) and entry.get("slug")
        }
        runtime_registry = load_runtime_registry(root)
        req(enabled_slugs <= set(runtime_registry), "Runtime addon recognition registry is missing enabled CI addon targets", failures)
        req(len(runtime_registry) >= 33, "Runtime addon recognition registry must retain at least 33 addon families", failures)
        req(
            sum(1 for tier, _display in runtime_registry.values() if tier == "required") >= 4,
            "Runtime addon recognition registry must retain the four required Legacy targets",
            failures,
        )
        recognized_slugs = {
            "better-farming",
            "danktech2",
            "cultivation",
            "electric-spawners",
            "extra-tools",
            "genetic-chickengineering",
            "hotbar-pets",
            "magic-8-ball",
            "mobcapturer",
            "sf-mobdrops",
            "slimefun-advancements",
            "slimeglue",
            "simple-material-generators",
            "souljars",
        }
        req(
            recognized_slugs <= set(runtime_registry),
            "Runtime addon recognition registry is missing Phase 1F Part 2 recognized addon families",
            failures,
        )
        req(
            all(runtime_registry[slug][0] == "recognized" for slug in recognized_slugs if slug in runtime_registry),
            "Recognition-only addon families must not be mislabeled as CI monitored",
            failures,
        )
        registry_resource = read(root, "src/main/resources/compatibility/addon-support-registry.txt")
        for token in (
            "danktech2|recognized|DankTech2|",
            "GeneticChickengineering-Reborn",
            "MagicBall 8",
            "SFMobDrops",
            "SimpleMaterialGenerators",
            "SoulJars",
        ):
            req(token in registry_resource, f"Phase 1F Part 2 addon alias missing: {token}", failures)

        registry = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/KnownAddonCompatibilityRegistry.java",
        )
        for token in (
            'RESOURCE_PATH = "compatibility/addon-support-registry.txt"',
            "KnownAddonSupport",
            "isRequired()",
            'replace("legacy", "")',
            'replace("upstream", "")',
            "isCiMonitored()",
            "isRecognizedOnly()",
            "getTierPriority()",
        ):
            req(token in registry, f"Runtime addon registry invariant missing: {token}", failures)

        versions = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java")
        for token in (
            '"◉ Known addon — Legacy CI monitored"',
            '"? Slimefun addon — compatibility unknown"',
            '"◉ " + ciMonitored + " CI monitored"',
            '"● " + recognized + " recognized"',
            '"? " + unknown + " unknown"',
            '"● Recognized addon — compatibility not verified"',
            '"Overall: ✔ No known compatibility problems"',
            "knownAddonRegistry.find(result.getPluginName())",
            "the exact installed JAR may differ from the build tested by CI",
            "compareToIgnoreCase",
        ):
            req(token in versions, f"Versions Phase 1F invariant missing: {token}", failures)
        req(
            'label = "✔ Compatible"' in versions,
            "Declared compatible status must remain distinct from CI monitoring",
            failures,
        )
        req(
            'case UNDECLARED' in versions,
            "Undeclared API status must remain intact rather than being promoted to compatible",
            failures,
        )
        req(
            "AddonCompatibilityStatus.COMPATIBLE" not in registry,
            "Recognition registry must not mutate the public compatibility status",
            failures,
        )

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        for token in (
            '"&6Slimefun Addon Compatibility Evidence"',
            '"&7Legacy registry: &9Recognized only',
            '"&7Runtime machine health:',
            '"&7Compatibility-layer linkage signal:',
            '"&8  This is a safe runtime signal, not a full bytecode proof; GitHub compatibility CI remains "',
            '"&9● Recognized addon — compatibility not verified"',
        ):
            req(token in doctor, f"Doctor compatibility evidence invariant missing: {token}", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        req(support.get("release") == current, "Support contract release must match projectVersion", failures)
        req(support.get("phase") == "Core Platform Phase 1F", "Support contract phase must be Phase 1F", failures)
        policy = support.get("compatibility_policy", {})
        for key in (
            "known_addon_runtime_recognition_registry",
            "ci_coverage_is_not_runtime_compatibility_guarantee",
            "versions_distinguishes_declared_ci_monitored_and_unknown",
            "recognized_addon_family_tier",
            "doctor_compatibility_evidence_report",
            "runtime_machine_health_in_compatibility_report",
            "safe_linkage_signal_is_not_binary_proof",
        ):
            req(policy.get(key) is True, f"Phase 1F support policy missing: {key}", failures)

        baseline = json.loads(read(root, "compatibility/release-baselines.json"))
        req(
            baseline.get("candidate", {}).get("version") == current,
            "Baseline registry candidate must match the current development version",
            failures,
        )
    except Exception as error:
        failures.append(f"Phase 1F verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1f.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1F verification: FAIL\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1F verification: PASS\n"
        "- runtime addon recognition registry covers every enabled compatibility-matrix target\n"
        "- /sf versions separates declared compatibility, CI monitoring and unknown compatibility\n"
        "- CI coverage is explicitly not promoted to exact-build compatibility\n"
        "- addon loading and public compatibility status semantics remain unchanged\n"
        "- recognition-only addon families remain distinct from CI-monitored targets\n"
        "- /sf doctor compatibility exposes declaration, registry, runtime-health and safe linkage evidence\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
