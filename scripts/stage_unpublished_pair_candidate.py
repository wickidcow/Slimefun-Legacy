#!/usr/bin/env python3
"""Verify an unpublished Networks/IE2 PR artifact without weakening published-pair locks.

This helper does not copy or publish anything. It only makes a CI-only preview
receipt for a JAR built from an exact manifest-pinned source. Published release
bytes continue through stage_published_pair.py and the immutable fixture lock.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from scripts.runtime.paired_addon_storage.run_probe import load_lock, require, validate_jar

PAIR_SLUGS = {"networksexp": "networks", "infinityexpansion2": "ie2"}


def preview_mode(
    manifest: dict,
    lock: dict,
    slug: str,
    source: str,
    repository: str,
    event: str,
    jar: Path | None = None,
) -> tuple[str, dict | None]:
    require(slug in PAIR_SLUGS, "Unrecognized storage-pair addon")
    pin = lock["addons"][PAIR_SLUGS[slug]]
    require(repository == pin["repository"], "Candidate repository differs from reviewed pair")
    require(re.fullmatch(r"[0-9a-f]{40}", source) is not None, "Exact source SHA is required")
    matching = [row for row in manifest["addons"] if row.get("repository") == repository
                and row.get("slug") == slug]
    require(len(matching) == 1 and matching[0].get("source_commit") == source,
            "Candidate source differs from the exact addon manifest")

    if source == pin["source"]:
        return "published", None

    # Only a PR may preview as-yet-unpublished source. Master, schedule and
    # manually dispatched canonical bundles must still fail closed rather than
    # silently replacing a release asset with a different compile output.
    require(event == "pull_request",
            "Unpublished pair source cannot enter a master or release bundle")
    require(jar is not None and jar.is_file() and not jar.is_symlink(),
            "Candidate plugin JAR must be a regular, nonsymlinked file")

    with zipfile.ZipFile(jar) as archive:
        descriptor = archive.read("plugin.yml").decode("utf-8")
    name = re.search(r"^name:\s*['\"]?([^'\"\s]+)['\"]?\s*$", descriptor, re.MULTILINE)
    version = re.search(r"^version:\s*['\"]?([^'\"\s]+)['\"]?\s*$", descriptor, re.MULTILINE)
    require(name is not None and name.group(1) == pin["plugin"], "Candidate plugin identity mismatch")
    require(version is not None and re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", version.group(1)) is not None,
            "Candidate version must be numeric")
    selected_version = version.group(1)
    require(tuple(map(int, selected_version.split("."))) >
            tuple(map(int, pin["version"].split("."))),
            "Unpublished candidate must have a newer version than the reviewed artifact")

    # validate_jar checks entry/path integrity and Java <=21 bytecode; the
    # descriptor checks below preserve the same minimum client and Folia policy
    # as the immutable release source verifier.
    details = validate_jar(jar, pin["plugin"], selected_version)
    require(re.search(r"^api-version:\s*['\"]?1\.21(?:\.11)?['\"]?\s*$",
                      descriptor, re.MULTILINE) is not None,
            "Candidate JAR does not retain the supported API floor")
    # Any YAML Folia declaration (including quoted keys, booleans and aliases) is
    # outside this pre-release compatibility review. Do not accidentally
    # accept a supported=true claim just because it is not a bare "true".
    require(re.search(r"^[ \t]*['\"]?folia-supported['\"]?[ \t]*:", descriptor,
                      re.MULTILINE | re.IGNORECASE) is None,
            "Unaccepted Folia compatibility declaration")

    return "unpublished-pr-preview", {
        "schema": 1,
        "mode": "unpublished-pr-preview",
        "eligible_for_release": False,
        "source": source,
        "repository": repository,
        "event": event,
        **details,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--addon", choices=tuple(PAIR_SLUGS), required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--event", required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    mode, proof = preview_mode(
        manifest, load_lock(), args.addon, args.source,
        args.repository, args.event, args.jar,
    )
    if mode == "unpublished-pr-preview":
        require(not args.report.exists() and not args.report.is_symlink(),
                "Refusing existing candidate receipt")
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(mode)


if __name__ == "__main__":
    main()
