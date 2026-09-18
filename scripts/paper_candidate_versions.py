#!/usr/bin/env python3
"""Resolve the newest published Paper artifact for a Minecraft version family.

Paper pre-release Minecraft versions are published with coordinates such as
`26.3-rc-3.build.1-alpha`. Compatibility lanes must not restrict matching to
`26.3.build.*`, otherwise they silently keep testing an older alpha after the
Minecraft version advances to pre-release or release-candidate builds.
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections.abc import Iterable


def candidate_key(value: str, target: str) -> tuple[int, int, int, str] | None:
    """Return an ordering key for a Paper artifact in the requested version family."""
    match = re.fullmatch(
        re.escape(target)
        + r"(?:(?:-(?P<phase>pre|rc)-?(?P<phase_num>\d+)))?"
        + r"\.build\.(?P<build>\d+)"
        + r"(?P<qualifier>-[0-9A-Za-z][0-9A-Za-z.-]*)?",
        value,
        re.IGNORECASE,
    )
    if not match:
        return None

    phase = (match.group("phase") or "").lower()
    phase_number = int(match.group("phase_num") or 0)
    build = int(match.group("build"))
    qualifier = (match.group("qualifier") or "").lower()

    if phase == "pre":
        stage = 1
    elif phase == "rc":
        stage = 2
    elif not qualifier or "stable" in qualifier or "release" in qualifier:
        stage = 3
    else:
        # Base-version alpha/beta/snapshot artifacts precede Minecraft pre/rc builds.
        stage = 0

    return (stage, phase_number, build, value)


def latest_candidate(versions: Iterable[str], target: str) -> str | None:
    """Select the newest Paper artifact belonging to a Minecraft version family."""
    candidates: list[tuple[tuple[int, int, int, str], str]] = []
    for value in versions:
        key = candidate_key(value.strip(), target)
        if key is not None:
            candidates.append((key, value.strip()))

    if not candidates:
        return None
    return max(candidates, key=lambda entry: entry[0])[1]


def minecraft_version_from_artifact(artifact_version: str) -> str:
    """Convert `26.3-rc-3.build.1-alpha` to the Paper/Minecraft version `26.3-rc-3`."""
    marker = ".build."
    if marker not in artifact_version:
        raise ValueError(f"Not a Paper build coordinate: {artifact_version}")
    return artifact_version.split(marker, 1)[0]


def versions_from_metadata(xml_text: str) -> list[str]:
    root = ET.fromstring(xml_text)
    return [
        node.text.strip()
        for node in root.findall("./versioning/versions/version")
        if node.text and node.text.strip()
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", required=True)
    args = parser.parse_args()

    selected = latest_candidate(versions_from_metadata(sys.stdin.read()), args.target)
    if selected:
        print(selected)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
