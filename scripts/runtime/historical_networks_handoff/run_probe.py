#!/usr/bin/env python3
"""True published Networks v1.0.65 -> v1.0.66 saved-world identity handoff."""
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path
import shutil
import subprocess
import time

OLD_NETWORKS = "114bfa0d2bc5a12f65e320640691bfa075470ddc9ff021969ade85e92333277f"
NEW_NETWORKS = "1769bdb6fc941c4c01d893bb0c3f0f2288598403c2c86b0368a16dffe2acc215"
BASELINE_CORE_V81 = "9d0e436658cb426ecf3468ab55f698fb7e41e23c46e26f9153ffe19a7fa901d1"


def require(valid: bool, message: str) -> None:
    if not valid:
        raise RuntimeError(message)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def boot(root: Path, java: Path, stage: str) -> None:
    proof = root / "plugins/SFLNetworksHistoryProbe"
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
                require(process.poll() is None, "Networks native server exited while starting " + stage)
                if "Done (" in log_path.read_text(encoding="utf-8", errors="replace"):
                    break
                time.sleep(0.4)
            else:
                raise TimeoutError("Networks published " + stage + " server did not boot")
            require(process.stdin is not None, "Networks native console inaccessible")
            time.sleep(3)
            process.stdin.write("sflnetworkshistory " + stage + "\n")
            process.stdin.flush()
            deadline = time.monotonic() + 135
            while time.monotonic() < deadline:
                bad = proof / f"{stage}.fail"
                if bad.exists():
                    raise RuntimeError("Networks native " + stage
                                       + " refused; category " + bad.read_text().strip())
                if (proof / f"{stage}.pass").is_file():
                    break
                require(process.poll() is None, "Networks exited before source acceptance")
                time.sleep(0.4)
            else:
                raise TimeoutError("Networks " + stage + " probe timed out")
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=100)
            require(process.returncode == 0, "Networks native " + stage + " stop was not clean")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=15)
    output = log_path.read_text(encoding="utf-8", errors="replace")
    marker = "SFL_TRUE_NETWORKS_" + stage.upper() + "_PASS"
    require(output.count(marker) == 1 and "SFL_TRUE_NETWORKS_FAIL" not in output,
            "Actual historical addon server lacked unique " + stage + " success marker")
    if stage == "stabilize":
        require(output.count("SFL_TRUE_NETWORKS_STABILIZED_OLD_WORLD_PASS") == 1,
                "Old-version independent world-save normalization proof was not observed")
    if stage in ("candidate", "replay"):
        diagnostic = "SFL_TRUE_NETWORKS_DIAGNOSTIC_" + (
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
    require(sha256(args.old_addon) == OLD_NETWORKS,
            "Old published Networks v1.0.65 JAR checksum mismatch")
    require(sha256(args.new_addon) == NEW_NETWORKS,
            "New published Networks v1.0.66 JAR checksum mismatch")
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
         "-d", str(classes), str(sources / "NetworksHistoryProbe.java"),
         str(sources / "NativeNbtStructuralEvidence.java")],
        check=True, timeout=200)
    shutil.copyfile(sources / "plugin.yml", classes / "plugin.yml")
    plugins = root / "plugins"
    plugins.mkdir()
    subprocess.run([str(jar), "cf", str(plugins / "SFLNetworksHistoryProbe.jar"),
                    "-C", str(classes), "."], check=True)
    (root / "eula.txt").write_text("eula=true\n")
    (root / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=historical-networks-disposable\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nmax-players=1\npause-when-empty-seconds=-1\n")
    (root / "bukkit.yml").write_text("settings:\n  allow-end: false\n")
    current_core = plugins / "Slimefun.jar"
    current_addon = plugins / "Networks.jar"
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

    proof = plugins / "SFLNetworksHistoryProbe"
    ids = (proof / "original-item-ids.txt").read_text().splitlines()
    statuses = (proof / "classification.tsv").read_text().splitlines()
    require(24 <= len(ids) <= 53 and len(statuses) == len(ids) + 1,
            "Published Networks comparison omitted old registered items")
    similarity_types = ("SIMILAR_TYPED_EQUAL_RAW_DRIFT",
                        "SIMILAR_TYPED_DIFFERENCE", "SIMILAR_TYPED_UNREADABLE")
    allowed = {"EXACT_NATIVE_MATCH", "STRICT_STYLE_REPAIRABLE",
               "REFUSED_NON_PRESENTATION", "UNRESOLVED", *similarity_types}
    parsed = [line.split("\t") for line in statuses[1:]]
    require(statuses[0] == "item_id\tclassification"
            and all(len(parts) == 2 for parts in parsed)
            and [parts[0] for parts in parsed] == ids
            and all(parts[1] in allowed for parts in parsed),
            "Unexpected or mismatched true-addon old-vs-new typed classification")
    classifications = [parts[1] for parts in parsed]
    exact = classifications.count("EXACT_NATIVE_MATCH")
    eligible = classifications.count("STRICT_STYLE_REPAIRABLE")
    similar = sum(classifications.count(category) for category in similarity_types)
    refused = len(ids) - exact - eligible
    require(similar <= refused and exact + eligible + refused == len(ids),
            "Typed drift subtype report does not conserve all old sources")
    require(all((proof / f"{stage}.pass").is_file()
                for stage in ("seed", "stabilize", "baseline", "candidate", "replay")),
            "Five independent Networks JVM boots not proven")
    normalization = (proof / "world-save-normalization-count.txt").read_text().strip()
    match = re.fullmatch(r"registered=([0-9]+);strict_cosmetic_normalizations=([0-9]+)", normalization)
    require(match is not None and int(match.group(1)) == len(ids)
            and 0 <= int(match.group(2)) <= len(ids),
            "Exact old-version native item stabilization report missing or invalid")
    message = (
        "PASS true published Networks v1.0.65 -> v1.0.66 identity across "
        "five independent JVM boots and the SAME two physical world chests\n"
        + normalization + "\n"
        f"source_ids={len(ids)} exact_native={exact} "
        f"strict_presentation_eligible={eligible} refused={refused} "
        f"similar_typed_equal={classifications.count(similarity_types[0])} "
        f"similar_typed_different={classifications.count(similarity_types[1])} "
        f"similar_typed_unreadable={classifications.count(similarity_types[2])}\n"
        "world_chest_written_only_in_seed=true; baseline_current_and_replay_read_only=true\n"
        "native_originals_and_owner_receipts_remain_private=true\n"
        "This does not authorize any production chest, machine, QuickShop, Cargo or SQL repair.\n")
    (root / "PASS.txt").write_text(message)
    print(message, flush=True)


if __name__ == "__main__":
    main()
