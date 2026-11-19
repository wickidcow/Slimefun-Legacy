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



def verify_r156_official_records(manifest: dict, release_lock: dict, read_jar) -> None:
    """Independent final ZIP gate: same version/source is NOT the same official JAR."""
    if manifest.get("bundle_revision", 0) < 156:
        return
    required = {
        "wickidcow/SF_CrystamaeHistoria": ("v1.0.3", "SF_CrystamaeHistoria1.0.3.jar"),
        "wickidcow/SF_Galactifun": ("v1.0.8", "SF_Galactifun1.0.8.jar"),
    }
    if release_lock.get("schema") != 1 or release_lock.get("bundle_revision") != 156:
        raise ValueError("Wrong official source/release lock revision")
    rows = release_lock.get("releases")
    if not isinstance(rows, list) or len(rows) != 2 or {
        row.get("repository") for row in rows
    } != set(required):
        raise ValueError("Both exact official addon releases are mandatory")
    by_repo = {row.get("repository"): row for row in manifest["addons"]}
    if len(by_repo) != len(manifest["addons"]):
        raise ValueError("Duplicate addon source in bundled manifest")
    for row in rows:
        repo = row["repository"]
        tag, asset = required[repo]
        if (row.get("tag"), row.get("asset")) != (tag, asset):
            raise ValueError(f"Unexpected released addon tag/name: {repo}")
        rec = by_repo.get(repo)
        if not isinstance(rec, dict):
            raise ValueError(f"Required published addon missing from ZIP: {repo}")
        receipt = {
            "tag": tag,
            "source_commit": row["source_commit"],
            "url": f"https://github.com/{repo}/releases/download/{tag}/{asset}",
            "sha256": row["sha256"],
            "bytes": row["bytes"],
            "verified_download": True,
        }
        if (rec.get("commit") != row["source_commit"] or rec.get("jar") != asset
                or rec.get("version") != tag.removeprefix("v")
                or rec.get("sha256") != row["sha256"]
                or rec.get("published_artifact") != receipt
                or rec.get("distributable_api") != "1.21.11-R0.1-SNAPSHOT"
                or not re.fullmatch(r"[0-9a-f]{64}", str(rec.get("source_build_sha256", "")))):
            raise ValueError(f"Missing exact official raw-release receipt: {repo}")
        data = read_jar(asset)
        if (len(data) != row["bytes"]
                or hashlib.sha256(data).hexdigest() != row["sha256"]):
            raise ValueError(f"Rebuilt JAR is not byte-identical to published release: {repo}")



def verify_r157_infernal_records(manifest: dict, lock: dict, read_jar) -> None:
    """The v1.1.0 Infernal JAR must be the exact released, SHA-256 pinned bytes."""
    if manifest.get("bundle_revision") != 157:
        return
    if (lock.get("schema") != 1 or lock.get("bundle_revision") != 157
            or lock.get("repository") != "wickidcow/SF_InfernalExpansion"
            or lock.get("tag") != "v1.1.0"
            or lock.get("asset") != "SF_InfernalExpansion1.1.0.jar"
            or lock.get("source_commit") != "06e32ea6d551c9961a50c993602d4e84cf8edcbe"
            or lock.get("sha256") != "02f3f315e6b8cce4bd7411876faac8150b9c2c0524a9669df4b0296f8ab619a2"
            or lock.get("bytes") != 42236):
        raise ValueError("Unexpected official Infernal Expansion release lock")
    records = [r for r in manifest.get("addons", [])
               if r.get("repository") == lock["repository"]]
    if len(records) != 1:
        raise ValueError("Exactly one Infernal Expansion record is required")
    rec = records[0]
    receipt = {
        "tag": lock["tag"],
        "source_commit": lock["source_commit"],
        "url": f"https://github.com/{lock['repository']}/releases/download/{lock['tag']}/{lock['asset']}",
        "sha256": lock["sha256"],
        "bytes": lock["bytes"],
        "verified_download": True,
    }
    if (rec.get("commit") != lock["source_commit"]
            or rec.get("jar") != lock["asset"]
            or rec.get("version") != "1.1.0"
            or rec.get("distributable_api") != "1.21.11-R0.1-SNAPSHOT"
            or rec.get("secondary_platforms") != []
            or rec.get("published_artifact") != receipt
            or rec.get("sha256") != lock["sha256"]
            or not re.fullmatch(r"[0-9a-f]{64}", str(rec.get("source_build_sha256", "")))):
        raise ValueError("Infernal Expansion release receipt or distributable target mismatch")
    jar = read_jar(lock["asset"])
    if len(jar) != lock["bytes"] or hashlib.sha256(jar).hexdigest() != lock["sha256"]:
        raise ValueError("Infernal Expansion JAR differs from officially published release")
    with zipfile.ZipFile(io.BytesIO(jar)) as archive:
        if archive.testzip() is not None or "plugin.yml" not in archive.namelist():
            raise ValueError("Infernal Expansion official JAR is corrupt")
        descriptor = archive.read("plugin.yml").decode("utf-8")
        clazz = archive.read("ga/kirzu/infernalexpansion/InfernalExpansion.class")
    if clazz[:4] != bytes.fromhex("cafebabe") or int.from_bytes(clazz[6:8], "big") != 65:
        raise ValueError("Infernal Expansion must distribute Java 21 bytecode")
    if not all(
        re.search(rf"(?mi)^\\s*{re.escape(key)}:\\s*['\\\"]?{re.escape(value)}['\\\"]?\\s*$", descriptor)
        for key, value in (("name", "InfernalExpansion"), ("version", "1.1.0"), ("api-version", "1.21.11"))
    ):
        raise ValueError("Infernal Expansion official plugin descriptor mismatch")


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
        if manifest.get("bundle_revision", 0) >= 156:
            lock_file = Path(__file__).resolve().parents[1] / "compatibility/r156-official-addon-releases.json"
            verify_r156_official_records(manifest, json.loads(lock_file.read_text(encoding="utf-8")), archive.read)
        if manifest.get("bundle_revision") == 157:
            infernal_lock = Path(__file__).resolve().parents[1] / "compatibility/r157-infernal-expansion-release.json"
            verify_r157_infernal_records(manifest, json.loads(infernal_lock.read_text(encoding="utf-8")), archive.read)
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
