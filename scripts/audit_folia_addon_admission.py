#!/usr/bin/env python3
"""Audit canonical Slimefun addon JAR descriptors for Folia admission metadata.

This is an admission audit only. A declaration of folia-supported: true is not
runtime/gameplay certification, and a missing declaration is not proof of a bug.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

import yaml

DESCRIPTORS = ("paper-plugin.yml", "paper-plugin.yaml", "plugin.yml", "plugin.yaml")
COMMIT_RE = re.compile(r"[0-9a-f]{40}")
SHA256_RE = re.compile(r"[0-9a-f]{64}")
REPOSITORY_RE = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def unique_json_object(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate manifest field: {key}")
        result[key] = value
    return result


def declared_folia_support(raw: bytes, label: str) -> str | None:
    """Read an explicit top-level Boolean without interpreting nested keys or prose."""
    try:
        descriptor = yaml.compose(raw.decode("utf-8-sig"), Loader=yaml.SafeLoader)
    except (UnicodeError, yaml.YAMLError) as exc:
        raise ValueError(f"Invalid plugin descriptor {label}: {exc}") from exc
    if not isinstance(descriptor, yaml.MappingNode):
        raise ValueError(f"Plugin descriptor must be a YAML mapping: {label}")

    seen = set()
    declaration = None
    for key, value in descriptor.value:
        # Merged mappings are not an explicit opt-in, and accepting them would
        # make the audit depend on a server-specific YAML merge policy.
        if key.tag == "tag:yaml.org,2002:merge":
            raise ValueError(f"YAML merge keys are not supported in plugin descriptors: {label}")
        if not isinstance(key, yaml.ScalarNode) or key.tag != "tag:yaml.org,2002:str":
            raise ValueError(f"Plugin descriptor keys must be strings: {label}")
        if key.value in seen:
            raise ValueError(f"Duplicate descriptor key {key.value!r}: {label}")
        seen.add(key.value)
        if key.value == "folia-supported":
            declaration = value

    if declaration is None:
        return None
    if (not isinstance(declaration, yaml.ScalarNode)
            or declaration.tag != "tag:yaml.org,2002:bool"
            or declaration.value.lower() not in {"true", "false"}):
        raise ValueError(f"folia-supported must be the Boolean true or false: {label}")
    return declaration.value.lower()


def audit(bundle_path: Path, expected_source: str | None = None) -> dict:
    if expected_source is not None and not COMMIT_RE.fullmatch(expected_source):
        raise ValueError("Expected core source must be a full commit hash")
    raw = bundle_path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(raw)) as bundle:
        names = bundle.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Bundle contains duplicate ZIP entries")
        if bundle.testzip() is not None:
            raise ValueError("Bundle contains corrupt ZIP entries")
        if "SF_ADDON_MANIFEST.json" not in names:
            raise ValueError("Bundle is missing SF_ADDON_MANIFEST.json")

        manifest = json.loads(bundle.read("SF_ADDON_MANIFEST.json"), object_pairs_hook=unique_json_object)
        if not isinstance(manifest, dict):
            raise ValueError("Manifest must be a JSON object")
        source = manifest.get("core_source_commit")
        if not isinstance(source, str) or not COMMIT_RE.fullmatch(source):
            raise ValueError("Manifest is missing a full core source commit")
        if expected_source is not None and source != expected_source:
            raise ValueError("Candidate bundle was built from a different core checkout")
        rows = manifest.get("addons")
        if not isinstance(rows, list) or not rows or any(not isinstance(row, dict) for row in rows):
            raise ValueError("Manifest contains no addon records")

        manifest_jars = [row.get("jar") for row in rows]
        if any(not isinstance(name, str) or name != name.strip()
               or "/" in name or "\\" in name or not name.endswith(".jar") for name in manifest_jars):
            raise ValueError("Invalid addon JAR name in manifest")
        if len(manifest_jars) != len({name.casefold() for name in manifest_jars}):
            raise ValueError("Manifest contains duplicate addon JAR names")

        actual_jars = sorted(name for name in names if name.endswith(".jar"))
        if sorted(manifest_jars) != actual_jars:
            raise ValueError(
                f"Bundle/manifest JAR mismatch: manifest={sorted(manifest_jars)!r}, actual={actual_jars!r}"
            )

        results = []
        repositories = set()
        for row in rows:
            jar_name = row["jar"]
            repository = row.get("repository")
            commit = row.get("commit")
            expected_digest = row.get("sha256")
            if (not isinstance(repository, str) or not REPOSITORY_RE.fullmatch(repository)
                    or not isinstance(commit, str) or not COMMIT_RE.fullmatch(commit)):
                raise ValueError(f"Invalid addon manifest row: {row!r}")
            if repository.casefold() in repositories:
                raise ValueError(f"Manifest contains duplicate addon repository: {repository}")
            repositories.add(repository.casefold())
            if not isinstance(expected_digest, str) or not SHA256_RE.fullmatch(expected_digest):
                raise ValueError(f"Missing or invalid addon SHA-256: {jar_name}")

            jar_bytes = bundle.read(jar_name)
            digest = sha256(jar_bytes)
            if digest != expected_digest:
                raise ValueError(f"Addon checksum mismatch: {jar_name}")
            with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar:
                jar_names = jar.namelist()
                if len(jar_names) != len(set(jar_names)):
                    raise ValueError(f"Addon JAR contains duplicate ZIP entries: {jar_name}")
                if jar.testzip() is not None:
                    raise ValueError(f"Addon JAR contains corrupt ZIP entries: {jar_name}")
                descriptor_name = next((name for name in DESCRIPTORS if name in jar_names), None)
                if descriptor_name is None:
                    raise ValueError(f"No plugin descriptor in {jar_name}")
                declared = declared_folia_support(jar.read(descriptor_name), f"{jar_name}/{descriptor_name}")

            if declared == "true":
                admission = "declared-true"
            elif declared == "false":
                admission = "declared-false"
            else:
                admission = "undeclared"

            results.append(
                {
                    "repository": repository,
                    "commit": commit,
                    "jar": jar_name,
                    "jar_sha256": digest,
                    "descriptor": descriptor_name,
                    "folia_supported": declared,
                    "admission": admission,
                }
            )

    counts = {
        "declared_true": sum(row["admission"] == "declared-true" for row in results),
        "declared_false": sum(row["admission"] == "declared-false" for row in results),
        "undeclared": sum(row["admission"] == "undeclared" for row in results),
    }
    return {
        "schema": 1,
        "scope": "descriptor-admission-only",
        "bundle": bundle_path.name,
        "bundle_sha256": sha256(raw),
        "bundle_revision": manifest.get("bundle_revision"),
        "core_source_commit": manifest.get("core_source_commit"),
        "expected_core_source_commit": expected_source,
        "manifest_hashes_verified": True,
        "addon_count": len(results),
        "counts": counts,
        "addons": sorted(results, key=lambda row: row["repository"].casefold()),
        "limits": [
            "folia-supported true is not gameplay or concurrency certification",
            "undeclared is not proof of incompatibility",
            "this audit does not start a server or exercise region ownership",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--expect-addons", type=int, help="Require the exact canonical addon roster count")
    parser.add_argument("--expect-source", help="Require the exact checkout used to build the bundle")
    args = parser.parse_args()

    try:
        report = audit(args.bundle, args.expect_source)
    except Exception as exc:
        print(f"Folia addon admission audit failed: {exc}", file=sys.stderr)
        return 1

    if args.expect_addons is not None and report["addon_count"] != args.expect_addons:
        print(
            f"Expected {args.expect_addons} addons, found {report['addon_count']}",
            file=sys.stderr,
        )
        return 1

    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
