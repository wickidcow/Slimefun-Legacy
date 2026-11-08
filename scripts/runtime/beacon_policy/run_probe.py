#!/usr/bin/env python3
"""Run real Slimefun Legacy Beacon Plus policy tests in disposable Paper worlds.

ALWAYS_FIRST creates a real BlockStorage beacon and selects SINGLE.
The same world is restarted as ONLINE (zero players), OFF, ALWAYS_RESTART.
Each JVM must report an exact native Paper chunk-ticket and saved-owner result.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

PHASES = ("ALWAYS_FIRST", "ONLINE", "OFF", "ALWAYS_RESTART")
PASS = "BEACON_NATIVE_CHUNK_POLICY_PASS"
PLUGIN_YML = """name: BeaconTicketProbe
version: '1.0'
main: audit.BeaconTicketProbe
api-version: '1.21.11'
depend: [Slimefun]
"""


def download_paper(mc: str, dest: Path) -> str:
    headers = {"User-Agent": "Slimefun-Legacy-Native-Beacon-CI/1.0"}
    url = f"https://fill.papermc.io/v3/projects/paper/versions/{mc}/builds"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=45) as resp:
        builds = json.load(resp)
    if not isinstance(builds, list):
        raise RuntimeError(f"Unexpected Paper builds payload: {str(builds)[:350]}")
    stable = next((b for b in builds
                   if b.get("channel") == "STABLE"
                   and b.get("downloads", {}).get("server:default", {}).get("url")), None)
    if not stable:
        raise RuntimeError(f"Paper {mc}: no STABLE server build")
    uri = stable["downloads"]["server:default"]["url"]
    with urllib.request.urlopen(urllib.request.Request(uri, headers=headers), timeout=120) as response, \
            dest.open("wb") as out:
        shutil.copyfileobj(response, out)
    if dest.stat().st_size < 20_000_000:
        raise RuntimeError(f"Paper {mc} server download was unusually small")
    return str(stable["id"])


def compile_probe(root: Path, legacy: Path, paper_api: Path, extra_classpath: str):
    classes = root / "beacon-probe-classes"
    classes.mkdir(parents=True)
    source = Path("scripts/runtime/beacon_policy/BeaconTicketProbe.java")
    subprocess.run(["javac", "--release", "21", "-cp",
                    os.pathsep.join([str(legacy), str(paper_api)]) + os.pathsep + extra_classpath,
                    "-d", str(classes), str(source)], check=True, timeout=120)
    with zipfile.ZipFile(root / "plugins" / "BeaconTicketProbe.jar", "w",
                         zipfile.ZIP_DEFLATED) as jar:
        for item in classes.rglob("*.class"):
            jar.write(item, item.relative_to(classes).as_posix())
        jar.writestr("plugin.yml", PLUGIN_YML)


def set_mode(config: Path, new_mode: str):
    text = config.read_text(encoding="utf-8")
    matches = list(re.finditer(
        r"(?m)^[ \t]{4}chunk-loading-mode[ \t]*:[ \t]*(ONLINE|ALWAYS|OFF)[ \t]*(?:#.*)?$",
        text, flags=re.IGNORECASE,
    ))
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one Beacon Plus mode entry; found {len(matches)}")
    match = matches[0]
    text = text[:match.start()] + f"    chunk-loading-mode: {new_mode}" + text[match.end():]
    config.write_text(text, encoding="utf-8")


def run_phase(root: Path, phase: str, timeout: int = 330):
    log_file = root / f"{phase.lower()}.log"
    with log_file.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(
            ["java", "-Xms512M", "-Xmx2G", "-jar", "paper.jar", "--nogui"],
            cwd=root, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
            text=True,
        )
        started = time.monotonic()
        success = False
        failed = False
        try:
            while time.monotonic() - started < timeout:
                if process.poll() is not None:
                    break
                time.sleep(2)
                log = log_file.read_text(encoding="utf-8", errors="replace")
                if "BEACON_NATIVE_CHUNK_POLICY_FAIL" in log:
                    failed = True
                    break
                if f"{PASS} {phase} " in log and "Done (" in log:
                    success = True
                    break
        finally:
            if process.poll() is None:
                try:
                    assert process.stdin is not None
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                    process.wait(timeout=75)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.kill()
                    process.wait(timeout=20)
            if process.stdin:
                process.stdin.close()
        output.flush()
    log = log_file.read_text(encoding="utf-8", errors="replace")
    if failed or not success or process.returncode != 0:
        raise RuntimeError(f"Native Beacon phase {phase} failed: "
                           f"pass={success} exit={process.returncode}\n"
                           + "\n".join(log.splitlines()[-110:]))
    print(f"{phase}: native Paper ticket & stored-owner verification PASS", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--slimefun-jar", type=Path, required=True)
    parser.add_argument("--paper-api-jar", type=Path, required=True)
    parser.add_argument("--compile-classpath-file", type=Path, required=True)
    parser.add_argument("--minecraft", choices=("1.21.11", "26.3"), required=True)
    args = parser.parse_args()

    slimefun, api = (path.resolve(strict=True) for path in
                     (args.slimefun_jar, args.paper_api_jar))
    extra_classpath = args.compile_classpath_file.resolve(strict=True).read_text(
        encoding="utf-8").strip().splitlines()
    if not extra_classpath:
        raise RuntimeError("Expected Gradle's resolved Paper/Adventure compile classpath")
    all_dependencies = os.pathsep.join(extra_classpath)
    repo = Path.cwd().resolve()
    fixture_parent = repo / "build" / "beacon-native-policy"
    if fixture_parent.is_symlink():
        raise RuntimeError("Refusing linked native test directory")
    fixture_parent.mkdir(parents=True, exist_ok=True)
    root = fixture_parent / args.minecraft
    if root.exists() or root.is_symlink():
        raise RuntimeError(f"Expected fresh disposable test directory: {root}")
    root.mkdir()
    paper_build = download_paper(args.minecraft, root / "paper.jar")
    (root / "plugins").mkdir()
    shutil.copyfile(slimefun, root / "plugins" / "Slimefun-Legacy.jar")
    compile_probe(root, slimefun, api, all_dependencies)
    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (root / "server.properties").write_text(
        "online-mode=false\nmax-players=2\n"
        "level-name=beacon-native-world\nspawn-protection=0\n"
        "view-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\n"
        "enable-rcon=false\nenable-query=false\n", encoding="utf-8")
    (root / "plugins" / "bStats").mkdir()
    (root / "plugins" / "bStats" / "config.yml").write_text("enabled: false\n", encoding="utf-8")
    plugin_dir = root / "plugins" / "Slimefun"
    plugin_dir.mkdir()
    config = plugin_dir / "configSFLAddons.yml"
    shutil.copyfile("src/main/resources/configSFLAddons.yml", config)
    for phase in PHASES:
        set_mode(config, "ALWAYS" if phase.startswith("ALWAYS") else phase)
        (root / "beacon-policy-phase.txt").write_text(phase, encoding="utf-8")
        run_phase(root, phase)
    print(f"PASS native Paper {args.minecraft} build {paper_build}: "
          "zero-player tickets, admin mode, owner registry and restarts", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"BEACON_NATIVE_CHUNK_POLICY_FAIL: {error}", file=sys.stderr)
        sys.exit(1)
