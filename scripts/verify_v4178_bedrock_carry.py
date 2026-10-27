#!/usr/bin/env python3
"""Read-only, immutable v4.1.77 -> v4.1.78 optional ZIP carry-forward guard.

Never edits release assets or changes Geyser mappings/resource-pack contents.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from verify_sfl_release_assets import (
    BUNDLE, PACK, CARRIED_GEYSER_PACK, CARRIED_GEYSER_PIN, PolicyError,
    load_json, plan_release,
)

CORE_ASSETS = {
    "Slimefun-Legacy4.1.78.jar": (
        680344841, 8696630,
        "sha256:39ca485409b27d1705a28a24e3d6e681e731709d4163bcd500d347b090ea97ba",
    ),
    BUNDLE: (
        680344842, 26473157,
        "sha256:46b199845904e64d144afa5adaa1170ef391bbfc9f3493fab91481f1565c0d04",
    ),
    PACK: (
        680344840, 9143959,
        "sha256:1b856fff90fe78b7d5964a86a8ed281517d073a082db485d73f31cd07aa64a83",
    ),
}
TARGET_SOURCE = "b14a3bcc5ef221c013f93d49b6b3a1bbdb3101e9"


def verify_releases(source: dict, target: dict, latest: dict, *, require_present: bool = False) -> dict:
    pin = CARRIED_GEYSER_PIN
    if source.get("id") != pin["source_release_id"] or source.get("tag_name") != pin["source_tag"]:
        raise PolicyError("Wrong original Geyser ZIP release")
    if target.get("id") != pin["target_release_id"] or target.get("tag_name") != pin["target_tag"]:
        raise PolicyError("Wrong target release; v4.1.78 only")
    if target.get("target_commitish") != TARGET_SOURCE:
        raise PolicyError("Published core release source commit is not the reviewed v4.1.78")
    if (latest.get("id"), latest.get("tag_name")) != (target.get("id"), target.get("tag_name")):
        raise PolicyError("A newer release exists: do not alter historical releases")
    if source.get("immutable") is True or target.get("immutable") is True:
        raise PolicyError("Refusing asset upload against an immutable release")
    source_plan = plan_release(source, allow_legacy=False)
    target_plan = plan_release(target, allow_legacy=False)
    original = source_plan["retained"].get(CARRIED_GEYSER_PACK)
    if not original or original["id"] != pin["source_asset_id"]:
        raise PolicyError("Original Geyser ZIP is absent or has the wrong numeric identity")
    required = target_plan["retained"]
    for name, (asset_id, size, digest) in CORE_ASSETS.items():
        actual = required.get(name)
        if not actual or (actual["id"], actual["size"], actual["digest"]) != (asset_id, size, digest):
            raise PolicyError(f"Previously published v4.1.78 asset changed: {name}")
    expected = set(CORE_ASSETS) | ({CARRIED_GEYSER_PACK} if CARRIED_GEYSER_PACK in required else set())
    if set(required) != expected:
        raise PolicyError("Release contains unexpected asset(s), refusing changes")
    carried = required.get(CARRIED_GEYSER_PACK)
    if require_present and not carried:
        raise PolicyError("The exact unchanged Geyser ZIP has not been attached")
    if carried and carried["id"] == original["id"]:
        raise PolicyError("Geyser ZIP must have a new asset identity on target release")
    return {
        "source_release_id": source["id"],
        "source_asset_id": original["id"],
        "target_release_id": target["id"],
        "target_source": target["target_commitish"],
        "asset_name": CARRIED_GEYSER_PACK,
        "size": pin["size"],
        "sha256": pin["digest"],
        "already_present": carried is not None,
        "target_asset_id": carried["id"] if carried else None,
        "unchanged_java_assets": sorted(CORE_ASSETS),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--target", type=Path, required=True)
    parser.add_argument("--latest", type=Path, required=True)
    parser.add_argument("--require-present", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report = verify_releases(
            load_json(args.source), load_json(args.target), load_json(args.latest),
            require_present=args.require_present,
        )
        output = json.dumps(report, indent=2, sort_keys=True) + "\n"
        if args.output:
            args.output.write_text(output, encoding="utf-8")
        print(output, end="")
        return 0
    except (OSError, ValueError, TypeError, KeyError) as exc:
        print(f"Refusing optional Geyser ZIP carry: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
