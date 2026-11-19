#!/usr/bin/env python3
"""Replace a source-verified Infernal Expansion rebuild with the exact official v1.1.0 JAR."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "compatibility/r157-infernal-expansion-release.json"
MATRIX = ROOT / "compatibility/sfl-addon-release-matrix.json"
SLUG = "infernalexpansion"
CLASS_PATH = "ga/kirzu/infernalexpansion/InfernalExpansion.class"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def release_receipt(lock: dict) -> dict:
    return {
        "tag": lock["tag"],
        "source_commit": lock["source_commit"],
        "url": (
            f"https://github.com/{lock['repository']}/releases/download/"
            f"{lock['tag']}/{lock['asset']}"
        ),
        "sha256": lock["sha256"],
        "bytes": lock["bytes"],
        "verified_download": True,
    }


def validate_lock(lock: dict, matrix: dict) -> dict:
    if lock.get("schema") != 1 or lock.get("bundle_revision") != 157:
        raise ValueError("Unsupported Infernal Expansion release lock")
    if matrix.get("bundle_revision") != 157:
        raise ValueError("Infernal release lock belongs to another bundle revision")
    if (
        lock.get("repository") != "wickidcow/SF_InfernalExpansion"
        or lock.get("tag") != "v1.1.0"
        or lock.get("asset") != "SF_InfernalExpansion1.1.0.jar"
        or lock.get("plugin_name") != "InfernalExpansion"
        or lock.get("plugin_version") != "1.1.0"
        or lock.get("api_version") != "1.21.11"
        or lock.get("java_class_major") != 65
        or type(lock.get("bytes")) is not int
        or lock["bytes"] <= 0
        or not re.fullmatch(r"[0-9a-f]{64}", str(lock.get("sha256", "")))
        or not re.fullmatch(r"[0-9a-f]{40}", str(lock.get("source_commit", "")))
    ):
        raise ValueError("Unexpected Infernal release identity or incomplete pin")
    selected = [
        row for row in matrix.get("addons", [])
        if row.get("repository") == lock["repository"]
    ]
    if len(selected) != 1 or selected[0].get("slug") != SLUG:
        raise ValueError("Infernal Expansion missing/ambiguous in approved matrix")
    if selected[0].get("source_commit") != lock["source_commit"]:
        raise ValueError("Infernal Expansion source commit differs from official release")
    return selected[0]


def plugin_descriptor(jar: bytes) -> tuple[str, str, str]:
    from io import BytesIO
    with zipfile.ZipFile(BytesIO(jar)) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Infernal JAR corrupt or contains duplicate entries")
        if "plugin.yml" not in names or CLASS_PATH not in names:
            raise ValueError("Infernal JAR missing plugin descriptor or main class")
        content = archive.read("plugin.yml").decode("utf-8")
        main_class = archive.read(CLASS_PATH)
    if main_class[:4] != b"\\xca\\xfe\\xba\\xbe" or int.from_bytes(main_class[6:8], "big") != 65:
        raise ValueError("Infernal JAR class must be Java 21 major version 65")
    result = []
    for key in ("name", "version", "api-version"):
        match = re.search(rf"(?mi)^\\s*{re.escape(key)}:\\s*['\\\"]?([^'\\\"\\r\\n#]+)", content)
        if not match:
            raise ValueError(f"Missing Infernal plugin descriptor {key}")
        result.append(match.group(1).strip())
    return tuple(result)


def stage(jars: Path, metadata: Path, official: Path, lock: dict, matrix: dict, verify_only: bool) -> dict:
    validate_lock(lock, matrix)
    if not jars.is_dir() or jars.is_symlink() or not metadata.is_dir() or metadata.is_symlink():
        raise ValueError("Unsafe or missing addon staging directories")
    if not official.is_file() or official.is_symlink():
        raise ValueError("Official Infernal Expansion JAR not supplied")
    jar_file = jars / lock["asset"]
    meta_file = metadata / f"{SLUG}.json"
    if not jar_file.is_file() or jar_file.is_symlink() or not meta_file.is_file() or meta_file.is_symlink():
        raise ValueError("Missing source-built Infernal Expansion JAR or metadata")
    row = json.loads(meta_file.read_text(encoding="utf-8"))
    if (row.get("repository"), row.get("slug"), row.get("commit"),
        row.get("jar"), row.get("version"), row.get("artifact_base")) != (
            lock["repository"], SLUG, lock["source_commit"],
            lock["asset"], lock["plugin_version"], "InfernalExpansion"
        ):
        raise ValueError("Source-build metadata identity does not match Infernal pin")
    raw = official.read_bytes()
    if len(raw) != lock["bytes"] or digest(raw) != lock["sha256"]:
        raise ValueError("Official Infernal release bytes differ from immutable SHA-256 pin")
    if plugin_descriptor(raw) != (
        lock["plugin_name"], lock["plugin_version"], lock["api_version"]
    ):
        raise ValueError("Official Infernal release descriptor is incompatible")

    source = jar_file.read_bytes()
    receipt = release_receipt(lock)
    if verify_only:
        if (source != raw or row.get("sha256") != digest(raw)
                or row.get("published_artifact") != receipt
                or not re.fullmatch(r"[0-9a-f]{64}", str(row.get("source_build_sha256", "")))
                or row.get("distributable_api") != "1.21.11-R0.1-SNAPSHOT"
                or row.get("secondary_platforms") != []):
            raise ValueError("Staged official Infernal release receipt mismatch")
        return row

    if row.get("sha256") != digest(source) or "published_artifact" in row:
        raise ValueError("Source-built Infernal SHA/receipt mismatch before staging")
    if plugin_descriptor(source)[0:2] != (lock["plugin_name"], lock["plugin_version"]):
        raise ValueError("Compiled source Infernal descriptor is unexpected")
    row["source_build_sha256"] = digest(source)
    row["sha256"] = digest(raw)
    row["published_artifact"] = receipt
    row["distributable_api"] = "1.21.11-R0.1-SNAPSHOT"
    row["secondary_platforms"] = []
    with tempfile.NamedTemporaryFile("wb", dir=jars, delete=False) as out:
        out.write(raw)
        pending_jar = Path(out.name)
    with tempfile.NamedTemporaryFile("w", dir=metadata, encoding="utf-8", delete=False) as out:
        out.write(json.dumps(row, indent=2) + "\\n")
        pending_meta = Path(out.name)
    os.replace(pending_jar, jar_file)
    os.replace(pending_meta, meta_file)
    return stage(jars, metadata, official, lock, matrix, True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jars", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--official", type=Path, required=True)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    try:
        lock = json.loads(LOCK.read_text(encoding="utf-8"))
        matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
        result = stage(args.jars, args.metadata, args.official, lock, matrix, args.verify_only)
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as exc:
        print(f"Infernal release staging failed: {exc}", file=sys.stderr)
        return 1
    print(f"PASS Infernal Expansion {result['jar']} official SHA-256 {result['sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
