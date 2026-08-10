#!/usr/bin/env python3
"""Verify the built Slimefun Legacy JAR as a release artifact."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import zipfile
from pathlib import Path

OWNED_PREFIXES = (
    "city/norain/slimefun4/",
    "com/xzavier0722/mc/plugin/slimefun4/",
    "io/github/thebusybiscuit/slimefun4/",
    "me/mrCookieSlime/",
    "net/guizhanss/slimefun4/",
)
EXCLUDED_OWNED_PREFIXES = ("io/github/thebusybiscuit/slimefun4/libraries/",)

# These are server/plugin APIs that must remain external to the Slimefun release JAR.
FORBIDDEN_EXTERNAL_PREFIXES = {
    "Paper/Bukkit API": ("org/bukkit/", "io/papermc/paper/"),
    "WorldEdit": ("com/sk89q/worldedit/",),
    "mcMMO": ("com/gmail/nossr50/",),
    "PlaceholderAPI": ("me/clip/placeholderapi/",),
    "ClearLag": ("me/minebuilders/clearlag/",),
    "ItemsAdder": ("dev/lone/itemsadder/",),
    "Orebfuscator": ("net/imprex/orebfuscator/", "com/lishid/orebfuscator/"),
    "Vault": ("net/milkbowl/vault/",),
    "Authlib": ("com/mojang/authlib/",),
}

# Private implementation libraries are allowed only after Shadow relocation.
FORBIDDEN_UNRELOCATED_PREFIXES = {
    "Dough": "io/github/bakedlibs/dough/",
    "PaperLib": "io/papermc/lib/",
    "Unirest": "kong/unirest/",
    "Commons Lang": "org/apache/commons/lang/",
    "GuizhanLib": "net/guizhanss/guizhanlib/",
    "bStats": "org/bstats/",
}
RELOCATED_LIBRARY_PREFIX = "io/github/thebusybiscuit/slimefun4/libraries/"


def read(root: Path, relative: str) -> str:
    return (root / relative).read_text(encoding="utf-8")


def load_json(root: Path, relative: str) -> dict:
    return json.loads(read(root, relative))


def project_version(root: Path) -> str:
    match = re.search(r"^projectVersion=(\d+\.\d+\.\d+)$", read(root, "gradle.properties"), re.M)
    return match.group(1) if match else ""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def source_commit(root: Path) -> str:
    for variable in ("SOURCE_COMMIT", "GITHUB_SHA"):
        value = os.environ.get(variable, "").strip()
        if re.fullmatch(r"[0-9a-fA-F]{40}", value):
            return value.lower()
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
        )
        value = result.stdout.strip()
        if result.returncode == 0 and re.fullmatch(r"[0-9a-fA-F]{40}", value):
            return value.lower()
    except OSError:
        pass
    return "unknown"


def parse_properties(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" in line:
            key, value = line.split("=", 1)
        elif ":" in line:
            key, value = line.split(":", 1)
        else:
            continue
        values[key.strip()] = value.strip()
    return values


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--json-report", type=Path, default=Path("build/reports/release-artifact.json"))
    parser.add_argument("--summary", type=Path, default=Path("build/reports/release-artifact.md"))
    args = parser.parse_args()

    root = args.root.resolve()
    jar = args.jar.resolve()
    failures: list[str] = []

    version = project_version(root)
    support = load_json(root, "compatibility/support-contract.json")
    baselines = load_json(root, "compatibility/release-baselines.json")
    expected_java = int(support.get("java", {}).get("bytecode_target", 0))
    expected_major = expected_java + 44
    commit = source_commit(root)

    if not jar.is_file():
        failures.append(f"Release JAR does not exist: {jar}")

    class_count = 0
    max_class_major = 0
    plugin_version = ""
    embedded_commit = ""
    embedded_build_time = ""
    external_hits: dict[str, list[str]] = {}
    unrelocated_hits: dict[str, list[str]] = {}
    relocated_library_entries = 0

    if jar.is_file():
        try:
            with zipfile.ZipFile(jar) as archive:
                names = archive.namelist()
                name_set = set(names)
                if len(names) != len(name_set):
                    failures.append("Release JAR contains duplicate ZIP entries")

                if "plugin.yml" not in name_set:
                    failures.append("Release JAR is missing plugin.yml")
                else:
                    plugin_yml = archive.read("plugin.yml").decode("utf-8")
                    match = re.search(r"(?m)^version:\s*['\"]?([^'\"\s]+)", plugin_yml)
                    plugin_version = match.group(1) if match else ""
                    if "name: Slimefun" not in plugin_yml:
                        failures.append("Embedded plugin.yml does not identify Slimefun")
                    if plugin_version != version:
                        failures.append(f"Embedded plugin.yml version is {plugin_version or '<missing>'}, expected {version}")
                    if "main: io.github.thebusybiscuit.slimefun4.implementation.Slimefun" not in plugin_yml:
                        failures.append("Embedded plugin.yml main class changed")

                if "git.properties" not in name_set:
                    failures.append("Release JAR is missing git.properties")
                else:
                    git_properties = parse_properties(archive.read("git.properties").decode("utf-8"))
                    embedded_version = git_properties.get("git.build.version", "")
                    embedded_commit = git_properties.get("git.commit.id.full", "").lower()
                    embedded_build_time = git_properties.get("git.build.time", "")
                    if embedded_version != version:
                        failures.append(f"git.build.version is {embedded_version or '<missing>'}, expected {version}")
                    if commit != "unknown" and embedded_commit != commit:
                        failures.append(
                            f"git.commit.id.full is {embedded_commit or '<missing>'}, expected source commit {commit}"
                        )
                    if not embedded_build_time:
                        failures.append("git.build.time is missing")

                for name in sorted(name_set):
                    if name.startswith(RELOCATED_LIBRARY_PREFIX) and name.endswith(".class"):
                        relocated_library_entries += 1

                    if name.endswith(".class") and name.startswith(OWNED_PREFIXES) and not name.startswith(EXCLUDED_OWNED_PREFIXES):
                        header = archive.read(name)[:8]
                        if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                            failures.append(f"Invalid class header: {name}")
                            continue
                        major = struct.unpack(">H", header[6:8])[0]
                        class_count += 1
                        max_class_major = max(max_class_major, major)
                        if major > expected_major:
                            failures.append(
                                f"Slimefun-owned class exceeds Java {expected_java}: {name} uses class major {major}"
                            )

                    for label, prefixes in FORBIDDEN_EXTERNAL_PREFIXES.items():
                        if any(name.startswith(prefix) for prefix in prefixes):
                            external_hits.setdefault(label, []).append(name)
                    for label, prefix in FORBIDDEN_UNRELOCATED_PREFIXES.items():
                        if name.startswith(prefix):
                            unrelocated_hits.setdefault(label, []).append(name)

                if class_count == 0:
                    failures.append("No Slimefun-owned classes were found in the release JAR")
                if relocated_library_entries == 0:
                    failures.append("No relocated private-library classes were found in the release JAR")
        except (OSError, zipfile.BadZipFile, UnicodeDecodeError) as error:
            failures.append(f"Could not inspect release JAR: {error}")

    for label, hits in sorted(external_hits.items()):
        failures.append(f"External compile-only API was bundled ({label}): {hits[0]}")
    for label, hits in sorted(unrelocated_hits.items()):
        failures.append(f"Private library was bundled without relocation ({label}): {hits[0]}")

    if support.get("release") != version:
        failures.append("Support contract release does not match projectVersion")
    if support.get("phase") != "Core Platform Phase 1L":
        failures.append("Support contract phase is not Core Platform Phase 1L")
    if baselines.get("candidate", {}).get("version") != version:
        failures.append("Release baseline candidate does not match projectVersion")
    if baselines.get("previous_stable", {}).get("version") != "4.1.29":
        failures.append("4.1.30 release candidate must compare against previous stable 4.1.29")
    if baselines.get("previous_stable", {}).get("source", {}).get("ref") != "9794baffdd4a96f71fa18ae45ced8bab30982fb0":
        failures.append("Previous stable 4.1.29 baseline is not pinned to the validated release commit")

    policy = support.get("compatibility_policy", {})
    for key in (
        "reproducible_release_archives",
        "release_artifact_metadata_verification",
        "release_artifact_optional_api_exclusion",
        "release_candidate_double_build_hash_match",
        "release_source_commit_recorded",
    ):
        if policy.get(key) is not True:
            failures.append(f"Release artifact policy must remain true: {key}")

    report = {
        "schema": 1,
        "status": "FAIL" if failures else "PASS",
        "project": "Slimefun Legacy",
        "version": version,
        "phase": support.get("phase"),
        "jar": jar.name,
        "jar_size": jar.stat().st_size if jar.is_file() else 0,
        "jar_sha256": sha256(jar) if jar.is_file() else "",
        "source_commit": commit,
        "embedded_commit": embedded_commit,
        "embedded_build_time": embedded_build_time,
        "plugin_version": plugin_version,
        "java_bytecode_target": expected_java,
        "owned_class_count": class_count,
        "max_owned_class_major": max_class_major,
        "previous_stable": baselines.get("previous_stable", {}).get("version"),
        "previous_stable_ref": baselines.get("previous_stable", {}).get("source", {}).get("ref"),
        "relocated_library_class_count": relocated_library_entries,
        "failures": failures,
    }

    args.json_report.parent.mkdir(parents=True, exist_ok=True)
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.json_report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    summary_lines = [
        "# Slimefun Legacy release artifact verification",
        "",
        f"- Status: **{report['status']}**",
        f"- Version: `{version}`",
        f"- Phase: `{support.get('phase', '')}`",
        f"- Source commit: `{commit}`",
        f"- Embedded commit: `{embedded_commit or '<missing>'}`",
        f"- JAR: `{jar.name}`",
        f"- SHA-256: `{report['jar_sha256']}`",
        f"- Size: `{report['jar_size']}` bytes",
        f"- Slimefun-owned classes: `{class_count}`",
        f"- Java bytecode target: `{expected_java}`",
        f"- Previous stable baseline: `{report['previous_stable']}`",
    ]
    if failures:
        summary_lines.extend(["", "## Failures", ""])
        summary_lines.extend(f"- {failure}" for failure in failures)
    else:
        summary_lines.extend(
            [
                "",
                "The embedded plugin metadata, source identity, bytecode ceiling, dependency packaging boundary, and release baseline are aligned.",
            ]
        )
    args.summary.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

    if failures:
        print("Release artifact verification failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print(
        f"Release artifact verification passed: {jar.name} {report['jar_sha256']} "
        f"({class_count} Slimefun-owned classes, Java {expected_java})."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
