#!/usr/bin/env python3
"""Validate and optionally query the registered Slimefun upstream candidates."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class SourceResult:
    source_id: str
    name: str
    repository: str
    branch: str
    reviewed_commit: str | None
    current_commit: str | None
    status: str
    message: str


def load_manifest(path: Path) -> dict[str, Any]:
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise ValueError("Unsupported upstream manifest schema_version")

    sources = manifest.get("sources")
    if not isinstance(sources, list) or not sources:
        raise ValueError("The upstream manifest must contain at least one source")

    seen: set[str] = set()
    for source in sources:
        for key in ("id", "name", "repository", "branch", "role", "review_policy", "notes"):
            if not isinstance(source.get(key), str) or not source[key].strip():
                raise ValueError(f"Source entry is missing a non-empty {key}: {source!r}")
        source_id = source["id"]
        if source_id in seen:
            raise ValueError(f"Duplicate upstream source id: {source_id}")
        seen.add(source_id)

        reviewed_commit = source.get("reviewed_commit")
        if reviewed_commit is not None and (
            not isinstance(reviewed_commit, str)
            or len(reviewed_commit) != 40
            or any(character not in "0123456789abcdef" for character in reviewed_commit.lower())
        ):
            raise ValueError(f"Invalid reviewed_commit for {source_id}")

    return manifest


def github_head(repository: str, branch: str, token: str | None) -> str:
    url = f"https://api.github.com/repos/{repository}/commits/{branch}"
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "Slimefun-Legacy-Upstream-Radar",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = json.load(response)
    sha = payload.get("sha")
    if not isinstance(sha, str) or len(sha) != 40:
        raise ValueError(f"GitHub returned an invalid commit for {repository}@{branch}")
    return sha


def check_sources(manifest: dict[str, Any], offline: bool) -> list[SourceResult]:
    token = os.environ.get("GITHUB_TOKEN")
    results: list[SourceResult] = []

    for source in manifest["sources"]:
        reviewed = source.get("reviewed_commit")
        if offline:
            results.append(SourceResult(
                source["id"],
                source["name"],
                source["repository"],
                source["branch"],
                reviewed,
                None,
                "OFFLINE",
                "Manifest validated; remote revision was not queried.",
            ))
            continue

        try:
            current = github_head(source["repository"], source["branch"], token)
            if reviewed is None:
                status = "UNPINNED"
                message = "No reviewed baseline is recorded yet. Review the current head before importing changes."
            elif reviewed == current:
                status = "CURRENT"
                message = "The registered reviewed baseline matches the current branch head."
            else:
                status = "UPDATE_AVAILABLE"
                message = "The source has commits newer than the registered reviewed baseline."
        except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError) as error:
            current = None
            status = "QUERY_FAILED"
            message = f"Unable to query GitHub: {error}"

        results.append(SourceResult(
            source["id"],
            source["name"],
            source["repository"],
            source["branch"],
            reviewed,
            current,
            status,
            message,
        ))

    return results


def write_markdown(path: Path, results: list[SourceResult]) -> None:
    lines = [
        "# Slimefun Legacy upstream candidate report",
        "",
        "This report is advisory. It never merges, downloads, or replaces source files.",
        "",
        "| Source | Repository / branch | Status | Reviewed | Current |",
        "| --- | --- | --- | --- | --- |",
    ]
    for result in results:
        reviewed = result.reviewed_commit[:12] if result.reviewed_commit else "Not recorded"
        current = result.current_commit[:12] if result.current_commit else "Not queried"
        lines.append(
            f"| {result.name} | `{result.repository}@{result.branch}` | **{result.status}** | "
            f"`{reviewed}` | `{current}` |"
        )
    lines.append("")
    for result in results:
        lines.extend((f"## {result.name}", "", result.message, ""))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def write_json(path: Path, results: list[SourceResult]) -> None:
    payload = [result.__dict__ for result in results]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("compatibility/upstream-sources.json"))
    parser.add_argument("--output", type=Path, default=Path("build/reports/upstream-candidates.md"))
    parser.add_argument("--json-output", type=Path, default=Path("build/reports/upstream-candidates.json"))
    parser.add_argument("--offline", action="store_true", help="Validate only; do not query GitHub")
    args = parser.parse_args()

    try:
        manifest = load_manifest(args.manifest)
        results = check_sources(manifest, args.offline)
        write_markdown(args.output, results)
        write_json(args.json_output, results)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Upstream candidate check failed: {error}", file=sys.stderr)
        return 1

    print(f"Validated {len(results)} upstream sources. Report: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
