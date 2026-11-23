#!/usr/bin/env python3
"""True published FluffyMachines v26.2.23 -> v26.2.24 saved-world identity handoff."""
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path
import shutil
import subprocess
import time

OLD_FLUFFYMACHINES = "2278fb367e6eeb95619718139f7fa5d43131b73c6b65eddfd0953353261392e9"
NEW_FLUFFYMACHINES = "1cfa3da859cea788af405c47bd3b1222ee975e8d27b368e2c5afe8c844afc033"
BASELINE_CORE_V81 = "9d0e436658cb426ecf3468ab55f698fb7e41e23c46e26f9153ffe19a7fa901d1"


def require(valid: bool, message: str) -> None:
    if not valid:
        raise RuntimeError(message)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def boot(root: Path, java: Path, stage: str) -> None:
    proof = root / "plugins/SFLFluffyMachinesHistoryProbe"
    for suffix in ("pass", "fail"):
        (proof / f"{stage}.{suffix}").unlink(missing_ok=True)
    log_path = root / f"console-{stage}.log"
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [str(java), "-Xms256M", "-Xmx1536M", "-Dterminal.jline=false",
             "-Dterminal.ansi=false", "-DcentralRepository=https://repo.maven.apache.org/maven2/",
             "-jar", "server.jar", "--nogui"],
            cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                require(process.poll() is None, "FluffyMachines native server exited while starting " + stage)
                if "Done (" in log_path.read_text(encoding="utf-8", errors="replace"):
                    break
                time.sleep(0.4)
            else:
                raise TimeoutError("FluffyMachines published " + stage + " server did not boot")
            require(process.stdin is not None, "FluffyMachines native console inaccessible")
            time.sleep(3)
            process.stdin.write("sflfluffymachineshistory " + stage + "\n")
            process.stdin.flush()
            deadline = time.monotonic() + 135
            while time.monotonic() < deadline:
                bad = proof / f"{stage}.fail"
                if bad.exists():
                    raise RuntimeError("FluffyMachines native " + stage
                                       + " refused; category " + bad.read_text().strip())
                if (proof / f"{stage}.pass").is_file():
                    break
                require(process.poll() is None, "FluffyMachines exited before source acceptance")
                time.sleep(0.4)
            else:
                raise TimeoutError("FluffyMachines " + stage + " probe timed out")
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=100)
            require(process.returncode == 0, "FluffyMachines native " + stage + " stop was not clean")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=15)
    output = log_path.read_text(encoding="utf-8", errors="replace")
    marker = "SFL_TRUE_FLUFFYMACHINES_" + stage.upper() + "_PASS"
    require(output.count(marker) == 1 and "SFL_TRUE_FLUFFYMACHINES_FAIL" not in output,
            "Actual historical addon server lacked unique " + stage + " success marker")
    if stage == "stabilize":
        require(output.count("SFL_TRUE_FLUFFYMACHINES_STABILIZED_OLD_WORLD_PASS") == 1,
                "Old-version independent world-save normalization proof was not observed")
    if stage in ("candidate", "replay"):
        diagnostic = "SFL_TRUE_FLUFFYMACHINES_DIAGNOSTIC_" + (
            "CANDIDATE" if stage == "candidate" else "RESTART") + "_PASS"
        require(output.count(diagnostic) == 1,
                "Actual published old addon native Doctor diagnostic missing on " + stage)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("server-jar", "baseline-core", "candidate-core",
                "old-addon", "new-addon", "java-home", "work-dir"):
        parser.add_argument("--" + arg, required=True, type=Path)
    parser.add_argument("--server-sha256", required=True)
    args = parser.parse_args()

    require(sha256(args.server_jar) == args.server_sha256,
            "Exact pinned Paper launcher checksum mismatch")
    require(sha256(args.old_addon) == OLD_FLUFFYMACHINES,
            "Old published FluffyMachines v26.2.23 JAR checksum mismatch")
    require(sha256(args.new_addon) == NEW_FLUFFYMACHINES,
            "New published FluffyMachines v26.2.24 JAR checksum mismatch")
    require(sha256(args.baseline_core) == BASELINE_CORE_V81,
            "Pinned baseline Slimefun Legacy v4.1.81 checksum mismatch")
    require(args.candidate_core.is_file(), "No separately built Doctor candidate core")

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
    sources = Path(__file__).resolve().parent
    classes = root / "probe-classes"
    classes.mkdir()
    libraries = list((root / "libraries").rglob("*.jar"))
    require(bool(libraries), "Native Paper API libraries missing after bootstrap")
    subprocess.run(
        [str(javac), "--release", "21",
         "-cp", ":".join([str(args.baseline_core.resolve()), *map(str, libraries)]),
         "-d", str(classes), str(sources / "FluffyMachinesHistoryProbe.java"),
         str(sources / "NativeNbtStructuralEvidence.java")],
        check=True, timeout=200)
    shutil.copyfile(sources / "plugin.yml", classes / "plugin.yml")
    plugins = root / "plugins"
    plugins.mkdir()
    subprocess.run([str(jar), "cf", str(plugins / "SFLFluffyMachinesHistoryProbe.jar"),
                    "-C", str(classes), "."], check=True)
    (root / "eula.txt").write_text("eula=true\n")
    (root / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=historical-fluffymachines-disposable\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nmax-players=1\npause-when-empty-seconds=-1\n")
    (root / "bukkit.yml").write_text("settings:\n  allow-end: false\n")
    current_core = plugins / "Slimefun.jar"
    current_addon = plugins / "FluffyMachines.jar"
    shutil.copyfile(args.baseline_core, current_core)
    shutil.copyfile(args.old_addon, current_addon)
    boot(root, java, "seed")
    # Separate independent *old addon + old core* JVM reopens the SAME
    # saved world and normalizes the fixture's original receipts only when
    # initial Paper save/load changed cosmetic text encoding exclusively.
    # It never writes the actual world inventory.
    boot(root, java, "stabilize")
    # Prove a third independent boot under the exact unchanged released
    # old core/addon pair is byte-stable BEFORE switching plugin versions.
    boot(root, java, "baseline")

    current_core.unlink()
    current_addon.unlink()
    shutil.copyfile(args.candidate_core, current_core)
    shutil.copyfile(args.new_addon, current_addon)
    boot(root, java, "candidate")
    boot(root, java, "replay")

    proof = plugins / "SFLFluffyMachinesHistoryProbe"
    ids = (proof / "original-item-ids.txt").read_text().splitlines()
    statuses = (proof / "classification.tsv").read_text().splitlines()
    require(24 <= len(ids) <= 53 and len(statuses) == len(ids) + 1,
            "Published FluffyMachines comparison omitted old registered items")
    allowed = {"EXACT_NATIVE_MATCH", "STRICT_STYLE_REPAIRABLE",
               "REFUSED_NON_PRESENTATION", "SIMILAR_NATIVE_DRIFT", "UNRESOLVED"}
    classifications = [line.split("\t", 1)[1] for line in statuses[1:]]
    require(all(v in allowed for v in classifications),
            "Unexpected true-addon old-vs-new classification")
    exact = classifications.count("EXACT_NATIVE_MATCH")
    eligible = classifications.count("STRICT_STYLE_REPAIRABLE")
    refused = len(ids) - exact - eligible
    require(all((proof / f"{stage}.pass").is_file()
                for stage in ("seed", "stabilize", "baseline", "candidate", "replay")),
            "Five independent FluffyMachines JVM boots not proven")
    normalization = (proof / "world-save-normalization-count.txt").read_text().strip()
    match = re.fullmatch(r"registered=([0-9]+);strict_cosmetic_normalizations=([0-9]+)", normalization)
    require(match is not None and int(match.group(1)) == len(ids)
            and 0 <= int(match.group(2)) <= len(ids),
            "Exact old-version native item stabilization report missing or invalid")
    message = (
        "PASS true published FluffyMachines v26.2.23 -> v26.2.24 identity across "
        "five independent JVM boots and the SAME two physical world chests\n"
        + normalization + "\n"
        f"source_ids={len(ids)} exact_native={exact} "
        f"strict_presentation_eligible={eligible} refused={refused}\n"
        "world_chest_written_only_in_seed=true; baseline_current_and_replay_read_only=true\n"
        "native_originals_and_owner_receipts_remain_private=true\n"
        "This does not authorize any production chest, machine, QuickShop, Cargo or SQL repair.\n")
    (root / "PASS.txt").write_text(message)
    print(message, flush=True)


if __name__ == "__main__":
    main()
