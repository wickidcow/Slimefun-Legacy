#!/usr/bin/env python3
"""Validate and emit the Phase 1C addon compatibility CI matrix."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPOSITORY = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
SLUG = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
VALID_CORES = {"original", "gugu", "united", "slimefun5", "slimefun-core", "legacy"}
DEFAULT_EXCLUSIONS = Path("compatibility/addon-compatibility-exclusions.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "registry",
        nargs="?",
        type=Path,
        default=Path("compatibility/addon-compatibility-matrix.json"),
    )
    parser.add_argument("--exclusions", type=Path, default=DEFAULT_EXCLUSIONS)
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--include-disabled", action="store_true")
    return parser.parse_args()


def fail(message: str) -> None:
    raise ValueError(message)


def load_exclusions(path: Path) -> set[str]:
    if not path.is_file():
        return set()
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schema") != 1:
        fail("addon exclusion registry schema must be 1")
    raw_exclusions = payload.get("exclusions")
    if not isinstance(raw_exclusions, list):
        fail("addon exclusion registry must contain an exclusions array")

    exclusions: set[str] = set()
    for index, raw in enumerate(raw_exclusions):
        if not isinstance(raw, dict):
            fail(f"exclusions[{index}] must be an object")
        repository = raw.get("repository")
        reason = raw.get("reason")
        if not isinstance(repository, str) or not REPOSITORY.fullmatch(repository):
            fail(f"exclusions[{index}].repository is invalid: {repository!r}")
        if not isinstance(reason, str) or not reason.strip():
            fail(f"exclusions[{index}].reason must be a non-empty string")
        if repository in exclusions:
            fail(f"duplicate addon compatibility exclusion: {repository}")
        exclusions.add(repository)
    return exclusions


def load_entries(
    path: Path,
    include_disabled: bool,
    exclusions: set[str],
) -> list[dict[str, object]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schema") != 1:
        fail("addon matrix schema must be 1")
    addons = payload.get("addons")
    if not isinstance(addons, list) or not addons:
        fail("addon matrix must contain a non-empty addons array")

    seen_slugs: set[str] = set()
    entries: list[dict[str, object]] = []
    required_count = 0
    advisory_count = 0
    for index, raw in enumerate(addons):
        if not isinstance(raw, dict):
            fail(f"addons[{index}] must be an object")
        repository = raw.get("repository")
        slug = raw.get("slug")
        tier = raw.get("tier")
        advisory = raw.get("advisory")
        enabled = raw.get("enabled", True)
        variants = raw.get("tested_core_variants", [])

        if not isinstance(repository, str) or not REPOSITORY.fullmatch(repository):
            fail(f"addons[{index}].repository is invalid: {repository!r}")
        if not isinstance(slug, str) or not SLUG.fullmatch(slug):
            fail(f"addons[{index}].slug is invalid: {slug!r}")
        if slug in seen_slugs:
            fail(f"duplicate addon slug: {slug}")
        seen_slugs.add(slug)
        if not isinstance(tier, str) or not tier:
            fail(f"addons[{index}].tier must be a non-empty string")
        if not isinstance(advisory, bool):
            fail(f"addons[{index}].advisory must be a boolean")
        if not isinstance(enabled, bool):
            fail(f"addons[{index}].enabled must be a boolean")
        if not isinstance(variants, list) or any(value not in VALID_CORES for value in variants):
            fail(f"addons[{index}].tested_core_variants contains an unknown core")

        if repository in exclusions:
            continue
        if not enabled and not include_disabled:
            continue

        if advisory:
            advisory_count += 1
        else:
            required_count += 1

        entry: dict[str, object] = {
            "repository": repository,
            "slug": slug,
            "tier": tier,
            "advisory": advisory,
            "ref": raw.get("ref", ""),
            "expected_commit": raw.get("expected_commit", ""),
            "target_version": raw.get("target_version", ""),
        }
        for key in ("ref", "expected_commit", "target_version"):
            if not isinstance(entry[key], str):
                fail(f"addons[{index}].{key} must be a string when provided")
        entries.append(entry)

    if required_count == 0:
        fail("addon matrix must include at least one eligible required target")
    if advisory_count == 0:
        fail("addon matrix must include at least one eligible advisory target")
    return entries


def main() -> int:
    args = parse_args()
    path = args.registry.resolve()
    exclusions_path = args.exclusions.resolve()
    if not path.is_file():
        print(f"Missing addon compatibility matrix: {path}", file=sys.stderr)
        return 1
    try:
        exclusions = load_exclusions(exclusions_path)
        entries = load_entries(path, args.include_disabled, exclusions)
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"Invalid addon compatibility matrix: {error}", file=sys.stderr)
        return 1

    matrix = json.dumps({"include": entries}, separators=(",", ":"))
    if args.github_output:
        args.github_output.parent.mkdir(parents=True, exist_ok=True)
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"matrix={matrix}\n")
            output.write(f"enabled_count={len(entries)}\n")
    else:
        print(matrix)
    print(
        f"Validated {len(entries)} enabled addon compatibility targets "
        f"after {len(exclusions)} explicit exclusion(s).",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
