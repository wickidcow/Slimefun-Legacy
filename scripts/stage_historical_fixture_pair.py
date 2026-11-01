#!/usr/bin/env python3
"""Stage immutable historical storage/restart test inputs, never release distribution pins.

The native paired-storage probe intentionally tests the reviewed historical
Networks 1.0.62 / InfinityExpansion2 2.0.16 fixture. The canonical 44-addon
bundle separately stages newer published releases from its distribution lock.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from scripts.runtime.paired_addon_storage.run_probe import load_lock, require
from scripts.stage_published_pair import stage


def fixture_pin(addon: str) -> dict:
    require(addon in ("networks", "ie2"), "Unknown historical storage fixture addon")
    lock = load_lock()
    require(lock.get("purpose") == "integration-test-fixture"
            and lock.get("distribution_selection") is False,
            "Historical fixture must never select release distribution sources")
    return lock["addons"][addon]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--addon", choices=("networks", "ie2"), required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = stage(fixture_pin(args.addon), args.source, args.repository, args.output)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
