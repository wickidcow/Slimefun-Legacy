#!/usr/bin/env python3
"""Bind native paired-storage evidence to the exact proposed release bytes.

The collector owns authenticated downloads, ZIP digest checks and run freshness.
This module only reads the downloaded evidence and candidate files; it never
builds, starts a server, changes a fixture pin, or authorizes publication.
"""
from __future__ import annotations

import hashlib
import io
import json
from datetime import datetime
from pathlib import Path, PurePosixPath
import re
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.runtime.paired_addon_storage import run_probe as paired

REPOSITORY = "wickidcow/Slimefun-Legacy"
WORKFLOW = ".github/workflows/native-entity-retirement.yml"
ADDONS = {
    "networks": ("wickidcow/SF_NetworksExp", "Networks"),
    "ie2": ("wickidcow/SF_InfinityExpansion2", "InfinityExpansion2"),
}
FIXED_JOBS = {
    "Build exact-source retirement candidate",
    "Build fixed paired storage test inputs",
    "Build exact published paired storage test inputs",
    "Native retirement on Folia 26.3 build 3",
    "Native retirement on Purpur 26.3 build 2646",
}
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
MAX_JSON = 8 * 1024 * 1024


class ReleaseRuntimeEvidenceError(ValueError):
    """The proposed release does not match complete native test evidence."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ReleaseRuntimeEvidenceError(message)


def _read(path: Path, limit: int = 128 * 1024 * 1024) -> bytes:
    _require(path.is_file() and not path.is_symlink(), "Missing or non-regular evidence file: " + str(path))
    _require(path.stat().st_size <= limit, "Evidence file exceeds its size limit")
    data = path.read_bytes()
    _require(len(data) <= limit, "Evidence file grew beyond its size limit")
    return data


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _identity(data: bytes) -> dict:
    return {"bytes": len(data), "sha256": _sha(data)}


def _object(pairs: list[tuple]) -> dict:
    value = {}
    for key, item in pairs:
        _require(key not in value, "Duplicate JSON key")
        value[key] = item
    return value


def _json_bytes(data: bytes) -> dict:
    def nonfinite(value: str):
        raise ReleaseRuntimeEvidenceError("Non-finite JSON number")
    value = json.loads(data.decode("utf-8"), object_pairs_hook=_object, parse_constant=nonfinite)
    _require(isinstance(value, dict), "Expected a JSON object")
    return value


def _json(path: Path) -> dict:
    return _json_bytes(_read(path, MAX_JSON))


def _positive(value) -> bool:
    return type(value) is int and value > 0


def _schema_one(value) -> bool:
    return type(value) is int and value == 1


def _time(value: str) -> datetime:
    _require(isinstance(value, str), "Missing evidence timestamp")
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    _require(result.tzinfo is not None, "Evidence timestamp lacks a timezone")
    return result


def _uuid(value: str) -> bool:
    return isinstance(value, str) and str(uuid.UUID(value)) == value


def _record_identity(value: dict) -> bool:
    return (isinstance(value, dict) and set(value) == {"bytes", "sha256"}
            and _positive(value["bytes"]) and isinstance(value["sha256"], str)
            and HEX64.fullmatch(value["sha256"]) is not None)


def _verify_run(directory: Path, expected_source: str, platforms: dict) -> dict:
    run = _json(directory / "run.json")
    _require(_positive(run.get("id")) and _positive(run.get("run_attempt")), "Invalid native run identity")
    _require(run.get("path") == WORKFLOW and run.get("head_branch") == "master"
             and run.get("head_sha") == expected_source and run.get("event") in {"push", "workflow_dispatch"}
             and run.get("status") == "completed" and run.get("conclusion") == "success",
             "Native run is not a successful exact-source master workflow")
    for key in ("repository", "head_repository"):
        _require(run.get(key, {}).get("full_name") == REPOSITORY
                 and _positive(run[key].get("id")), "Native run repository mismatch")
    _require(run["repository"]["id"] == run["head_repository"]["id"], "Native run came from a fork")

    payload = _json(directory / "jobs.json")
    rows = payload.get("jobs")
    _require(isinstance(rows, list) and payload.get("total_count") == len(rows), "Incomplete native job pages")
    required = (FIXED_JOBS
                | {"Paired storage and restart on " + key for key in platforms}
                | {"Current published storage and restart on " + key for key in platforms})
    _require(len(rows) == len(required), "Native run must contain all historical and release jobs")
    jobs, ids = {}, set()
    for job in rows:
        _require(isinstance(job, dict) and job.get("name") in required, "Unexpected native job")
        _require(job["name"] not in jobs and _positive(job.get("id")) and job["id"] not in ids,
                 "Duplicate native job identity")
        _require(job.get("run_id") == run["id"] and job.get("head_sha") == expected_source
                 and _positive(job.get("run_attempt")) and job["run_attempt"] <= run["run_attempt"],
                 "Native job source or attempt mismatch")
        _require(job.get("status") == "completed" and job.get("conclusion") == "success",
                 "Required native job did not succeed: " + job["name"])
        _require(_time(job["started_at"]) <= _time(job["completed_at"]), "Invalid native job interval")
        jobs[job["name"]] = job
        ids.add(job["id"])
    _require(set(jobs) == required, "Missing required native job")
    _require(max(job["run_attempt"] for job in jobs.values()) == run["run_attempt"],
             "Native jobs snapshot contains no job from the current attempt")

    payload = _json(directory / "artifacts.json")
    rows = payload.get("artifacts")
    _require(isinstance(rows, list) and payload.get("total_count") == len(rows), "Incomplete native artifact pages")
    producers = {
        "paired-storage-inputs": "Build fixed paired storage test inputs",
        "paired-storage-release-inputs": "Build exact published paired storage test inputs",
        **{"paired-storage-" + key: "Paired storage and restart on " + key for key in platforms},
        **{"paired-storage-release-" + key: "Current published storage and restart on " + key
           for key in platforms},
    }
    artifacts, ids = {}, set()
    for artifact in rows:
        _require(isinstance(artifact, dict) and _positive(artifact.get("id"))
                 and artifact["id"] not in ids, "Duplicate or invalid native artifact identity")
        ids.add(artifact["id"])
        name = artifact.get("name")
        if name not in producers:
            continue
        _require(name not in artifacts and artifact.get("expired") is False
                 and _positive(artifact.get("size_in_bytes")), "Missing, ambiguous or expired native artifact")
        _require(isinstance(artifact.get("digest"), str)
                 and re.fullmatch(r"sha256:[0-9a-f]{64}", artifact["digest"]) is not None,
                 "Native artifact has no API SHA-256")
        origin = artifact.get("workflow_run", {})
        _require(origin.get("id") == run["id"] and origin.get("head_sha") == expected_source
                 and origin.get("repository_id") == run["repository"]["id"]
                 and origin.get("head_repository_id") == run["repository"]["id"],
                 "Native artifact belongs to another run or source")
        job = jobs[producers[name]]
        _require(_time(job["started_at"]) <= _time(artifact["created_at"]) <= _time(job["completed_at"]),
                 "Native artifact does not belong to its successful producing job")
        artifacts[name] = artifact
    _require(set(artifacts) == set(producers), "Missing original native input or lane artifacts")
    return {"run": run, "jobs": jobs, "artifacts": artifacts}


def _bundle_pair(bundle: Path, matrix: dict, expected_source: str) -> tuple[dict, dict]:
    rows = matrix.get("addons")
    _require(isinstance(rows, list) and rows, "Missing canonical addon matrix")
    matrix_rows = {}
    for row in rows:
        _require(isinstance(row, dict) and isinstance(row.get("repository"), str)
                 and row["repository"] not in matrix_rows, "Duplicate or invalid canonical matrix repository")
        matrix_rows[row["repository"]] = row

    with zipfile.ZipFile(io.BytesIO(_read(bundle))) as archive:
        names = archive.namelist()
        _require(len(names) == len(set(names)), "Duplicate canonical bundle member")
        _require(sum(entry.file_size for entry in archive.infolist()) <= 256 * 1024 * 1024,
                 "Canonical bundle expansion exceeds its limit")
        for entry in archive.infolist():
            path = PurePosixPath(entry.filename)
            _require(not path.is_absolute() and ".." not in path.parts and "\\" not in entry.filename
                     and ((entry.external_attr >> 16) & 0o170000) != 0o120000
                     and not entry.flag_bits & 1, "Unsafe canonical bundle member")
        _require(archive.testzip() is None, "Canonical bundle CRC failure")
        _require(archive.getinfo("SF_ADDON_MANIFEST.json").file_size <= MAX_JSON, "Oversized canonical manifest")
        manifest = _json_bytes(archive.read("SF_ADDON_MANIFEST.json"))
        _require(manifest.get("core_source_commit") == expected_source, "Canonical bundle core source mismatch")
        records = manifest.get("addons")
        _require(isinstance(records, list) and records, "Missing canonical bundle addon records")
        by_repo, jars = {}, set()
        for row in records:
            _require(isinstance(row, dict) and isinstance(row.get("repository"), str)
                     and row["repository"] not in by_repo, "Duplicate or invalid canonical addon repository")
            name = row.get("jar")
            _require(isinstance(name, str) and PurePosixPath(name).name == name
                     and name.endswith(".jar") and name not in jars, "Invalid or duplicate canonical addon JAR")
            _require(row["repository"] in matrix_rows
                     and row.get("commit") == matrix_rows[row["repository"]].get("source_commit")
                     and isinstance(row.get("commit"), str) and HEX40.fullmatch(row["commit"]) is not None,
                     "Canonical addon source differs from the reviewed matrix")
            _require(isinstance(row.get("sha256"), str) and HEX64.fullmatch(row["sha256"]) is not None,
                     "Invalid canonical addon digest")
            _require(name in names and archive.getinfo(name).file_size <= 128 * 1024 * 1024,
                     "Missing or oversized canonical addon JAR")
            _require(_sha(archive.read(name)) == row["sha256"], "Canonical addon payload digest mismatch")
            by_repo[row["repository"]] = row
            jars.add(name)
        _require(set(by_repo) == set(matrix_rows), "Canonical bundle repository membership differs from the matrix")
        _require({name for name in names if name.endswith(".jar")} == jars,
                 "Canonical bundle contains undeclared addon JARs")
        pair = {}
        for key, (repository, plugin) in ADDONS.items():
            _require(repository in by_repo, "Canonical bundle is missing a paired addon")
            row = by_repo[repository]
            pair[key] = {"record": row, "bytes": archive.read(row["jar"]), "plugin": plugin}
    return manifest, pair


def _verify_inputs(directory: Path, core: Path, pair: dict, lock: dict, lock_bytes: bytes,
                   expected_source: str, match_candidate: bool) -> tuple[dict, bytes]:
    raw = _read(directory / "inputs.json", MAX_JSON)
    manifest = _json_bytes(raw)
    _require(_schema_one(manifest.get("schema")) and manifest.get("purpose") == lock["purpose"]
             and manifest.get("source") == expected_source, "Native paired input source or purpose mismatch")
    _require(manifest.get("lock_sha256") == _sha(lock_bytes)
             and _read(directory / "fixture-lock.json", MAX_JSON) == lock_bytes,
             "Native paired fixture lock differs from the release checkout")
    _require(manifest.get("receipt_source_sha256") == paired.digest(paired.RECEIPT_SOURCE),
             "Native paired receipt source differs from the release checkout")
    metadata = _json(directory / "core-metadata.json")
    _require(_schema_one(metadata.get("schema")), "Invalid native core metadata schema")
    version = paired.validate_core(core, metadata, expected_source)
    _require(manifest.get("core_version") == version, "Native paired core version mismatch")
    _require(set(manifest.get("inputs", {})) == {"core", "networks", "ie2"}
             and set(manifest.get("addon_checkouts", {})) == set(ADDONS)
             and set(manifest.get("classpath_receipts", {})) == set(ADDONS), "Incomplete paired provenance")
    paired.validate_compiled_inputs(directory, manifest, lock)
    core_bytes = _read(core)
    _require(_read(directory / "core.jar") == core_bytes, "Native paired core is not the exact release JAR")
    for key, plugin, expected_version in [("core", "Slimefun", version)] + [
            (key, plugin, lock["addons"][key]["version"]) for key, (_, plugin) in ADDONS.items()]:
        details = paired.validate_jar(directory / (key + ".jar"), plugin, expected_version)
        _require(_positive(manifest["inputs"][key].get("classes"))
                 and manifest["inputs"][key] == {"file": key + ".jar", **details},
                 "Native packaged JAR or descriptor differs from input provenance: " + key)

    for key, (repository, plugin) in ADDONS.items():
        pin, selected = lock["addons"][key], pair[key]["record"]
        _require(pin.get("repository") == repository and pin.get("plugin") == plugin
                 and isinstance(pin.get("source"), str) and HEX40.fullmatch(pin["source"]) is not None
                 and isinstance(pin.get("tree"), str) and HEX40.fullmatch(pin["tree"]) is not None,
                 "Invalid paired fixture source pin")
        paired.validate_published_jar(directory / (key + ".jar"), pin)
        _require(manifest["addon_checkouts"][key] == {
            field: pin[field] for field in ("repository", "source", "tree")},
            "Native checkout provenance differs from its reviewed fixture")
        # Historical fixture results remain independently checked against their
        # original immutable published artifacts, *not* misrepresented as the
        # newly distributed pair. Only the second release lane matches the ZIP.
        if match_candidate:
            _require(selected["commit"] == pin["source"],
                     "Native tested addon source differs from the canonical candidate: " + key)
            _require(selected.get("version") == pin["version"] == manifest["inputs"][key]["version"],
                     "Native tested addon version differs from the canonical candidate: " + key)
            _require(pair[key]["bytes"] == _read(directory / (key + ".jar"))
                     and _identity(pair[key]["bytes"]) == {
                         field: manifest["inputs"][key][field] for field in ("bytes", "sha256")},
                     "Native tested addon bytes differ from the canonical candidate: " + key)
        proof_path = directory / (key + "-core-classpath.json")
        _require(_schema_one(_json(proof_path).get("schema")), "Invalid native classpath proof schema")
        _require(manifest["classpath_receipts"][key] == paired.validate_classpath_receipt(
            proof_path, _sha(core_bytes), len(core_bytes)), "Native addon classpath proof changed: " + key)
    _require(manifest.get("fixture_sources") == paired.verify_fixture_sources(directory, lock, packaged=True),
             "Native fixture sources differ from their reviewed hashes")
    _require(manifest.get("jeg_compile_dependency", {}).get("sha256")
             == lock.get("jeg_compile_dependency", {}).get("sha256"), "Native compile dependency pin mismatch")
    return manifest, raw


def _verify_lane(directory: Path, manifest: dict, input_bytes: bytes, platform: dict,
                 used_tokens: set, used_runs: set) -> dict:
    _require(_read(directory / "inputs.json", MAX_JSON) == input_bytes, "Native lane used different input manifest bytes")
    proof = _json(directory / "verification.json")
    _require(_schema_one(proof.get("schema")) and proof.get("status") == "PASS"
             and proof.get("source") == manifest["source"] and proof.get("platform") == platform
             and proof.get("input_manifest_sha256") == _sha(input_bytes) and not proof.get("error"),
             "Native lane is incomplete or belongs to another source/platform/input")
    _require(_uuid(proof.get("run_id")) and proof["run_id"] not in used_runs, "Reused native lane receipt")
    used_runs.add(proof["run_id"])
    bound = proof.get("bound_files")
    expected_names = {"server.jar", "plugins/Slimefun.jar", "plugins/Networks.jar",
                      "plugins/InfinityExpansion2.jar", "plugins/PairedIE2StorageProbe.jar",
                      "plugins/PairedStorageReceipt.jar"}
    _require(isinstance(bound, dict) and set(bound) == expected_names
             and all(_record_identity(value) for value in bound.values()), "Incomplete native installed-file ledger")
    _require(bound["server.jar"]["sha256"] == platform["launcher_sha256"], "Native launcher identity mismatch")
    for key, name in (("core", "Slimefun"), ("networks", "Networks"), ("ie2", "InfinityExpansion2")):
        _require(bound["plugins/" + name + ".jar"] == {
            field: manifest["inputs"][key][field] for field in ("bytes", "sha256")},
            "Native lane installed another plugin: " + key)
    pids = []
    for phase in ("seed", "verify"):
        result = proof.get(phase, {})
        _require(_schema_one(result.get("schema")) and result.get("phase") == phase
                 and result.get("status") == "PASS" and not result.get("error")
                 and type(result.get("exit_code")) is int and result["exit_code"] == 0
                 and result.get("timed_out") is False
                 and _positive(result.get("pid")), "Native phase did not complete normally")
        _require(_uuid(result.get("token")) and result["token"] not in used_tokens, "Stale native phase token")
        used_tokens.add(result["token"])
        pids.append(result["pid"])
        _require(result.get("installed_before") == bound == result.get("installed_after"),
                 "Native installed bytes changed across a JVM")
        _require(_json(directory / (phase + "-verification.json")) == result,
                 "Native phase summary differs from original evidence")
        started = _json(directory / (phase + "-startup.json"))
        stopped = _json(directory / (phase + "-shutdown.json"))
        _require(_schema_one(started.get("schema")) and _schema_one(stopped.get("schema"))
                 and started == result.get("startup") and stopped == result.get("shutdown"),
                 "Native startup/shutdown receipt substitution")
        native = _read(directory / (phase + "-native.log"), paired.MAX_LOG_BYTES)
        console = _read(directory / (phase + "-console.log"), paired.MAX_LOG_BYTES)
        text = _read(directory / (phase + "-result.txt"), MAX_JSON).decode("utf-8").strip()
        _require(result.get("native_log") == _identity(native) and result.get("console_log") == _identity(console)
                 and result.get("result") == text, "Native phase log/result identity changed")
        paired.validate_phase_evidence(
            phase, result["token"], result["pid"], result["exit_code"], result["timed_out"],
            text, started, stopped, native.decode("utf-8"), console.decode("utf-8"), manifest, platform)
        snapshot = result.get("database_snapshot", {})
        _require(snapshot.get("inputs_receipt_sha256") == _sha(input_bytes), "Database snapshot input binding mismatch")
        paired.validate_database_evidence(snapshot, phase, result["pid"])
    _require(pids[0] != pids[1], "Native storage phases did not use separate JVMs")
    return {"platform": platform, "status": "PASS", "input_manifest_sha256": _sha(input_bytes),
            "seed_pid": pids[0], "verify_pid": pids[1]}


def verify_native_evidence(evidence_dir: Path, core: Path, bundle: Path, matrix: Path,
                           fixture_lock: Path, expected_source: str,
                           release_lock: Path | None = None,
                           distribution_lock: Path | None = None) -> dict:
    """Require two independent native recovery pairs: historical and exact release.

    Optional lock parameters support offline synthetic tests; production caller
    uses the committed immutable lock files, never a downloaded workflow file.
    """
    try:
        _require(isinstance(expected_source, str) and HEX40.fullmatch(expected_source) is not None,
                 "Expected source must be a complete Git commit")
        historical_bytes = _read(fixture_lock, MAX_JSON)
        historical = _json_bytes(historical_bytes)
        _require(_schema_one(historical.get("schema"))
                 and historical.get("purpose") == "integration-test-fixture"
                 and historical.get("distribution_selection") is False
                 and set(historical.get("addons", {})) == set(ADDONS),
                 "Invalid historical paired storage fixture lock")

        release_path = (Path(release_lock) if release_lock is not None
                        else ROOT / "scripts/runtime/paired_addon_storage/release-lock.json")
        distribution_path = (Path(distribution_lock) if distribution_lock is not None
                             else ROOT / "scripts/published_storage_pair_lock.json")
        current_bytes = _read(release_path, MAX_JSON)
        current = _json_bytes(current_bytes)
        distributed = _json(distribution_path)
        _require(_schema_one(current.get("schema"))
                 and current.get("purpose") == "exact-published-release-storage-pair"
                 and current.get("distribution_selection") is True
                 and current.get("distribution_lock_path") == "scripts/published_storage_pair_lock.json"
                 and distributed.get("purpose") == "published-distribution"
                 and distributed.get("distribution_selection") is True
                 and set(current.get("addons", {})) == set(ADDONS)
                 and current["addons"] == distributed.get("addons"),
                 "Current release storage pair differs from immutable published distribution lock")
        for key in ("fixture_sources", "jeg_compile_dependency", "platforms"):
            _require(current.get(key) == historical.get(key),
                     "Current release pair cannot alter historical native fixture contract: " + key)
        platforms = historical.get("platforms", {})
        _require(set(platforms) == {"purpur-1.21.11-2568", "purpur-26.3-2652"},
                 "Missing required native platform")
        for key, platform in platforms.items():
            _require(platform.get("software") == "Purpur"
                     and key == "purpur-" + platform["minecraft"] + "-" + platform["build"]
                     and isinstance(platform.get("launcher_sha256"), str)
                     and HEX64.fullmatch(platform["launcher_sha256"]) is not None,
                     "Invalid reviewed native platform")
        _require(len(historical.get("fixture_sources", [])) == 2,
                 "Both reviewed Java fixtures are required")

        directory = Path(evidence_dir) / "native"
        metadata = _verify_run(directory, expected_source, platforms)
        bundle_manifest, pair = _bundle_pair(Path(bundle), _json(Path(matrix)), expected_source)
        old_manifest, old_bytes = _verify_inputs(
            directory / "paired-storage-inputs", Path(core), pair, historical,
            historical_bytes, expected_source, match_candidate=False)
        current_manifest, current_input_bytes = _verify_inputs(
            directory / "paired-storage-release-inputs", Path(core), pair, current,
            current_bytes, expected_source, match_candidate=True)
        _require(old_manifest["core_version"] == current_manifest["core_version"],
                 "Current and historical paired fixtures used different core versions")
        used_tokens, used_runs = set(), set()
        historical_lanes = [
            _verify_lane(directory / ("paired-storage-" + key), old_manifest,
                         old_bytes, platform, used_tokens, used_runs)
            for key, platform in platforms.items()
        ]
        release_lanes = [
            _verify_lane(directory / ("paired-storage-release-" + key), current_manifest,
                         current_input_bytes, platform, used_tokens, used_runs)
            for key, platform in platforms.items()
        ]
        return {
            "schema": 1, "status": "PASS_EXACT_RELEASE_NATIVE_PAIR",
            "source": expected_source, "core_version": current_manifest["core_version"],
            "core": _identity(_read(Path(core))), "bundle": _identity(_read(Path(bundle))),
            "bundle_revision": bundle_manifest.get("bundle_revision"),
            "native_run": {"id": metadata["run"]["id"], "attempt": metadata["run"]["run_attempt"]},
            "required_jobs": len(metadata["jobs"]),
            "artifacts": {name: {"id": row["id"], "digest": row["digest"]}
                          for name, row in metadata["artifacts"].items()},
            "addons": {key: {"repository": row["record"]["repository"],
                            "source": row["record"]["commit"],
                            "version": row["record"]["version"], **_identity(row["bytes"])}
                       for key, row in pair.items()},
            "historical_addons": {key: {"source": historical["addons"][key]["source"],
                                       "version": historical["addons"][key]["version"],
                                       "sha256": historical["addons"][key]["published_artifact"]["sha256"]}
                                  for key in ADDONS},
            "historical_lanes": historical_lanes,
            "lanes": release_lanes,
            "scope": "Both immutable historical fixture and exact released Networks/IE2 JARs "
                     "passed independent two-JVM storage/restart and database tests. This "
                     "does not certify live customer worlds, crashes, or universal anti-dupe.",
        }
    except ReleaseRuntimeEvidenceError:
        raise
    except (OSError, KeyError, AttributeError, TypeError, ValueError, RuntimeError,
            zipfile.BadZipFile) as error:
        raise ReleaseRuntimeEvidenceError("Invalid native release evidence: " + str(error)) from error
