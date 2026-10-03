#!/usr/bin/env python3
"""Verify the pinned resource-pack release asset and release-workflow wiring."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path


def fail(message: str) -> None:
    raise ValueError(message)


def load_pin(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    required = {
        "schema",
        "repository",
        "release_tag",
        "release_id",
        "asset_name",
        "asset_id",
        "size",
        "sha256",
        "minecraft_minimum",
        "minecraft_supported_through",
        "stability",
    }
    missing = sorted(required - data.keys())
    if missing:
        fail(f"resource-pack pin is missing fields: {', '.join(missing)}")
    if data["schema"] != 1:
        fail(f"unexpected resource-pack pin schema: {data['schema']!r}")
    if data["repository"] != "wickidcow/SFL_RP_Official":
        fail(f"unexpected resource-pack repository: {data['repository']!r}")
    if not re.fullmatch(r"v\d+\.\d+\.\d+", str(data["release_tag"])):
        fail(f"stable resource-pack tag must be vMAJOR.MINOR.PATCH: {data['release_tag']!r}")
    if data["asset_name"] != "SlimefunLegacyRP.zip":
        fail(f"unexpected resource-pack asset name: {data['asset_name']!r}")
    if not isinstance(data["release_id"], int) or data["release_id"] <= 0:
        fail("resource-pack release_id must be a positive integer")
    if not isinstance(data["asset_id"], int) or data["asset_id"] <= 0:
        fail("resource-pack asset_id must be a positive integer")
    if not isinstance(data["size"], int) or data["size"] <= 0:
        fail("resource-pack size must be a positive integer")
    if not re.fullmatch(r"[0-9a-f]{64}", str(data["sha256"])):
        fail("resource-pack sha256 must be a lowercase 64-character digest")
    if data["stability"] != "stable":
        fail("Slimefun production releases must pin a stable resource-pack release")
    if data["minecraft_minimum"] != "1.21.11":
        fail("resource-pack minimum Minecraft line must remain 1.21.11")
    return data


def verify_static(root: Path, pin: dict) -> None:
    release_workflow = (root / ".github/workflows/reproducible-release.yml").read_text(encoding="utf-8")
    mirror_workflow = (root / ".github/workflows/mirror-resource-pack-release.yml").read_text(encoding="utf-8")
    cleanup_workflow = (root / ".github/workflows/cleanup-sfl-release-assets.yml").read_text(encoding="utf-8")
    docs = (root / "docs/RESOURCE_PACK.md").read_text(encoding="utf-8")

    checks = {
        "release workflow resource-pack name": "CANONICAL_RESOURCE_PACK: SlimefunLegacyRP.zip" in release_workflow,
        "release workflow pin": "RESOURCE_PACK_PIN: compatibility/resource-pack-release.json" in release_workflow,
        "release workflow verification": "Require pinned official resource pack" in release_workflow,
        "release upload contains pack": 'gh release upload "$TAG" "$JAR" "$BUNDLE" "$RESOURCE_PACK"' in release_workflow,
        "release creation contains pack": 'gh release create "$TAG" "$JAR" "$BUNDLE" "$RESOURCE_PACK"' in release_workflow,
        "release layout requires pack": 'grep -Fxq "$RESOURCE_PACK" assets-after.txt' in release_workflow,
        "release layout requires three assets": 'if [[ "$ASSET_COUNT" -ne 3 ]]' in release_workflow,
        "mirror workflow uploads pack": 'gh release upload "$TARGET_TAG" "$PACK"' in mirror_workflow,
        "mirror workflow verifies published digest": "Published Slimefun release resource-pack digest mismatch" in mirror_workflow,
        "cleanup preserves canonical pack": "`SlimefunLegacyRP.zip` is retained when present" in cleanup_workflow,
        "cleanup recognizes final three-asset layout": 'if [[ "$ASSET_COUNT" -ne 2 && "$ASSET_COUNT" -ne 3 ]]' in cleanup_workflow,
        "cleanup rejects unknown third asset": "it is not the canonical SlimefunLegacyRP.zip" in cleanup_workflow,
        "docs describe separate asset": "separate** `SlimefunLegacyRP.zip` asset" in docs,
        "docs identify pin": "`compatibility/resource-pack-release.json`" in docs,
    }
    failed = [name for name, ok in checks.items() if not ok]
    if failed:
        fail("resource-pack release wiring failed: " + ", ".join(failed))

    if pin["asset_name"] not in release_workflow or pin["asset_name"] not in mirror_workflow:
        fail("pinned resource-pack filename is not wired into both release workflows")


def verify_source_release(path: Path, pin: dict) -> None:
    release = json.loads(path.read_text(encoding="utf-8"))
    if release.get("id") != pin["release_id"]:
        fail(f"resource-pack release ID mismatch: {release.get('id')} != {pin['release_id']}")
    if release.get("tag_name") != pin["release_tag"]:
        fail(f"resource-pack release tag mismatch: {release.get('tag_name')!r}")
    if release.get("draft") or release.get("prerelease"):
        fail("pinned resource-pack release must be published and non-prerelease")
    assets = {asset.get("name"): asset for asset in release.get("assets", [])}
    asset = assets.get(pin["asset_name"])
    if not asset:
        fail(f"resource-pack release is missing {pin['asset_name']}")
    if asset.get("id") != pin["asset_id"]:
        fail(f"resource-pack asset ID mismatch: {asset.get('id')} != {pin['asset_id']}")
    if asset.get("size") != pin["size"]:
        fail(f"resource-pack asset size mismatch: {asset.get('size')} != {pin['size']}")
    if asset.get("digest") != "sha256:" + pin["sha256"]:
        fail(f"resource-pack GitHub digest mismatch: {asset.get('digest')!r}")


def verify_zip(path: Path, pin: dict) -> None:
    data = path.read_bytes()
    if len(data) != pin["size"]:
        fail(f"resource-pack ZIP size mismatch: {len(data)} != {pin['size']}")
    digest = hashlib.sha256(data).hexdigest()
    if digest != pin["sha256"]:
        fail(f"resource-pack ZIP SHA-256 mismatch: {digest} != {pin['sha256']}")
    try:
        with zipfile.ZipFile(path) as archive:
            bad = archive.testzip()
            if bad:
                fail(f"resource-pack ZIP CRC failure: {bad}")
            names = {name for name in archive.namelist() if not name.endswith("/")}
    except zipfile.BadZipFile as exc:
        fail(f"invalid resource-pack ZIP: {exc}")
    for required in ("pack.mcmeta", "pack.png"):
        if required not in names:
            fail(f"resource-pack ZIP is missing {required}")
    if not any(name.startswith("assets/") for name in names):
        fail("resource-pack ZIP contains no assets/ resources")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--pin")
    parser.add_argument("--release-json")
    parser.add_argument("--zip")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    pin_path = Path(args.pin).resolve() if args.pin else root / "compatibility/resource-pack-release.json"
    try:
        pin = load_pin(pin_path)
        verify_static(root, pin)
        if args.release_json:
            verify_source_release(Path(args.release_json), pin)
        if args.zip:
            verify_zip(Path(args.zip), pin)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Resource-pack release verification failed: {exc}", file=sys.stderr)
        return 1

    print(
        "Resource-pack release verification passed: "
        f"{pin['repository']} {pin['release_tag']} / {pin['asset_name']} / {pin['sha256']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
