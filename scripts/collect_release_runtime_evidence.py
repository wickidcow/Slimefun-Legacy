#!/usr/bin/env python3
"""Require runtime evidence for the exact files about to be released.

This command only reads GitHub Actions. It never dispatches a workflow or writes
a release, tag, branch or asset. Missing, failed or mismatched evidence stops the
release before its publishing step.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import stat
import subprocess
import time
import zipfile
from datetime import datetime
from pathlib import Path, PurePosixPath


REPOSITORY = "wickidcow/Slimefun-Legacy"
NATIVE_WORKFLOW = ".github/workflows/native-entity-retirement.yml"
FULL_STACK_WORKFLOW = ".github/workflows/paper-26.3-full-stack.yml"
BUNDLE_WORKFLOW = ".github/workflows/build-sfl-addons-compat-bundle.yml"
NATIVE_ARTIFACT_JOBS = {
    "paired-storage-inputs": "Build fixed paired storage test inputs",
    "paired-storage-purpur-1.21.11-2568": "Paired storage and restart on purpur-1.21.11-2568",
    "paired-storage-purpur-26.3-2652": "Paired storage and restart on purpur-26.3-2652",
}
FULL_STACK_ARTIFACT_JOBS = {
    "full-stack-core": "Build universal candidate against 26.3",
    "full-stack-addon-bundle": "Fetch exact addon bundle under test",
}
RUN_FIELDS = (
    "id", "path", "workflow_id", "event", "head_branch", "head_sha", "status",
    "conclusion", "run_attempt", "created_at", "run_started_at", "updated_at", "html_url",
)
JOB_FIELDS = (
    "id", "run_id", "run_attempt", "head_sha", "name", "status", "conclusion",
    "started_at", "completed_at", "html_url",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def integer(value: object) -> bool:
    return type(value) is int and value > 0


def timestamp(value: object) -> datetime:
    require(isinstance(value, str), "Missing evidence timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError("Invalid evidence timestamp") from error
    require(parsed.tzinfo is not None, "Evidence timestamps must include a timezone")
    return parsed


# Only transient GitHub transport failures retry. Evidence identity, run-attempt,
# digest and source checks remain strict, and no historical fallback is allowed.
GH_MAX_ATTEMPTS = 4
GH_TRANSIENT_ERROR = re.compile(
    r"\b(?:429|500|502|503|504)\b|rate.?limit|too many requests|"
    r"bad gateway|service unavailable|gateway timeout|connection reset|connection timed out",
    re.IGNORECASE,
)


def github_bytes(endpoint: str) -> bytes:
    for attempt in range(GH_MAX_ATTEMPTS):
        try:
            return subprocess.run(
                ["gh", "api", "--method", "GET", endpoint], capture_output=True,
                check=True, timeout=180,
            ).stdout
        except subprocess.CalledProcessError as error:
            detail = error.stderr or b""
            if isinstance(detail, bytes):
                detail = detail.decode("utf-8", errors="replace")
            if not GH_TRANSIENT_ERROR.search(detail) or attempt == GH_MAX_ATTEMPTS - 1:
                # Avoid leaking authenticated command errors to CI logs.
                raise RuntimeError("GitHub Actions evidence download failed") from error
        except subprocess.TimeoutExpired as error:
            if attempt == GH_MAX_ATTEMPTS - 1:
                raise RuntimeError("GitHub Actions evidence download failed") from error
        except OSError as error:
            raise RuntimeError("GitHub Actions evidence download failed") from error
        time.sleep(min(2 ** attempt, 4))
    raise RuntimeError("GitHub Actions evidence download failed")


def github_json(endpoint: str) -> dict:
    value = json.loads(github_bytes(endpoint))
    require(isinstance(value, dict), "GitHub returned an invalid evidence object")
    return value


def collection(endpoint: str, key: str) -> dict:
    rows = []
    total = None
    separator = "&" if "?" in endpoint else "?"
    for page in range(1, 21):
        response = github_json(f"{endpoint}{separator}per_page=100&page={page}")
        count = response.get("total_count")
        batch = response.get(key)
        require(type(count) is int and count >= 0 and isinstance(batch, list),
                "Incomplete GitHub Actions evidence collection")
        require(total is None or count == total, "Evidence collection changed during pagination")
        total = count
        rows.extend(batch)
        require(len(rows) <= total, "Duplicate or inconsistent evidence pages")
        if len(rows) == total:
            ids = [row.get("id") for row in rows]
            require(all(integer(value) for value in ids) and len(set(ids)) == len(ids),
                    "Duplicate or invalid evidence object IDs")
            return {"total_count": total, key: rows}
        require(len(batch) == 100, "Truncated GitHub Actions evidence collection")
    raise ValueError("Evidence collection exceeds the bounded pagination limit")


def run_snapshot(run: dict) -> dict:
    value = {key: run.get(key) for key in RUN_FIELDS}
    for key in ("repository", "head_repository"):
        repository = run.get(key)
        require(isinstance(repository, dict), "Missing workflow repository identity")
        value[key] = {field: repository.get(field) for field in ("id", "full_name")}
    return value


def validate_run(run: dict, repository: str, source: str, workflow: str,
                 events: set[str]) -> None:
    require(run.get("repository", {}).get("full_name") == repository
            and run.get("head_repository", {}).get("full_name") == repository,
            "Workflow repository does not match the release repository")
    require(run.get("path") == workflow and run.get("head_sha") == source
            and run.get("head_branch") == "master" and run.get("event") in events,
            "Workflow does not identify the required release source and event")
    require(integer(run.get("id")) and integer(run.get("run_attempt")),
            "Invalid workflow run or attempt identity")
    require(run.get("status") == "completed" and run.get("conclusion") == "success",
            "The latest required workflow execution has not completed successfully")


def latest_run(repository: str, source: str, workflow: str, events: set[str]) -> dict:
    name = PurePosixPath(workflow).name
    payload = collection(
        f"repos/{repository}/actions/workflows/{name}/runs?head_sha={source}&branch=master",
        "workflow_runs",
    )
    rows = [row for row in payload["workflow_runs"]
            if row.get("path") == workflow and row.get("head_sha") == source
            and row.get("head_branch") == "master" and row.get("event") in events
            and row.get("conclusion") != "skipped"]
    require(bool(rows), "No eligible non-skipped runtime workflow exists for the exact release source")
    # GitHub can emit later skipped workflow_run triggers for the same source.
    # They contain no runtime evidence and must not mask a real execution.
    # Still include failed, cancelled, queued and running executions: a newer
    # eligible failure or pending attempt must never fall back to older green.
    selected = max(rows, key=lambda row: (
        timestamp(row.get("run_started_at") or row.get("created_at")), row["id"],
    ))
    current = github_json(f"repos/{repository}/actions/runs/{selected['id']}")
    validate_run(current, repository, source, workflow, events)
    frozen = github_json(
        f"repos/{repository}/actions/runs/{current['id']}/attempts/{current['run_attempt']}"
    )
    validate_run(frozen, repository, source, workflow, events)
    current_snapshot, attempt_snapshot = run_snapshot(current), run_snapshot(frozen)
    stable_fields = set(current_snapshot) - {"created_at", "updated_at", "run_started_at"}
    require({key: current_snapshot[key] for key in stable_fields}
            == {key: attempt_snapshot[key] for key in stable_fields},
            "Workflow attempt changed while selecting evidence")
    # Run and attempt endpoints can have different timestamps after a retry.
    # Preserve each endpoint separately so the final read compares like with like.
    attempt_snapshot["run_endpoint_snapshot"] = current_snapshot
    return attempt_snapshot


def jobs_snapshot(repository: str, run_id: int) -> dict:
    payload = collection(f"repos/{repository}/actions/runs/{run_id}/jobs?filter=latest", "jobs")
    return {"total_count": payload["total_count"], "jobs": [
        {key: job.get(key) for key in JOB_FIELDS} for job in sorted(payload["jobs"], key=lambda row: row["id"])
    ]}


def artifact_for_job(artifacts: dict, name: str, jobs: dict, job_name: str,
                     run: dict) -> dict:
    rows = [row for row in artifacts["artifacts"] if row.get("name") == name]
    require(len(rows) == 1, f"Expected one immutable artifact named {name}")
    artifact = rows[0]
    require(integer(artifact.get("id")) and artifact.get("expired") is False,
            f"Missing or expired artifact: {name}")
    require(re.fullmatch(r"sha256:[0-9a-f]{64}", str(artifact.get("digest", ""))) is not None,
            f"Missing authoritative archive digest: {name}")
    origin = artifact.get("workflow_run", {})
    require(origin.get("id") == run["id"] and origin.get("head_sha") == run["head_sha"]
            and origin.get("repository_id") == run["repository"]["id"]
            and origin.get("head_repository_id") == run["head_repository"]["id"],
            f"Artifact belongs to another source or repository: {name}")
    producing_jobs = [job for job in jobs["jobs"] if job.get("name") == job_name]
    require(len(producing_jobs) == 1, f"Ambiguous artifact producing job: {name}")
    job = producing_jobs[0]
    require(job.get("run_id") == run["id"] and job.get("head_sha") == run["head_sha"]
            and job.get("status") == "completed" and job.get("conclusion") == "success"
            and integer(job.get("run_attempt")) and job["run_attempt"] <= run["run_attempt"],
            f"Artifact producing job did not succeed on the selected source: {name}")
    require(timestamp(job.get("started_at")) <= timestamp(artifact.get("created_at"))
            <= timestamp(job.get("completed_at")),
            f"Artifact was not produced by the retained successful job: {name}")
    return artifact


def extract_artifact(raw: bytes, metadata: dict, destination: Path) -> dict:
    digest = hashlib.sha256(raw).hexdigest()
    require(metadata.get("digest") == f"sha256:{digest}"
            and metadata.get("size_in_bytes") == len(raw),
            "Downloaded artifact does not match the authoritative archive identity")
    require(not destination.exists(), "Refusing to reuse a runtime evidence directory")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(0 < len(entries) <= 10000
                and sum(entry.file_size for entry in entries) <= 512 * 1024 * 1024,
                "Unexpected runtime evidence archive size")
        names = set()
        for entry in entries:
            path = PurePosixPath(entry.filename)
            require(entry.filename and "\\" not in entry.filename and not path.is_absolute()
                    and all(part not in {"", ".", ".."} for part in path.parts)
                    and entry.filename.rstrip("/") == path.as_posix()
                    and path.as_posix() not in names,
                    "Unsafe or duplicate runtime evidence archive path")
            mode = stat.S_IFMT(entry.external_attr >> 16)
            require(mode in {0, stat.S_IFREG, stat.S_IFDIR},
                    "Runtime evidence archive contains a non-regular entry")
            names.add(path.as_posix())
        require(archive.testzip() is None, "Runtime evidence archive failed its CRC check")
        destination.mkdir(parents=True)
        for entry in entries:
            target = destination.joinpath(*PurePosixPath(entry.filename).parts)
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(entry))
    return {"artifact_id": metadata["id"], "name": metadata["name"],
            "sha256": digest, "bytes": len(raw), "members": len(entries)}


def write_json(path: Path, value: dict | list) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def file_identity(path: Path) -> dict:
    require(stat.S_ISREG(path.lstat().st_mode), "Release input must be a regular file")
    data = path.read_bytes()
    require(bool(data), "Release input is empty")
    return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def collect_workflow(repository: str, source: str, workflow: str, events: set[str],
                     artifact_jobs: dict[str, str], directory: Path) -> tuple[dict, dict, dict]:
    directory.mkdir()
    run = latest_run(repository, source, workflow, events)
    jobs = jobs_snapshot(repository, run["id"])
    artifacts = collection(f"repos/{repository}/actions/runs/{run['id']}/artifacts", "artifacts")
    write_json(directory / "run.json", run)
    write_json(directory / "jobs.json", jobs)
    write_json(directory / "artifacts.json", artifacts)
    downloaded = []
    for name, job_name in artifact_jobs.items():
        metadata = artifact_for_job(artifacts, name, jobs, job_name, run)
        raw = github_bytes(f"repos/{repository}/actions/artifacts/{metadata['id']}/zip")
        receipt = extract_artifact(raw, metadata, directory / name)
        (directory / f"{name}.zip").write_bytes(raw)
        downloaded.append(receipt)
    write_json(directory / "downloads.json", downloaded)
    return run, jobs, artifacts


def require_unchanged(repository: str, source: str, workflow: str, events: set[str],
                      frozen: tuple[dict, dict, dict], names: dict[str, str]) -> None:
    run, jobs, artifacts = frozen
    require(latest_run(repository, source, workflow, events) == run,
            "Runtime workflow changed while collecting release evidence")
    require(jobs_snapshot(repository, run["id"]) == jobs,
            "Runtime job results changed while collecting release evidence")
    current = collection(f"repos/{repository}/actions/runs/{run['id']}/artifacts", "artifacts")
    require(current["total_count"] == artifacts["total_count"]
            and sorted(current["artifacts"], key=lambda row: row["id"])
            == sorted(artifacts["artifacts"], key=lambda row: row["id"]),
            "Runtime artifact metadata changed while collecting release evidence")
    for name, job_name in names.items():
        require(artifact_for_job(current, name, jobs, job_name, run)
                == artifact_for_job(artifacts, name, jobs, job_name, run),
                "Runtime artifact changed while collecting release evidence")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True, choices=[REPOSITORY])
    parser.add_argument("--source", required=True)
    parser.add_argument("--bundle-run", type=int, required=True)
    parser.add_argument("--core", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--matrix", type=Path, default=Path("compatibility/sfl-addon-release-matrix.json"))
    parser.add_argument("--fixture-lock", type=Path,
                        default=Path("scripts/runtime/paired_addon_storage/fixture-lock.json"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source) or args.bundle_run <= 0:
        parser.error("Expected an immutable full source SHA and positive bundle run ID")
    require(not args.output.exists(), "Refusing to reuse collected release evidence")
    args.output.mkdir(parents=True)
    summary = {"schema": 1, "status": "FAIL", "source": args.source,
               "publication_authorization": False}
    try:
        summary["release_inputs"] = {"core": file_identity(args.core), "bundle": file_identity(args.bundle)}
        if __package__:
            from .verify_release_runtime_evidence import verify_native_evidence
            from .verify_release_full_stack_evidence import verify_full_stack_evidence
        else:
            from verify_release_runtime_evidence import verify_native_evidence
            from verify_release_full_stack_evidence import verify_full_stack_evidence

        bundle = github_json(f"repos/{args.repository}/actions/runs/{args.bundle_run}")
        validate_run(bundle, args.repository, args.source, BUNDLE_WORKFLOW,
                     {"push", "schedule", "workflow_dispatch"})
        write_json(args.output / "bundle-run.json", run_snapshot(bundle))
        native = collect_workflow(args.repository, args.source, NATIVE_WORKFLOW,
                                  {"push", "workflow_dispatch"}, NATIVE_ARTIFACT_JOBS,
                                  args.output / "native")
        summary["native"] = verify_native_evidence(
            args.output, args.core, args.bundle, args.matrix, args.fixture_lock, args.source,
        )
        require(summary["native"].get("status") == "PASS_EXACT_RELEASE_NATIVE_PAIR",
                "Native evidence did not accept the exact release pair")
        full_stack = collect_workflow(args.repository, args.source, FULL_STACK_WORKFLOW,
                                      {"workflow_run"}, FULL_STACK_ARTIFACT_JOBS,
                                      args.output / "fullstack")
        summary["full_stack"] = verify_full_stack_evidence(
            args.output, args.core, args.bundle, args.source,
        )
        require(summary["full_stack"].get("status") == "PASS",
                "Full-stack evidence did not accept the exact release files")
        require_unchanged(args.repository, args.source, NATIVE_WORKFLOW,
                          {"push", "workflow_dispatch"}, native, NATIVE_ARTIFACT_JOBS)
        require_unchanged(args.repository, args.source, FULL_STACK_WORKFLOW,
                          {"workflow_run"}, full_stack, FULL_STACK_ARTIFACT_JOBS)
        require(run_snapshot(github_json(f"repos/{args.repository}/actions/runs/{args.bundle_run}"))
                == run_snapshot(bundle), "Canonical bundle run changed during release verification")
        require(summary["release_inputs"]
                == {"core": file_identity(args.core), "bundle": file_identity(args.bundle)},
                "Release files changed during runtime evidence verification")
        summary["status"] = "PASS"
        print("Verified exact release core and addon bytes against native and full-stack evidence")
        return 0
    finally:
        write_json(args.output / "release-runtime-evidence.json", summary)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError, zipfile.BadZipFile) as failure:
        raise SystemExit(f"Release runtime evidence rejected: {failure}") from None
