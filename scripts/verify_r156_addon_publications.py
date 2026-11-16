#!/usr/bin/env python3
"""Fail-closed r156 official raw-JAR provenance check (no artifact replacement)."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile
from io import BytesIO
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "compatibility/sfl-addon-release-matrix.json"
LOCK = ROOT / "compatibility/r156-official-addon-releases.json"
EXPECTED = {
    "wickidcow/SF_CrystamaeHistoria": ("v1.0.3", "SF_CrystamaeHistoria1.0.3.jar"),
    "wickidcow/SF_Galactifun": ("v1.0.8", "SF_Galactifun1.0.8.jar"),
}
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")


def validate_local(matrix: dict, lock: dict) -> list[dict]:
    if lock.get("schema") != 1 or lock.get("bundle_revision") != 156:
        raise ValueError("Unsupported addon release provenance lock")
    if matrix.get("bundle_revision") != 156:
        raise ValueError("Published provenance belongs to a different bundle revision")
    source_rows = matrix.get("addons")
    if not isinstance(source_rows, list) or len(source_rows) != 44:
        raise ValueError("Expected exactly 44 maintained addon source pins")
    source = {row.get("repository"): row.get("source_commit") for row in source_rows}
    if len(source) != 44:
        raise ValueError("Duplicate addon source repository")
    rows = lock.get("releases")
    if not isinstance(rows, list) or len(rows) != len(EXPECTED):
        raise ValueError("Both official addon release locks are mandatory")
    found = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {
            "repository", "tag", "source_commit", "asset", "bytes", "sha256"
        }:
            raise ValueError("Malformed official addon release record")
        repo = row["repository"]
        if repo not in EXPECTED or repo in found:
            raise ValueError("Unexpected or duplicate released addon")
        found.add(repo)
        if (row["tag"], row["asset"]) != EXPECTED[repo]:
            raise ValueError(f"Wrong official version or raw asset: {repo}")
        if not isinstance(row["source_commit"], str) or not COMMIT.fullmatch(row["source_commit"]):
            raise ValueError(f"Invalid published source commit: {repo}")
        if source.get(repo) != row["source_commit"]:
            raise ValueError(f"Addon matrix pin does not match released source: {repo}")
        if type(row["bytes"]) is not int or not 100_000 <= row["bytes"] <= 15_000_000:
            raise ValueError(f"Invalid official asset size: {repo}")
        if not isinstance(row["sha256"], str) or not SHA256.fullmatch(row["sha256"]):
            raise ValueError(f"Missing immutable asset SHA-256: {repo}")
    if found != set(EXPECTED):
        raise ValueError("Missing official addon release")
    return rows


def read_json(url: str) -> dict:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "Slimefun-Legacy-r156-source-provenance",
    }
    token = os.getenv("GH_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    last = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=45) as response:
                data = response.read(2_000_001)
            if len(data) > 2_000_000:
                raise ValueError("Unexpected oversized GitHub API response")
            return json.loads(data)
        except urllib.error.HTTPError as exc:
            last = exc
            if exc.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Official release API unavailable: {last}")


def verify_remote(rows: list[dict]) -> None:
    for row in rows:
        repo, tag, expected = row["repository"], row["tag"], row["source_commit"]
        prefix = f"https://api.github.com/repos/{repo}"
        gitref = read_json(f"{prefix}/git/ref/tags/{tag}")
        if gitref.get("object") != {
            "sha": expected,
            "type": "commit",
            "url": f"{prefix}/git/commits/{expected}",
        }:
            raise ValueError(f"Official tag no longer points to exact source commit: {repo}")
        release = read_json(f"{prefix}/releases/tags/{tag}")
        if release.get("draft") is not False or release.get("prerelease") is not False:
            raise ValueError(f"Official release is a draft/prerelease: {repo}")
        if release.get("tag_name") != tag or release.get("target_commitish") != expected:
            raise ValueError(f"Official release tag/source mismatch: {repo}")
        assets = [a for a in release.get("assets", []) if a.get("name") == row["asset"]]
        if len(assets) != 1:
            raise ValueError(f"Official raw JAR missing/ambiguous: {repo}")
        asset = assets[0]
        expected_url = f"https://github.com/{repo}/releases/download/{tag}/{row['asset']}"
        if (asset.get("browser_download_url") != expected_url
                or asset.get("digest") != f"sha256:{row['sha256']}"
                or asset.get("size") != row["bytes"]):
            raise ValueError(f"Official release metadata/digest mismatch: {repo}")
        req = urllib.request.Request(expected_url, headers={
            "User-Agent": "Slimefun-Legacy-r156-source-provenance",
        })
        with urllib.request.urlopen(req, timeout=90) as response:
            data = response.read(row["bytes"] + 1)
        if len(data) != row["bytes"] or hashlib.sha256(data).hexdigest() != row["sha256"]:
            raise ValueError(f"Downloaded official raw JAR byte digest mismatch: {repo}")
        with zipfile.ZipFile(BytesIO(data)) as plugin:
            if plugin.testzip() is not None or "plugin.yml" not in plugin.namelist():
                raise ValueError(f"Invalid official plugin JAR: {repo}")
            descriptor = plugin.read("plugin.yml").decode("utf-8")
        version = re.escape(tag.removeprefix("v"))
        if not re.search(rf"(?m)^version:\s*['\"]?{version}['\"]?\s*$", descriptor):
            raise ValueError(f"Official plugin descriptor/version mismatch: {repo}")
        print(f"Verified official raw JAR: {repo} {tag}, {row['bytes']} bytes, sha256:{row['sha256']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--remote", action="store_true", help="Validate official tag, release API and downloaded JAR bytes")
    args = parser.parse_args()
    try:
        matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
        lock = json.loads(LOCK.read_text(encoding="utf-8"))
        rows = validate_local(matrix, lock)
        if args.remote:
            verify_remote(rows)
    except (OSError, ValueError, KeyError, TypeError, RuntimeError, zipfile.BadZipFile, urllib.error.URLError) as exc:
        print(f"Official addon release provenance: FAIL: {exc}", file=sys.stderr)
        return 1
    print("Official r156 addon release source/asset locks: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
