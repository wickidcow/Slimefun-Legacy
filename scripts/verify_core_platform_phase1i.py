#!/usr/bin/env python3
"""Verify Slimefun Legacy 4.1.27 Core Platform Phase 1I invariants."""
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
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/world/ChunkRuntimeState.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/world/WorldChunkRuntimeSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/world/WorldChunkRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/world/DefaultWorldChunkRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/storage/BlockDataRuntimeSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/storage/BlockDataRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/storage/DefaultBlockDataRuntimeService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/MachineChunkCoordinationSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/runtime/MachineChunkCoordinationService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultMachineChunkCoordinationService.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/world/TestWorldChunkRuntimeSnapshot.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/storage/TestBlockDataRuntimeSnapshot.java",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/runtime/TestMachineChunkCoordinationSnapshot.java",
    )
    for rel in required_files:
        req((root / rel).is_file(), f"Missing Phase 1I file: {rel}", failures)

    try:
        current = project_version(root)
        req(tuple(map(int, current.split("."))) >= (4, 1, 27), "Phase 1I requires 4.1.27 or newer", failures)

        world_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/world/WorldChunkRuntimeService.java")
        world_impl = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/world/DefaultWorldChunkRuntimeService.java")
        for token in (
            "WorldChunkRuntimeSnapshot getSnapshot()",
            "ChunkRuntimeState getChunkState",
            "isChunkReady",
            "isWorldTracked",
        ):
            req(token in world_api, f"World/chunk API invariant missing: {token}", failures)
        for token in (
            "ChunkLoadEvent",
            "ChunkUnloadEvent",
            "WorldLoadEvent",
            "WorldUnloadEvent",
            "ConcurrentHashMap",
            "ChunkRuntimeState.UNLOADING",
            "ChunkRuntimeState.FAILED",
        ):
            req(token in world_impl, f"World/chunk observer invariant missing: {token}", failures)
        for forbidden in ("addPluginChunkTicket", "loadChunk(", "unloadChunk(", "getChunkAt("):
            req(forbidden not in world_impl, f"World/chunk observer must not mutate chunk loading: {forbidden}", failures)

        block_runtime = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/storage/DefaultBlockDataRuntimeService.java")
        for token in (
            "scheduler.isOwnedByCurrentRegion(anchor)",
            "scheduler.runAt(anchor",
            "ChunkDataLoadMode.LOAD_ON_STARTUP",
            "getAllLoadedChunkData()",
            "SlimefunItem.getById(block.getSfId())",
            "recordDeferredStorageLoad",
            "recordStorageLoadFailure",
        ):
            req(token in block_runtime, f"Block-data runtime invariant missing: {token}", failures)

        controller = read(root, "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataController.java")
        for token in (
            "Slimefun.getSchedulerService().isFolia()",
            "scheduleWorldChunkLoad(world, cKey)",
            "Slimefun.getSchedulerService().runAt(anchor",
            "world.getChunkAt(chunkX, chunkZ, false)",
            "scheduleExistingLoadedChunks(world)",
            "world.isChunkLoaded(chunkX, chunkZ)",
        ):
            req(token in controller, f"Folia world-startup storage invariant missing: {token}", failures)

        startup = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/SlimefunStartupTask.java")
        for token in (
            "Slimefun.getWorldChunkRuntimeService()",
            "Slimefun.getBlockDataRuntimeService()",
        ):
            req(token in startup, f"Startup runtime-listener wiring missing: {token}", failures)
        req("new ChunkListener()" not in startup, "Legacy direct ChunkListener is still registered", failures)
        req("new WorldListener()" not in startup, "Legacy direct WorldListener is still registered", failures)

        coordination = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/runtime/DefaultMachineChunkCoordinationService.java")
        for token in ("ticker.getTickLocations()", "worldChunks.getChunkState", "case READY"):
            req(token in coordination, f"Machine/chunk coordination invariant missing: {token}", failures)
        for forbidden in ("enableTicker(", "disableTicker(", "setPaused(", "retryMachine(", "clear("):
            req(forbidden not in coordination, f"Machine/chunk coordination must remain observational: {forbidden}", failures)

        slimefun = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        for token in (
            "DefaultWorldChunkRuntimeService worldChunkRuntimeService",
            "DefaultBlockDataRuntimeService blockDataRuntimeService",
            "DefaultMachineChunkCoordinationService machineChunkCoordinationService",
            "getWorldChunkRuntimeService()",
            "getBlockDataRuntimeService()",
            "getMachineChunkCoordinationService()",
        ):
            req(token in slimefun, f"Slimefun Phase 1I wiring invariant missing: {token}", failures)

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        for token in (
            'case "chunks", "worlds", "blocks" -> sendChunkHealth(sender)',
            '"&6Slimefun World, Chunk and Block Runtime"',
            "getWorldChunkRuntimeService().getSnapshot()",
            "getBlockDataRuntimeService().getSnapshot()",
            "getMachineChunkCoordinationService().getSnapshot()",
            "Diagnostics only: this command does not load/unload chunks",
        ):
            req(token in doctor, f"Doctor Phase 1I invariant missing: {token}", failures)

        support = json.loads(read(root, "compatibility/support-contract.json"))
        req(support.get("release") == current, "Support contract release must match projectVersion", failures)
        support_phase = str(support.get("phase", ""))
        phase_match = re.fullmatch(r"Core Platform Phase 1([A-Z])", support_phase)
        req(
            phase_match is not None and phase_match.group(1) >= "I",
            "Support contract phase must be Phase 1I or a later Core Platform Phase 1 release",
            failures,
        )
        policy = support.get("compatibility_policy", {})
        for key in (
            "world_chunk_runtime_service",
            "world_chunk_lifecycle_is_observational",
            "world_chunk_observer_does_not_pin_chunks",
            "ownership_safe_chunk_storage_load_fallback",
            "folia_world_startup_chunk_resolution",
            "block_data_runtime_read_only_facade",
            "machine_chunk_coordination_observational",
            "chunk_diagnostics_do_not_mutate_world_state",
        ):
            req(policy.get(key) is True, f"Phase 1I support policy missing: {key}", failures)
        req(
            policy.get("phase1i_changes_normal_cargo_energy_machine_semantics") is False,
            "Phase 1I must keep normal Cargo/Energy/machine semantics unchanged",
            failures,
        )
        req(policy.get("database_format_changed") is False, "Phase 1I must not change database format", failures)
        req(policy.get("storage_schema_changed") is False, "Phase 1I must not change storage schema", failures)

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
                req(sha256(path) == expected, f"Phase 1I changed guarded normal Slimefun core file: {rel}", failures)

        history = read(root, "EVERYTHING_THAT_CHANGED.md")
        readme = read(root, "README.md")
        req("# Slimefun Legacy 4.1.27 — Core Platform Phase 1I" in history, "4.1.27 history entry missing", failures)
        req(f"Slimefun Legacy {current} is tested primarily" in readme, "README current version missing", failures)
    except Exception as error:
        failures.append(f"Phase 1I verifier failed to inspect repository: {error}")

    report = root / "build/reports/core-platform-phase1i.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1I verification: FAIL\n" + "\n".join(f"- {item}" for item in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1I verification: PASS\n"
        "- Part 1 world/chunk lifecycle observer validated without chunk pinning or load mutation\n"
        "- Part 2 ownership-aware block-data runtime and Folia startup chunk resolution validated\n"
        "- Part 3 machine/chunk coordination diagnostics remain observational\n"
        "- database/storage schemas and saved-world formats remain unchanged\n"
        "- normal Slimefun Cargo, Energy, Guide, Ticker and protected machine core hashes remain unchanged\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
