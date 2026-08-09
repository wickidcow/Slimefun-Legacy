#!/usr/bin/env python3
"""Verify Slimefun Legacy's GuizhanLibPlugin compatibility bridge wiring."""

from __future__ import annotations

import sys
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    build = (root / "build.gradle.kts").read_text(encoding="utf-8")
    catalog = (root / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    gradle_properties = (root / "gradle.properties").read_text(encoding="utf-8")
    plugin_yml = (root / "src/main/resources/plugin.yml").read_text(encoding="utf-8")
    doctor = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java").read_text(
        encoding="utf-8"
    )
    bridge = (
        root
        / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/GuizhanLibCompatibilityBridge.java"
    ).read_text(encoding="utf-8")
    workflow = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
    jar_verifier = (root / "scripts/verify_guizhanlib_bridge_jar.py").read_text(encoding="utf-8")
    license_path = root / "src/main/resources/META-INF/LICENSES/GuizhanLibPlugin-LICENSE.txt"
    notice_path = root / "src/main/resources/META-INF/LICENSES/GuizhanLibPlugin-NOTICE.txt"
    libby_license_path = root / "src/main/resources/META-INF/LICENSES/Libby-LICENSE.txt"
    bridge_doc_path = root / "docs/GUIZHANLIB_COMPATIBILITY_BRIDGE.md"

    require("projectVersion=4.1.29" in gradle_properties, "bridge release must build as Slimefun Legacy 4.1.29")
    require("provides:\n  - GuizhanLibPlugin" in plugin_yml, "plugin.yml must provide the GuizhanLibPlugin alias")
    require(
        'guizhanlib-plugin = { module = "net.guizhanss:GuizhanLibPlugin", version = "2.5.0" }' in catalog,
        "version catalog must pin the compatibility source artifact to GuizhanLibPlugin 2.5.0",
    )
    require("val guizhanLibPluginBridge by configurations.creating" in build, "bridge configuration is missing")
    require("isTransitive = false" in build, "bridge source artifact must stay non-transitive")
    require("add(guizhanLibPluginBridge.name, libs.guizhanlib.plugin.get())" in build, "bridge artifact is not wired")
    require(
        'relocate("net.guizhanss.guizhanlib", "io.github.thebusybiscuit.slimefun4.libraries.guizhanlib")' in build,
        "Slimefun's private GuizhanLib relocation must remain intact",
    )
    require("val guizhanLibBridgeJar = tasks.register<Jar>" in build, "final bridge JAR task is missing")
    require('include("net/guizhanss/guizhanlib/**")' in build, "public GuizhanLib API is not copied into final JAR")
    require(
        'include("net/guizhanss/minecraft/guizhanlib/gugu/**")' in build,
        "legacy Gugu helper compatibility classes are not copied into final JAR",
    )
    require(
        'include("net/guizhanss/minecraft/guizhanlib/GuizhanLib.class")' not in build,
        "bridge must not package the GuizhanLibPlugin JavaPlugin main class",
    )
    require(
        'exclude("net/guizhanss/minecraft/guizhanlib/gugu/localization/LocalizationLoader*.class")' in build,
        "legacy LocalizationLoader must be excluded because it calls the external plugin singleton",
    )
    require("artifact(guizhanLibBridgeJar)" in build, "Maven publication must publish the final bridge JAR")
    require("dependsOn(guizhanLibBridgeJar)" in build, "normal build must produce the final bridge JAR")

    require(license_path.is_file(), "GuizhanLibPlugin GPL-3.0 license copy is missing")
    require(notice_path.is_file(), "GuizhanLibPlugin attribution notice is missing")
    require("GNU GENERAL PUBLIC LICENSE" in license_path.read_text(encoding="utf-8"), "license resource is invalid")
    require("GuizhanLibPlugin 2.5.0" in notice_path.read_text(encoding="utf-8"), "attribution notice is incomplete")
    require(libby_license_path.is_file(), "Libby MIT license copy is missing")
    require("MIT License" in libby_license_path.read_text(encoding="utf-8"), "Libby license resource is invalid")
    require(bridge_doc_path.is_file(), "bridge staging/compatibility documentation is missing")

    require("class GuizhanLibCompatibilityBridge" in bridge, "bridge runtime diagnostics service is missing")
    require('PROVIDED_PLUGIN = "GuizhanLibPlugin"' in bridge, "runtime bridge must inspect the provider alias")
    require('COMPATIBILITY_VERSION = "2.5.0"' in bridge, "runtime bridge version must match packaged API")
    require("getPluginDependencies()" in bridge, "hard GuizhanLibPlugin dependents are not reported")
    require("getPluginSoftDependencies()" in bridge, "soft GuizhanLibPlugin dependents are not reported")
    require("fallbackReady" in bridge, "bridge readiness state is missing")

    require('case "guizhanlib", "guizhan" -> sendGuizhanLibBridge(sender);' in doctor, "Doctor action is missing")
    require("GuizhanLib Compatibility Bridge" in doctor, "Doctor diagnostics header is missing")
    require("main JavaPlugin class is not emulated" in doctor, "compatibility boundary warning is missing")
    require("LocalizationLoader remain external-only" in doctor, "plugin-singleton legacy helper warning is missing")
    require("/slimefun doctor guizhanlib" in doctor, "Doctor usage text is missing the bridge command")

    require("verify_guizhanlib_bridge_jar.py" in workflow, "GitHub build must verify the assembled bridge JAR")
    require("REQUIRED_PUBLIC_CLASSES" in jar_verifier, "artifact bridge verifier is missing public API checks")
    require("FORBIDDEN_CLASSES" in jar_verifier, "artifact bridge verifier is missing implementation-boundary checks")
    require("GuizhanLibPlugin-LICENSE.txt" in jar_verifier, "artifact verifier must require the GPL license resource")
    require("Libby-LICENSE.txt" in jar_verifier, "artifact verifier must require the Libby MIT license resource")

    print("GuizhanLib compatibility bridge verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
