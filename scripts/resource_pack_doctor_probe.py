#!/usr/bin/env python3
"""Disposable real-Paper migration probe using a cached server's code dependencies."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import time


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("server-template", "core", "work-dir", "java-home"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    root = args.work_dir.resolve()
    root.mkdir(parents=True, exist_ok=False)  # Never replace an existing world.
    template = args.server_template.resolve()
    repo = Path(__file__).resolve().parent.parent
    for name in ("libraries", "cache", "versions"):
        shutil.copytree(template / name, root / name)
    for name in ("server.jar", "eula.txt"):
        shutil.copy2(template / name, root / name)
    (root / "plugins/Slimefun").mkdir(parents=True)
    core = root / "plugins/Slimefun.jar"
    shutil.copy2(args.core.resolve(), core)
    (root / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nlevel-name=rp-doctor-world\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\nview-distance=2\n"
        "simulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\n"
        "enable-query=false\nenable-rcon=false\n", encoding="utf-8")
    (root / "bukkit.yml").write_text("settings:\n  allow-end: false\n", encoding="utf-8")
    (root / "plugins/Slimefun/config.yml").write_text(
        "options:\n  auto-update: false\nstability:\n  item-doctor:\n    enabled: false\n", encoding="utf-8")
    (root / "plugins/Slimefun/item-models.yml").write_text(
        "STEEL_INGOT: 2200080\nSLIMEFUN_GUIDE: 2200001\n", encoding="utf-8")
    classes = root / "probe-classes"
    classes.mkdir()
    classpath = [str(core)] + [str(p) for p in (root / "libraries").rglob("*.jar")]
    subprocess.run([str(args.java_home / "bin/javac"), "--release", "21", "-cp", ":".join(classpath),
                    "-d", str(classes), str(repo / "scripts/resource-pack-fixture/ResourcePackDoctorProbe.java")], check=True)
    shutil.copy2(repo / "scripts/resource-pack-fixture/plugin.yml", classes / "plugin.yml")
    subprocess.run([str(args.java_home / "bin/jar"), "cf", str(root / "plugins/ResourcePackDoctorProbe.jar"),
                    "-C", str(classes), "."], check=True)
    proof = root / "plugins/ResourcePackDoctorProbe"
    processes = []

    def command(process, text):
        process.stdin.write(text + "\n")
        process.stdin.flush()

    def wait(predicate, process, log, timeout=120):
        deadline = time.monotonic() + timeout
        while not predicate():
            if process.poll() is not None or "RESOURCE_PACK_DOCTOR_PROBE_FAIL" in log.read_text():
                raise RuntimeError("Native probe failed; inspect " + str(log))
            if time.monotonic() >= deadline:
                raise TimeoutError("Native probe timed out; inspect " + str(log))
            time.sleep(0.25)

    def boot(number):
        path = root / f"console-{number}.log"
        log = path.open("w", encoding="utf-8")
        process = subprocess.Popen([str(args.java_home / "bin/java"), "-Xms256M", "-Xmx768M",
            "-Dterminal.jline=false", "-Dterminal.ansi=false", "-jar", "server.jar", "--nogui"],
            cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        processes.append(process)
        wait(lambda: "Done (" in path.read_text(), process, path)
        time.sleep(4)
        print(f"Paper boot {number} ready", flush=True)
        return process, log

    def probe(process, number, action):
        command(process, "rpdoctorprobe " + action)
        wait(lambda: (proof / f"{action}.pass").is_file(), process, root / f"console-{number}.log")
        print((proof / f"{action}.pass").read_text().strip(), flush=True)

    def stop(process, log):
        command(process, "stop")
        process.wait(timeout=60)
        log.close()
        if process.returncode != 0:
            raise RuntimeError(f"Server exited with {process.returncode}")

    try:
        process, log = boot(1)
        probe(process, 1, "prepare")
        command(process, "sf doctor resource-pack install scan")
        time.sleep(3)
        probe(process, 1, "preview")
        command(process, "sf doctor resource-pack install confirm")
        time.sleep(2)
        probe(process, 1, "checkpoint")
        stop(process, log)
        process, log = boot(2)
        time.sleep(4)
        probe(process, 2, "installed")
        probe(process, 2, "deferred")
        command(process, "sf doctor resource-pack resume")
        time.sleep(3)
        command(process, "sf doctor resource-pack uninstall confirm")
        time.sleep(3)
        probe(process, 2, "uninstalled")
        stop(process, log)
        config_file = root / "plugins/Slimefun/configSFLAddons.yml"
        config_file.write_text(config_file.read_text().replace("ownership-mode: none", "ownership-mode: external"))
        process, log = boot(3)
        time.sleep(4)
        command(process, "sf doctor resource-pack status")
        time.sleep(1)
        if "ownership-mode: external" not in config_file.read_text():
            raise RuntimeError("Cleanup checkpoint overwrote a later ownership choice")
        stop(process, log)
        result = "Native resource-pack Doctor probe PASS\ncore SHA-256: " + hashlib.sha256(core.read_bytes()).hexdigest()
        result += "\nserver SHA-256: " + hashlib.sha256((root / "server.jar").read_bytes()).hexdigest() + "\n"
        (root / "PASS.txt").write_text(result)
        print(result, flush=True)
    finally:
        for process in processes:
            if process.poll() is None:
                command(process, "stop")
                try:
                    process.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    process.wait(timeout=15)


if __name__ == "__main__":
    main()
