#!/usr/bin/env python3
"""Validate the published addon ZIP without extracting or executing plugin files."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


def valid_public_name(name: str, minimum: str, primary: str) -> bool:
    if name.startswith("SF_SFWorldEdit"):
        pattern = rf"SF_SFWorldEdit\d+(?:\.\d+)+_\({re.escape(minimum)}-{re.escape(primary)}\)\.jar"
    else:
        pattern = r"(?:SF_[A-Za-z0-9]+|SFL_DracFun-Reborn)\d+(?:\.\d+)+\.jar"
    return re.fullmatch(pattern, name) is not None


def validate_bundle(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive entries")
        for name in names:
            parts = PurePosixPath(name).parts
            if not name or "\\" in name or name.startswith("/") or ".." in parts:
                raise ValueError(f"Unsafe archive path: {name!r}")
        bad = archive.testzip()
        if bad is not None:
            raise ValueError(f"Corrupt archive member: {bad}")
        manifest = json.loads(archive.read("SF_ADDON_MANIFEST.json"))
        records = manifest.get("addons", [])
        if not isinstance(records, list) or not records:
            raise ValueError("Published manifest contains no addon records")
        versions = manifest.get("compatibility", {}).get("minecraft_versions", {})
        minimum, primary = versions.get("minimum"), versions.get("primary")
        if minimum != "1.21.11" or not isinstance(primary, str) or primary not in versions.get("supported", []):
            raise ValueError(f"Invalid declared compatibility range: {versions}")
        expected, repositories = set(), set()
        for record in records:
            name, repository = record.get("jar"), record.get("repository")
            if not isinstance(name, str) or not valid_public_name(name, minimum, primary):
                raise ValueError(f"Invalid public addon JAR name: {name!r}")
            if name in expected or not isinstance(repository, str) or repository in repositories:
                raise ValueError(f"Duplicate JAR or invalid/duplicate repository: {name!r}")
            expected.add(name)
            repositories.add(repository)
            data = archive.read(name)
            digest = record.get("sha256")
            if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
                raise ValueError(f"Missing or invalid SHA-256: {name}")
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError(f"JAR checksum mismatch: {name}")
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                members = jar.namelist()
                if len(members) != len(set(members)) or jar.testzip() is not None:
                    raise ValueError(f"Corrupt or ambiguous plugin JAR: {name}")
                if not any(d in members for d in ("plugin.yml", "paper-plugin.yml", "paper-plugin.yaml")):
                    raise ValueError(f"Plugin descriptor missing: {name}")
        actual = {name for name in names if name.endswith(".jar")}
        if actual != expected:
            raise ValueError(f"Addon JAR set mismatch: extra={sorted(actual - expected)}, missing={sorted(expected - actual)}")
        return {"addon_count": len(expected), "minimum": minimum, "primary": primary,
                "core_source_commit": manifest.get("core_source_commit")}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args(argv)
    try:
        result = validate_bundle(args.bundle)
    except (OSError, ValueError, KeyError, TypeError, AttributeError, RuntimeError, zipfile.BadZipFile) as exc:
        print(f"Published addon bundle validation failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
