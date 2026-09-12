#!/usr/bin/env python3
"""Validate and emit the required-addon Paper runtime smoke matrix."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPOSITORY = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
SLUG = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
PLUGIN_NAME = re.compile(r"^[A-Za-z0-9_.-]+$")
DEFAULT_RUNTIME_MATRIX = Path("compatibility/addon-runtime-smoke-matrix.json")
DEFAULT_COMPATIBILITY_MATRIX = Path("compatibility/addon-compatibility-matrix.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("registry", nargs="?", type=Path, default=DEFAULT_RUNTIME_MATRIX)
    parser.add_argument("--compatibility-registry", type=Path, default=DEFAULT_COMPATIBILITY_MATRIX)
    parser.add_argument("--github-output", type=Path)
    return parser.parse_args()


def fail(message: str) -> None:
    raise ValueError(message)


def load_json(path: Path) -> dict[str, object]:
    if not path.is_file():
        fail(f"missing registry: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        fail(f"registry must contain a JSON object: {path}")
    return payload


def required_compatibility_targets(path: Path) -> dict[str, dict[str, object]]:
    payload = load_json(path)
    if payload.get("schema") != 1:
        fail("addon compatibility matrix schema must be 1")
    raw_addons = payload.get("addons")
    if not isinstance(raw_addons, list):
        fail("addon compatibility matrix must contain an addons array")

    required: dict[str, dict[str, object]] = {}
    for index, raw in enumerate(raw_addons):
        if not isinstance(raw, dict):
            fail(f"compatibility addons[{index}] must be an object")
        repository = raw.get("repository")
        if not isinstance(repository, str) or not REPOSITORY.fullmatch(repository):
            continue
        if raw.get("enabled", True) is True and raw.get("tier") == "required" and raw.get("advisory") is False:
            required[repository] = raw
    return required


def load_runtime_entries(runtime_path: Path, compatibility_path: Path) -> tuple[str, list[dict[str, object]]]:
    payload = load_json(runtime_path)
    if payload.get("schema") != 1:
        fail("addon runtime smoke matrix schema must be 1")
    minecraft = payload.get("minecraft")
    if not isinstance(minecraft, str) or not minecraft.strip():
        fail("addon runtime smoke matrix minecraft must be a non-empty string")
    raw_addons = payload.get("addons")
    if not isinstance(raw_addons, list) or not raw_addons:
        fail("addon runtime smoke matrix must contain a non-empty addons array")

    required = required_compatibility_targets(compatibility_path)
    seen_repositories: set[str] = set()
    seen_slugs: set[str] = set()
    seen_plugins: set[str] = set()
    entries: list[dict[str, object]] = []

    for index, raw in enumerate(raw_addons):
        if not isinstance(raw, dict):
            fail(f"addons[{index}] must be an object")
        repository = raw.get("repository")
        slug = raw.get("slug")
        runtime_plugin = raw.get("runtime_plugin")
        enabled = raw.get("enabled", True)

        if not isinstance(repository, str) or not REPOSITORY.fullmatch(repository):
            fail(f"addons[{index}].repository is invalid: {repository!r}")
        if not isinstance(slug, str) or not SLUG.fullmatch(slug):
            fail(f"addons[{index}].slug is invalid: {slug!r}")
        if not isinstance(runtime_plugin, str) or not PLUGIN_NAME.fullmatch(runtime_plugin):
            fail(f"addons[{index}].runtime_plugin is invalid: {runtime_plugin!r}")
        if not isinstance(enabled, bool):
            fail(f"addons[{index}].enabled must be a boolean")

        if repository in seen_repositories:
            fail(f"duplicate runtime repository: {repository}")
        if slug in seen_slugs:
            fail(f"duplicate runtime slug: {slug}")
        if runtime_plugin.lower() in seen_plugins:
            fail(f"duplicate runtime plugin name: {runtime_plugin}")
        seen_repositories.add(repository)
        seen_slugs.add(slug)
        seen_plugins.add(runtime_plugin.lower())

        compatibility = required.get(repository)
        if compatibility is None:
            fail(
                f"runtime target {repository} must also be an enabled, non-advisory required target "
                "in addon-compatibility-matrix.json"
            )
        if compatibility.get("slug") != slug:
            fail(
                f"runtime target {repository} slug {slug!r} does not match compatibility slug "
                f"{compatibility.get('slug')!r}"
            )

        if not enabled:
            continue

        entries.append(
            {
                "repository": repository,
                "slug": slug,
                "runtime_plugin": runtime_plugin,
                "ref": compatibility.get("ref", ""),
                "expected_commit": compatibility.get("expected_commit", ""),
                "target_version": compatibility.get("target_version", ""),
            }
        )

    missing_required = sorted(set(required) - seen_repositories)
    if missing_required:
        fail("required compatibility targets missing from runtime smoke matrix: " + ", ".join(missing_required))
    if not entries:
        fail("addon runtime smoke matrix has no enabled entries")
    return minecraft, entries


def main() -> int:
    args = parse_args()
    try:
        minecraft, entries = load_runtime_entries(args.registry.resolve(), args.compatibility_registry.resolve())
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"Invalid addon runtime smoke matrix: {error}", file=sys.stderr)
        return 1

    matrix = json.dumps({"include": entries}, separators=(",", ":"))
    if args.github_output:
        args.github_output.parent.mkdir(parents=True, exist_ok=True)
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"matrix={matrix}\n")
            output.write(f"enabled_count={len(entries)}\n")
            output.write(f"minecraft={minecraft}\n")
    else:
        print(matrix)

    print(
        f"Validated {len(entries)} required addon runtime target(s) for Paper/Minecraft {minecraft}.",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
