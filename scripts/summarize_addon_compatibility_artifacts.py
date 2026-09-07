#!/usr/bin/env python3
"""Aggregate per-addon compatibility artifacts into one authoritative report."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

PASS = "PASS"
BASELINE_BUILD_FAILED = "BASELINE_BUILD_FAILED"
LEGACY_COMPATIBILITY_FAILED = "LEGACY_COMPATIBILITY_FAILED"
INSTRUMENTATION_ERROR = "INSTRUMENTATION_ERROR"
KNOWN_STATUSES = {
    PASS,
    BASELINE_BUILD_FAILED,
    LEGACY_COMPATIBILITY_FAILED,
    INSTRUMENTATION_ERROR,
}
ARTIFACT_PREFIX = "addon-compatibility-"
DEFAULT_EXCLUSIONS = Path("compatibility/addon-compatibility-exclusions.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("matrix", type=Path)
    parser.add_argument("artifact_root", type=Path)
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--exclusions", type=Path, default=DEFAULT_EXCLUSIONS)
    return parser.parse_args()


def load_exclusions(path: Path) -> set[str]:
    if not path.is_file():
        return set()
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schema") != 1:
        raise ValueError("addon exclusion registry schema must be 1")
    raw_exclusions = payload.get("exclusions")
    if not isinstance(raw_exclusions, list):
        raise ValueError("addon exclusion registry must contain an exclusions array")

    exclusions: set[str] = set()
    for index, raw in enumerate(raw_exclusions):
        if not isinstance(raw, dict):
            raise ValueError(f"exclusions[{index}] must be an object")
        repository = raw.get("repository")
        reason = raw.get("reason")
        if not isinstance(repository, str) or "/" not in repository:
            raise ValueError(f"exclusions[{index}].repository is invalid: {repository!r}")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError(f"exclusions[{index}].reason must be a non-empty string")
        if repository in exclusions:
            raise ValueError(f"duplicate addon compatibility exclusion: {repository}")
        exclusions.add(repository)
    return exclusions


def load_targets(matrix_path: Path, exclusions: set[str]) -> list[dict[str, object]]:
    payload = json.loads(matrix_path.read_text(encoding="utf-8"))
    addons = payload.get("addons")
    if not isinstance(addons, list):
        raise ValueError("Compatibility matrix does not contain an addons array")

    targets: list[dict[str, object]] = []
    seen: set[str] = set()
    for entry in addons:
        if not isinstance(entry, dict) or not entry.get("enabled", True):
            continue
        slug = str(entry.get("slug", "")).strip()
        repository = str(entry.get("repository", "")).strip()
        tier = str(entry.get("tier", "")).strip()
        if repository in exclusions:
            continue
        if not slug or not repository or not tier:
            raise ValueError(f"Enabled matrix entry is missing slug/repository/tier: {entry!r}")
        if slug in seen:
            raise ValueError(f"Duplicate enabled addon slug: {slug}")
        seen.add(slug)
        targets.append(
            {
                "slug": slug,
                "repository": repository,
                "tier": tier,
                "advisory": bool(entry.get("advisory", False)),
            }
        )
    if not targets:
        raise ValueError("Compatibility matrix has no eligible enabled addon targets")
    return targets


def find_status(artifact_dir: Path) -> tuple[str, str | None]:
    if not artifact_dir.is_dir():
        return INSTRUMENTATION_ERROR, "artifact directory is missing"

    candidates = [
        path
        for path in artifact_dir.rglob("status.txt")
        if "binary-linkage" not in path.parts and "legacy-floor" not in path.parts
    ]
    if len(candidates) != 1:
        return (
            INSTRUMENTATION_ERROR,
            f"expected exactly one addon status.txt, found {len(candidates)}",
        )

    status = candidates[0].read_text(encoding="utf-8", errors="replace").strip()
    if status not in KNOWN_STATUSES:
        return INSTRUMENTATION_ERROR, f"unknown status value: {status or '<blank>'}"
    return status, None


def render_summary(rows: list[dict[str, object]], counts: Counter[str]) -> str:
    total = len(rows)
    pass_count = counts[PASS]
    baseline_count = counts[BASELINE_BUILD_FAILED]
    regression_count = counts[LEGACY_COMPATIBILITY_FAILED]
    instrumentation_count = counts[INSTRUMENTATION_ERROR]

    lines = [
        "## Aggregate addon compatibility audit",
        "",
        f"Audited **{total}** eligible enabled addon targets.",
        "",
        "| Classification | Count | Meaning |",
        "| --- | ---: | --- |",
        f"| `{PASS}` | {pass_count} | Baseline + candidate source builds and binary linkage passed |",
        f"| `{BASELINE_BUILD_FAILED}` | {baseline_count} | Addon also fails the known-good baseline; not evidence of a new Legacy regression |",
        f"| `{LEGACY_COMPATIBILITY_FAILED}` | {regression_count} | Baseline passes but candidate Legacy compatibility fails |",
        f"| `{INSTRUMENTATION_ERROR}` | {instrumentation_count} | Missing/invalid artifact or comparison harness failure |",
        "",
        "| Tier | Repository | Classification | Detail |",
        "| --- | --- | --- | --- |",
    ]
    for row in rows:
        detail = str(row.get("detail") or "")
        lines.append(
            f"| {row['tier']} | `{row['repository']}` | `{row['status']}` | {detail} |"
        )

    blocking = [
        row
        for row in rows
        if row["status"] == INSTRUMENTATION_ERROR
        or (not row["advisory"] and row["status"] != PASS)
    ]
    advisory_non_pass = [
        row for row in rows if row["advisory"] and row["status"] != PASS
    ]

    lines.extend(["", "### Release decision", ""])
    if blocking:
        lines.append(
            f"**BLOCKED:** {len(blocking)} target(s) require review before treating the compatibility run as authoritative."
        )
    elif advisory_non_pass:
        lines.append(
            f"**PASS WITH ADVISORIES:** all required addon targets passed and instrumentation is complete; "
            f"{len(advisory_non_pass)} advisory target(s) reported compatibility/build issues above."
        )
    else:
        lines.append(
            "**PASS:** all required and advisory addon targets passed, with no instrumentation failures."
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    args = parse_args()
    try:
        exclusions = load_exclusions(args.exclusions)
        targets = load_targets(args.matrix, exclusions)
    except Exception as exc:  # noqa: BLE001 - command-line audit must fail closed
        print(f"Unable to load compatibility matrix: {exc}", file=sys.stderr)
        return 2

    rows: list[dict[str, object]] = []
    counts: Counter[str] = Counter()
    for target in targets:
        slug = str(target["slug"])
        artifact_dir = args.artifact_root / f"{ARTIFACT_PREFIX}{slug}"
        status, detail = find_status(artifact_dir)
        counts[status] += 1
        rows.append({**target, "status": status, "detail": detail})

    args.summary.parent.mkdir(parents=True, exist_ok=True)
    summary = render_summary(rows, counts)
    args.summary.write_text(summary, encoding="utf-8")
    print(summary, end="")

    has_instrumentation = counts[INSTRUMENTATION_ERROR] > 0
    has_required_regression = any(
        not bool(row["advisory"]) and row["status"] == LEGACY_COMPATIBILITY_FAILED
        for row in rows
    )
    has_required_failure = any(
        not bool(row["advisory"]) and row["status"] != PASS for row in rows
    )

    if has_instrumentation:
        return 3
    if has_required_regression:
        return 4
    if has_required_failure:
        return 5
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
