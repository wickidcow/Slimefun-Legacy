#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.28 Core Platform Phase 1J invariants."""
from __future__ import annotations

import hashlib
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


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "compatibility/cross-fork-api-matrix.json",
        "scripts/generate_cross_fork_api_matrix.py",
        "scripts/probe_cross_fork_api.py",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonApiCompatibilityFacade.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonApiCompatibilitySnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/CrossForkApiCapability.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRegistrationDisposition.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRegistrationRuntimeSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRegistrationSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRegistrationService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonApiCompatibilityFacade.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonRegistrationService.java",
    )
    for rel in required_files:
        req((root / rel).is_file(), f"Missing Phase 1J file: {rel}", failures)

    try:
        current = project_version(root)
        req(tuple(map(int, current.split("."))) >= (4, 1, 28), "Phase 1J requires 4.1.28 or newer", failures)

        facade = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonApiCompatibilityFacade.java")
        for token in (
            "SlimefunCoreVariant getRunningCoreVariant()",
            "Set<SlimefunCoreVariant> getCompatibilityTargets()",
            "Set<CrossForkApiCapability> getCapabilities()",
            "AddonRegistrationService getRegistrationService()",
            "RegistryRuntimeService getRegistryRuntimeService()",
            "AddonCompatibilityService getCompatibilityService()",
            "AddonRuntimeHealthService getRuntimeHealthService()",
        ):
            req(token in facade, f"Addon API facade invariant missing: {token}", failures)

        facade_impl = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonApiCompatibilityFacade.java",
        )
        for token in (
            "SlimefunCoreVariant.ORIGINAL",
            "SlimefunCoreVariant.GUGU",
            "SlimefunCoreVariant.UNITED",
            "SlimefunCoreVariant.LEGACY",
            "EnumSet.allOf(CrossForkApiCapability.class)",
        ):
            req(token in facade_impl, f"Cross-fork facade target/capability missing: {token}", failures)
        for forbidden in ("SLIMEFUN5,", "SLIMEFUN_CORE,"):
            req(forbidden not in facade_impl, f"Architecture-intake core was incorrectly promoted to API target: {forbidden}", failures)

        registration_api = read(
            root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRegistrationService.java"
        )
        for token in (
            "runAfterInitialRegistration",
            "AddonRegistrationDisposition",
            "getAddonSnapshots()",
            "getAddonSnapshot",
        ):
            req(token in registration_api, f"Addon registration API invariant missing: {token}", failures)

        registration_impl = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonRegistrationService.java",
        )
        for token in (
            "SlimefunItemRegistryFinalizedEvent",
            "pendingCallbacks.add",
            "runtimeHealth.runGuarded",
            "post-registration:",
            "drainPendingCallbacks()",
            "registryRuntime.getSnapshot().isInitialRegistrationFinalized()",
        ):
            req(token in registration_impl, f"Addon registration compatibility invariant missing: {token}", failures)
        for forbidden in (
            "disablePlugin(",
            "enablePlugin(",
            "SlimefunItem.register(",
            "registry.getAllSlimefunItems().clear",
        ):
            req(forbidden not in registration_impl, f"Registration compatibility layer must not mutate addon/core state: {forbidden}", failures)

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "DefaultAddonRegistrationService addonRegistrationService",
            "DefaultAddonApiCompatibilityFacade addonApiCompatibilityFacade",
            "registerEvents(addonRegistrationService, this)",
            "getAddonRegistrationService()",
            "getAddonApiCompatibilityFacade()",
        ):
            req(token in slimefun, f"Slimefun Phase 1J service wiring missing: {token}", failures)

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        for token in (
            'args[2].equalsIgnoreCase("api")',
            '"&6Slimefun Cross-Fork Addon API"',
            "getAddonApiCompatibilityFacade().getSnapshot()",
            "getAddonRegistrationService().getAddonSnapshot",
            "/sf doctor compatibility api <plugin>",
        ):
            req(token in doctor, f"Doctor cross-fork API invariant missing: {token}", failures)

        cross_fork = json.loads(read(root, "compatibility/cross-fork-api-matrix.json"))
        req(cross_fork.get("schema") == 1, "Cross-fork API matrix schema must be 1", failures)
        req(cross_fork.get("release") == current, "Cross-fork API matrix release must match projectVersion", failures)
        policy = cross_fork.get("policy", {})
        req(policy.get("legacy_contract_is_release_blocking") is True, "Local Legacy API contract must block release", failures)
        req(policy.get("external_fork_drift_is_advisory") is True, "External fork drift must remain advisory", failures)
        req(policy.get("automatic_upstream_merges") is False, "Cross-fork verification must not auto-merge upstream", failures)
        cores = {str(core.get("id")): core for core in cross_fork.get("cores", [])}
        for core_id in ("original", "gugu", "united"):
            req(core_id in cores, f"Cross-fork API target missing: {core_id}", failures)
            if core_id in cores:
                req(cores[core_id].get("advisory") is True, f"External {core_id} probe must be advisory", failures)
                req(bool(cores[core_id].get("probes")), f"External {core_id} probe has no representative APIs", failures)

        # Release-blocking local representative contract. These are deliberately common, mature addon entry points.
        representative = {
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/SlimefunAddon.java": (
                "JavaPlugin getJavaPlugin()",
                "String getBugTrackerURL()",
                "getPluginVersion()",
                "hasDependency(",
            ),
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java": (
                "void register(",
                "SlimefunAddon",
            ),
            "src/main/java/io/github/thebusybiscuit/slimefun4/api/events/SlimefunItemRegistryFinalizedEvent.java": (
                "class SlimefunItemRegistryFinalizedEvent",
            ),
        }
        for rel, tokens in representative.items():
            source = read(root, rel)
            for token in tokens:
                req(token in source, f"Representative shared API contract missing from {rel}: {token}", failures)

        workflow = read(root, ".github/workflows/compatibility-ci.yml")
        for token in (
            "prepare-cross-fork-api-matrix:",
            "cross-fork-api-probes:",
            "scripts/generate_cross_fork_api_matrix.py",
            "scripts/probe_cross_fork_api.py",
            "continue-on-error: ${{ matrix.advisory }}",
            "no automatic merge is performed",
        ):
            req(token in workflow, f"Cross-fork CI invariant missing: {token}", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        req(support.get("release") == current, "Support contract release must match projectVersion", failures)
        req(support.get("phase") == "Core Platform Phase 1J", "Support contract phase must be Phase 1J", failures)
        support_policy = support.get("compatibility_policy", {})
        for key in (
            "addon_api_compatibility_facade",
            "cross_fork_original_gugu_united_targets",
            "addon_registration_compatibility_service",
            "post_registration_callbacks_guarded",
            "late_runtime_registration_remains_supported",
            "cross_fork_source_drift_probes",
            "cross_fork_external_drift_is_advisory",
            "doctor_cross_fork_api_diagnostics",
        ):
            req(support_policy.get(key) is True, f"Phase 1J support policy missing: {key}", failures)
        req(support_policy.get("cross_fork_automatic_merges") is False, "Phase 1J must not auto-merge other forks", failures)
        req(
            support_policy.get("phase1j_changes_normal_cargo_energy_machine_semantics") is False,
            "Phase 1J must not change normal Cargo/Energy/machine semantics",
            failures,
        )
        req(support_policy.get("database_format_changed") is False, "Phase 1J must not change database format", failures)
        req(support_policy.get("storage_schema_changed") is False, "Phase 1J must not change storage schema", failures)

        core_registry = json.loads(read(root, "compatibility/core-api-registry.json"))
        req(core_registry.get("release") == current, "Core API registry release must match projectVersion", failures)
        capabilities = set(core_registry.get("compatibility_capabilities", []))
        for capability in (
            "cross-fork-api-facade",
            "post-registration-callback-service",
            "external-cross-fork-source-drift-probes",
            "cross-fork-doctor-diagnostics",
        ):
            req(capability in capabilities, f"Core API registry capability missing: {capability}", failures)

        matrix = json.loads(read(root, "compatibility/addon-compatibility-matrix.json"))
        baseline = json.loads(read(root, "compatibility/release-baselines.json"))
        req(matrix.get("release") == current, "Addon matrix release must match projectVersion", failures)
        req(baseline.get("candidate", {}).get("version") == current, "Baseline candidate must match projectVersion", failures)

        hash_guard = json.loads(read(root, "compatibility/phase1e-normal-core-sha256.json"))
        guarded = hash_guard.get("files", {})
        req(bool(guarded), "Normal-core hash guard is empty", failures)
        for rel, expected in guarded.items():
            path = root / rel
            req(path.is_file(), f"Guarded normal-core file missing: {rel}", failures)
            if path.is_file():
                req(sha256(path) == expected, f"Phase 1J changed guarded normal Slimefun core file: {rel}", failures)

        history = read(root, "EVERYTHING_THAT_CHANGED.md")
        readme = read(root, "README.md")
        req("# Slimefun Legacy 4.1.28 — Core Platform Phase 1J" in history, "4.1.28 history entry missing", failures)
        req(f"Slimefun Legacy {current} is tested primarily" in readme, "README current version missing", failures)
    except Exception as error:
        failures.append(f"Phase 1J verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1j.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1J verification: FAIL\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1J verification: PASS\n"
        "- Part 1 additive cross-fork addon API facade validated for Original/Gugu/United/Legacy targets\n"
        "- Part 2 post-registration callback compatibility is queued/guarded without freezing runtime registration\n"
        "- Part 3 local representative API contract and advisory external source-drift probes validated\n"
        "- protected Legacy API and addon source/binary compatibility remain release-blocking gates\n"
        "- normal Cargo, Energy, Guide, Ticker and protected machine core hashes remain unchanged\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
