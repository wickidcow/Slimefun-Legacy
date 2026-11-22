#!/usr/bin/env python3
"""Verify exact published v55 -> v81 physical world item persistence, two real JVMs."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import time

PINNED_OLD = "353c530f9369ebce1d08ddfdbd52b69a2a3b97172cb27210105285068845c6e0"
PINNED_NEW = "9d0e436658cb426ecf3468ab55f698fb7e41e23c46e26f9153ffe19a7fa901d1"


def require(ok: bool, reason: str) -> None:
    if not ok:
        raise RuntimeError(reason)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def phase(root: Path, java: Path, label: str) -> None:
    proof = root / "plugins/SFLHistoricalHandoffProbe"
    (proof / f"{label}.pass").unlink(missing_ok=True)
    (proof / f"{label}.fail").unlink(missing_ok=True)
    logfile = root / f"console-{label}.log"
    with logfile.open("w", encoding="utf-8") as log:
        proc = subprocess.Popen(
            [str(java), "-Xms256M", "-Xmx1152M", "-Dterminal.jline=false",
             "-Dterminal.ansi=false", "-jar", "server.jar", "--nogui"],
            cwd=root, stdin=subprocess.PIPE, stdout=log,
            stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 200
            while time.monotonic() < deadline:
                if "Done (" in logfile.read_text(encoding="utf-8", errors="replace"):
                    break
                require(proc.poll() is None, f"Published {label} server exited early")
                time.sleep(0.4)
            else:
                raise TimeoutError(f"Published {label} server did not start")
            time.sleep(3)
            require(proc.stdin is not None, "Native test console is inaccessible")
            proc.stdin.write(f"sflhistoricprobe {label}\n")
            proc.stdin.flush()
            deadline = time.monotonic() + 110
            while time.monotonic() < deadline:
                failure = proof / f"{label}.fail"
                if failure.exists():
                    raise RuntimeError(f"Native historical probe refused: {failure.read_text()}")
                if (proof / f"{label}.pass").is_file():
                    break
                require(proc.poll() is None, "Server exited before historical PASS")
                time.sleep(0.4)
            else:
                raise TimeoutError(f"Historical {label} native proof timed out")
            proc.stdin.write("stop\n")
            proc.stdin.flush()
            proc.wait(timeout=75)
            require(proc.returncode == 0, f"Historical {label} server shutdown was not clean")
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait(timeout=10)
    output = logfile.read_text(encoding="utf-8", errors="replace")
    require("SFL_HISTORICAL_CORE_FAIL" not in output,
            f"Historical {label} native item observation failed")
    require(output.count(f"SFL_HISTORICAL_CORE_{label.upper()}_PASS") == 1,
            f"Historical {label} JVM emitted an invalid proof marker")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("server-jar", "old-core", "new-core", "candidate-core", "java-home", "work-dir"):
        parser.add_argument("--" + field, type=Path, required=True)
    parser.add_argument("--server-sha256", required=True)
    args = parser.parse_args()
    require(sha256(args.server_jar) == args.server_sha256, "Paper launcher pin mismatch")
    require(sha256(args.old_core) == PINNED_OLD, "Historical v4.1.55 pin mismatch")
    require(sha256(args.new_core) == PINNED_NEW, "Modern v4.1.81 pin mismatch")
    root = args.work_dir.resolve()
    root.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(args.server_jar, root / "server.jar")
    java = args.java_home.resolve() / "bin/java"
    javac = args.java_home.resolve() / "bin/javac"
    jar = args.java_home.resolve() / "bin/jar"
    with (root / "bootstrap.log").open("w", encoding="utf-8") as output:
        subprocess.run([str(java), "-jar", "server.jar", "--version"],
                       cwd=root, stdout=output, stderr=subprocess.STDOUT,
                       check=True, timeout=240)
    source = Path(__file__).with_name("HistoricalCoreProbe.java")
    classes = root / "probe-classes"
    classes.mkdir()
    libraries = [str(p) for p in (root / "libraries").rglob("*.jar")]
    require(bool(libraries), "Paper did not provision native Bukkit compilation libraries")
    subprocess.run([str(javac), "--release", "21",
                    "-cp", ":".join([str(args.old_core.resolve()), *libraries]),
                    "-d", str(classes), str(source)], check=True)
    shutil.copyfile(Path(__file__).with_name("plugin.yml"), classes / "plugin.yml")
    plugins = root / "plugins"
    plugins.mkdir()
    subprocess.run([str(jar), "cf", str(plugins / "SFLHistoricalHandoffProbe.jar"),
                    "-C", str(classes), "."], check=True)
    (root / "eula.txt").write_text("eula=true\n")
    (root / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=historic-core-disposable-world\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "pause-when-empty-seconds=-1\nspawn-protection=0\nmax-players=1\n")
    (root / "bukkit.yml").write_text("settings:\n  allow-end: false\n")
    server_core = plugins / "Slimefun.jar"
    shutil.copyfile(args.old_core, server_core)
    phase(root, java, "seed")
    server_core.unlink()
    shutil.copyfile(args.new_core, server_core)
    phase(root, java, "verify")
    require(args.candidate_core.is_file(), "Unreleased Doctor candidate JAR missing")
    server_core.unlink()
    shutil.copyfile(args.candidate_core, server_core)
    phase(root, java, "candidate")
    report = plugins / "SFLHistoricalHandoffProbe/comparison.tsv"
    require(report.is_file() and len(report.read_text().splitlines()) >= 3,
            "Native historical item comparison is incomplete")
    print(report.read_text(), flush=True)
    candidate_report = plugins / "SFLHistoricalHandoffProbe/candidate-comparison.tsv"
    require(candidate_report.is_file()
            and len(candidate_report.read_text().strip().splitlines()) >= 3,
            "Unreleased candidate Doctor did not prove published historic items")
    print(candidate_report.read_text(), flush=True)
    (root / "PASS.txt").write_text(
        "PASS three independent JVM boots: published v55 source, published v81"
        " mismatch, exact PR candidate Doctor historical strict repair;"
        " every original world byte/count unchanged; typed-owner and"
        " same-material cross-ID negative refused; no chest writes\n")


if __name__ == "__main__":
    main()
