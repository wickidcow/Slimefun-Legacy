#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.26 Core Platform Phase 1H invariants."""
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
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    required_files = (
        "EVERYTHING_THAT_CHANGED.md",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/registry/AddonRegistrySnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/registry/RegistryRuntimeSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/registry/RegistryRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/registry/DefaultRegistryRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/CoreReadinessState.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/CoreReadinessSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/CoreReadinessService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultCoreReadinessService.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/registry/TestRegistryRuntimeSnapshot.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/runtime/TestCoreReadinessSnapshot.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/addons/TestAddonRuntimeHealthGuard.java",
    )
    for rel in required_files:
        req((root / rel).is_file(), f"Missing Phase 1H file: {rel}", failures)

    try:
        current = project_version(root)
        req(tuple(map(int, current.split("."))) >= (4, 1, 26), "Phase 1H requires 4.1.26 or newer", failures)

        registry_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/registry/RegistryRuntimeService.java")
        registry_impl = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/registry/DefaultRegistryRuntimeService.java")
        for token in ("RegistryRuntimeSnapshot getSnapshot()", "List<AddonRegistrySnapshot> getAddonSnapshots()"):
            req(token in registry_api, f"Registry runtime API invariant missing: {token}", failures)
        for token in (
            "markInitialRegistrationFinalized",
            "finalized.compareAndSet(false, true)",
            "Math.max(0, totalItems - finalizedItemCount)",
            "getAllSlimefunItems()",
            "getAllItemGroups()",
        ):
            req(token in registry_impl, f"Registry observer invariant missing: {token}", failures)
        for forbidden in (".add(", ".remove(", ".put(", ".clear("):
            req(forbidden not in registry_impl, f"Registry runtime service must stay read-only: {forbidden}", failures)

        readiness = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultCoreReadinessService.java")
        for token in (
            "CoreReadinessState.READY",
            "CoreReadinessState.DEGRADED",
            "CoreReadinessState.STARTING",
            "CoreReadinessState.STOPPING",
            "CoreReadinessState.FAILED",
            "registrySnapshot.isInitialRegistrationFinalized()",
            "schedulerSnapshot.isAcceptingTasks()",
            "storageSnapshot.isReady()",
            "machineSnapshot.isHalted()",
        ):
            req(token in readiness, f"Core readiness invariant missing: {token}", failures)
        for forbidden in ("setPaused(", "retryMachine(", "retryAllMachines(", ".save(", ".delete("):
            req(forbidden not in readiness, f"Core readiness service must remain observational: {forbidden}", failures)

        health = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRuntimeHealthService.java")
        for token in (
            "default boolean runGuarded",
            "default <T> @Nonnull Optional<T> callGuarded",
            "catch (RuntimeException | LinkageError failure)",
            "recordFailure(plugin, operation, failure)",
        ):
            req(token in health, f"Guarded addon callback invariant missing: {token}", failures)
        req("disablePlugin" not in health, "Guarded callback API must not disable plugins", failures)

        integrations = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/integrations/IntegrationsManager.java")
        req(
            '.runGuarded(integration, "integration-hook:" + pluginName' in integrations,
            "Third-party integration hook does not use the shared guarded callback path",
            failures,
        )

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "DefaultRegistryRuntimeService registryRuntimeService",
            "DefaultCoreReadinessService coreReadinessService",
            "getRegistryRuntimeService()",
            "getCoreReadinessService()",
        ):
            req(token in slimefun, f"Slimefun Phase 1H wiring invariant missing: {token}", failures)

        post_setup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java")
        req(
            "registryRuntime.markInitialRegistrationFinalized()" in post_setup,
            "Initial registry finalization is not published by PostSetup",
            failures,
        )

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        for token in (
            'case "registry" -> sendRegistryHealth(sender)',
            '"&6Slimefun Registry Runtime"',
            "Slimefun.getCoreReadinessService().getSnapshot()",
            "Slimefun.getRegistryRuntimeService().getSnapshot()",
            "runtime additions",
            "Read-only registry diagnostics",
        ):
            req(token in doctor, f"Doctor Phase 1H invariant missing: {token}", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        req(support.get("release") == current, "Support contract release must match projectVersion", failures)
        req(support.get("phase") == "Core Platform Phase 1H", "Support contract phase must be Phase 1H", failures)
        policy = support.get("compatibility_policy", {})
        for key in (
            "registry_runtime_service",
            "registry_initial_finalization_observable",
            "runtime_registration_count_observational",
            "core_readiness_service",
            "core_readiness_is_observational",
            "guarded_addon_callback_api",
            "guarded_addon_callbacks_do_not_auto_disable",
        ):
            req(policy.get(key) is True, f"Phase 1H support policy missing: {key}", failures)
        req(
            policy.get("phase1h_changes_normal_cargo_energy_machine_semantics") is False,
            "Phase 1H safety policy must keep normal Cargo/Energy/machine semantics unchanged",
            failures,
        )

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
                req(sha256(path) == expected, f"Phase 1H changed guarded normal Slimefun core file: {rel}", failures)

        history = read(root, "EVERYTHING_THAT_CHANGED.md")
        readme = read(root, "README.md")
        req("# Slimefun Legacy 4.1.26 — Core Platform Phase 1H" in history, "4.1.26 history entry missing", failures)
        req("Slimefun Legacy 4.1.26 is tested primarily" in readme, "README current version missing", failures)
    except Exception as error:
        failures.append(f"Phase 1H verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1h.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1H verification: FAIL\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1H verification: PASS\n"
        "- Part 1 read-only registry runtime and ownership snapshots validated\n"
        "- Part 2 combined core readiness aggregation validated\n"
        "- Part 3 guarded addon callback foundation validated\n"
        "- registry and readiness services remain observational\n"
        "- normal Slimefun Cargo, Energy, Guide, Ticker and protected machine core hashes remain unchanged\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
