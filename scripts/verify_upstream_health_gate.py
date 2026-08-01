#!/usr/bin/env python3
"""Regression tests for the conservative Gugu upstream health gate."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

SHA = "a" * 40


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload), encoding="utf-8")


def parse_outputs(path: Path) -> dict[str, str]:
    outputs: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            outputs[key] = value
    return outputs


def run_case(root: Path, name: str, checks: dict, workflows: dict, statuses: dict, extra: tuple[str, ...] = ()) -> dict[str, str]:
    script = root / "scripts/check_upstream_health.py"
    with tempfile.TemporaryDirectory(prefix=f"slimefun-health-{name}-") as raw:
        temp = Path(raw)
        checks_path = temp / "checks.json"
        workflows_path = temp / "workflows.json"
        statuses_path = temp / "statuses.json"
        report_path = temp / "report.md"
        output_path = temp / "github-output.txt"
        write_json(checks_path, checks)
        write_json(workflows_path, workflows)
        write_json(statuses_path, statuses)

        command = [
            sys.executable,
            str(script),
            "--repository",
            "SlimefunGuguProject/Slimefun4",
            "--sha",
            SHA,
            "--report",
            str(report_path),
            "--github-output",
            str(output_path),
            "--checks-json",
            str(checks_path),
            "--workflow-runs-json",
            str(workflows_path),
            "--statuses-json",
            str(statuses_path),
            *extra,
        ]
        result = subprocess.run(command, cwd=root, text=True, capture_output=True, check=False)
        if result.returncode != 0:
            raise AssertionError(f"{name}: evaluator failed\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}")
        if not report_path.is_file() or not output_path.is_file():
            raise AssertionError(f"{name}: evaluator did not create its report and GitHub outputs")
        return parse_outputs(output_path)


def assert_output(name: str, outputs: dict[str, str], **expected: str) -> None:
    for key, value in expected.items():
        actual = outputs.get(key)
        if actual != value:
            raise AssertionError(f"{name}: expected {key}={value!r}, got {actual!r}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    script = root / "scripts/check_upstream_health.py"
    if not script.is_file():
        print("ERROR: missing scripts/check_upstream_health.py", file=sys.stderr)
        return 1

    healthy_checks = {
        "total_count": 1,
        "check_runs": [
            {
                "id": 10,
                "name": "build",
                "status": "completed",
                "conclusion": "success",
                "app": {"slug": "github-actions"},
            }
        ],
    }
    healthy_workflows = {
        "total_count": 1,
        "workflow_runs": [
            {
                "id": 20,
                "workflow_id": 5,
                "name": "Build",
                "status": "completed",
                "conclusion": "success",
                "run_attempt": 1,
            }
        ],
    }
    healthy_statuses = {"state": "success", "total_count": 1, "statuses": [{"context": "ci", "state": "success"}]}

    outputs = run_case(root, "healthy", healthy_checks, healthy_workflows, healthy_statuses)
    assert_output("healthy", outputs, state="healthy", allowed="true", override_used="false", decision="healthy")

    failed_checks = json.loads(json.dumps(healthy_checks))
    failed_checks["check_runs"][0]["conclusion"] = "failure"
    outputs = run_case(root, "failed", failed_checks, healthy_workflows, healthy_statuses)
    assert_output("failed", outputs, state="unhealthy", allowed="false", override_used="false", decision="blocked")

    pending_workflows = json.loads(json.dumps(healthy_workflows))
    pending_workflows["workflow_runs"][0]["status"] = "in_progress"
    pending_workflows["workflow_runs"][0]["conclusion"] = None
    outputs = run_case(root, "pending", healthy_checks, pending_workflows, healthy_statuses)
    assert_output("pending", outputs, state="pending", allowed="false", override_used="false", decision="blocked")

    outputs = run_case(
        root,
        "missing",
        {"total_count": 0, "check_runs": []},
        {"total_count": 0, "workflow_runs": []},
        {"state": "pending", "total_count": 0, "statuses": []},
    )
    assert_output("missing", outputs, state="unknown", allowed="false", override_used="false", decision="blocked")

    outputs = run_case(
        root,
        "override",
        failed_checks,
        healthy_workflows,
        healthy_statuses,
        ("--allow-unhealthy", "--override-reason", "Manual draft compatibility test"),
    )
    assert_output("override", outputs, state="unhealthy", allowed="true", override_used="true", decision="overridden")

    rerun_checks = {
        "total_count": 2,
        "check_runs": [
            {
                "id": 1,
                "name": "build",
                "status": "completed",
                "conclusion": "failure",
                "completed_at": "2026-07-26T10:00:00Z",
                "app": {"slug": "github-actions"},
            },
            {
                "id": 2,
                "name": "build",
                "status": "completed",
                "conclusion": "success",
                "completed_at": "2026-07-26T11:00:00Z",
                "app": {"slug": "github-actions"},
            },
        ],
    }
    rerun_workflows = {
        "total_count": 2,
        "workflow_runs": [
            {
                "id": 30,
                "workflow_id": 7,
                "name": "Build",
                "status": "completed",
                "conclusion": "failure",
                "run_attempt": 1,
                "updated_at": "2026-07-26T10:00:00Z",
            },
            {
                "id": 30,
                "workflow_id": 7,
                "name": "Build",
                "status": "completed",
                "conclusion": "success",
                "run_attempt": 2,
                "updated_at": "2026-07-26T11:00:00Z",
            },
        ],
    }
    outputs = run_case(root, "rerun", rerun_checks, rerun_workflows, healthy_statuses)
    assert_output("rerun", outputs, state="healthy", allowed="true", override_used="false", decision="healthy")

    print("Gugu upstream health-gate regression verification passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
