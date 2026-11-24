#!/usr/bin/env python3
"""Fail-closed, single-JAR update of the already refreshed v4.1.81 addons ZIP.

Only Networks 1.0.64 -> 1.0.65 may change. Both previously refreshed addon
JARs and the other 41 JARs must remain byte-for-byte identical.
"""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

ORIGINAL_ZIP_SHA256 = "274635576ce574e5e2fde0126795d0c0bfa678cae6e93ae5937b1f30b706e863"
FIRST_ZIP_SHA256 = "dbb3ae33479716bd836dfbd28e5e7eb73be14471e94e99f274946e0fb129189e"
CORE_COMMIT = "d728bf04ebd738dd5779e27b2ea83c68f0edbd10"
REPO = "wickidcow/SF_NetworksExp"
OLD_NAME = "SF_Networks1.0.64.jar"
OLD_VERSION = "1.0.64"
OLD_SOURCE = "1d46a1afd04ceae7f04c7e0849397350ce273ec5"
OLD_SHA256 = "8d5784711b3c7a1051a0200da520b5d5c752f3e65327a240cc5452542b446d48"
NEW_NAME = "SF_Networks1.0.65.jar"
NEW_VERSION = "1.0.65"
NEW_SOURCE = "8853a85c99acdb22c8d05d93f010272889922c9d"
NEW_SHA256 = "114bfa0d2bc5a12f65e320640691bfa075470ddc9ff021969ade85e92333277f"
NEW_BYTES = 1684870
NEW_URL = "https://github.com/wickidcow/SF_NetworksExp/releases/download/v1.0.65/SF_Networks1.0.65.jar"
PREVIOUS_TWO = {
    "wickidcow/SF_FluffyMachines": (
        "SF_FluffyMachines26.2.22.jar",
        "ae3eafe860523ec02b0ba435a790aa38c2dc509352eaf2cf5f7890507ec9e54e",
    ),
    "wickidcow/SF_InfinityExpansion2": (
        "SF_InfinityExpansion2.0.21.jar",
        "7c307269b0e3afe9d32a0c0a5f7f742d8f50d4f55a31b0dfd3befdd55debb633",
    ),
}


def require(value: bool, error: str) -> None:
    if not value:
        raise ValueError(error)


def digest(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def check_jar(data: bytes) -> None:
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        require(jar.testzip() is None, "New official Networks JAR failed CRC validation")
        members = jar.namelist()
        require(len(members) == len(set(members)), "Duplicate JAR members")
        descriptor = jar.read("plugin.yml").decode("utf-8")
        for field, expected in (("name", "Networks"), ("version", NEW_VERSION)):
            match = re.search(rf"(?mi)^\s*{field}:\s*['\"]?([^'\"\s#]+)", descriptor)
            require(match is not None and match.group(1) == expected,
                    f"New Networks plugin.yml {field} mismatch")
        require(re.search(r"(?mi)^\s*api-version:\s*['\"]?1\.21(?:\.11)?['\"]?\s*$", descriptor) is not None,
                "New Networks JAR unexpectedly raises its Minecraft API floor")
        for path in members:
            if path.endswith(".class"):
                header = jar.read(path)[:8]
                require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe"
                        and int.from_bytes(header[6:8], "big") <= 65,
                        f"Class bytecode exceeds Java 21: {path}")


