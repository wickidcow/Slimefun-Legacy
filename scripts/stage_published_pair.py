#!/usr/bin/env python3
"""Stage the released storage pair without distributing newer-API probe outputs."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from scripts.runtime.paired_addon_storage.run_probe import require, validate_jar

RELEASE_LOCK_PATH = ROOT / "scripts/published_storage_pair_lock.json"


def load_release_lock(path: Path = RELEASE_LOCK_PATH) -> dict:
    """Use the reviewed distributable lock, never the historical native test fixture."""
    data = json.loads(path.read_text(encoding="utf-8"))
    require(data.get("schema") == 1 and data.get("purpose") == "published-distribution"
            and data.get("distribution_selection") is True,
            "Invalid published distribution lock identity")
    addons = data.get("addons")
    require(isinstance(addons, dict) and set(addons) == {"networks", "ie2"},
            "Published distribution lock must contain exactly the reviewed storage pair")
    for key, pin in addons.items():
        require(isinstance(pin, dict) and re.fullmatch(r"[0-9a-f]{40}", pin.get("source", "")) is not None
                and re.fullmatch(r"[0-9a-f]{40}", pin.get("tree", "")) is not None,
                f"Invalid published distribution source identity: {key}")
        artifact = pin.get("published_artifact")
        require(isinstance(artifact, dict) and re.fullmatch(r"[0-9a-f]{64}", artifact.get("sha256", "")) is not None
                and type(artifact.get("bytes")) is int and artifact["bytes"] > 0,
                f"Invalid published distribution artifact identity: {key}")
    return data


def stage(pin: dict, source: str, repository: str, output: Path, local: Path | None = None) -> dict:
    require(source == pin["source"] and repository == pin["repository"],
            "Selected source differs from the reviewed published artifact")
    artifact = pin["published_artifact"]
    expected_url = (f'https://github.com/{repository}/releases/download/v{pin["version"]}/'
                    + Path(pin["jar"]).name)
    require(artifact["url"] == expected_url and re.fullmatch(r"[0-9a-f]{64}", artifact["sha256"])
            and type(artifact["bytes"]) is int and artifact["bytes"] > 0,
            "Invalid published artifact pin")
    require(output.absolute() == output.resolve() and not output.exists() and not output.is_symlink(),
            "Refusing an existing or symlinked published artifact destination")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=".published-", suffix=".jar", dir=output.parent,
                                     delete=False) as stream:
        temporary = Path(stream.name)
    try:
        if local is None:
            subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error",
                            "--retry", "2", "--retry-all-errors", "--max-time", "240",
                            artifact["url"], "--output", str(temporary)], check=True, timeout=750)
        else:
            require(local.is_file() and not local.is_symlink(), "Expected a regular local artifact")
            shutil.copyfile(local, temporary)
        details = validate_jar(temporary, pin["plugin"], pin["version"])
        require(all(details[key] == artifact[key] for key in ("bytes", "sha256")),
                "Published artifact size or SHA-256 mismatch")
        with zipfile.ZipFile(temporary) as archive:
            descriptor = archive.read("plugin.yml").decode("utf-8")
        require(re.search(r"^api-version:\s*['\"]?1\.21(?:\.11)?['\"]?\s*$",
                          descriptor, re.MULTILINE) is not None,
                "Published artifact does not retain the supported API floor")
        require(re.search(r"^folia-supported:\s*true\s*$", descriptor, re.MULTILINE | re.IGNORECASE)
                is None, "The published storage pair has no Folia runtime acceptance")
        # Exclusive creation also refuses a destination introduced during the download.
        with output.open("xb") as destination, temporary.open("rb") as content:
            shutil.copyfileobj(content, destination)
        require(validate_jar(output, pin["plugin"], pin["version"]) == details,
                "Staged artifact changed during the copy")
        return {"repository": repository, "source": source, "url": artifact["url"], **details}
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--addon", choices=("networks", "ie2"), required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = stage(load_release_lock()["addons"][args.addon], args.source, args.repository, args.output)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
