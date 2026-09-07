#!/usr/bin/env python3

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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Audit all downloaded addon compatibility artifacts against the configured matrix."
    )
    parser.add_argument("matrix", type=Path)
    parser.add_argument("artifacts", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--json-report", type=Path)
    return parser.parse_args()


def load_enabled_targets(matrix_path: Path) -> list[dict[str, object]]:
    payload = json.loads(matrix_path.read_text(encoding="utf-8"))
    addons = payload.get("addons")
    if not isinstance(addons, list):
        raise ValueError("Compatibility matrix does not contain an addons list")

    enabled: list[dict[str, object]] = []
    seen_slugs: set[str] = set()
    for entry in addons:
        if not isinstance(entry, dict) or not entry.get("enabled", True):
            continue

        slug = str(entry.get("slug", "")).strip()
        repository = str(entry.get("repository", "")).strip()
        tier = str(entry.get("tier", "")).strip()
        if not slug or not repository or not tier:
            raise ValueError(f"Enabled matrix entry is missing slug/repository/tier: {entry!r}")
        if slug in seen_slugs:
            raise ValueError(f"Duplicate enabled addon slug: {slug}")
        seen_slugs.add(slug)
        enabled.append(entry)

    return enabled


def find_primary_status(artifact_dir: Path) -> tuple[str, str | None]:
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
            f"expected exactly one primary status.txt, found {len(candidates)}",
        )

    status = candidates[0].read_text(encoding="utf-8", errors="replace").strip()
    if status not in KNOWN_STATUSES:
        return INSTRUMENTATION_ERROR, f"unknown compatibility status: {status or '<blank>'}"
    return status, None


def build_report(
    targets: list[dict[str, object]], artifacts_root: Path
) -> tuple[list[dict[str, object]], Counter[str], bool]:
    rows: list[dict[str, object]] = []
    counts: Counter[str] = Counter()
    should_fail = False

    for entry in targets:
        slug = str(entry["slug"])
        advisory = bool(entry.get("advisory", False))
        artifact_dir = artifacts_root / f"addon-compatibility-{slug}"
        status, detail = find_primary_status(artifact_dir)
        counts[status] += 1

        if status == INSTRUMENTATION_ERROR:
            should_fail = True
        elif not advisory and status != PASS:
            should_fail = True

        rows.append(
            {
                "slug": slug,
                "repository": str(entry["repository"]),
                "tier": str(entry["tier"]),
                "advisory": advisory,
                "status": status,
                "detail": detail,
            }
        )

    return rows, counts, should_fail


def render_markdown(rows: list[dict[str, object]], counts: Counter[str]) -> str:
    total = len(rows)
    lines = [
        "## Aggregate addon compatibility audit",
        "",
        f"Audited **{total}** enabled addon targets from the compatibility matrix.",
        "",
        "| Classification | Count |",
        "| --- | ---: |",
        f"| `{PASS}` | {counts[PASS]} |",
        f"| `{BASELINE_BUILD_FAILED}` | {counts[BASELINE_BUILD_FAILED]} |",
        f"| `{LEGACY_COMPATIBILITY_FAILED}` | {counts[LEGACY_COMPATIBILITY_FAILED]} |",
        f"| `{INSTRUMENTATION_ERROR}` | {counts[INSTRUMENTATION_ERROR]} |",
        "",
    ]

    non_pass = [row for row in rows if row["status"] != PASS]
    if not non_pass:
        lines.extend(
            [
                "All enabled addon targets returned `PASS`. No hidden advisory classifications were found.",
                "",
            ]
        )
    else:
        lines.extend(
            [
                "### Non-PASS results",
                "",
                "| Repository | Tier | Advisory | Result | Detail |",
                "| --- | --- | --- | --- | --- |",
            ]
        )
        for row in non_pass:
            detail = str(row.get("detail") or "").replace("|", "\\|")
            lines.append(
                f"| `{row['repository']}` | `{row['tier']}` | "
                f"{'yes' if row['advisory'] else 'no'} | `{row['status']}` | {detail} |"
            )
        lines.append("")

    lines.extend(
        [
            "Policy: advisory addon build/regression results are reported but do not block by themselves; "
            "required non-PASS results block, and any instrumentation error blocks because the compatibility "
            "suite cannot be trusted when a result artifact is missing or malformed.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    try:
        targets = load_enabled_targets(args.matrix)
        if not targets:
            raise ValueError("Compatibility matrix contains no enabled addon targets")
        if not args.artifacts.is_dir():
            raise ValueError(f"Artifact root does not exist: {args.artifacts}")

        rows, counts, should_fail = build_report(targets, args.artifacts)
        report = render_markdown(rows, counts)

        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")
        print(report, end="")

        if args.json_report is not None:
            args.json_report.parent.mkdir(parents=True, exist_ok=True)
            args.json_report.write_text(
                json.dumps(
                    {
                        "enabled_count": len(rows),
                        "counts": dict(counts),
                        "results": rows,
                        "passed": not should_fail,
                    },
                    indent=2,
                    sort_keys=True,
                )
                + "\n",
                encoding="utf-8",
            )

        return 1 if should_fail else 0
    except Exception as exc:  # noqa: BLE001 - audit failures are instrumentation failures
        message = f"Addon compatibility aggregate audit failed: {type(exc).__name__}: {exc}"
        print(message, file=sys.stderr)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            "## Aggregate addon compatibility audit\n\n"
            f"`{INSTRUMENTATION_ERROR}`: {message}\n",
            encoding="utf-8",
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
