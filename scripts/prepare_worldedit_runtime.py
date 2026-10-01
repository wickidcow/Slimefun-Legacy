#!/usr/bin/env python3
"""Stage a verified, version-matched WorldEdit runtime for disposable full-stack tests.

This is CI dependency setup, not a server updater or a release publisher. An absent
or unverified dependency is a failed check, never a reason to exclude SFWorldEdit.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import Request, urlopen
import zipfile

API = "https://api.modrinth.com/v2"
USER_AGENT = "Slimefun-Legacy-WorldEdit-Validation/1.0"
MAX_DOWNLOAD = 64 * 1024 * 1024
MAX_EXPANDED = 256 * 1024 * 1024
BUKKIT_LOADERS = frozenset(("bukkit", "paper", "spigot", "folia"))
SOURCE_REPOSITORY = "https://github.com/EngineHub/WorldEdit"
MAIN_CLASS = "com.sk89q.worldedit.bukkit.WorldEditPlugin"
MAX_CANDIDATES = 12


class BytecodeCompatibilityError(ValueError):
    """A verified artifact is not eligible for the selected Java runtime."""


def object_json(data: bytes, label: str) -> object:
    def unique_keys(pairs: list[tuple[str, object]]) -> dict:
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key in {label}: {key}")
            result[key] = value
        return result
    return json.loads(data.decode("utf-8"), object_pairs_hook=unique_keys)


def request_bytes(url: str, limit: int, *, allowed_host: str) -> bytes:
    parts = urlsplit(url)
    if parts.scheme != "https" or parts.hostname != allowed_host or parts.port not in (None, 443):
        raise ValueError("Unexpected download host or transport")
    if parts.username is not None or parts.password is not None or parts.fragment:
        raise ValueError("Unexpected URL credentials or fragment")
    with urlopen(Request(url, headers={"User-Agent": USER_AGENT}), timeout=60) as response:
        final = urlsplit(response.geturl())
        if final.scheme != "https" or final.hostname != allowed_host or final.port not in (None, 443):
            raise ValueError("Dependency redirect left the approved host")
        payload = response.read(limit + 1)
        if len(payload) > limit:
            raise ValueError("Dependency response exceeds the download bound")
        return payload


def validate_project(project: object) -> str:
    if not isinstance(project, dict) or project.get("slug") != "worldedit":
        raise ValueError("Expected the official WorldEdit project")
    source = project.get("source_url")
    if not isinstance(source, str) or source.rstrip("/").casefold() != SOURCE_REPOSITORY.casefold():
        raise ValueError("WorldEdit metadata no longer points to the reviewed source repository")
    identifier = project.get("id")
    if not isinstance(identifier, str) or not re.fullmatch(r"[A-Za-z0-9]+", identifier):
        raise ValueError("Invalid WorldEdit project identity")
    return identifier


def select_version(versions: object, project_id: str, minecraft: str,
                   allow_prerelease: bool = False, version_id: str | None = None) -> dict:
    if not isinstance(versions, list):
        raise ValueError("Expected a version list")
    eligible = []
    seen = set()
    for version in versions:
        if not isinstance(version, dict):
            raise ValueError("Malformed version entry")
        identifier = version.get("id")
        if not isinstance(identifier, str) or not re.fullmatch(r"[A-Za-z0-9]+", identifier):
            raise ValueError("Invalid version identity")
        if identifier in seen:
            raise ValueError("Duplicate version identity")
        seen.add(identifier)
        if version.get("project_id") != project_id:
            raise ValueError("Version belongs to a different project")
        loaders, games = version.get("loaders"), version.get("game_versions")
        if not isinstance(loaders, list) or not all(isinstance(x, str) for x in loaders):
            raise ValueError("Invalid loader list")
        if not isinstance(games, list) or not all(isinstance(x, str) for x in games):
            raise ValueError("Invalid Minecraft version list")
        channel = version.get("version_type")
        if channel not in ("release", "beta", "alpha"):
            raise ValueError("Unrecognized WorldEdit release channel")
        if minecraft not in games or not BUKKIT_LOADERS.intersection(loaders):
            continue
        if channel != "release" and not allow_prerelease:
            continue
        if version_id is not None and identifier != version_id:
            continue
        stamp = version.get("date_published")
        if not isinstance(stamp, str):
            raise ValueError("Missing publication timestamp")
        when = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
        if when.tzinfo is None:
            raise ValueError("Publication timestamp must contain an offset")
        eligible.append((channel == "release", when, identifier, version))
    if not eligible:
        raise ValueError(f"No allowed Bukkit WorldEdit build explicitly supports Minecraft {minecraft}")
    # Prefer a matching stable build; use a beta only after explicit opt-in.
    return max(eligible, key=lambda row: row[:3])[3]


def select_file(version: dict) -> dict:
    files = version.get("files")
    if not isinstance(files, list) or not files:
        raise ValueError("Version has no files")
    primary = [f for f in files if isinstance(f, dict) and f.get("primary") is True]
    if len(primary) != 1:
        raise ValueError("Expected exactly one primary WorldEdit artifact")
    file = primary[0]
    name = file.get("filename")
    if not isinstance(name, str) or not re.fullmatch(r"worldedit-bukkit-[A-Za-z0-9_.+\-]+\.jar", name):
        raise ValueError("Primary artifact is not a Bukkit WorldEdit JAR")
    if "sources" in name.casefold() or "javadoc" in name.casefold():
        raise ValueError("Source or Javadoc artifact is not a runtime plugin")
    size = file.get("size")
    if type(size) is not int or not 1 <= size <= MAX_DOWNLOAD:
        raise ValueError("Invalid artifact size")
    hashes = file.get("hashes")
    digest = hashes.get("sha512") if isinstance(hashes, dict) else None
    if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-fA-F]{128}", digest):
        raise ValueError("A complete SHA-512 artifact digest is required")
    url = file.get("url")
    if not isinstance(url, str):
        raise ValueError("Missing artifact download URL")
    parts = urlsplit(url)
    if (parts.scheme != "https" or parts.hostname != "cdn.modrinth.com"
            or parts.port not in (None, 443) or parts.username is not None
            or parts.password is not None or parts.fragment):
        raise ValueError("WorldEdit download must use the official Modrinth CDN")
    return file


def scalar(descriptor: str, name: str) -> str:
    # Only known, top-level single-line WorldEdit fields are accepted. Ambiguous
    # YAML is refused, not guessed or treated as an enabled dependency.
    matches = re.findall(r"(?m)^" + re.escape(name) + r"\s*:\s*([^\r\n]+)$", descriptor)
    if len(matches) != 1:
        raise ValueError(f"Expected one top-level plugin descriptor {name}")
    text = matches[0].strip()
    if text.startswith(("'", '"')):
        if len(text) < 2 or text[-1] != text[0]:
            raise ValueError("Unsupported quoted plugin descriptor")
        text = text[1:-1]
    elif "#" in text:
        text = text.partition("#")[0].rstrip()
    if not text or any(ord(ch) < 32 for ch in text):
        raise ValueError("Empty or invalid descriptor scalar")
    return text


def validate_jar(payload: bytes, file: dict, java_version: int) -> dict:
    if len(payload) != file["size"]:
        raise ValueError("Downloaded WorldEdit size differs from published metadata")
    if hashlib.sha512(payload).hexdigest() != file["hashes"]["sha512"].lower():
        raise ValueError("Downloaded WorldEdit checksum mismatch")
    with zipfile.ZipFile(io.BytesIO(payload)) as jar:
        entries = jar.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)) or len(names) > 100_000:
            raise ValueError("Duplicate or excessive JAR entries")
        if sum(entry.file_size for entry in entries) > MAX_EXPANDED:
            raise ValueError("Uncompressed WorldEdit JAR exceeds the inspection bound")
        for name in names:
            if name.startswith(("/", "\\")) or ".." in name.replace("\\", "/").split("/"):
                raise ValueError("Unsafe path in WorldEdit JAR")
        if jar.testzip() is not None:
            raise ValueError("WorldEdit JAR CRC failure")
        main_entry = MAIN_CLASS.replace(".", "/") + ".class"
        if "plugin.yml" not in names or main_entry not in names:
            raise ValueError("Missing Bukkit descriptor or WorldEdit plugin class")
        descriptor = jar.read("plugin.yml").decode("utf-8")
        if scalar(descriptor, "name") != "WorldEdit" or scalar(descriptor, "main") != MAIN_CLASS:
            raise ValueError("Unexpected Bukkit plugin identity")
        max_major, count = 0, 0
        for name in names:
            if not name.endswith(".class"):
                continue
            overlay = re.match(r"META-INF/versions/(\d+)/", name)
            if name.startswith("META-INF/versions/") and overlay is None:
                raise ValueError("Malformed multi-release class entry")
            if overlay is not None and int(overlay[1]) > java_version:
                continue
            header = jar.read(name)[:8]
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                raise ValueError("Malformed class header")
            if int.from_bytes(header[4:6], "big") == 65535:
                raise ValueError("Preview bytecode cannot be used in the ordinary runtime")
            major = int.from_bytes(header[6:8], "big")
            if major < 45:
                raise ValueError("Unsupported class-file format")
            if major > java_version + 44:
                raise BytecodeCompatibilityError(
                    f"WorldEdit class {name} requires Java {major - 44}, beyond Java {java_version}")
            max_major = max(max_major, major)
            count += 1
    return {"name": "WorldEdit", "descriptor_version": scalar(descriptor, "version"),
            "sha256": hashlib.sha256(payload).hexdigest(), "sha512": hashlib.sha512(payload).hexdigest(),
            "size": len(payload), "applicable_classes": count, "max_class_major": max_major}



def select_compatible_artifact(versions: object, project_id: str, minecraft: str,
                               allow_prerelease: bool = False, version_id: str | None = None) -> tuple:
    # Published Minecraft support alone does not establish Java-21 eligibility.
    # Never fall back after a checksum, transport, identity or archive failure.
    remaining = versions
    rejected = []
    for _ in range(MAX_CANDIDATES):
        version = select_version(remaining, project_id, minecraft, allow_prerelease, version_id)
        file = select_file(version)
        payload = request_bytes(file["url"], MAX_DOWNLOAD, allowed_host="cdn.modrinth.com")
        try:
            report = validate_jar(payload, file, 21 if minecraft == "1.21.11" else 25)
        except BytecodeCompatibilityError as failure:
            if version_id is not None:
                raise  # An explicit pin never silently selects another version.
            rejected.append({"version_id": version["id"], "version_number": version.get("version_number"),
                             "reason": str(failure)})
            remaining = [row for row in remaining if row["id"] != version["id"]]
            continue
        report["bytecode_rejected_candidates"] = rejected
        return version, file, payload, report
    raise ValueError(f"No verified WorldEdit artifact fits this Java lane within {MAX_CANDIDATES} candidates")


def stage(output: Path, minecraft: str, allow_prerelease: bool = False,
          version_id: str | None = None) -> dict:
    if minecraft not in ("1.21.11", "26.2", "26.3"):
        raise ValueError("Unknown runtime lane; explicitly review new versions")
    if version_id is not None and not re.fullmatch(r"[A-Za-z0-9]+", version_id):
        raise ValueError("Invalid requested version ID")
    output.mkdir(parents=True, exist_ok=True)
    target = output / "WorldEdit-runtime.jar"
    evidence = output / "worldedit-runtime.json"
    if target.exists() or evidence.exists():
        raise ValueError("Refusing to replace pre-existing dependency/evidence files")
    project = object_json(request_bytes(f"{API}/project/worldedit", 1024 * 1024,
                                        allowed_host="api.modrinth.com"), "project")
    project_id = validate_project(project)
    query = urlencode({"game_versions": json.dumps([minecraft])})
    versions = object_json(request_bytes(f"{API}/project/{quote(project_id)}/version?{query}",
                                         4 * 1024 * 1024, allowed_host="api.modrinth.com"), "versions")
    version, file, payload, report = select_compatible_artifact(
        versions, project_id, minecraft, allow_prerelease, version_id)
    report.update({"minecraft": minecraft, "project_id": project_id, "version_id": version["id"],
                   "version_number": version.get("version_number"), "channel": version["version_type"],
                   "published": version["date_published"], "url": file["url"],
                   "game_versions": version["game_versions"], "loaders": version["loaders"],
                   "filename": file["filename"], "prerelease_allowed": allow_prerelease,
                   "scope": "dependency verified/staged; runtime activation must still be tested"})
    # No destination is made visible until all metadata, hash and JAR checks pass.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=output, suffix=".part", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
        temporary = None
        evidence.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except BaseException:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
        target.unlink(missing_ok=True)
        evidence.unlink(missing_ok=True)
        raise
    return report


def verify_staged(output: Path, minecraft: str, allow_prerelease: bool = False) -> dict:
    """Revalidate the exact staged dependency before eligibility and after each boot."""
    target = output / "WorldEdit-runtime.jar"
    evidence = output / "worldedit-runtime.json"
    if target.is_symlink() or evidence.is_symlink():
        raise ValueError("Dependency/evidence must not be symbolic links")
    if not target.is_file() or not evidence.is_file():
        raise ValueError("Verified WorldEdit dependency or provenance is missing")
    if evidence.stat().st_size > 1024 * 1024 or target.stat().st_size > MAX_DOWNLOAD:
        raise ValueError("Staged dependency/evidence exceeds its inspection bound")
    report = object_json(evidence.read_bytes(), "staged provenance")
    if not isinstance(report, dict) or report.get("minecraft") != minecraft:
        raise ValueError("WorldEdit evidence belongs to a different runtime lane")
    if report.get("channel") not in ("release", "beta", "alpha"):
        raise ValueError("Missing dependency release-channel evidence")
    if report["channel"] != "release" and not allow_prerelease:
        raise ValueError("Prerelease WorldEdit was not permitted for this runtime lane")
    if not isinstance(report.get("sha512"), str) or not re.fullmatch(r"[0-9a-f]{128}", report["sha512"]):
        raise ValueError("Invalid staged artifact digest")
    verified = validate_jar(target.read_bytes(), {
        "size": report.get("size"), "hashes": {"sha512": report.get("sha512", "")}
    }, 21 if minecraft == "1.21.11" else 25)
    for name, value in verified.items():
        if report.get(name) != value:
            raise ValueError(f"Staged WorldEdit does not match provenance field {name}")
    return report


def verify_boot_log(output: Path, minecraft: str, log: Path, allow_prerelease: bool = False) -> dict:
    """Require provider and addon enable evidence; the main smoke runner checks other failures."""
    report = verify_staged(output, minecraft, allow_prerelease)
    text = log.read_text(encoding="utf-8", errors="replace")
    provider = re.escape(report["descriptor_version"])
    if not re.search(r"Enabling WorldEdit v" + provider + r"(?=\s|$)", text):
        raise ValueError("The exact staged WorldEdit version did not enable in this boot")
    if not re.search(r"Enabling WorldEditSlimefun v[^\s]+", text):
        raise ValueError("WorldEditSlimefun was not enabled in this boot")
    if re.search(r"Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|"
                 r"AbstractMethodError|IncompatibleClassChangeError", text):
        raise ValueError("A runtime linkage/enable failure invalidates this boot")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft", required=True, choices=("1.21.11", "26.2", "26.3"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allow-prerelease", action="store_true")
    parser.add_argument("--version-id", help="Replay a previously recorded WorldEdit version ID")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check-staged", action="store_true")
    mode.add_argument("--verify-log", type=Path)
    args = parser.parse_args()
    try:
        if args.check_staged:
            report = verify_staged(args.output, args.minecraft, args.allow_prerelease)
        elif args.verify_log is not None:
            report = verify_boot_log(args.output, args.minecraft, args.verify_log, args.allow_prerelease)
        else:
            report = stage(args.output, args.minecraft, args.allow_prerelease, args.version_id)
        if args.version_id is not None and report.get("version_id") != args.version_id:
            raise ValueError("Staged dependency does not match the requested version ID")
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as failure:
        parser.exit(1, f"WorldEdit dependency validation failed: {failure}\n")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
