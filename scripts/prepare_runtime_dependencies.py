#!/usr/bin/env python3
"""Install hash-pinned test dependencies; never fall back to a different release."""
from __future__ import annotations

import hashlib
import io
import json
import re
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Callable

MAX_BYTES = 64 * 1024 * 1024
USER_AGENT = "Slimefun-Legacy-Compatibility/1.0 (github.com/wickidcow/Slimefun-Legacy)"


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("Dependency exceeds the reviewed download bound")
    return data


def prepare(lock: dict, minecraft: str, plugins: Path, report: Path,
            downloader: Callable[[str], bytes] = fetch) -> dict:
    if lock.get("schema") != 1 or not isinstance(lock.get("dependencies"), list) or not lock["dependencies"]:
        raise ValueError("Missing or unsupported dependency lock")
    staged = []
    names, files = set(), set()
    for spec in lock["dependencies"]:
        name, filename = spec["plugin_name"], spec["filename"]
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", name):
            raise ValueError("Invalid plugin identity")
        if Path(filename).name != filename or not re.fullmatch(r"[A-Za-z0-9_.-]+\.jar", filename):
            raise ValueError("Invalid dependency filename")
        if name.casefold() in names or filename.casefold() in files:
            raise ValueError("Duplicate dependency identity")
        names.add(name.casefold())
        files.add(filename.casefold())
        if minecraft not in spec["minecraft_versions"]:
            raise ValueError(f"{name} is not pinned for Minecraft {minecraft}")
        digest = spec["sha512"]
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{128}", digest):
            raise ValueError("Missing exact SHA-512")
        url = urllib.parse.urlsplit(spec["url"])
        if url.scheme != "https" or url.netloc != "cdn.modrinth.com" or url.query or url.fragment:
            raise ValueError("Unreviewed dependency URL")
        version_id = spec["version_id"]
        if not re.fullmatch(r"[A-Za-z0-9]+", version_id):
            raise ValueError("Invalid pinned version identity")
        if f"/versions/{version_id}/" not in url.path or not url.path.endswith("/" + filename):
            raise ValueError("Dependency URL is not the pinned version/file")
        data = downloader(spec["url"])
        if not data or len(data) > MAX_BYTES or hashlib.sha512(data).hexdigest() != digest:
            raise ValueError(f"Checksum or size mismatch: {name}")
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            if jar.testzip() is not None or jar.namelist().count("plugin.yml") != 1:
                raise ValueError(f"Invalid plugin archive: {name}")
            descriptor = jar.read("plugin.yml").decode("utf-8")
            match = re.search(r"(?m)^name:\s*['\"]?([A-Za-z0-9_.-]+)['\"]?\s*$", descriptor)
            if not match or match[1] != name:
                raise ValueError(f"Unexpected plugin identity: {name}")
        destination = plugins / filename
        if destination.exists() and destination.read_bytes() != data:
            raise ValueError(f"Refusing to replace a different dependency: {filename}")
        staged.append((spec, data, destination))

    # Validation precedes filesystem publication. A failed download never starts a server.
    plugins.mkdir(parents=True, exist_ok=True)
    result = {"minecraft": minecraft, "dependencies": []}
    for spec, data, destination in staged:
        destination.write_bytes(data)
        result["dependencies"].append({
            **spec, "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)
        })
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit("Usage: prepare_runtime_dependencies.py <lock.json> <minecraft> <plugins> <report.json>")
    lock = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    result = prepare(lock, sys.argv[2], Path(sys.argv[3]), Path(sys.argv[4]))
    for item in result["dependencies"]:
        print(f"Verified runtime dependency: {item['plugin_name']} {item['version']} {item['sha256']}")


if __name__ == "__main__":
    main()
