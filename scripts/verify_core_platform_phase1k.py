#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1K dependency-boundary invariants."""
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


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    resolution_rel = (
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/"
        "PluginDependencyResolution.java"
    )
    snapshot_rel = (
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/"
        "PluginDependencySnapshot.java"
    )
    diagnostics_rel = (
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/"
        "PluginDependencyDiagnosticsService.java"
    )
    doctor_rel = "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java"
    versions_rel = "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java"
    required_files = (
        resolution_rel,
        snapshot_rel,
        diagnostics_rel,
        doctor_rel,
        versions_rel,
        "scripts/verify_core_platform_phase1j.py",
        "compatibility/support-contract.json",
        "compatibility/cross-fork-api-matrix.json",
        "compatibility/core-api-registry.json",
        "compatibility/addon-compatibility-matrix.json",
        "compatibility/release-baselines.json",
        "src/main/resources/plugin.yml",
        "README.md",
        "EVERYTHING_THAT_CHANGED.md",
    )
    for rel in required_files:
        req((root / rel).is_file(), f"Missing Phase 1K file: {rel}", failures)

    try:
        current = project_version(root)
        req(bool(current), "Could not determine projectVersion", failures)
        if current:
            req(tuple(map(int, current.split("."))) >= (4, 1, 29), "Phase 1K requires 4.1.29 or newer", failures)

        diagnostics = read(root, diagnostics_rel)
        for token in (
            "final class PluginDependencyDiagnosticsService",
            "plugin.getPluginMeta()",
            "getPluginDependencies()",
            "getPluginSoftDependencies()",
            "getProvidedPlugins()",
            "pluginManager.getPlugin(declaredName)",
            "getRequiredConsumers",
            "getSoftConsumers",
            "findProvider",
            "provider alias",
            "does not install, enable, disable, replace, or emulate dependencies",
        ):
            req(token in diagnostics, f"Dependency diagnostics invariant missing: {token}", failures)

        for forbidden in (
            "enablePlugin(",
            "disablePlugin(",
            "loadPlugin(",
            "Class.forName(",
            "URLClassLoader",
            "HttpClient",
            "openConnection(",
            "download",
        ):
            req(forbidden not in diagnostics, f"Dependency diagnostics must remain read-only: {forbidden}", failures)

        resolution = read(root, resolution_rel)
        for token in (
            "enum State",
            "ENABLED",
            "DISABLED",
            "MISSING",
            "isProviderAlias()",
            "isSatisfied()",
            "isProblem()",
        ):
            req(token in resolution, f"Dependency resolution invariant missing: {token}", failures)

        snapshot = read(root, snapshot_rel)
        for token in (
            "getRequiredDependencies()",
            "getSoftDependencies()",
            "getProvidedPlugins()",
            "hasRequiredDependencyProblems()",
            "getRequiredDependencyProblemCount()",
        ):
            req(token in snapshot, f"Dependency snapshot invariant missing: {token}", failures)

        doctor = read(root, doctor_rel)
        for token in (
            'case "dependencies", "dependency", "deps"',
            '"&6Slimefun Plugin Dependency Boundaries"',
            "new PluginDependencyDiagnosticsService(plugin)",
            "/sf doctor dependencies <name>",
            "Declared hard dependencies:",
            "Provider alias warning:",
            "does not install, enable, replace, or emulate third-party plugin dependencies",
            "Guarded runtime linkage evidence:",
            "it does not intercept arbitrary third-party plugin onEnable failures.",
            "Startup evidence:",
            "Slimefun cannot infer the plugin-side startup cause",
        ):
            req(token in doctor, f"Doctor dependency diagnostic invariant missing: {token}", failures)
        for forbidden in ("enablePlugin(", "disablePlugin(", "loadPlugin(", "Class.forName("):
            req(forbidden not in doctor, f"Doctor dependency diagnostics must not mutate/probe plugin loading: {forbidden}", failures)

        versions = read(root, versions_rel)
        for token in (
            "Addon dependency health:",
            "Guarded addon callbacks:",
            "Boundary evidence is observational",
            'Component.text(" · Deps!"',
            'Component.text(" · Alias"',
            '" · Linkage!"',
            '" · Runtime!"',
            'Component.text(" · Startup?"',
            "appendBoundaryEvidence",
            "Provider aliases satisfy descriptor lookup only",
            "arbitrary Paper plugin onEnable failures are not intercepted",
        ):
            req(token in versions, f"Versions addon-boundary evidence invariant missing: {token}", failures)
        for forbidden in ("enablePlugin(", "disablePlugin(", "loadPlugin(", "Class.forName("):
            req(forbidden not in versions, f"Versions boundary diagnostics must remain observational: {forbidden}", failures)

        plugin_yml = read(root, "src/main/resources/plugin.yml")
        req("GuizhanLibPlugin" not in plugin_yml, "Slimefun core must not declare or provide GuizhanLibPlugin", failures)
        req(not re.search(r"(?m)^provides\s*:", plugin_yml), "Slimefun core must not impersonate another plugin through provides:", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        req(support.get("release") == current, "Support contract release must match projectVersion", failures)
        req(support.get("phase") == "Core Platform Phase 1K", "Support contract phase must be Phase 1K", failures)
        support_policy = support.get("compatibility_policy", {})
        for key in (
            "plugin_dependency_diagnostics",
            "dependency_diagnostics_are_read_only",
            "provider_alias_is_not_class_compatibility_proof",
            "gugu_api_probe_remains_advisory",
            "versions_addon_dependency_health",
            "versions_guarded_runtime_failure_evidence",
            "disabled_addon_startup_cause_is_not_inferred",
        ):
            req(support_policy.get(key) is True, f"Phase 1K support policy missing: {key}", failures)
        for key in (
            "third_party_plugin_dependency_emulation",
            "gugu_runtime_core_target",
            "phase1k_changes_normal_cargo_energy_machine_semantics",
            "database_format_changed",
            "storage_schema_changed",
            "plugin_startup_log_interception",
        ):
            req(support_policy.get(key) is False, f"Phase 1K support policy must remain false: {key}", failures)

        cross_fork = json.loads(read(root, "compatibility/cross-fork-api-matrix.json"))
        req(cross_fork.get("release") == current, "Cross-fork matrix release must match projectVersion", failures)
        cross_policy = cross_fork.get("policy", {})
        req(cross_policy.get("external_fork_drift_is_advisory") is True, "External fork drift must stay advisory", failures)
        req(cross_policy.get("gugu_runtime_core_target") is False, "Gugu must not become a Legacy runtime-core target", failures)
        req(cross_policy.get("third_party_plugin_emulation") is False, "Cross-fork policy must not emulate third-party plugins", failures)
        cores = {str(core.get("id")): core for core in cross_fork.get("cores", [])}
        for core_id in ("original", "gugu", "united"):
            req(core_id in cores, f"Cross-fork advisory probe missing: {core_id}", failures)
            if core_id in cores:
                req(cores[core_id].get("advisory") is True, f"Cross-fork probe must remain advisory: {core_id}", failures)

        core_registry = json.loads(read(root, "compatibility/core-api-registry.json"))
        req(core_registry.get("release") == current, "Core API registry release must match projectVersion", failures)
        capabilities = set(core_registry.get("compatibility_capabilities", []))
        for capability in (
            "plugin-dependency-diagnostics",
            "third-party-dependency-boundary",
            "provider-alias-diagnostic-warning",
            "addon-dependency-health-evidence",
            "guarded-addon-runtime-failure-evidence",
        ):
            req(capability in capabilities, f"Phase 1K capability missing: {capability}", failures)

        addon_matrix = json.loads(read(root, "compatibility/addon-compatibility-matrix.json"))
        baselines = json.loads(read(root, "compatibility/release-baselines.json"))
        req(addon_matrix.get("release") == current, "Addon compatibility matrix release must match projectVersion", failures)
        req(baselines.get("candidate", {}).get("version") == current, "Candidate baseline must match projectVersion", failures)
        req(
            baselines.get("previous_stable", {}).get("version") == "4.1.21",
            "Phase 1K must not move the previous-stable baseline",
            failures,
        )
        req(
            baselines.get("legacy_floor", {}).get("version") == "4.1.15",
            "Phase 1K must not move the historical legacy floor",
            failures,
        )

        phase1j = read(root, "scripts/verify_core_platform_phase1j.py")
        req("phase_at_least_1j" in phase1j, "Phase 1J verifier must accept later Phase 1 releases", failures)
        req(
            'support.get("phase") == "Core Platform Phase 1J"' not in phase1j,
            "Phase 1J verifier still hard-codes an exact phase label",
            failures,
        )

        readme = read(root, "README.md")
        history = read(root, "EVERYTHING_THAT_CHANGED.md")
        req(f"Slimefun Legacy {current} is tested primarily" in readme, "README current-version support line missing", failures)
        for token in (
            "Core Platform Phase 1K (Dependency & Addon Boundary Hardening)",
            "/sf doctor dependencies",
            "Provider aliases are reported only as descriptor-level resolution",
            "does not install, enable, replace, or emulate third-party plugin dependencies",
            "Gugu is not a Slimefun Legacy runtime-core target",
            "Phase 1K Part 2 carries the same boundary evidence into `/sf versions`",
            "does **not** intercept arbitrary Paper plugin startup/onEnable exceptions or parse the server log",
        ):
            req(token in readme, f"README Phase 1K documentation missing: {token}", failures)
        req("# Slimefun Legacy 4.1.29 — Core Platform Phase 1K" in history, "4.1.29 Phase 1K history entry missing", failures)
        req("## Part 2 — Addon Boundary Evidence in `/sf versions`" in history, "Phase 1K Part 2 history entry missing", failures)
        req("# Slimefun Legacy 4.1.28 — Core Platform Phase 1J" in history, "4.1.28 Phase 1J history must remain preserved", failures)
    except Exception as error:
        failures.append(f"Phase 1K verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1k.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1K verification: FAIL\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1K verification: PASS\n"
        "- read-only hard/soft plugin dependency diagnostics are present\n"
        "- dependency problems and reverse consumers are exposed through Doctor\n"
        "- Paper provider aliases are identified without claiming class/API compatibility\n"
        "- Slimefun does not install, enable, disable, replace, or emulate third-party dependencies\n"
        "- Gugu/Original/United probes remain advisory while Legacy remains the runtime/release target\n"
        "- /sf versions surfaces addon hard-dependency, provider-alias, and guarded callback evidence\n"
        "- disabled-addon startup causes are not guessed when declared hard dependencies are healthy\n"
        "- arbitrary third-party plugin startup/onEnable failures are not intercepted or log-parsed\n"
        "- no Cargo, Energy, machine, database, storage-schema, or saved-world semantics are changed\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
