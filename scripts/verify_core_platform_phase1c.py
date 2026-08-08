#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1C source invariants."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
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


def run_verifier(root: Path, script_name: str, failures: list[str], *arguments: str) -> None:
    result = subprocess.run(
        [sys.executable, str(root / "scripts" / script_name), *arguments],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        failures.append(
            f"{script_name} failed:\n{result.stdout.strip()}\n{result.stderr.strip()}".strip()
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "docs/history/CORE_PLATFORM_PHASE1C.md",
        "docs/history/SLIMEFUN_LEGACY_4.1.21.md",
        "compatibility/addon-compatibility-matrix.json",
        "compatibility/core-api-registry.json",
        "docs/ADDON_COMPATIBILITY.md",
        "docs/addon-compatibility-manifest.schema.json",
        "scripts/check_addon_binary_linkage.py",
        "scripts/generate_addon_compatibility_matrix.py",
        "scripts/verify_binary_linkage_checker.py",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonCompatibilityService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/OptionalDependencyService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonCompatibilityService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/AddonCompatibilityManifestReader.java",
    )
    for relative in required_files:
        require((root / relative).is_file(), f"Missing Phase 1C file: {relative}", failures)

    try:
        declaration = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonCompatibilityDeclaration.java",
        )
        for token in (
            "@SlimefunAPI",
            "testCore",
            "testCores",
            "platformRequirements",
            "requirePlugin",
            "optionalPlugin",
            "Collections.unmodifiableSet",
        ):
            require(token in declaration, f"Addon declaration invariant is missing: {token}", failures)

        service = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonCompatibilityService.java",
        )
        for token in (
            "EXPLICIT_REGISTRATION",
            "PROVIDER_INTERFACE",
            "EMBEDDED_MANIFEST",
            "PlatformCompatibilityReport",
            "Required plugin is missing or disabled",
            "Optional integration is inactive",
            "AddonCompatibilityStatus.UNDECLARED",
        ):
            require(token in service, f"Runtime addon compatibility invariant is missing: {token}", failures)
        require(
            'warnings.add("Optional integration is inactive:' not in service,
            "Inactive optional integrations must remain informational",
            failures,
        )
        require(
            'warnings.add("Declaration note:' not in service,
            "Declaration notes must remain informational",
            failures,
        )

        manifest = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/AddonCompatibilityManifestReader.java",
        )
        for token in (
            'MANIFEST_PATH = "slimefun-compatibility.json"',
            "SUPPORTED_SCHEMA = 1",
            "tested_core_variants",
            "minimum_minecraft",
            "required_capabilities",
            "required_plugins",
            "optional_plugins",
        ):
            require(token in manifest, f"Manifest reader invariant is missing: {token}", failures)

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "DefaultAddonCompatibilityService",
            "DefaultOptionalDependencyService",
            "getAddonCompatibilityService()",
            "getOptionalDependencyService()",
        ):
            require(token in slimefun, f"Slimefun service accessor is missing: {token}", failures)

        doctor = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java",
        )
        require('case "compatibility", "compat"' in doctor, "Doctor compatibility mode is missing", failures)
        versions = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java",
        )
        require("addAddonCompatibilitySummary" in versions, "Versions compatibility summary is missing", failures)

        workflow = read(root, ".github/workflows/compatibility-ci.yml")
        for token in (
            "prepare-addon-matrix:",
            "generate_addon_compatibility_matrix.py",
            "fromJSON(needs.prepare-addon-matrix.outputs.matrix)",
            "binary-linkage/result.json",
            "binary-linkage/summary.md",
        ):
            require(token in workflow, f"Dynamic addon CI invariant is missing: {token}", failures)
        require("wickidcow/SF_FastMachines" not in workflow, "Addon repositories remain duplicated in workflow YAML", failures)

        compare = read(root, "scripts/compare_addon_slimefun_compatibility.py")
        for token in (
            "analyze_linkage",
            "binary_linkage",
            "find_built_addon_jar",
            "write_linkage_report",
        ):
            require(token in compare, f"Source/binary comparison invariant is missing: {token}", failures)
    except FileNotFoundError as error:
        failures.append(f"Unable to inspect missing file: {error}")

    try:
        matrix = json.loads(read(root, "compatibility/addon-compatibility-matrix.json"))
        require(matrix.get("schema") == 1, "Addon matrix schema must be 1", failures)
        require(
            matrix.get("release") == project_version(root),
            "Addon matrix release must match the current Legacy release",
            failures,
        )
        addons = matrix.get("addons", [])
        required_names = {
            "FastMachines Legacy",
            "Networks Expansion Legacy",
            "SlimeTinker IE2 Legacy",
            "BetterChests Legacy",
        }
        representative_names = {
            "Networks upstream",
            "Infinity Expansion 2",
            "DynaTech",
            "Supreme",
            "Magic Expansion",
            "FluffyMachines",
            "FastMachines upstream",
            "SlimeTinker upstream",
        }
        names = {entry.get("name") for entry in addons if isinstance(entry, dict)}
        require(required_names <= names, "Required Legacy addon targets are incomplete", failures)
        require(representative_names <= names, "Representative addon targets are incomplete", failures)
        require(
            all(entry.get("advisory") is True for entry in addons if entry.get("name") in representative_names),
            "Independent representative repositories must remain advisory",
            failures,
        )

        core_registry = json.loads(read(root, "compatibility/core-api-registry.json"))
        ids = {entry.get("id") for entry in core_registry.get("core_variants", [])}
        require(
            {"original", "gugu", "united", "slimefun5", "slimefun-core", "legacy"} <= ids,
            "Core API registry is incomplete",
            failures,
        )

        schema = json.loads(read(root, "docs/addon-compatibility-manifest.schema.json"))
        require(schema.get("properties", {}).get("schema", {}).get("const") == 1, "Manifest JSON schema is stale", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        require(
            support.get("release") == project_version(root),
            "Support contract release must match the current Legacy release",
            failures,
        )
        require(
            isinstance(support.get("phase"), str) and bool(support.get("phase")),
            "Support contract phase marker is missing",
            failures,
        )
        policy = support.get("compatibility_policy", {})
        for key in (
            "runtime_addon_compatibility_registry",
            "embedded_addon_compatibility_manifest",
            "central_optional_dependency_service",
            "source_and_binary_addon_matrix",
            "dynamic_addon_ci_matrix",
        ):
            require(policy.get(key) is True, f"Support contract policy is missing: {key}", failures)
    except (FileNotFoundError, json.JSONDecodeError, TypeError) as error:
        failures.append(f"Unable to validate Phase 1C manifests: {error}")

    with tempfile.TemporaryDirectory(prefix="slimefun-matrix-") as raw:
        output = Path(raw) / "github-output.txt"
        run_verifier(
            root,
            "generate_addon_compatibility_matrix.py",
            failures,
            str(root / "compatibility/addon-compatibility-matrix.json"),
            "--github-output",
            str(output),
        )
        if output.is_file():
            require("matrix={\"include\":" in output.read_text(encoding="utf-8"), "Matrix generator output is missing", failures)
    run_verifier(root, "verify_binary_linkage_checker.py", failures)

    require(
        release_at_least(project_version(root), (4, 1, 21)),
        "Gradle release must retain the Phase 1C-or-later line",
        failures,
    )
    require(
        "# Slimefun Legacy 4.1.21" in read(root, "CHANGELOG.md"),
        "Changelog no longer contains the Phase 1C release",
        failures,
    )
    require(
        f"[Release Notes](docs/history/SLIMEFUN_LEGACY_{project_version(root)}.md)" in read(root, "README.md"),
        "README release link is stale",
        failures,
    )

    report = root / "build/reports/core-platform-phase1c.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1C failures:\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print("Core Platform Phase 1C verification failed:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 1

    report.write_text(
        "Slimefun Legacy Core Platform Phase 1C foundation\n"
        "Runtime addon compatibility registry: PASS\n"
        "Embedded compatibility manifest: PASS\n"
        "Optional dependency boundary: PASS\n"
        "Dynamic source-build matrix: PASS\n"
        "Precompiled addon binary linkage: PASS\n"
        "Existing API baseline enforcement: PASS\n",
        encoding="utf-8",
    )
    print("Slimefun Legacy Core Platform Phase 1C foundation verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
