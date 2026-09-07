#!/usr/bin/env python3
"""Verify every maintained wickidcow Slimefun addon repository has an enabled CI target."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.github.com/search/repositories"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "matrix",
        nargs="?",
        type=Path,
        default=Path("compatibility/addon-compatibility-matrix.json"),
    )
    parser.add_argument("--owner", default="wickidcow")
    return parser.parse_args()


def load_matrix(path: Path) -> tuple[dict[str, bool], str, set[str]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    policy = payload.get("policy", {})
    prefix = policy.get("fork_discovery_prefix", "SF_")
    explicit = set(policy.get("fork_discovery_explicit", ["WorldEditSlimefun"]))
    if not isinstance(prefix, str) or not prefix:
        raise ValueError("policy.fork_discovery_prefix must be a non-empty string")
    if not all(isinstance(name, str) and name for name in explicit):
        raise ValueError("policy.fork_discovery_explicit must contain repository names")

    coverage: dict[str, bool] = {}
    for index, addon in enumerate(payload.get("addons", [])):
        if not isinstance(addon, dict):
            raise ValueError(f"addons[{index}] must be an object")
        repository = addon.get("repository")
        enabled = addon.get("enabled", True)
        if isinstance(repository, str):
            coverage[repository] = bool(enabled)
    return coverage, prefix, explicit


def request_page(owner: str, page: int) -> list[dict[str, object]]:
    query = urllib.parse.urlencode(
        {
            "q": f"user:{owner}",
            "per_page": 100,
            "page": page,
        }
    )
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "slimefun-legacy-fork-coverage",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(f"{API}?{query}", headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
    items = payload.get("items", [])
    if not isinstance(items, list):
        raise RuntimeError("GitHub repository search returned an invalid items payload")
    return items


def discover_repositories(owner: str, prefix: str, explicit: set[str]) -> list[dict[str, object]]:
    discovered: list[dict[str, object]] = []
    for page in range(1, 11):
        items = request_page(owner, page)
        for repository in items:
            name = repository.get("name")
            if isinstance(name, str) and (name.startswith(prefix) or name in explicit):
                discovered.append(repository)
        if len(items) < 100:
            break
    else:
        raise RuntimeError("GitHub repository search exceeded 1,000 repositories")
    return discovered


def main() -> int:
    args = parse_args()
    try:
        coverage, prefix, explicit = load_matrix(args.matrix)
        discovered = discover_repositories(args.owner, prefix, explicit)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError, urllib.error.URLError) as error:
        print(f"Fork coverage verification failed: {error}", file=sys.stderr)
        return 2

    expected = {f"{args.owner}/{repo['name']}" for repo in discovered}
    missing = sorted(repository for repository in expected if repository not in coverage)
    disabled = sorted(repository for repository in expected if coverage.get(repository) is False)

    archived = sorted(
        f"{args.owner}/{repo['name']}"
        for repo in discovered
        if bool(repo.get("archived"))
    )
    active_count = len(expected) - len(archived)

    print(
        f"Discovered {len(expected)} Slimefun addon forks for {args.owner}: "
        f"{active_count} active, {len(archived)} archived."
    )

    if archived:
        print("Archived forks still requiring advisory test coverage:")
        for repository in archived:
            print(f"  - {repository}")

    if missing or disabled:
        if missing:
            print("Missing addon compatibility targets:", file=sys.stderr)
            for repository in missing:
                print(f"  - {repository}", file=sys.stderr)
        if disabled:
            print("Disabled addon compatibility targets:", file=sys.stderr)
            for repository in disabled:
                print(f"  - {repository}", file=sys.stderr)
        return 1

    print(f"All {len(expected)} discovered forks have enabled compatibility targets.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
