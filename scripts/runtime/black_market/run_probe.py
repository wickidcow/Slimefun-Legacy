#!/usr/bin/env python3
"""Non-shipping native receipt/restart gate, only in a disposable full-stack fixture.

Four fresh JVMs execute the installed MagicExpansion store: seed, read after
restart, refuse a missing marker, and read after exact synthetic restoration.
No live plugin data is used. This is not a client-click or crash-atomicity test.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid
import zipfile

HERE = Path(__file__).resolve().parent
OWNER = "8160641d-8db8-49cc-9716-d19e4da49bfb"
MAGIC = "wickidcow/SF_MagicExpansion"
MAGIC_RELEASES = {
    137: ("13924991a1960daa40fe221cab5a4eef95d46ecb", "SF_MagicExpansion1.1.13.jar"),
    138: ("9443393b3ef5f0982194d9084eaed91d7d820dca", "SF_MagicExpansion1.1.15.jar"),
    139: ("9443393b3ef5f0982194d9084eaed91d7d820dca", "SF_MagicExpansion1.1.15.jar"),
    140: ("9443393b3ef5f0982194d9084eaed91d7d820dca", "SF_MagicExpansion1.1.15.jar"),
    141: ("1f7f6fed1e4b7b8cda9331cedc8a963332808101", "SF_MagicExpansion1.1.16.jar"),
    142: ("1f7f6fed1e4b7b8cda9331cedc8a963332808101", "SF_MagicExpansion1.1.16.jar"),
    143: ("1f7f6fed1e4b7b8cda9331cedc8a963332808101", "SF_MagicExpansion1.1.16.jar"),
    144: ("1f7f6fed1e4b7b8cda9331cedc8a963332808101", "SF_MagicExpansion1.1.16.jar"),
    # r145 changes only GeneticChickEngineering relative to r144.
    145: ("1f7f6fed1e4b7b8cda9331cedc8a963332808101", "SF_MagicExpansion1.1.16.jar"),
    # r146 advances only MagicExpansion to the verified 1.1.17 hotfix.
    146: ("e0097f1d8e984e8d19692fcc5a28729366b72d48", "SF_MagicExpansion1.1.17.jar"),
    # r147 updates SlimeHUD only; preserve the MagicExpansion hotfix.
    147: ("e0097f1d8e984e8d19692fcc5a28729366b72d48", "SF_MagicExpansion1.1.17.jar"),
    # r148 updates SlimeHUD only; preserve the exact MagicExpansion release.
    148: ("e0097f1d8e984e8d19692fcc5a28729366b72d48", "SF_MagicExpansion1.1.17.jar"),
    # r149 advances Networks only; preserve the exact MagicExpansion hotfix.
    149: ("e0097f1d8e984e8d19692fcc5a28729366b72d48", "SF_MagicExpansion1.1.17.jar"),
    # r150 changes only MagicExpansion; retain all r149 source selections otherwise.
    150: ("7c2c1a927176ebd0aaab4481dcc21a51320f7f10", "SF_MagicExpansion1.1.18.jar"),
    # DIAGNOSTIC BRANCH ONLY: r151 full-addon preview uses unpublished Magic 1.1.19.
    # Do not copy this source lock into the official r151 release candidate or master.
    151: ("c9be11af6b32f3e0d517803b9362c7c88312b150", "SF_MagicExpansion1.1.19.jar"),
    # r152 recognizes final merged Magic 1.1.19 source, not the prior staging PR.
    152: ("b28c304f2909f15b43da53910ce7650886cc0e6f", "SF_MagicExpansion1.1.19.jar"),
    # r153 drops BuildingStaff and updates DankTech2; Magic stays at the same merged source.
    153: ("b28c304f2909f15b43da53910ce7650886cc0e6f", "SF_MagicExpansion1.1.19.jar"),
}
MAGIC_VERSIONS = {
    137: "1.1.13",
    138: "1.1.15",
    139: "1.1.15",
    140: "1.1.15",
    141: "1.1.16",
    142: "1.1.16",
    143: "1.1.16",
    144: "1.1.16",
    145: "1.1.16",
    146: "1.1.17",
    147: "1.1.17",
    148: "1.1.17",
    149: "1.1.17",
    150: "1.1.18",
    151: "1.1.19",
    152: "1.1.19",
    153: "1.1.19",
}
DIRECTORIES = {"full-stack-1.21.11", "full-stack-26.2", "full-stack-26.3", "full-stack-purpur-26.3"}
PLUGIN = "BlackMarketRestartProbe"
PHASES = ("seed", "verify", "missing", "restored")
DESCRIPTOR = """name: BlackMarketRestartProbe
version: '1'
main: audit.BlackMarketRestartProbe
api-version: '1.21.11'
depend: [Slimefun, magicexpansion]
commands:
  bmreceiptprobe:
    description: Disposable console-only test command. Never ship this plugin.
