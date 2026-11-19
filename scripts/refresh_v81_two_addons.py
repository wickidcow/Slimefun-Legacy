#!/usr/bin/env python3
"""Fail-closed in-place v4.1.81 addon ZIP refresh; do not touch the core JAR or 42 other addons."""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

ORIGINAL_SHA256 = "dbb3ae33479716bd836dfbd28e5e7eb73be14471e94e99f274946e0fb129189e"
CORE_COMMIT = "d728bf04ebd738dd5779e27b2ea83c68f0edbd10"

# (repository, old file, old version, old source, old digest,
#  new file, new version, new source, new digest, new length, plugin.yml name)
REPLACEMENTS = [
    (
        "wickidcow/SF_FluffyMachines",
        "SF_FluffyMachines26.2.21.jar", "26.2.21", "365c30bdd9fedd0bb9989c13829341c501150a5a",
        "d7c2cd27c5d93a70cd32d546110103c706765a5a11af5e924bbc740bb88a5d7c",
        "SF_FluffyMachines26.2.22.jar", "26.2.22", "274275331f0d3087d96998aa4576ef1cc2b3a1ff",
        "ae3eafe860523ec02b0ba435a790aa38c2dc509352eaf2cf5f7890507ec9e54e",
        389763, "FluffyMachines",
    ),
    (
        "wickidcow/SF_InfinityExpansion2",
        "SF_InfinityExpansion2.0.19.jar", "2.0.19", "1cc5d9962ea7d11e282240ef29a741e681a1fbd4",
        "4652fc9c6d9200f46ddbe5ad6ce56d524112c6dc8a16f2da163eb7308823afbd",
        "SF_InfinityExpansion2.0.21.jar", "2.0.21", "94e64236312a8c0b3fb18812143c6a02a806405b",
        "7c307269b0e3afe9d32a0c0a5f7f742d8f50d4f55a31b0dfd3befdd55debb633",
        1795834, "InfinityExpansion2",
    ),
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_new_jar(data: bytes, name: str, version: str) -> None:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        require(archive.testzip() is None, f"Damaged new JAR: {name}")
        members = archive.namelist()
        require(len(members) == len(set(members)), f"Duplicate member in {name}")
        descriptor = archive.read("plugin.yml").decode("utf-8")
        for field, expected in (("name", name), ("version", version)):
            found = re.search(rf"(?mi)^\s*{field}:\s*['\"]?([^'\"\s#]+)", descriptor)
            require(found is not None and found.group(1) == expected,
                    f"Wrong {field} in new {name}: {found.group(1) if found else None}")
        require(re.search(r"(?mi)^\s*api-version:\s*['\"]?1\.21(?:\.11)?['\"]?\s*$", descriptor) is not None,
                f"New {name} unexpectedly raises its API floor")
        for path in members:
            if not path.endswith(".class"):
                continue
            header = archive.read(path)[:8]
            require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe"
                    and int.from_bytes(header[6:8], "big") <= 65,
                    f"Unsupported Java bytecode: {name}/{path}")


