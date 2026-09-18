#!/usr/bin/env python3
"""Regression tests for Paper version-family candidate resolution."""

from __future__ import annotations

from paper_candidate_versions import (
    latest_candidate,
    minecraft_version_from_artifact,
)


def main() -> int:
    versions = [
        "26.2.build.124-stable",
        "26.3.build.7-alpha",
        "26.3.build.8-alpha",
        "26.3-pre-1.build.4-alpha",
        "26.3-rc-1.build.2-alpha",
        "26.3-rc-2.build.5-alpha",
        "26.3-rc-3.build.1-alpha",
    ]
    assert latest_candidate(versions, "26.3") == "26.3-rc-3.build.1-alpha"
    assert latest_candidate(versions, "26.2") == "26.2.build.124-stable"
    assert minecraft_version_from_artifact("26.3-rc-3.build.1-alpha") == "26.3-rc-3"

    with_stable = versions + ["26.3.build.1-stable", "26.3.build.2-stable"]
    assert latest_candidate(with_stable, "26.3") == "26.3.build.2-stable"

    # Final Paper coordinates may also be published without a prerelease
    # qualifier. Any final 26.3 build must outrank Minecraft RC artifacts.
    with_unqualified_final = versions + ["26.3.build.1", "26.3.build.3"]
    assert latest_candidate(with_unqualified_final, "26.3") == "26.3.build.3"

    malformed = ["26.30.build.1", "26.3foo.build.9", "26.3-rc-x.build.1-alpha"]
    assert latest_candidate(malformed, "26.3") is None

    print("Paper candidate version resolver tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
