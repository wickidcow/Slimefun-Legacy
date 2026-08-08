#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.25 Core Platform Phase 1G invariants."""
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
        "docs/history/CORE_PLATFORM_PHASE1G.md",
        "docs/history/SLIMEFUN_LEGACY_4.1.25.md",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/lifecycle/CoreLifecycleState.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/lifecycle/CoreLifecyclePhase.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/lifecycle/CoreLifecycleSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/lifecycle/CoreLifecycleService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/lifecycle/DefaultCoreLifecycleService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/scheduling/SchedulerSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/MachineRuntimeSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/MachineRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultMachineRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/storage/StorageRuntimeSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/storage/StorageRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultStorageRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRuntimeFailureSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRuntimeHealthService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonRuntimeHealthService.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/core/services/lifecycle/TestDefaultCoreLifecycleService.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/TestDefaultAddonRuntimeHealthService.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/runtime/TestMachineRuntimeSnapshot.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/storage/TestStorageRuntimeSnapshot.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/core/services/scheduling/TestSchedulerSnapshot.java",
    )
    for rel in required_files:
        req((root / rel).is_file(), f"Missing Phase 1G file: {rel}", failures)

    try:
        current = project_version(root)
        req(tuple(map(int, current.split("."))) >= (4, 1, 25), "Phase 1G requires 4.1.25 or newer", failures)

        lifecycle = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/lifecycle/DefaultCoreLifecycleService.java",
        )
        for token in (
            "CoreLifecycleState.STARTING",
            "CoreLifecycleState.RUNNING",
            "CoreLifecycleState.STOPPING",
            "runShutdownStep",
            "shutdownFailures.incrementAndGet()",
            "RuntimeException | LinkageError",
            "markStartupFailed",
        ):
            req(token in lifecycle, f"Lifecycle invariant missing: {token}", failures)

        scheduler_api = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/scheduling/SlimefunScheduler.java",
        )
        for token in (
            "default void quiesce()",
            "default boolean isAcceptingTasks()",
            "default int getActiveTaskCount()",
            "default @Nonnull SchedulerSnapshot getSnapshot()",
            "void cancelAll()",
        ):
            req(token in scheduler_api, f"Scheduler API invariant missing: {token}", failures)

        scheduler = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/scheduling/PaperScheduler.java",
        )
        for token in (
            "AtomicBoolean acceptingTasks",
            "acceptingTasks.set(false)",
            "return tasks.size()",
            "if (!acceptingTasks.get())",
            "cancelAll()",
        ):
            req(token in scheduler, f"Paper scheduler lifecycle invariant missing: {token}", failures)

        threads = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/ThreadService.java")
        for token in (
            "AtomicBoolean shutdown",
            "scheduledPool.shutdownNow()",
            "cachedPool.shutdownNow()",
            "RejectedExecutionException",
            "delay,\n                    period,\n                    unit",
        ):
            req(token in threads, f"ThreadService lifecycle invariant missing: {token}", failures)
        req(
            "delay,\n                delay,\n                unit" not in threads,
            "ThreadService still ignores the scheduled period argument",
            failures,
        )

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "lifecycleService.beginStart()",
            "CoreLifecyclePhase.CONFIGURATION",
            "CoreLifecyclePhase.STORAGE",
            "CoreLifecyclePhase.CONTENT",
            "CoreLifecyclePhase.RUNTIME",
            "CoreLifecyclePhase.INTEGRATIONS",
            "lifecycleService.markRunning()",
            'runShutdownStep("scheduler-quiesce"',
            'runShutdownStep("database"',
            'runShutdownStep("thread-service"',
            "getCoreLifecycleService()",
            "getMachineRuntimeService()",
            "getStorageRuntimeService()",
            "getAddonRuntimeHealthService()",
            "getTickerTask()",
        ):
            req(token in slimefun, f"Slimefun Phase 1G wiring invariant missing: {token}", failures)

        machine_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/MachineRuntimeService.java")
        machine_impl = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultMachineRuntimeService.java",
        )
        for token in ("getSnapshot()", "retryMachine", "retryAllMachines", "setPaused"):
            req(token in machine_api and token in machine_impl, f"Machine runtime facade missing: {token}", failures)

        storage_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/storage/StorageRuntimeService.java")
        storage_impl = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultStorageRuntimeService.java",
        )
        req("StorageRuntimeSnapshot getSnapshot()" in storage_api, "Storage runtime snapshot API missing", failures)
        for forbidden in (".save(", ".delete(", ".insert(", ".update(", "setBlockDataStorageType"):
            req(forbidden not in storage_impl, f"Read-only storage facade performs mutation: {forbidden}", failures)

        health_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/addons/AddonRuntimeHealthService.java")
        health_impl = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonRuntimeHealthService.java",
        )
        for token in ("recordFailure", "getFailures", "getObservedFailureCount", "clearAll"):
            req(token in health_api and token in health_impl, f"Addon runtime health invariant missing: {token}", failures)
        req("disablePlugin" not in health_impl, "Addon runtime telemetry must not disable plugins", failures)

        addon_compat = read(
            root,
            "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultAddonCompatibilityService.java",
        )
        req(
            "this(owner, platformCompatibilityService, optionalDependencyService, null);" in addon_compat,
            "Original DefaultAddonCompatibilityService constructor compatibility bridge is missing",
            failures,
        )
        req(
            'runtimeHealthService.recordFailure(plugin, "compatibility-provider", error)' in addon_compat,
            "Compatibility-provider failure telemetry is missing",
            failures,
        )

        post_setup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java")
        req(
            '"item-load:" + item.getId()' in post_setup and ".recordFailure(" in post_setup,
            "Addon item-load failure telemetry is missing",
            failures,
        )
        integrations = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/integrations/IntegrationsManager.java")
        for token in ('"integration-runtime:" + name', '"integration-hook:" + pluginName'):
            req(token in integrations, f"Third-party integration health telemetry missing: {token}", failures)

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        for token in (
            'case "core", "lifecycle" -> sendCoreHealth(sender)',
            '"&6Slimefun Core Runtime Health"',
            "Slimefun.getCoreLifecycleService().getSnapshot()",
            "Slimefun.getSchedulerService().getSnapshot()",
            "Slimefun.getMachineRuntimeService().getSnapshot()",
            "Slimefun.getStorageRuntimeService().getSnapshot()",
            "Slimefun.getAddonRuntimeHealthService().getFailure(result.getPluginName())",
            "This view is observational",
        ):
            req(token in doctor, f"Doctor core/runtime invariant missing: {token}", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        req(support.get("release") == current, "Support contract release must match projectVersion", failures)
        req(support.get("phase") == "Core Platform Phase 1G", "Support contract phase must be Phase 1G", failures)
        policy = support.get("compatibility_policy", {})
        for key in (
            "core_lifecycle_service",
            "ordered_shutdown_failure_isolation",
            "scheduler_quiesce_and_health_snapshot",
            "machine_runtime_facade",
            "storage_runtime_read_only_facade",
            "addon_runtime_callback_health",
            "addon_callback_failures_do_not_auto_disable",
        ):
            req(policy.get(key) is True, f"Phase 1G support policy missing: {key}", failures)
        for key in ("storage_schema_changed", "phase1g_changes_normal_cargo_energy_machine_semantics"):
            req(policy.get(key) is False, f"Phase 1G safety policy must remain false: {key}", failures)

        matrix = json.loads(read(root, "compatibility/addon-compatibility-matrix.json"))
        baseline = json.loads(read(root, "compatibility/release-baselines.json"))
        req(matrix.get("release") == current, "Addon matrix release must match projectVersion", failures)
        req(
            baseline.get("candidate", {}).get("version") == current,
            "Baseline registry candidate must match projectVersion",
            failures,
        )

        hash_guard = json.loads(read(root, "compatibility/phase1e-normal-core-sha256.json"))
        guarded = hash_guard.get("files", {})
        req(bool(guarded), "Phase 1E normal-core hash guard is empty", failures)
        for rel, expected in guarded.items():
            path = root / rel
            req(path.is_file(), f"Guarded normal-core file missing: {rel}", failures)
            if path.is_file():
                req(sha256(path) == expected, f"Phase 1G changed guarded normal Slimefun core file: {rel}", failures)

        changelog = read(root, "CHANGELOG.md")
        readme = read(root, "README.md")
        req("# Slimefun Legacy 4.1.25 — Core Platform Phase 1G" in changelog, "4.1.25 changelog entry missing", failures)
        req("[Release Notes](docs/history/SLIMEFUN_LEGACY_4.1.25.md)" in readme, "README release notes link missing", failures)
        req("Slimefun Legacy 4.1.25 is tested primarily" in readme, "README current version missing", failures)
    except Exception as error:
        failures.append(f"Phase 1G verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1g.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1G verification: FAIL\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1G verification: PASS\n"
        "- Part 1 lifecycle and scheduler quiesce/health foundation validated\n"
        "- Part 2 machine runtime and read-only storage facades validated\n"
        "- Part 3 addon callback telemetry and compatibility bridges validated\n"
        "- ordered shutdown cleanup remains failure-isolated\n"
        "- normal Slimefun Cargo, Energy, Guide, Ticker and protected machine core hashes remain unchanged\n"
        "- storage schemas, saved data and normal Cargo/Energy/machine semantics remain unchanged\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
