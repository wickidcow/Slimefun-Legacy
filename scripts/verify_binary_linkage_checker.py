#!/usr/bin/env python3
"""Synthetic regression test for the addon JVM binary-linkage checker."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from check_addon_binary_linkage import analyze_linkage

PACKAGE = "io.github.thebusybiscuit.slimefun4.api.phase1c"


def run(command: list[str], cwd: Path) -> None:
    result = subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)
    if result.returncode != 0:
        raise AssertionError(
            f"Command failed: {' '.join(command)}\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}"
        )


def write_java(root: Path, package: str, class_name: str, body: str) -> Path:
    path = root / Path(package.replace(".", "/")) / f"{class_name}.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"package {package};\n{body}\n", encoding="utf-8")
    return path


def compile_jar(
    work: Path,
    name: str,
    sources: list[Path],
    classpath: Path | list[Path] | None = None,
) -> Path:
    classes = work / f"{name}-classes"
    classes.mkdir(parents=True)
    command = ["javac", "--release", "21", "-d", str(classes)]
    if classpath:
        entries = classpath if isinstance(classpath, list) else [classpath]
        command.extend(["-classpath", os.pathsep.join(str(entry) for entry in entries)])
    command.extend(str(source) for source in sources)
    run(command, work)
    jar = work / f"{name}.jar"
    run(["jar", "--create", "--file", str(jar), "-C", str(classes), "."], work)
    return jar


def main() -> int:
    if not shutil.which("javac") or not shutil.which("jar"):
        print("Binary linkage checker verification requires javac and jar.", file=sys.stderr)
        return 1

    try:
        with tempfile.TemporaryDirectory(prefix="slimefun-linkage-") as raw:
            work = Path(raw)
            v1_source = write_java(
                work / "v1-src",
                PACKAGE,
                "CompatibilitySurface",
                "public class CompatibilitySurface { "
                "public static int RETAINED_FIELD = 1; "
                "public void retained() {} "
                "public void legacyBridge() {} "
                "}",
            )
            core_v1 = compile_jar(work, "core-v1", [v1_source])

            addon_source = write_java(
                work / "addon-src",
                "example.addon",
                "AddonEntry",
                f"import {PACKAGE}.CompatibilitySurface; "
                "public final class AddonEntry { "
                "public int run(CompatibilitySurface surface) { "
                "surface.retained(); surface.legacyBridge(); "
                "return CompatibilitySurface.RETAINED_FIELD; "
                "} }",
            )
            addon = compile_jar(work, "addon", [addon_source], core_v1)

            compatible_source = write_java(
                work / "compatible-src",
                PACKAGE,
                "CompatibilitySurface",
                "public class CompatibilitySurface { "
                "public static int RETAINED_FIELD = 2; "
                "public void retained() {} "
                "public void legacyBridge() {} "
                "public void additiveApi() {} "
                "}",
            )
            compatible = compile_jar(work, "core-compatible", [compatible_source])
            compatible_result = analyze_linkage(addon, compatible, core_v1)
            if not compatible_result.passed:
                raise AssertionError(f"Additive core unexpectedly failed: {compatible_result.to_json()}")

            broken_source = write_java(
                work / "broken-src",
                PACKAGE,
                "CompatibilitySurface",
                "public class CompatibilitySurface { "
                "public static int RETAINED_FIELD = 2; "
                "public void retained() {} "
                "}",
            )
            broken = compile_jar(work, "core-broken", [broken_source])
            broken_result = analyze_linkage(addon, broken, core_v1)
            if broken_result.passed:
                raise AssertionError("Removed method was not detected")
            if not any(reference.name == "legacyBridge" for reference in broken_result.missing_methods):
                raise AssertionError(f"Expected missing legacyBridge method: {broken_result.to_json()}")

            external_source = write_java(
                work / "external-src",
                "external.platform",
                "ExternalBase",
                "public class ExternalBase { public void externalApi() {} }",
            )
            external = compile_jar(work, "external-platform", [external_source])
            inherited_core_source = write_java(
                work / "inherited-core-src",
                PACKAGE,
                "InheritedSurface",
                "public class InheritedSurface extends external.platform.ExternalBase {}",
            )
            inherited_core = compile_jar(
                work, "core-inherited", [inherited_core_source], external
            )
            inherited_addon_source = write_java(
                work / "inherited-addon-src",
                "example.addon",
                "InheritedAddonEntry",
                f"import {PACKAGE}.InheritedSurface; "
                "public final class InheritedAddonEntry { "
                "public void run(InheritedSurface surface) { surface.externalApi(); } "
                "}",
            )
            inherited_addon = compile_jar(
                work,
                "addon-inherited",
                [inherited_addon_source],
                [inherited_core, external],
            )
            inherited_result = analyze_linkage(
                inherited_addon, inherited_core, inherited_core
            )
            if not inherited_result.passed:
                raise AssertionError(
                    "External inherited member was misclassified as a removed Slimefun API: "
                    f"{inherited_result.to_json()}"
                )

        print("Addon binary-linkage checker regression verification passed.")
        return 0
    except (AssertionError, OSError) as error:
        print(f"Binary linkage checker verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
