#!/usr/bin/env python3
"""Read and validate the Slimefun Legacy release-baseline registry."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")
FULL_GIT_SHA_RE = re.compile(r"^[0-9a-fA-F]{40}$")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate(data: dict) -> dict[str, str]:
    require(data.get("schema") == 1, "release baseline registry schema must be 1")
    require(data.get("project") == "Slimefun Legacy", "unexpected project in release baseline registry")

    candidate = data.get("candidate")
    previous = data.get("previous_stable")
    floor = data.get("legacy_floor")
    policy = data.get("policy")
    require(isinstance(candidate, dict), "candidate baseline definition is missing")
    require(isinstance(previous, dict), "previous_stable baseline definition is missing")
    require(isinstance(floor, dict), "legacy_floor baseline definition is missing")
    require(isinstance(policy, dict), "baseline lifecycle policy is missing")

    candidate_version = candidate.get("version")
    previous_version = previous.get("version")
    floor_version = floor.get("version")
    for label, value in (
        ("candidate.version", candidate_version),
        ("previous_stable.version", previous_version),
        ("legacy_floor.version", floor_version),
    ):
        require(isinstance(value, str) and VERSION_RE.fullmatch(value) is not None, f"{label} must be x.y.z")

    def source(entry: dict, label: str) -> tuple[str, str]:
        value = entry.get("source")
        require(isinstance(value, dict), f"{label}.source is missing")
        mode = value.get("mode")
        ref = value.get("ref")
        require(mode == "git-ref", f"{label}.source.mode must be git-ref")
        require(isinstance(ref, str) and bool(ref.strip()), f"{label}.source.ref is missing")
        normalized_ref = ref.strip()
        require(
            FULL_GIT_SHA_RE.fullmatch(normalized_ref) is not None,
            f"{label}.source.ref must be a full 40-character Git commit SHA",
        )
        return mode, normalized_ref

    previous_mode, previous_ref = source(previous, "previous_stable")
    floor_mode, floor_ref = source(floor, "legacy_floor")

    require(previous.get("release_blocking") is True, "previous stable baseline must be release blocking")
    require(floor.get("release_blocking") is False, "legacy floor must remain advisory")

    required_policy = (
        "single_source_for_ci_baselines",
        "previous_stable_advances_each_release",
        "legacy_floor_is_advisory",
        "required_addon_candidate_regressions_block_release",
        "historical_floor_regressions_do_not_block_release",
        "baseline_source_builds_are_pinned",
    )
    for key in required_policy:
        require(policy.get(key) is True, f"baseline lifecycle policy must enable {key}")

    return {
        "candidate_version": candidate_version,
        "previous_version": previous_version,
        "previous_ref": previous_ref,
        "previous_mode": previous_mode,
        "floor_version": floor_version,
        "floor_ref": floor_ref,
        "floor_mode": floor_mode,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("registry", nargs="?", default="compatibility/release-baselines.json")
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    registry = Path(args.registry)
    data = json.loads(registry.read_text(encoding="utf-8"))
    values = validate(data)

    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as output:
            for key, value in values.items():
                output.write(f"{key}={value}\n")
    else:
        print(json.dumps(values, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
