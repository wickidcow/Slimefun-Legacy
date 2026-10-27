#!/usr/bin/env python3
"""Read-only release-asset preflight and preservation checks.

Consumes GitHub REST release JSON. This program never calls GitHub or deletes
files/assets. The workflow executes only its explicit legacy-asset deletion plan.
The API digest is upload metadata, not a fresh download/hash of release bytes.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

BUNDLE = "SF_Addons_1.21.11-26.3.zip"
PACK = "SlimefunLegacyRP.zip"
BEDROCK_PACK = "SFL_GEYSER_RP_experimental_v1.0.3_complete.zip"
BEDROCK_OVERLAY = "SFL_GEYSER_RP_experimental_v1.0.3.zip"
BEDROCK_PIN_PATH = Path(__file__).resolve().parents[1] / "compatibility/bedrock-resource-pack-release.json"
BEDROCK_ARCHIVE_PATH = "compatibility/bedrock/" + BEDROCK_PACK
# Preserve only the unchanged optional drop-in ZIP already published on 4.1.77.
# This is a release-asset identity rule, not Bedrock/Geyser functionality.
CARRIED_GEYSER_PACK = "SlimefunLegacyRP_Geyser.zip"
CARRIED_GEYSER_PIN = {
    "source_tag": "v4.1.77",
    "source_release_id": 421464301,
    "source_asset_id": 669444618,
    "target_tag": "v4.1.78",
    "target_release_id": 425012427,
    "size": 1339867,
    "digest": "sha256:e11e5f8e6ede75a17421039e50178e83e11e9cf7f020e99596cfc078801b8c58",
}
# The historical base contributes unchanged components, but is not approved as
# a live asset. Only the current overlay may remain on its exact origin release.
BEDROCK_SOURCE_ASSETS = (
    {"release_tag": "v4.1.75", "release_id": 415923695, "asset_id": 664289959,
     "asset_name": "GEYSER_SFL_RP_v0.0.2.zip", "size_bytes": 1329705,
     "sha256": "b27941425a646ba97c4789ecf2841e20f08663e79b3d538b552516d40cc67ee1"},
    {"release_tag": "v4.1.75", "release_id": 415923695, "asset_id": 664360404,
     "asset_name": BEDROCK_OVERLAY, "size_bytes": 1265679,
     "sha256": "90d78fcf8382bdee1bce3a364515769cb8325c174b58c34b512039502cc50e51"},
)
ROLLBACK = "SF_Addons_r136_1.21.11-26.3.zip"
# The existing, deliberately retained asset; not permission for arbitrary extras.
# Source: release 412654205 / asset 641318435, inspected October 15, 2026.
ROLLBACK_PIN = {
    "release_id": 412654205,
    "tag": "v4.1.74",
    "id": 641318435,
    "name": ROLLBACK,
    "size": 24440999,
    "digest": "sha256:79b9cb70a522a9ed97de3d9107d4750a913c4372043e5ee8cfeca0a1f85afc9f",
}
LEGACY_EXACT = {
    "SFL_AddonsNDependencies.zip",
    "SFL_AddonsNDependencies.sha256",
    "SFL_Addons_1.21.11-26.3.zip",
}


class PolicyError(ValueError):
    """Refuse ambiguous, incomplete or unapproved release state."""


def is_legacy(name: str) -> bool:
    return (
        name in LEGACY_EXACT
        or re.fullmatch(r"SF_.*\.jar", name) is not None
        or re.fullmatch(r"SlimefunLegacy-ResourcePack-.*\.zip", name) is not None
    )


def positive_int(value: Any) -> bool:
    return type(value) is int and value > 0


def retained_identity(asset: dict[str, Any]) -> dict[str, Any]:
    digest = asset.get("digest")
    if not isinstance(digest, str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise PolicyError(f"Missing/invalid SHA-256 metadata for retained asset {asset['name']}")
    if not positive_int(asset.get("size")):
        raise PolicyError(f"Empty/invalid retained asset {asset['name']}")
    return {key: asset[key] for key in ("id", "name", "size", "digest", "state")}


def load_bedrock_pin() -> dict[str, Any]:
    pin = load_json(BEDROCK_PIN_PATH)
    if not isinstance(pin, dict) or type(pin.get("schema_version")) is not int or pin["schema_version"] != 2:
        raise PolicyError("Invalid Bedrock preservation pin schema")
    if pin.get("repository") != "wickidcow/Slimefun-Legacy" or pin.get("asset_name") != BEDROCK_PACK:
        raise PolicyError("Unexpected Bedrock preservation repository or filename")
    if pin.get("archive_path") != BEDROCK_ARCHIVE_PATH:
        raise PolicyError("Unexpected Bedrock repository archive path")
    if {"release_tag", "release_id", "asset_id"} & set(pin):
        raise PolicyError("Complete Bedrock pin must use source_assets for provenance")
    if not positive_int(pin.get("size_bytes")):
        raise PolicyError("Invalid Bedrock preservation pin size_bytes")
    if not isinstance(pin.get("sha256"), str) or not re.fullmatch(r"[0-9a-f]{64}", pin["sha256"]):
        raise PolicyError("Invalid Bedrock preservation SHA-256 pin")
    if pin.get("carry_forward_tags") != ["v4.1.76"]:
        raise PolicyError("Bedrock carry-forward tags must contain only the reviewed v4.1.76 successor")
    if pin.get("stability") != "experimental":
        raise PolicyError("Bedrock pack must retain its experimental qualification")
    sources = pin.get("source_assets")
    if not isinstance(sources, list) or len(sources) != len(BEDROCK_SOURCE_ASSETS):
        raise PolicyError("Complete Bedrock pin requires exactly two reviewed source assets")
    for source, expected in zip(sources, BEDROCK_SOURCE_ASSETS):
        if (not isinstance(source, dict) or set(source) != set(expected)
                or not all(positive_int(source.get(key)) for key in ("release_id", "asset_id", "size_bytes"))
                or source != expected):
            raise PolicyError("Bedrock source asset provenance mismatch")
    return pin


def retained_bedrock_identity(release: dict[str, Any], asset: dict[str, Any]) -> dict[str, Any]:
    pin = load_bedrock_pin()
    tag = release["tag_name"]
    if asset["name"] == BEDROCK_OVERLAY:
        expected = pin["source_assets"][1]
        if (tag != expected["release_tag"] or release["id"] != expected["release_id"]
                or asset["id"] != expected["asset_id"]):
            raise PolicyError("Bedrock overlay is approved only on its exact original release/asset identity")
    elif asset["name"] == BEDROCK_PACK:
        if tag not in pin["carry_forward_tags"]:
            raise PolicyError("Complete Bedrock pack is not approved on this release tag")
        expected = pin
    else:
        raise PolicyError("Unexpected Bedrock asset role")
    identity = retained_identity(asset)
    if identity["size"] != expected["size_bytes"] or identity["digest"] != "sha256:" + expected["sha256"]:
        raise PolicyError("Bedrock pack size/digest differs from its reviewed source or complete-archive pin")
    return identity


def retained_carried_geyser_identity(release: dict[str, Any], asset: dict[str, Any]) -> dict[str, Any]:
    """Only the original v4.1.77 asset or its unchanged v4.1.78 copy qualifies."""
    pin = CARRIED_GEYSER_PIN
    tag = release["tag_name"]
    if tag == pin["source_tag"]:
        if release["id"] != pin["source_release_id"] or asset["id"] != pin["source_asset_id"]:
            raise PolicyError("Original Geyser ZIP release/asset identity changed")
    elif tag == pin["target_tag"]:
        if release["id"] != pin["target_release_id"] or asset["id"] == pin["source_asset_id"]:
            raise PolicyError("Carried Geyser ZIP must be a separate asset on the exact successor")
    else:
        raise PolicyError("Geyser ZIP is approved on v4.1.77 and v4.1.78 only")
    identity = retained_identity(asset)
    if identity["size"] != pin["size"] or identity["digest"] != pin["digest"]:
        raise PolicyError("Optional Geyser ZIP does not match unchanged v4.1.77 source bytes")
    return identity


def plan_release(release: Any, *, allow_legacy: bool = True) -> dict[str, Any]:
    if not isinstance(release, dict) or not positive_int(release.get("id")):
        raise PolicyError("Expected a GitHub release object with a positive numeric ID")
    if release.get("draft") is not False or release.get("prerelease") is not False:
        raise PolicyError("Cleanup requires an explicitly non-draft, non-prerelease release")
    tag = release.get("tag_name")
    if not isinstance(tag, str) or not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise PolicyError("Expected a stable vMAJOR.MINOR.PATCH release tag")
    assets = release.get("assets")
    if not isinstance(assets, list):
        raise PolicyError("Missing or invalid assets array")
    core = f"Slimefun-Legacy{tag[1:]}.jar"
    names: set[str] = set()
    ids: set[int] = set()
    keep: dict[str, dict[str, Any]] = {}
    remove: list[dict[str, Any]] = []
    for asset in assets:
        if not isinstance(asset, dict) or not positive_int(asset.get("id")):
            raise PolicyError("Asset identity must be a positive numeric ID")
        name = asset.get("name")
        if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_().+\-]+", name):
            raise PolicyError("Asset names must be unambiguous, flat filenames")
        if name in names or asset["id"] in ids:
            raise PolicyError("Duplicate asset name or numeric ID")
        names.add(name)
        ids.add(asset["id"])
        if asset.get("state") != "uploaded":
            raise PolicyError(f"Asset is not fully uploaded: {name}")
        if name in {core, BUNDLE, PACK}:
            keep[name] = retained_identity(asset)
        elif name == CARRIED_GEYSER_PACK:
            keep[name] = retained_carried_geyser_identity(release, asset)
        elif name in {BEDROCK_PACK, BEDROCK_OVERLAY}:
            keep[name] = retained_bedrock_identity(release, asset)
        elif name == ROLLBACK:
            expected = ROLLBACK_PIN
            if release["id"] != expected["release_id"] or tag != expected["tag"]:
                raise PolicyError("The retained r136 rollback belongs only to its reviewed release")
            identity = retained_identity(asset)
            if any(identity[key] != expected[key] for key in ("id", "name", "size", "digest")):
                raise PolicyError("Retained r136 rollback identity/digest differs from the reviewed pin")
            keep[name] = identity
        elif allow_legacy and is_legacy(name):
            remove.append({"id": asset["id"], "name": name})
        else:
            raise PolicyError(f"Unapproved release asset: {name}; no cleanup is authorized")
    if core not in keep or BUNDLE not in keep:
        raise PolicyError("Expected the exact tag-matching core JAR and canonical addon bundle")
    if remove and release.get("immutable") is True:
        raise PolicyError("Cannot remove assets from an immutable release")
    return {
        "release_id": release["id"],
        "tag": tag,
        "retained": dict(sorted(keep.items())),
        "remove": sorted(remove, key=lambda asset: asset["id"]),
    }


def verify_preserved(before: Any, after: Any) -> dict[str, Any]:
    planned = plan_release(before)
    actual = plan_release(after, allow_legacy=False)
    if (actual["release_id"], actual["tag"]) != (planned["release_id"], planned["tag"]):
        raise PolicyError("Release identity changed during cleanup")
    if actual["retained"] != planned["retained"]:
        raise PolicyError("Retained assets changed during cleanup; preserve all original evidence")
    return actual


def load_json(path: Path) -> Any:
    def unique_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise PolicyError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("plan", "verify"))
    parser.add_argument("before", type=Path)
    parser.add_argument("--after", type=Path)
    parser.add_argument("--delete-ids", type=Path)
    args = parser.parse_args(argv)
    if args.mode == "verify" and (args.after is None or args.delete_ids is not None):
        parser.error("verify requires --after and does not accept --delete-ids")
    if args.mode == "plan" and args.after is not None:
        parser.error("plan does not accept --after")
    try:
        before = load_json(args.before)
        result = (plan_release(before) if args.mode == "plan"
                  else verify_preserved(before, load_json(args.after)))
        if args.delete_ids is not None:
            # Write only after the entire preflight succeeds; the shell stops on failure.
            args.delete_ids.write_text("".join(f"{asset['id']}\n" for asset in result["remove"]), encoding="utf-8")
        print(json.dumps(result, indent=2))
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"Release asset verification failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