def repack(original: Path, official_jar: Path, output: Path) -> dict:
    require(original.resolve() != output.resolve() and not output.exists(),
            "Refuse to alter or overwrite the source ZIP on disk")
    original_bytes = original.read_bytes()
    require(digest(original_bytes) == ORIGINAL_ZIP_SHA256,
            "Live v81 ZIP is not the exact IE2+Fluffy-refreshed archive; refuse update")
    new_jar = official_jar.read_bytes()
    require(len(new_jar) == NEW_BYTES and digest(new_jar) == NEW_SHA256,
            "Networks v1.0.65 JAR is not byte-identical to the published release")
    check_jar(new_jar)

    with zipfile.ZipFile(io.BytesIO(original_bytes)) as src:
        infos = src.infolist()
        names = src.namelist()
        require(len(names) == len(set(names)), "Existing ZIP contains duplicate entries")
        require(src.testzip() is None, "Original v81 ZIP CRC failure")
        require(all(n and not n.startswith("/") and ".." not in n.split("/") and "\\" not in n
                    for n in names), "Unsafe path in original ZIP")
        manifest = json.loads(src.read("SF_ADDON_MANIFEST.json"))
        require(manifest.get("bundle_revision") == 156, "Unexpected v81 bundle revision")
        require(manifest.get("core_source_commit") == CORE_COMMIT, "Wrong original v81 core")
        records = manifest.get("addons", [])
        require(isinstance(records, list) and len(records) == 44, "Expected 44 addons")
        by_repo = {r["repository"]: r for r in records}
        require(len(by_repo) == 44, "Duplicate addon repositories")
        require({r["jar"] for r in records} == {n for n in names if n.endswith(".jar")},
                "Original ZIP's JAR inventory is not manifest-consistent")
        for record in records:
            require(digest(src.read(record["jar"])) == record["sha256"],
                    f"Original JAR checksum does not match manifest: {record['jar']}")
        require(sorted(src.read("SHA256SUMS.txt").decode("utf-8").splitlines()) ==
                sorted(f'{r["sha256"]}  {r["jar"]}' for r in records),
                "Original SHA256SUMS.txt differs from the JAR manifest")
        refresh = manifest.get("archive_refresh")
        require(isinstance(refresh, dict) and
                refresh.get("method") == "two-official-addon-in-place-replacement" and
                refresh.get("original_zip_sha256") == FIRST_ZIP_SHA256 and
                not refresh.get("subsequent_updates"),
                "Previous IE2/Fluffy update history missing or already further updated")
        prior = {r["repository"]: r for r in refresh.get("addons", [])}
        for repository, (expected_name, expected_sha) in PREVIOUS_TWO.items():
            require(repository in by_repo and
                    by_repo[repository].get("jar") == expected_name and
                    by_repo[repository].get("sha256") == expected_sha and
                    prior.get(repository, {}).get("current") == expected_name and
                    prior.get(repository, {}).get("published_sha256") == expected_sha,
                    f"Published {repository} refresh does not match prior verified version")

        record = by_repo.get(REPO)
        require(isinstance(record, dict), "Networks repository missing from v81 ZIP")
        require((record.get("jar"), record.get("version"), record.get("commit"),
                 record.get("sha256")) == (OLD_NAME, OLD_VERSION, OLD_SOURCE, OLD_SHA256),
                "Existing Networks 1.0.64 fingerprint differs from official pinned version: "
                f"{record.get('jar')!r}, {record.get('version')!r}, "
                f"{record.get('commit')!r}, {record.get('sha256')!r}")
        require(NEW_NAME not in names and OLD_NAME in names, "Networks JAR inventory mismatch")
        record.update({
            "jar": NEW_NAME, "source_jar": NEW_NAME, "version": NEW_VERSION,
            "commit": NEW_SOURCE, "sha256": NEW_SHA256,
            "published_artifact": {
                "repository": REPO, "source": NEW_SOURCE,
                "url": NEW_URL, "bytes": NEW_BYTES, "sha256": NEW_SHA256,
            },
        })
        record.pop("source_build_sha256", None)
        record.pop("unpublished_pr_preview", None)
        refresh["subsequent_updates"] = [{
            "release": "v4.1.81",
            "method": "single-official-addon-in-place-replacement",
            "previous_zip_sha256": ORIGINAL_ZIP_SHA256,
            "repository": REPO,
            "previous": OLD_NAME,
            "current": NEW_NAME,
            "published_sha256": NEW_SHA256,
            "source_commit": NEW_SOURCE,
        }]
        replacements = {
            "SF_ADDON_MANIFEST.json": (
                json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"
            ).encode("utf-8"),
            "SHA256SUMS.txt": (
                "\n".join(sorted(f'{r["sha256"]}  {r["jar"]}' for r in records)) + "\n"
            ).encode("utf-8"),
        }
        with zipfile.ZipFile(output, "w") as dst:
            for info in infos:
                if info.filename == OLD_NAME:
                    continue
                dst.writestr(info, replacements.get(info.filename, src.read(info.filename)))
            dst.writestr(NEW_NAME, new_jar, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

    with zipfile.ZipFile(original) as src, zipfile.ZipFile(output) as updated:
        require(updated.testzip() is None, "Refreshed ZIP failed CRC validation")
        names = updated.namelist()
        require(len(names) == len(set(names)), "Refreshed ZIP duplicate member")
        require(len([n for n in names if n.endswith(".jar")]) == 44,
                "Updated ZIP no longer contains 44 addon JARs")
        require(OLD_NAME not in names and NEW_NAME in names, "Networks replacement incomplete")
        require(updated.read(NEW_NAME) == new_jar, "New official Networks JAR bytes mutated")
        for info in src.infolist():
            if info.filename in (OLD_NAME, "SF_ADDON_MANIFEST.json", "SHA256SUMS.txt"):
                continue
            require(updated.read(info.filename) == src.read(info.filename),
                    f"Unrelated v81 member changed: {info.filename}")
        new_manifest = json.loads(updated.read("SF_ADDON_MANIFEST.json"))
        require({r["jar"] for r in new_manifest["addons"]} ==
                {n for n in names if n.endswith(".jar")},
                "Updated JARs are inconsistent with updated manifest")

    return {"output": str(output), "sha256": digest(output.read_bytes()),
            "addon_count": 44, "replaced": {"repository": REPO, "from": OLD_NAME, "to": NEW_NAME,
                                          "sha256": NEW_SHA256},
            "untouched_addon_jars": 43}


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("Usage: refresh_v81_networks.py <existing-v81.zip> <official-networks-1.0.65.jar> <new-v81.zip>")
    try:
        print(json.dumps(repack(*(Path(x) for x in sys.argv[1:])), indent=2))
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as exc:
        sys.exit(f"Refusing v81 Networks refresh: {exc}")
