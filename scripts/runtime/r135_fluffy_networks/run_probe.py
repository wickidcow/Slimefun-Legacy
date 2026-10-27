#!/usr/bin/env python3
"""Run a test-only Networks↔Fluffy integration probe on disposable r135/r136/r137/r138/r139/r140/r141/r142/r143/r144/r145/r146/r147 Paper 26.3 full-stack data.

The regular full-stack smoke has already booted and shut down cleanly twice.
This runner adds only a temporary test plugin, starts one more offline local Paper
process, and exercises the real registered ticker and Networks storage adapter.
It must never target an existing production Minecraft installation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time
import zipfile

HERE = Path(__file__).resolve().parent
EXPECTED_VERSIONS = {
    "wickidcow/SF_FluffyMachines": ("SF_FluffyMachines26.2.16.jar", "3d5286877e2307b4b9bc48be886d14d75e6889c6"),
}
NETWORK_VERSIONS = {
    135: ("SF_Networks1.0.54.jar", "c24064cbeafe4f112c7d46bf3de5617fa151d88d"),
    136: ("SF_Networks1.0.55.jar", "bcdef34d362dc74aa1aab23c8961a629daa79950"),
    # r137/r138 change only MagicExpansion; keep the exact r136 storage adapters.
    137: ("SF_Networks1.0.55.jar", "bcdef34d362dc74aa1aab23c8961a629daa79950"),
    138: ("SF_Networks1.0.55.jar", "bcdef34d362dc74aa1aab23c8961a629daa79950"),
    139: ("SF_Networks1.0.57.jar", "f5486918ab7e115298b453dc23e26a2225aedaba"),
    140: ("SF_Networks1.0.58.jar", "4ebecea309e89cc60fc4f6d932505312b5468ed1"),
    141: ("SF_Networks1.0.59.jar", "560d590095206a8159d82c70e7cabe6d16aaa0d8"),
    142: ("SF_Networks1.0.59.jar", "560d590095206a8159d82c70e7cabe6d16aaa0d8"),
    143: ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff"),
    # r144 updates only CrystamaeHistoria and MobCapturer; retain the r143 storage adapters.
    144: ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff"),
    # r145 repairs GeneticChickEngineering startup; retain the r143 storage adapters.
    145: ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff"),
    # r146 changes only MagicExpansion; retain the r143 storage adapters.
    146: ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff"),
    # r147 updates SlimeHUD only; preserve the released storage pair.
    147: ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff"),
    # r148 updates SlimeHUD only; retain the verified 1.0.60 storage adapter.
    148: ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff"),
    # r149 advances only the published Networks maintenance JAR.
    149: ("SF_Networks1.0.61.jar", "9bf9f48b49dbedbf9e617ca840d9ccd57cf0c717"),
    # r150 changes only MagicExpansion; keep exact published Networks 1.0.61.
    150: ("SF_Networks1.0.61.jar", "9bf9f48b49dbedbf9e617ca840d9ccd57cf0c717"),
    # Diagnostic r151 integrates tested 1.0.62 transfer and Auto Crafter dirty-write fixes.
    151: ("SF_Networks1.0.62.jar", "288640e6dbf907a6ba238d98af206aab2d56f92b"),
    # r152 recognizes final merged Networks 1.0.62, not the staging PR SHA.
    152: ("SF_Networks1.0.62.jar", "79e4c0116cc4b4683c668a4920e67692f925b83f"),
    # r153 changes only DankTech2 and the BuildingStaff roster; Networks stays unchanged.
    153: ("SF_Networks1.0.62.jar", "79e4c0116cc4b4683c668a4920e67692f925b83f"),
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def expected_core_version(properties: Path) -> str:
    """Read the expected version from the checked-out source, never the running plugin."""
    values = [line.partition("=")[2].strip()
              for line in properties.read_text(encoding="utf-8").splitlines()
              if line.partition("=")[0].strip() == "projectVersion"]
    if len(values) != 1 or re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", values[0]) is None:
        raise RuntimeError("Native conservation test requires one valid projectVersion in gradle.properties")
    return values[0]


def validated_core_version(value: str, label: str) -> str:
    if re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", value or "") is None:
        raise RuntimeError(f"Native conservation test requires a valid {label} core version")
    return value


def prepare(work: Path, bundle: Path, java: Path, runtime_core_version: str | None = None) -> dict | None:
    with zipfile.ZipFile(bundle) as archive:
        manifest = json.loads(archive.read("SF_ADDON_MANIFEST.json"))
    revision = manifest.get("bundle_revision")
    if type(revision) is not int or revision not in NETWORK_VERSIONS:
        raise RuntimeError(f"Native conservation test requires r135, r136, r137, r138, r139, r140, r141, r142, r143, r144, r145, r146, r147, r148, r149, r150, r151, r152 or r153; found bundle revision {revision!r}")
    expected_versions = dict(EXPECTED_VERSIONS)
    expected_versions["wickidcow/SF_NetworksExp"] = NETWORK_VERSIONS[revision]

    workspace = (Path.cwd() / "build").resolve()
    if not work.is_relative_to(workspace) or work.name != "full-stack-26.3":
        raise RuntimeError("Refusing to run outside a disposable build/full-stack-26.3 directory")
    if not (work / "smoke-result.txt").is_file() or not (work / "server.jar").is_file():
        raise RuntimeError("A passing two-boot Paper 26.3 full-stack staging directory is required")
    if "runtime smoke: PASS" not in (work / "smoke-result.txt").read_text(encoding="utf-8"):
        raise RuntimeError("Full-stack baseline did not pass before the native interoperability probe")
    if not (work / "eula.txt").is_file() or not (work / "server.properties").is_file():
        raise RuntimeError("Disposable test server fixture is incomplete")

    source_core_version = expected_core_version(Path.cwd() / "gradle.properties")
    core_version = validated_core_version(
        runtime_core_version if runtime_core_version is not None else source_core_version,
        "expected runtime",
    )

    rows = {row["repository"]: row for row in manifest["addons"]}
    resolved = {}
    for repo, (filename, commit) in expected_versions.items():
        row = rows.get(repo)
        if not row or row.get("jar") != filename or row.get("commit") != commit:
            raise RuntimeError(f"Exact r{revision} addon source mismatch: {repo}")
        binary = work / "plugins" / filename
        if not binary.is_file() or sha256(binary) != row.get("sha256"):
            raise RuntimeError(f"Staged addon JAR digest mismatch: {binary}")
        resolved[repo] = {"jar": filename, "commit": commit, "sha256": sha256(binary)}

    libraries = sorted({
        jar.resolve()
        for directory in ("libraries", "versions", "plugins")
        for jar in (work / directory).rglob("*.jar")
        if ".paper-remapped" not in jar.parts
    })
    if len(libraries) < 3:
        raise RuntimeError("Paper runtime libraries and staged addon JARs are missing")

    classes = work / "native-r135-probe-classes"
    if classes.exists() or (work / "plugins" / "R135FluffyNetworksProbe.jar").exists():
        raise RuntimeError("Refusing to overwrite a prior native integration probe")
    classes.mkdir()
    javac = java.with_name("javac")
    if not javac.is_file():
        raise RuntimeError(f"Missing complete Java 25 JDK: {javac}")
    command = [str(javac), "--release", "25", "-encoding", "UTF-8",
               "-cp", os.pathsep.join(map(str, libraries)), "-d", str(classes),
               str(HERE / "R135FluffyNetworksProbe.java")]
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    (work / "native-r135-compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError("R135 native plugin did not compile; see native-r135-compile.log")

    plugin_jar = work / "plugins" / "R135FluffyNetworksProbe.jar"
    with zipfile.ZipFile(plugin_jar, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(HERE / "plugin.yml", "plugin.yml")
        for item in sorted(classes.rglob("*.class")):
            archive.write(item, item.relative_to(classes).as_posix())
    return {
        "status": "STARTED", "schema": 1, "bundle_revision": revision,
        "source_core_version": source_core_version,
        "expected_core_version": core_version,
        "plugin_jar_sha256": sha256(plugin_jar), "tested_addons": resolved,
        "core_jar_sha256": sha256(work / "plugins" / "Slimefun-Legacy-full-stack.jar"),
        "paper_server_sha256": sha256(work / "server.jar"),
    }


def run(work: Path, java: Path, evidence: dict, timeout: int) -> None:
    log_file = work / "native-r135.console.log"
    result_file = work / "plugins" / "R135FluffyNetworksProbe" / "result.txt"
    if result_file.exists():
        raise RuntimeError("Stale probe evidence is forbidden")
    version = NETWORK_VERSIONS[evidence["bundle_revision"]][0].removeprefix("SF_Networks").removesuffix(".jar")
    core_version = evidence.get("expected_core_version")
    if not isinstance(core_version, str) or re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", core_version) is None:
        raise RuntimeError("Native conservation test is missing a valid expected core version")
    command = [str(java), f"-Dalbion.probe.slimefun.version={core_version}",
               f"-Dalbion.probe.networks.version={version}",
               "-Dterminal.jline=false", "-Dterminal.ansi=false",
               "-Xms768M", "-Xmx3G", "-jar", "server.jar", "--nogui"]

    with log_file.open("w", encoding="utf-8") as output:
        proc = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE,
                                stdout=output, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + timeout
            started = False
            while time.monotonic() < deadline and proc.poll() is None:
                log = log_file.read_text(encoding="utf-8", errors="replace")
                if "Done (" in log and "R135 native integration probe ready" in log:
                    started = True
                    break
                if "Error occurred while enabling" in log or "Could not load plugin" in log:
                    break
                time.sleep(1)
            if not started:
                raise RuntimeError("R135 integration plugin did not enable and start; inspect native-r135.console.log")
            assert proc.stdin is not None
            proc.stdin.write("r135probe run\n")
            proc.stdin.flush()
            while time.monotonic() < deadline and proc.poll() is None and not result_file.is_file():
                time.sleep(1)
            if not result_file.is_file():
                raise RuntimeError("R135 native integration command did not produce evidence")
            result = result_file.read_text(encoding="utf-8").splitlines()
            evidence["result"] = result[0] if result else "FAIL"
            evidence["detail"] = result[1] if len(result) > 1 else "no reason reported"
        finally:
            if proc.poll() is None:
                if proc.stdin:
                    try:
                        proc.stdin.write("stop\n")
                        proc.stdin.flush()
                    except (BrokenPipeError, OSError):
                        pass
                try:
                    proc.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=20)
            if proc.stdin:
                proc.stdin.close()

    evidence["process_exit_code"] = proc.returncode
    evidence["server_log_sha256"] = sha256(log_file)
    if proc.returncode != 0 or evidence.get("result") != "PASS":
        raise RuntimeError(f"Native Networks-Fluffy integration failed: {evidence.get('detail', 'unknown')}; see {log_file}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=420)
    parser.add_argument(
        "--expected-core-version",
        help="Exact Slimefun version staged in the disposable runtime; defaults to projectVersion",
    )
    args = parser.parse_args()
    work = args.runtime_dir.resolve()
    java = Path(os.environ["JAVA_HOME"]) / "bin" / "java"
    if not java.is_file():
        raise RuntimeError("The native r135 test requires a complete JDK 25")
    evidence = prepare(work, args.bundle.resolve(), java, args.expected_core_version)
    out = work / "native-r135-result.json"
    try:
        run(work, java, evidence, args.timeout)
    except Exception as error:
        evidence["result"] = "FAIL"
        evidence["error"] = str(error)
        raise
    finally:
        out.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(evidence, indent=2), flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"Native Networks-Fluffy conservation FAILED: {error}", file=sys.stderr)
        sys.exit(1)
