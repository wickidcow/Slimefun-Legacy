#!/usr/bin/env python3
"""Stage immutable *current* published Networks/IE2 JARs for native release tests.

Historical storage fixtures are handled only by stage_historical_fixture_pair.py.
This script does not rewrite or publish existing addon releases.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from scripts.runtime.paired_addon_storage.run_probe import load_lock, require
from scripts.stage_published_pair import stage


def main() -> None:
    require(os.environ.get("SFL_PAIRED_LOCK_MODE") == "release",
            "Current distribution staging requires explicit native release mode")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--addon", choices=("networks", "ie2"), required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    lock = load_lock()
    require(lock.get("purpose") == "exact-published-release-storage-pair"
            and lock.get("distribution_selection") is True,
            "Current pair was not selected from the reviewed distribution lock")
    selected = lock["addons"][args.addon]
    result = stage(selected, args.source, args.repository, args.output)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
