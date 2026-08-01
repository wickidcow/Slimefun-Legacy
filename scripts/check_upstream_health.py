#!/usr/bin/env python3
"""Evaluate the health of an upstream GitHub commit before a sync merge.

The gate combines modern Check Runs, GitHub Actions workflow runs, and legacy
commit statuses. It is deliberately conservative: failed, pending, unavailable,
or completely missing health signals block an automatic sync unless a manual
override with a reason was explicitly supplied.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

PASS_CONCLUSIONS = {"success", "neutral", "skipped"}
FAIL_CONCLUSIONS = {
    "action_required",
    "cancelled",
    "failure",
    "stale",
    "startup_failure",
    "timed_out",
}
PENDING_STATUSES = {"queued", "in_progress", "pending", "requested", "waiting"}


@dataclass(frozen=True)
class ApiResult:
    name: str
    payload: dict[str, Any]
    error: str | None = None


@dataclass(frozen=True)
class HealthEvaluation:
    state: str
    allowed: bool
    override_used: bool
    summary: str
    failures: tuple[str, ...]
    pending: tuple[str, ...]
    warnings: tuple[str, ...]
    check_runs: tuple[dict[str, Any], ...]
    workflow_runs: tuple[dict[str, Any], ...]
    statuses: tuple[dict[str, Any], ...]
    api_results: tuple[ApiResult, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True, help="GitHub repository as OWNER/REPO")
    parser.add_argument("--sha", required=True, help="Full upstream commit SHA")
    parser.add_argument("--report", required=True, type=Path, help="Markdown report path")
    parser.add_argument("--github-output", type=Path, help="Optional GitHub Actions output file")
    parser.add_argument("--allow-unhealthy", action="store_true", help="Permit a manually reviewed draft sync")
    parser.add_argument("--override-reason", default="", help="Required reason for --allow-unhealthy")
    parser.add_argument("--api-base", default=os.environ.get("GITHUB_API_URL", "https://api.github.com"))
    parser.add_argument("--checks-json", type=Path, help="Offline Check Runs fixture")
    parser.add_argument("--workflow-runs-json", type=Path, help="Offline workflow runs fixture")
    parser.add_argument("--statuses-json", type=Path, help="Offline combined-status fixture")
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return value


def request_json(api_base: str, endpoint: str, token: str | None) -> tuple[dict[str, Any], str | None]:
    url = f"{api_base.rstrip('/')}/{endpoint.lstrip('/')}"
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "Slimefun-Legacy-Upstream-Health-Gate",
        "X-GitHub-Api-Version": "2022-11-28",
    }

    def perform(auth_token: str | None) -> dict[str, Any]:
        request_headers = dict(headers)
        if auth_token:
            request_headers["Authorization"] = f"Bearer {auth_token}"
        request = urllib.request.Request(url, headers=request_headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode("utf-8")
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError("GitHub API returned a non-object response")
        return value

    try:
        return perform(token), None
    except urllib.error.HTTPError as exc:
        # A repository-scoped GITHUB_TOKEN may not be authorized for another
        # public repository. Retry without credentials before declaring the
        # public health signal unavailable.
        if token and exc.code in {401, 403, 404}:
            try:
                return perform(None), None
            except Exception as retry_exc:  # noqa: BLE001 - preserve API diagnostics
                return {}, f"HTTP {exc.code}; unauthenticated retry failed: {retry_exc}"
        return {}, f"HTTP {exc.code}: {exc.reason}"
    except Exception as exc:  # noqa: BLE001 - gate must report and block, not crash silently
        return {}, str(exc)


def fetch_sources(args: argparse.Namespace) -> tuple[ApiResult, ApiResult, ApiResult]:
    repository = urllib.parse.quote(args.repository, safe="/")
    sha = urllib.parse.quote(args.sha, safe="")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")

    if args.checks_json:
        checks = ApiResult("check runs", load_json(args.checks_json))
    else:
        payload, error = request_json(
            args.api_base,
            f"repos/{repository}/commits/{sha}/check-runs?filter=latest&per_page=100",
            token,
        )
        checks = ApiResult("check runs", payload, error)

    if args.workflow_runs_json:
        workflows = ApiResult("workflow runs", load_json(args.workflow_runs_json))
    else:
        payload, error = request_json(
            args.api_base,
            f"repos/{repository}/actions/runs?head_sha={sha}&per_page=100",
            token,
        )
        workflows = ApiResult("workflow runs", payload, error)

    if args.statuses_json:
        statuses = ApiResult("commit statuses", load_json(args.statuses_json))
    else:
        payload, error = request_json(
            args.api_base,
            f"repos/{repository}/commits/{sha}/status",
            token,
        )
        statuses = ApiResult("commit statuses", payload, error)

    return checks, workflows, statuses


def latest_by(items: Iterable[dict[str, Any]], key_fields: tuple[str, ...], rank_fields: tuple[str, ...]) -> list[dict[str, Any]]:
    latest: dict[tuple[Any, ...], dict[str, Any]] = {}

    def nested(item: dict[str, Any], field: str) -> Any:
        value: Any = item
        for component in field.split("."):
            if not isinstance(value, dict):
                return None
            value = value.get(component)
        return value

    def rank(item: dict[str, Any]) -> tuple[str, ...]:
        values: list[str] = []
        for field in rank_fields:
            value = nested(item, field)
            if isinstance(value, int):
                values.append(f"{value:020d}")
            else:
                values.append(str(value) if value is not None else "")
        return tuple(values)

    for index, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        key = tuple(nested(item, field) for field in key_fields)
        if all(value in {None, ""} for value in key):
            key = ("unnamed", index)
        current = latest.get(key)
        if current is None or rank(item) > rank(current):
            latest[key] = item
    return sorted(latest.values(), key=lambda item: str(item.get("name") or item.get("path") or item.get("id") or ""))


def evaluate(args: argparse.Namespace, api_results: tuple[ApiResult, ApiResult, ApiResult]) -> HealthEvaluation:
    checks_result, workflows_result, statuses_result = api_results
    failures: list[str] = []
    pending: list[str] = []
    warnings: list[str] = []

    for result in api_results:
        if result.error:
            warnings.append(f"{result.name} API unavailable: {result.error}")

    raw_checks = checks_result.payload.get("check_runs", []) if not checks_result.error else []
    if not isinstance(raw_checks, list):
        warnings.append("check runs API returned an unexpected payload")
        raw_checks = []
    check_runs = latest_by(raw_checks, ("app.slug", "name"), ("completed_at", "started_at", "id"))
    total_checks = checks_result.payload.get("total_count", len(raw_checks)) if not checks_result.error else 0
    if isinstance(total_checks, int) and total_checks > len(raw_checks):
        warnings.append(f"check runs response was truncated ({len(raw_checks)} of {total_checks})")

    for run in check_runs:
        name = str(run.get("name") or "unnamed check")
        status = str(run.get("status") or "unknown").lower()
        conclusion = run.get("conclusion")
        conclusion_text = str(conclusion).lower() if conclusion is not None else ""
        if status != "completed" or not conclusion_text:
            pending.append(f"Check run '{name}' is {status or 'pending'}")
        elif conclusion_text not in PASS_CONCLUSIONS:
            failures.append(f"Check run '{name}' concluded {conclusion_text}")

    raw_workflows = workflows_result.payload.get("workflow_runs", []) if not workflows_result.error else []
    if not isinstance(raw_workflows, list):
        warnings.append("workflow runs API returned an unexpected payload")
        raw_workflows = []
    workflow_runs = latest_by(
        raw_workflows,
        ("workflow_id",),
        ("run_attempt", "updated_at", "id"),
    )
    total_workflows = workflows_result.payload.get("total_count", len(raw_workflows)) if not workflows_result.error else 0
    if isinstance(total_workflows, int) and total_workflows > len(raw_workflows):
        warnings.append(f"workflow runs response was truncated ({len(raw_workflows)} of {total_workflows})")

    for run in workflow_runs:
        name = str(run.get("name") or run.get("path") or "unnamed workflow")
        status = str(run.get("status") or "unknown").lower()
        conclusion = run.get("conclusion")
        conclusion_text = str(conclusion).lower() if conclusion is not None else ""
        if status != "completed" or not conclusion_text:
            pending.append(f"Workflow '{name}' is {status or 'pending'}")
        elif conclusion_text not in PASS_CONCLUSIONS:
            failures.append(f"Workflow '{name}' concluded {conclusion_text}")

    raw_statuses = statuses_result.payload.get("statuses", []) if not statuses_result.error else []
    if not isinstance(raw_statuses, list):
        warnings.append("commit status API returned an unexpected payload")
        raw_statuses = []
    statuses = [item for item in raw_statuses if isinstance(item, dict)]
    combined_state = str(statuses_result.payload.get("state") or "").lower() if not statuses_result.error else ""
    status_count = statuses_result.payload.get("total_count", len(statuses)) if not statuses_result.error else 0
    if isinstance(status_count, int) and status_count > 0:
        if combined_state in {"failure", "error"}:
            failures.append(f"Combined commit status is {combined_state}")
        elif combined_state in {"pending", "expected"}:
            pending.append(f"Combined commit status is {combined_state}")
        elif combined_state != "success":
            warnings.append(f"Combined commit status is unrecognized: {combined_state or 'empty'}")

    signal_count = len(check_runs) + len(workflow_runs) + (1 if isinstance(status_count, int) and status_count > 0 else 0)
    coverage_problem = any(result.error for result in api_results) or any("truncated" in warning for warning in warnings)

    if failures:
        state = "unhealthy"
    elif pending:
        state = "pending"
    elif coverage_problem:
        state = "unknown"
    elif signal_count == 0:
        state = "unknown"
        warnings.append("No check runs, workflow runs, or commit statuses were reported for this commit")
    else:
        state = "healthy"

    override_reason = args.override_reason.strip()
    if args.allow_unhealthy and not override_reason:
        warnings.append("Manual override was requested without an override reason")

    override_used = state != "healthy" and args.allow_unhealthy and bool(override_reason)
    allowed = state == "healthy" or override_used

    if state == "healthy":
        summary = f"Upstream commit is healthy across {signal_count} reported health signal(s)."
    elif override_used:
        summary = f"Upstream health is {state}, but a manual draft-sync override was accepted."
    elif state == "pending":
        summary = "Upstream checks are still pending; automatic sync is blocked."
    elif state == "unhealthy":
        summary = "Upstream checks contain failures; automatic sync is blocked."
    else:
        summary = "Upstream health could not be proven; automatic sync is blocked."

    return HealthEvaluation(
        state=state,
        allowed=allowed,
        override_used=override_used,
        summary=summary,
        failures=tuple(dict.fromkeys(failures)),
        pending=tuple(dict.fromkeys(pending)),
        warnings=tuple(dict.fromkeys(warnings)),
        check_runs=tuple(check_runs),
        workflow_runs=tuple(workflow_runs),
        statuses=tuple(statuses),
        api_results=api_results,
    )


def markdown_escape(value: Any) -> str:
    return str(value if value is not None else "").replace("|", "\\|").replace("\n", " ")


def write_report(args: argparse.Namespace, evaluation: HealthEvaluation) -> None:
    args.report.parent.mkdir(parents=True, exist_ok=True)
    decision = "ALLOWED" if evaluation.allowed else "BLOCKED"
    lines = [
        "# Gugu upstream health gate",
        "",
        f"- Repository: `{args.repository}`",
        f"- Commit: `{args.sha}`",
        f"- Health state: **{evaluation.state}**",
        f"- Gate decision: **{decision}**",
        f"- Manual override used: **{'yes' if evaluation.override_used else 'no'}**",
        f"- Summary: {evaluation.summary}",
    ]
    if evaluation.override_used:
        lines.append(f"- Override reason: {args.override_reason.strip()}")

    lines.extend(["", "## API coverage", "", "| Source | Result |", "|---|---|"])
    for result in evaluation.api_results:
        outcome = f"Unavailable: {result.error}" if result.error else "Available"
        lines.append(f"| {markdown_escape(result.name)} | {markdown_escape(outcome)} |")

    if evaluation.failures:
        lines.extend(["", "## Blocking failures", ""])
        lines.extend(f"- {item}" for item in evaluation.failures)
    if evaluation.pending:
        lines.extend(["", "## Pending checks", ""])
        lines.extend(f"- {item}" for item in evaluation.pending)
    if evaluation.warnings:
        lines.extend(["", "## Coverage warnings", ""])
        lines.extend(f"- {item}" for item in evaluation.warnings)

    lines.extend(["", "## Latest check runs", ""])
    if evaluation.check_runs:
        lines.extend(["| Check | Status | Conclusion | Link |", "|---|---|---|---|"])
        for run in evaluation.check_runs:
            url = run.get("html_url") or run.get("details_url") or ""
            link = f"[open]({url})" if url else ""
            lines.append(
                "| {name} | {status} | {conclusion} | {link} |".format(
                    name=markdown_escape(run.get("name") or "unnamed"),
                    status=markdown_escape(run.get("status") or ""),
                    conclusion=markdown_escape(run.get("conclusion") or ""),
                    link=link,
                )
            )
    else:
        lines.append("No check runs were reported.")

    lines.extend(["", "## Latest GitHub Actions workflow runs", ""])
    if evaluation.workflow_runs:
        lines.extend(["| Workflow | Status | Conclusion | Attempt | Link |", "|---|---|---|---|---|"])
        for run in evaluation.workflow_runs:
            url = run.get("html_url") or ""
            link = f"[open]({url})" if url else ""
            lines.append(
                "| {name} | {status} | {conclusion} | {attempt} | {link} |".format(
                    name=markdown_escape(run.get("name") or run.get("path") or "unnamed"),
                    status=markdown_escape(run.get("status") or ""),
                    conclusion=markdown_escape(run.get("conclusion") or ""),
                    attempt=markdown_escape(run.get("run_attempt") or ""),
                    link=link,
                )
            )
    else:
        lines.append("No GitHub Actions workflow runs were reported.")

    lines.extend(["", "## Legacy commit statuses", ""])
    if evaluation.statuses:
        lines.extend(["| Context | State | Description | Link |", "|---|---|---|---|"])
        for status in evaluation.statuses:
            url = status.get("target_url") or ""
            link = f"[open]({url})" if url else ""
            lines.append(
                "| {context} | {state} | {description} | {link} |".format(
                    context=markdown_escape(status.get("context") or "unnamed"),
                    state=markdown_escape(status.get("state") or ""),
                    description=markdown_escape(status.get("description") or ""),
                    link=link,
                )
            )
    else:
        lines.append("No legacy commit statuses were reported.")

    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_github_outputs(path: Path | None, evaluation: HealthEvaluation, report: Path) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    safe_summary = evaluation.summary.replace("\n", " ").replace("\r", " ")
    with path.open("a", encoding="utf-8") as handle:
        handle.write(f"state={evaluation.state}\n")
        handle.write(f"allowed={'true' if evaluation.allowed else 'false'}\n")
        handle.write(f"override_used={'true' if evaluation.override_used else 'false'}\n")
        handle.write(f"decision={'overridden' if evaluation.override_used else ('healthy' if evaluation.allowed else 'blocked')}\n")
        handle.write(f"summary={safe_summary}\n")
        handle.write(f"report={report}\n")


def main() -> int:
    args = parse_args()
    if "/" not in args.repository or args.repository.startswith("/") or args.repository.endswith("/"):
        print("ERROR: --repository must use OWNER/REPO", file=sys.stderr)
        return 2
    if len(args.sha) != 40 or any(character not in "0123456789abcdefABCDEF" for character in args.sha):
        print("ERROR: --sha must be one full 40-character commit SHA", file=sys.stderr)
        return 2

    try:
        api_results = fetch_sources(args)
        evaluation = evaluate(args, api_results)
        write_report(args, evaluation)
        write_github_outputs(args.github_output, evaluation, args.report)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERROR: unable to evaluate upstream health: {exc}", file=sys.stderr)
        return 2

    print(evaluation.summary)
    print(f"Health state: {evaluation.state}")
    print(f"Gate decision: {'allowed' if evaluation.allowed else 'blocked'}")
    print(f"Report: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
