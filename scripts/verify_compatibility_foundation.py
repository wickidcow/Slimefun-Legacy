#!/usr/bin/env python3
"""Verify the machine-readable Slimefun Legacy compatibility contract."""
from __future__ import annotations

import json
import sys
from pathlib import Path


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    contract = json.loads((root / "compatibility/support-contract.json").read_text(encoding="utf-8"))
    gradle_properties = (root / "gradle.properties").read_text(encoding="utf-8")
    versions = (root / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    build = (root / "build.gradle.kts").read_text(encoding="utf-8")
    plugin = (root / "src/main/resources/plugin.yml").read_text(encoding="utf-8")
    readme = (root / "README.md").read_text(encoding="utf-8")
    build_ci = (root / ".github/workflows/build-ci.yml").read_text(encoding="utf-8")
    compatibility_ci = (root / ".github/workflows/compatibility-ci.yml").read_text(encoding="utf-8")
    api_ci = (root / ".github/workflows/api-compatibility.yml").read_text(encoding="utf-8")

    failures: list[str] = []
    release = contract["release"]
    primary_platform = contract["primary_platform"]
    paper_release = primary_platform["release_line"]
    minecraft_release = primary_platform["minecraft"]
    paper_api = primary_platform["paper_api"]
    bytecode = contract["java"]["bytecode_target"]
    toolchain = contract["java"]["build_toolchain"]
    descriptor_api = contract["plugin_descriptor"]["api_version"]
    gameplay_changed = contract["compatibility_policy"].get("gameplay_behavior_changed")

    require(f"projectVersion={release}" in gradle_properties, "gradle.properties release differs from contract", failures)
    require(f'paperApi = "{paper_api}"' in versions, "Paper API catalog version differs from contract", failures)
    require(f"JavaLanguageVersion.of({toolchain})" in build, "Java toolchain differs from contract", failures)
    require(f"options.release.set({bytecode})" in build, "Java bytecode target differs from contract", failures)
    require('gradleProperty("paperApiVersion")' in build, "Paper API override property is missing", failures)
    require(f"name: {contract['plugin_name']}" in plugin, "plugin.yml name differs from contract", failures)
    require(f"api-version: '{descriptor_api}'" in plugin, "plugin.yml api-version differs from contract", failures)
    require("folia-supported: true" in plugin, "plugin.yml must retain Folia declaration", failures)
    require(contract["plugin_descriptor"]["api_version_is_support_floor"] is False, "descriptor api-version must not be represented as support floor", failures)
    require(contract["compatibility_policy"]["database_format_changed"] is False, f"{release} must not change database format", failures)
    require(type(gameplay_changed) is bool, f"{release} must explicitly declare whether gameplay behavior changed", failures)
    require("Compatibility Foundation" in readme, "README does not describe Compatibility Foundation", failures)
    require(
        f"Paper {paper_release}" in readme and f"Minecraft {minecraft_release}" in readme,
        "README omits tested platform line from support contract",
        failures,
    )
    require(
        "gradle.properties" in build_ci and "LEGACY_ARTIFACT_VERSION=$VERSION" in build_ci,
        "Build CI must derive artifact version from gradle.properties",
        failures,
    )
    require(
        "Slimefun-Legacy${VERSION}.jar" in build_ci and "dist/${OUTPUT_NAME}" in build_ci,
        "Build CI does not stage the standardized versioned Slimefun Legacy JAR",
        failures,
    )
    require(
        'LEGACY_ARTIFACT_VERSION: "' not in build_ci,
        "Build CI must not hardcode a separate artifact version",
        failures,
    )
    require("check_bytecode_target.py" in compatibility_ci, "compatibility CI does not enforce bytecode target", failures)
    require("summarize_deprecations.py" in compatibility_ci, "compatibility CI does not publish deprecation report", failures)
    require("PAPER_API_CANDIDATE" in compatibility_ci, "candidate Paper API compile job is missing", failures)
    require("write_api_surface.py" in api_ci, "API CI does not publish candidate surface", failures)

    report = root / "build/reports/compatibility-foundation.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text("Compatibility Foundation failures:\n" + "\n".join(f"- {item}" for item in failures) + "\n", encoding="utf-8")
        print("Compatibility Foundation verification failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    report.write_text(
        "Slimefun Legacy Compatibility Foundation\n"
        f"Release: {release}\n"
        f"Primary: Paper {paper_release} / Minecraft {minecraft_release}\n"
        f"Paper API: {paper_api}\n"
        f"Build Java: {toolchain}\n"
        f"Bytecode Java: {bytecode}\n"
        f"Gameplay behavior changed: {str(gameplay_changed).lower()}\n"
        "Artifact version source: gradle.properties projectVersion\n"
        "PASS\n",
        encoding="utf-8",
    )
    print(f"Slimefun Legacy {release} Compatibility Foundation verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
