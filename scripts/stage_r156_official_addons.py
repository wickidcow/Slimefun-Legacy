#!/usr/bin/env python3
"""Ship exact verified official v1.0.3/v1.0.8 raw JAR bytes, not rebuilt equivalents."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import zipfile
from io import BytesIO
from pathlib import Path

from scripts.verify_r156_addon_publications import LOCK, MATRIX, validate_local, verify_remote

SLUGS = {
    "wickidcow/SF_CrystamaeHistoria": ("crystamaehistoria", "CrystamaeHistoria"),
    "wickidcow/SF_Galactifun": ("galactifun", "Galactifun"),
}
BASE_API = "1.21.11-R0.1-SNAPSHOT"


def descriptor(data: bytes) -> tuple[str, str, str]:
    with zipfile.ZipFile(BytesIO(data)) as plugin:
        names = plugin.namelist()
        if len(names) != len(set(names)) or "plugin.yml" not in names or plugin.testzip() is not None:
            raise ValueError("Official/rebuilt JAR is corrupt or lacks a unique plugin.yml")
        info = plugin.read("plugin.yml").decode("utf-8")
    values = []
    for key in ("name", "version", "api-version"):
        match = re.search(rf"(?mi)^{re.escape(key)}:\s*['\"]?([^'\"\r\n#]+)", info)
        if not match:
            raise ValueError(f"Missing official plugin descriptor field {key}")
        values.append(match.group(1).strip())
    return tuple(values)


def validate_inputs(jars: Path, metadata: Path, rows: list[dict], official: dict[str, bytes]) -> list[tuple]:
    if {r["repository"] for r in rows} != set(SLUGS) or set(official) != set(SLUGS):
        raise ValueError("Both official r156 raw JARs must be supplied together")
    if not jars.is_dir() or jars.is_symlink() or not metadata.is_dir() or metadata.is_symlink():
        raise ValueError("Unsafe/missing collected staging directories")
    pending = []
    for row in rows:
        repo = row["repository"]
        slug, expected_name = SLUGS[repo]
        jar = jars / row["asset"]
        meta = metadata / f"{slug}.json"
        if not jar.is_file() or jar.is_symlink() or not meta.is_file() or meta.is_symlink():
            raise ValueError(f"Required staged source build missing/unsafe: {repo}")
        rec = json.loads(meta.read_text(encoding="utf-8"))
        source = jar.read_bytes()
        digest = hashlib.sha256(source).hexdigest()
        if (rec.get("repository") != repo or rec.get("slug") != slug
                or rec.get("jar") != row["asset"] or rec.get("commit") != row["source_commit"]
                or rec.get("version") != row["tag"][1:]
                or rec.get("sha256") != digest or "published_artifact" in rec):
            raise ValueError(f"Staged source-build identity/provenance mismatch: {repo}")
        actual_name, version, _ = descriptor(source)
        if (actual_name, version) != (expected_name, row["tag"][1:]):
            raise ValueError(f"Wrong source-built plugin descriptor: {repo}")
        raw = official[repo]
        if len(raw) != row["bytes"] or hashlib.sha256(raw).hexdigest() != row["sha256"]:
            raise ValueError(f"Official raw JAR bytes failed checksum/size: {repo}")
        actual_name, version, api = descriptor(raw)
        if (actual_name, version, api) != (expected_name, row["tag"][1:], "1.21.11"):
            raise ValueError(f"Official raw JAR version/API floor mismatch: {repo}")
        proof = {
            "tag": row["tag"],
            "source_commit": row["source_commit"],
            "url": f"https://github.com/{repo}/releases/download/{row['tag']}/{row['asset']}",
            "sha256": row["sha256"],
            "bytes": row["bytes"],
            "verified_download": True,
        }
        rec["source_build_sha256"] = digest
        rec["sha256"] = row["sha256"]
        rec["distributable_api"] = BASE_API
        rec["published_artifact"] = proof
        pending.append((jar, meta, raw, json.dumps(rec, indent=2).encode("utf-8") + b"\n"))
    return pending


def verify_staged(jars: Path, metadata: Path, rows: list[dict]) -> None:
    for row in rows:
        repo = row["repository"]
        slug, expected_name = SLUGS[repo]
        jar = jars / row["asset"]
        meta = metadata / f"{slug}.json"
        if (not jar.is_file() or jar.is_symlink() or not meta.is_file()
                or meta.is_symlink() or jars.is_symlink() or metadata.is_symlink()):
            raise ValueError(f"Official JAR/evidence missing: {repo}")
        rec = json.loads(meta.read_text(encoding="utf-8"))
        raw = jar.read_bytes()
        if len(raw) != row["bytes"] or hashlib.sha256(raw).hexdigest() != row["sha256"]:
            raise ValueError(f"Bundled JAR is not official raw release bytes: {repo}")
        if descriptor(raw) != (expected_name, row["tag"][1:], "1.21.11"):
            raise ValueError(f"Bundled official JAR lost 1.21.11 API/version identity: {repo}")
        receipt = {
            "tag": row["tag"],
            "source_commit": row["source_commit"],
            "url": f"https://github.com/{repo}/releases/download/{row['tag']}/{row['asset']}",
            "sha256": row["sha256"],
            "bytes": row["bytes"],
            "verified_download": True,
        }
        if (rec.get("repository") != repo or rec.get("slug") != slug
                or rec.get("jar") != row["asset"] or rec.get("commit") != row["source_commit"]
                or rec.get("version") != row["tag"][1:]
                or rec.get("sha256") != row["sha256"] or rec.get("published_artifact") != receipt
                or rec.get("distributable_api") != BASE_API
                or not re.fullmatch(r"[0-9a-f]{64}", str(rec.get("source_build_sha256", "")))):
            raise ValueError(f"Official JAR manifest provenance missing/altered: {repo}")
        print(f"Verified packaged official raw JAR bytes: {row['asset']} sha256:{row['sha256']}")


def stage(jars: Path, metadata: Path, rows: list[dict], official: dict[str, bytes]) -> None:
    pending = validate_inputs(jars, metadata, rows, official)
    # Validate both downloads and both source builds before replacing either CI-only file.
    # No original release tags/assets or player data are changed.
    with tempfile.TemporaryDirectory(prefix=".r156-official-", dir=jars.parent) as tmp:
        temp = Path(tmp)
        for index, (_, _, raw, meta_bytes) in enumerate(pending):
            (temp / f"{index}.jar").write_bytes(raw)
            (temp / f"{index}.json").write_bytes(meta_bytes)
        for index, (jar, meta, _, _) in enumerate(pending):
            os.replace(temp / f"{index}.jar", jar)
            os.replace(temp / f"{index}.json", meta)
    verify_staged(jars, metadata, rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jars", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    try:
        matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
        lock = json.loads(LOCK.read_text(encoding="utf-8"))
        rows = validate_local(matrix, lock)
        if not args.verify_only:
            stage(args.jars, args.metadata, rows, verify_remote(rows))
        verify_staged(args.jars, args.metadata, rows)
    except (OSError, ValueError, KeyError, TypeError, RuntimeError, zipfile.BadZipFile) as exc:
        print(f"Official addon binary staging: FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