"""


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path: Path) -> dict[str, str]:
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def validate_fixture(work: Path, bundle: Path) -> dict:
    """Read-only admission. All assertions precede compilation or fixture writes."""
    expected_parent = Path.cwd() / "build"
    if work.parent != expected_parent or work.name not in DIRECTORIES:
        raise RuntimeError("Only an exact build/full-stack-* fixture is allowed")
    for path in (expected_parent, work, work / "plugins"):
        if path.is_symlink() or not path.is_dir():
            raise RuntimeError(f"Fixture must use real directories: {path}")
    for name in ("smoke-result.txt", "server.jar", "server.properties", "eula.txt", "runtime-build.txt"):
        path = work / name
        if path.is_symlink() or not path.is_file():
            raise RuntimeError(f"Missing or linked fixture file: {path}")
    if "runtime smoke: PASS" not in (work / "smoke-result.txt").read_text(encoding="utf-8"):
        raise RuntimeError("A passing two-boot full-stack fixture is mandatory")
    props = properties(work / "server.properties")
    if props.get("level-name") != "full-stack-smoke-world" or props.get("online-mode") != "false":
        raise RuntimeError("Not the generated offline full-stack world")
    if props.get("enable-rcon") != "false" or props.get("enable-query") != "false":
        raise RuntimeError("Remote administration/query must be disabled")
    if properties(work / "eula.txt").get("eula") != "true":
        raise RuntimeError("Fixture EULA not accepted by its creating workflow")
    for path in (work / "bm-probe-classes", work / "plugins" / PLUGIN,
                 work / "plugins" / f"{PLUGIN}.jar", work / "black-market-result.json"):
        if path.exists() or path.is_symlink():
            raise RuntimeError(f"Refusing stale probe files: {path}")

    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise RuntimeError("Duplicate ZIP entries")
        manifest = json.loads(archive.read("SF_ADDON_MANIFEST.json"))
        revision = manifest.get("bundle_revision")
        if type(revision) is not int or revision not in MAGIC_RELEASES:
            raise RuntimeError("This exact-source probe requires candidate r137, r138, r139, r140, r141, r142, r143, r144, r145, r146, r147, r148, r149, r150, r151, r152 or r153")
        magic_commit, magic_jar = MAGIC_RELEASES[revision]
        rows = manifest.get("addons", [])
        expected_addons = 44 if revision >= 153 else 45
        if len(rows) != expected_addons or len({r["repository"] for r in rows}) != expected_addons:
            raise RuntimeError(f"Expected {expected_addons} unique canonical addon repositories")
        if len({r["jar"] for r in rows}) != expected_addons:
            raise RuntimeError("Duplicate addon filenames")
        if revision >= 153 and any(r["repository"] == "wickidcow/SF_BuildingStaff" for r in rows):
            raise RuntimeError("BuildingStaff is excluded from r153 and newer bundles")
        magic = [r for r in rows if r["repository"] == MAGIC]
        if len(magic) != 1 or magic[0].get("commit") != magic_commit or magic[0].get("jar") != magic_jar:
            raise RuntimeError("Wrong MagicExpansion source or JAR for bundle revision")
        tested = {}
        for row in rows:
            name = row["jar"]
            if Path(name).name != name or not name.endswith(".jar"):
                raise RuntimeError("Unsafe addon filename")
            expected = row.get("sha256", "")
            if not re.fullmatch(r"[0-9a-f]{64}", expected):
                raise RuntimeError("Missing/invalid addon checksum")
            binary = work / "plugins" / name
            if binary.is_symlink() or not binary.is_file():
                raise RuntimeError(f"Missing/linked staged addon: {name}")
            if digest(binary) != expected or hashlib.sha256(archive.read(name)).hexdigest() != expected:
                raise RuntimeError(f"Bundle/staged addon checksum mismatch: {name}")
            tested[row["repository"]] = {"jar": name, "commit": row["commit"], "sha256": expected}
    core = work / "plugins" / "Slimefun-Legacy-full-stack.jar"
    if core.is_symlink() or not core.is_file():
        raise RuntimeError("Candidate core missing or linked")
    return {"schema": 1, "result": "STARTED", "bundle_revision": revision,
            "magic_version": MAGIC_VERSIONS[revision],
            "bundle_sha256": digest(bundle), "server_sha256": digest(work / "server.jar"),
            "core_sha256": digest(core), "tested_addons": tested, "phases": [],
            "runtime_build": (work / "runtime-build.txt").read_text(encoding="utf-8")}


def prepare(work: Path, java: Path, token: str) -> None:
    libraries = sorted({jar.resolve() for directory in ("libraries", "versions", "plugins")
                        for jar in (work / directory).rglob("*.jar") if ".paper-remapped" not in jar.parts})
    if len(libraries) < 3 or not java.is_file() or not java.with_name("javac").is_file():
        raise RuntimeError("Full JDK and initialized server libraries are required")
    classes = work / "bm-probe-classes"
    classes.mkdir()
    result = subprocess.run([str(java.with_name("javac")), "--release", "21", "-encoding", "UTF-8",
                             "-cp", os.pathsep.join(map(str, libraries)), "-d", str(classes),
                             str(HERE / "BlackMarketRestartProbe.java")],
                            text=True, capture_output=True, timeout=120, check=False)
    (work / "black-market-compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode != 0:
        raise RuntimeError("Native probe compilation failed; see black-market-compile.log")
    with zipfile.ZipFile(work / "plugins" / f"{PLUGIN}.jar", "x", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("plugin.yml", DESCRIPTOR)
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes).as_posix())
    data = work / "plugins" / PLUGIN
    data.mkdir()
    (data / "authorization.txt").write_text(token, encoding="utf-8")
    # Bind only the already-admitted disposable fixture to loopback for these boots.
    path = work / "server.properties"
    lines = [line for line in path.read_text(encoding="utf-8").splitlines()
             if not line.strip().startswith("server-ip=")]
    path.write_text("\n".join(lines) + "\nserver-ip=127.0.0.1\n", encoding="utf-8")


def native_phase(
    work: Path, java: Path, token: str, expected_magic_version: str, phase: str, timeout: int
) -> dict:
    data = work / "plugins" / PLUGIN
    result_path = data / f"{phase}.result"
    log_path = work / f"black-market-{phase}.console.log"
    if result_path.exists() or log_path.exists():
        raise RuntimeError("Stale phase evidence")
    command = [str(java), f"-Dsfl.blackmarket.probe.token={token}",
               f"-Dsfl.blackmarket.probe.magic.version={expected_magic_version}",
               "-Dterminal.jline=false",
               "-Dterminal.ansi=false", "-Xms768M", "-Xmx3G", "-jar", "server.jar", "--nogui"]
    with log_path.open("x", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE, stdout=output,
                                   stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + timeout
            ready = False
            while process.poll() is None and time.monotonic() < deadline:
                text = log_path.read_text(encoding="utf-8", errors="replace")
                if "Done (" in text and "BLACK_MARKET_PROBE READY" in text:
                    ready = True
                    break
                if "Error occurred while enabling" in text or "Could not load plugin" in text:
                    break
                time.sleep(1)
            if not ready:
                raise RuntimeError(f"Native {phase} did not start correctly")
            assert process.stdin is not None
            process.stdin.write(f"bmreceiptprobe {phase}\n")
            process.stdin.flush()
            while process.poll() is None and time.monotonic() < deadline and not result_path.is_file():
                time.sleep(1)
            if not result_path.is_file():
                raise RuntimeError(f"Native {phase} did not execute or produce evidence")
        finally:
            if process.poll() is None:
                try:
                    assert process.stdin is not None
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                except (BrokenPipeError, OSError):
                    pass
                try:
                    process.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=20)
            if process.stdin:
                process.stdin.close()
    lines = result_path.read_text(encoding="utf-8").splitlines()
    text = log_path.read_text(encoding="utf-8", errors="replace")
    if process.returncode != 0 or len(lines) != 5 or lines[:3] != ["PASS", phase, token]:
        raise RuntimeError(f"Native {phase} failed or exited abnormally")
    if lines[3] != str(process.pid) or not lines[4].isdigit() or int(lines[4]) < 10:
        raise RuntimeError("Native evidence PID/assertions mismatch")
    if f"BLACK_MARKET_PROBE PASS {phase}" not in text:
        raise RuntimeError("Native success absent from console")
    for error in ("Error occurred while enabling", "Could not load plugin", "NoClassDefFoundError",
                  "NoSuchMethodError", "BLACK_MARKET_PROBE FAIL"):
        if error in text:
            raise RuntimeError(f"Unexpected runtime failure in {phase}: {error}")
    return {"phase": phase, "result": "PASS", "pid": process.pid, "exit_code": process.returncode,
            "assertions": int(lines[4]), "console_sha256": digest(log_path),
            "evidence_sha256": digest(result_path)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=420)
    parser.add_argument("--allow-disposable-writes", action="store_true", required=True)
    args = parser.parse_args()
    # Do not resolve away a symlink before the real-directory admission check.
    work = Path(os.path.abspath(args.runtime_dir))
    evidence = validate_fixture(work, args.bundle.resolve())
    token = str(uuid.uuid4())
    java = Path(os.environ["JAVA_HOME"]) / "bin" / "java"
    out = work / "black-market-result.json"
    try:
        prepare(work, java, token)
        evidence["probe_jar_sha256"] = digest(work / "plugins" / f"{PLUGIN}.jar")
        data = work / "plugins" / PLUGIN
        cycle = data / "synthetic-ledger" / "cycle.yml"
        player = data / "synthetic-ledger" / "players" / f"{OWNER}.yml"
        held = data / "cycle.stopped-fixture"
        for phase in PHASES:
            # This loop runs only after the preceding JVM has exited cleanly.
            if phase == "missing":
                if cycle.read_bytes() != (data / "cycle.baseline").read_bytes():
                    raise RuntimeError("Cannot prepare missing-marker case from changed seed")
                cycle.rename(held)
            elif phase == "restored":
                if cycle.exists() or player.read_bytes() != (data / "player.baseline").read_bytes():
                    raise RuntimeError("Missing-marker phase changed receipts or recreated the marker")
                held.rename(cycle)
            evidence["phases"].append(
                native_phase(work, java, token, evidence["magic_version"], phase, args.timeout)
            )
            if digest(work / "server.jar") != evidence["server_sha256"]:
                raise RuntimeError("Server binary changed between restarts")
            if digest(work / "plugins" / "Slimefun-Legacy-full-stack.jar") != evidence["core_sha256"]:
                raise RuntimeError("Core binary changed between restarts")
            for row in evidence["tested_addons"].values():
                if digest(work / "plugins" / row["jar"]) != row["sha256"]:
                    raise RuntimeError("Addon binary changed between restarts")
        # The restart probe is intentionally temporary. Remove only the loadable
        # test-plugin JAR and compiler scratch directory after all four JVMs pass so a later
        # native probe reusing this disposable server does not load BlackMarketRestartProbe
        # without its authorization/version system properties. Retain its data/results/logs.
        (work / "plugins" / f"{PLUGIN}.jar").unlink(missing_ok=True)
        shutil.rmtree(work / "bm-probe-classes", ignore_errors=True)

        evidence["result"] = "PASS"
        evidence["limits"] = "Installed store + native Bukkit serialization; synthetic records, not client clicks, live recovery, or crash-atomic proof."
    except Exception as error:
        evidence["result"] = "FAIL"
        evidence["error"] = str(error)
        raise
    finally:
        out.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(evidence, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