def repack(original: Path, new_fluffy: Path, new_ie2: Path, output: Path) -> dict:
    original_bytes = original.read_bytes()
    require(digest(original_bytes) == ORIGINAL_SHA256, "Existing v81 ZIP has unexpected bytes; refuse overwrite")
    require(output != original and not output.exists(), "Refuse in-place local archive modification")
    replacements = {REPLACEMENTS[0][0]: new_fluffy.read_bytes(), REPLACEMENTS[1][0]: new_ie2.read_bytes()}
    with zipfile.ZipFile(io.BytesIO(original_bytes)) as src:
        infos = src.infolist()
        names = src.namelist()
        require(len(names) == len(set(names)), "Duplicate members in original ZIP")
        require(src.testzip() is None, "Original ZIP corrupt")
        require(all(x and not x.startswith("/") and ".." not in x.split("/") and "\\" not in x
                    for x in names), "Unsafe archive path")
        manifest = json.loads(src.read("SF_ADDON_MANIFEST.json"))
        require(manifest.get("bundle_revision") == 156, "Unexpected source bundle revision")
        require(manifest.get("core_source_commit") == CORE_COMMIT, "Unexpected v81 core provenance")
        require(len(manifest.get("addons", [])) == 44, "Expected exactly 44 original addons")
        require("archive_refresh" not in manifest, "Archive already refreshed")
        rows = manifest["addons"]
        by_repo = {row["repository"]: row for row in rows}
        require(len(by_repo) == len(rows), "Duplicate repository in original manifest")
        original_jars = {x for x in names if x.endswith(".jar")}
        require(original_jars == {r["jar"] for r in rows}, "Original JAR names differ from manifest")
        for row in rows:
            require(digest(src.read(row["jar"])) == row["sha256"],
                    f"Original JAR/manifest checksum mismatch: {row['jar']}")
        checksum_lines = sorted(f'{r["sha256"]}  {r["jar"]}' for r in rows)
        require(sorted(src.read("SHA256SUMS.txt").decode().splitlines()) == checksum_lines,
                "Original SHA256SUMS.txt is not consistent with the archived JARs")
        replacement_by_old = {}
        record_summary = []
        for (repo, old_file, old_version, old_source, old_sha, new_file, new_version,
             new_source, new_sha, new_size, plugin), data in zip(REPLACEMENTS, replacements.values()):
            row = by_repo.get(repo)
            require(row is not None and row.get("jar") == old_file and row.get("version") == old_version
                    and row.get("commit") == old_source and row.get("sha256") == old_sha,
                    f"Unexpected original version or provenance for {repo}: "
                    f"jar={row.get('jar')!r}, version={row.get('version')!r}, "
                    f"commit={row.get('commit')!r}, sha256={row.get('sha256')!r}")
            require(len(data) == new_size and digest(data) == new_sha,
                    f"New official JAR size or SHA-256 mismatch: {repo}")
            require(new_file not in names, f"New addon file already exists: {new_file}")
            validate_new_jar(data, plugin, new_version)
            row.update({"jar": new_file, "source_jar": new_file, "version": new_version,
                        "commit": new_source, "sha256": new_sha})
            # Do not carry an old source-build receipt over to newly verified official bytes.
            row.pop("source_build_sha256", None)
            row.pop("unpublished_pr_preview", None)
            url = f"https://github.com/{repo}/releases/download/v{new_version}/{new_file}"
            row["published_artifact"] = {
                "repository": repo, "source": new_source, "url": url, "bytes": new_size,
                "sha256": new_sha,
            }
            replacement_by_old[old_file] = (new_file, data)
            record_summary.append({"repository": repo, "previous": old_file, "current": new_file,
                                   "published_sha256": new_sha})
        manifest["archive_refresh"] = {
            "release": "v4.1.81", "method": "two-official-addon-in-place-replacement",
            "original_zip_sha256": ORIGINAL_SHA256,
            "core_source_commit_unchanged": True,
            "addons": record_summary,
        }
        new_checksum_lines = sorted(f'{row["sha256"]}  {row["jar"]}' for row in rows)
        metadata = {
            "SF_ADDON_MANIFEST.json": (json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8"),
            "SHA256SUMS.txt": ("\n".join(new_checksum_lines) + "\n").encode("utf-8"),
        }
        with zipfile.ZipFile(output, "w") as dst:
            for info in infos:
                if info.filename in replacement_by_old:
                    continue
                dst.writestr(info, metadata.get(info.filename, src.read(info.filename)))
            for new_file, data in replacement_by_old.values():
                dst.writestr(new_file, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(original) as prior, zipfile.ZipFile(output) as current:
        require(current.testzip() is None, "Updated ZIP failed CRC validation")
        actual = current.namelist()
        require(len(actual) == len(set(actual)), "Duplicate members in updated ZIP")
        require(len([n for n in actual if n.endswith(".jar")]) == 44, "Changed addon count")
        require({x for x in actual if x.endswith(".jar")} ==
                {r["jar"] for r in manifest["addons"]}, "Updated JAR names differ from manifest")
        changed = set(replacement_by_old) | set(metadata)
        for name in prior.namelist():
            if name in changed:
                continue
            require(current.read(name) == prior.read(name), f"Unrelated archive member changed: {name}")
        for old in replacement_by_old:
            require(old not in actual, f"Old addon JAR remains: {old}")
        for new_file, data in replacement_by_old.values():
            require(current.read(new_file) == data, f"New addon JAR changed after repack: {new_file}")
    return {"output": str(output), "sha256": digest(output.read_bytes()),
            "addon_count": 44, "replaced": record_summary}


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit("Usage: refresh_v81_two_addons.py <original-v81.zip> <new-fluffy.jar> <new-ie2.jar> <output.zip>")
    try:
        print(json.dumps(repack(*(Path(p) for p in sys.argv[1:])), indent=2))
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as exc:
        sys.exit(f"Refusing v81 addon refresh: {exc}")
