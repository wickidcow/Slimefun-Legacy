#!/usr/bin/env python3
"""Static safety checks for the Gugu upstream synchronization workflow."""
from __future__ import annotations

import sys
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    workflow = root / ".github/workflows/sync-gugu-upstream.yml"
    script = root / "scripts/sync_upstream.sh"
    health_script = root / "scripts/check_upstream_health.py"
    health_verifier = root / "scripts/verify_upstream_health_gate.py"
    docs = root / "GUGU_UPSTREAM_SYNC.md"
    marker = root / ".gugu-upstream-base"

    problems: list[str] = []
    for path in (workflow, script, health_script, health_verifier, docs, marker):
        if not path.is_file():
            problems.append(f"missing {path.relative_to(root)}")

    if problems:
        for problem in problems:
            print(f"ERROR: {problem}", file=sys.stderr)
        return 1

    workflow_text = workflow.read_text(encoding="utf-8")
    script_text = script.read_text(encoding="utf-8")
    health_text = health_script.read_text(encoding="utf-8")
    docs_text = docs.read_text(encoding="utf-8")

    forbidden = ("rsync -a --delete", "git checkout --theirs", "git merge -X theirs", "git reset --hard upstream")
    for needle in forbidden:
        if needle in workflow_text or needle in script_text:
            problems.append(f"unsafe sync operation found: {needle}")

    required_workflow = (
        "fetch-depth: 0",
        "scripts/sync_upstream.sh",
        "scripts/check_upstream_health.py",
        "override_upstream_health",
        "override_reason",
        "steps.health.outputs.allowed == 'true'",
        "gugu-upstream-health-report",
        "--draft",
        "verify_legacy.py",
        ".gugu-upstream-base",
        "force-with-lease",
    )
    for needle in required_workflow:
        if needle not in workflow_text and needle not in script_text:
            problems.append(f"missing sync safeguard: {needle}")

    required_script = (
        "git merge --no-ff --no-edit",
        "git merge --no-ff -s ours",
        "git diff --name-only --diff-filter=U",
        "git merge --abort",
        'git reset --hard "$BASE_SHA"',
        'RANGE_BASE="$INTEGRATED_UPSTREAM_SHA"',
        r'''printf '%s\n' "$UPSTREAM_SHA" > "$MARKER_FILE"''',
        "GUGU_UPSTREAM_HEALTH_STATE",
        "GUGU_UPSTREAM_HEALTH_DECISION",
    )
    for needle in required_script:
        if needle not in script_text:
            problems.append(f"missing merge safeguard: {needle}")

    required_health = (
        "check-runs?filter=latest",
        "actions/runs?head_sha=",
        "/status",
        "PASS_CONCLUSIONS",
        "No check runs, workflow runs, or commit statuses",
        "allow_unhealthy",
        "override_reason",
        "allowed=",
    )
    for needle in required_health:
        if needle not in health_text:
            problems.append(f"missing upstream health safeguard: {needle}")

    for needle in (
        "Upstream health gate",
        "failed, pending, unavailable, or missing",
        "override_upstream_health",
        "override_reason",
    ):
        if needle not in docs_text:
            problems.append(f"missing upstream health documentation: {needle}")

    marker_value = marker.read_text(encoding="utf-8").strip() if marker.is_file() else ""
    if len(marker_value) != 40 or any(ch not in "0123456789abcdefABCDEF" for ch in marker_value):
        problems.append(".gugu-upstream-base must contain one full 40-character commit SHA")

    if problems:
        for problem in problems:
            print(f"ERROR: {problem}", file=sys.stderr)
        return 1

    print("Gugu sync safety verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
