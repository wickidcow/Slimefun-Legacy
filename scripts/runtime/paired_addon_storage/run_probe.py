#!/usr/bin/env python3
"""Core-owned, fixed-source Networks/IE2 storage and orderly-restart gate."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import time
import uuid
import zipfile
from xml.etree import ElementTree

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT))
from scripts.runtime.entity_retirement.run_probe import validate_core
from scripts.runtime.paired_addon_storage.paired_database_snapshot import snapshot_database

# The historical storage fixture remains the default and cannot silently become a
# release candidate test. CI must opt into the separately pinned published pair.
PAIR_MODE = os.environ.get("SFL_PAIRED_LOCK_MODE", "historical")
LOCK_PATH = HERE / ("release-lock.json" if PAIR_MODE == "release" else "fixture-lock.json")
RECEIPT_SOURCE = HERE / "PairedStorageReceipt.java"
PHASE_RESULTS = {
    "seed": "PASS: seed real IE2 storage roundtrips=24 total=192 no-viewer=true "
            "topology=controller+2-monitors deduplicated=true rebuilt=true",
    "verify": "PASS: verify independent JVM rediscovered controller+2-monitors and withdrew exactly "
              "192 tagged items; synthetic unassigned balance was not relabelled",
}
BAD_LOG = re.compile(
    r"\[(?:[^\]\r\n]*/|\d{2}:\d{2}:\d{2} )?(?:ERROR|SEVERE)\]|\bPAIRED_STORAGE_RECEIPT FAIL\b|"
    r"\b(?:NoClassDefFoundError|NoSuchMethodError|LinkageError|AssertionError)\b|"
    r"Error occurred while (?:enabling|disabling)|Exception in thread|"
    r"(?:^|\s)(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?::|$)", re.MULTILINE)
MAX_LOG_BYTES = 16 * 1024 * 1024

# This init script reads Gradle's resolved classpaths; it changes no addon sources.
CLASSPATH_CHECK = r"""
gradle.projectsEvaluated {
    rootProject.tasks.register("verifyPairedCoreDependency") {
        doLast {
            def expected = System.getProperty("sfl.paired.core.sha256")
            def destination = System.getProperty("sfl.paired.classpath.receipt")
            if (!(expected ==~ /[0-9a-f]{64}/) || !destination) {
                throw new GradleException("Exact paired core checksum and receipt path are required")
            }
            def result = [schema: 1, expected_core_sha256: expected, classpaths: [:]]
            ["compileClasspath", "testRuntimeClasspath"].each { name ->
                def cores = rootProject.configurations.getByName(name).files.findAll { file ->
                    if (!file.isFile() || !file.name.endsWith(".jar")) return false
                    def archive = new java.util.zip.ZipFile(file)
                    try {
                        return archive.getEntry("io/github/thebusybiscuit/slimefun4/implementation/Slimefun.class") != null
                    } finally { archive.close() }
                }
                if (cores.size() != 1) throw new GradleException("Expected exactly one core in " + name)
                def bytes = cores.first().bytes
                def hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).encodeHex().toString()
                if (hash != expected) throw new GradleException("Wrong paired core in " + name)
                result.classpaths[name] = [sha256: hash, bytes: bytes.length]
            }
            new File(destination).withWriter("UTF-8") { writer ->
                writer.write(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(result)) + "\n")
            }
        }
    }
}
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def regular(path: Path) -> None:
    require(path.is_file() and not path.is_symlink(), "Expected a regular file: " + str(path))


def read_json(path: Path) -> dict:
    regular(path)
    require(path.stat().st_size <= MAX_LOG_BYTES, "Oversized JSON receipt")
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), "JSON object required")
    return value


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def file_identity(path: Path) -> dict:
    regular(path)
    return {"bytes": path.stat().st_size, "sha256": digest(path)}


def relative_file(root: Path, name: str) -> Path:
    relative = PurePosixPath(name)
    require(not relative.is_absolute() and ".." not in relative.parts and "\\" not in name,
            "Unsafe relative fixture path")
    path = root / relative
    require(path.absolute() == path.resolve() and path.resolve().is_relative_to(root.resolve()),
            "Symlink or escaped fixture input")
    regular(path)
    return path


def load_lock() -> dict:
    lock = read_json(LOCK_PATH)
    require(PAIR_MODE in {"historical", "release"}, "Unknown paired storage source mode")
    expected_purpose = ("exact-published-release-storage-pair"
                        if PAIR_MODE == "release" else "integration-test-fixture")
    require(lock.get("schema") == 1 and lock.get("purpose") == expected_purpose
            and lock.get("distribution_selection") is (PAIR_MODE == "release"),
            "Paired storage lock purpose or distribution mode mismatch")
    if PAIR_MODE == "release":
        # The native release candidate may select only immutable published
        # sources and byte receipts, never branch heads or historical fixtures.
        published = read_json(ROOT / "scripts/published_storage_pair_lock.json")
        require(published.get("purpose") == "published-distribution"
                and published.get("distribution_selection") is True
                and lock.get("distribution_lock_path") == "scripts/published_storage_pair_lock.json"
                and lock.get("addons") == published.get("addons"),
                "Native release pair differs from immutable distribution lock")
    require(set(lock.get("addons", {})) == {"networks", "ie2"}, "Exactly two test addon pins are required")
    for addon in lock["addons"].values():
        require(re.fullmatch(r"[\w.-]+/[\w.-]+", addon["repository"]) is not None
                and re.fullmatch(r"[0-9a-f]{40}", addon["source"]) is not None
                and re.fullmatch(r"[0-9a-f]{40}", addon["tree"]) is not None
                and re.fullmatch(r"[\w.+-]+", addon["version"]) is not None, "Invalid addon test pin")
        published = addon.get("published_artifact")
        require(isinstance(published, dict) and set(published) == {"url", "bytes", "sha256"},
                "Published addon artifact identity is required")
        require(published["url"] == "https://github.com/" + addon["repository"]
                + "/releases/download/v" + addon["version"] + "/" + PurePosixPath(addon["jar"]).name,
                "Unexpected published addon URL")
        require(type(published["bytes"]) is int and published["bytes"] > 0
                and isinstance(published["sha256"], str)
                and re.fullmatch(r"[0-9a-f]{64}", published["sha256"]) is not None,
                "Invalid published addon size or digest")
    platforms = lock.get("platforms", {})
    require(set(platforms) == {"purpur-1.21.11-2568", "purpur-26.3-2652"}, "Unreviewed native platform")
    for platform in platforms.values():
        require(platform["software"] == "Purpur"
                and platform["url"] == "https://api.purpurmc.org/v2/purpur/"
                + platform["minecraft"] + "/" + platform["build"] + "/download",
                "Unexpected native download URL")
        for key in ("launcher_sha256", "mojang_sha256"):
            require(re.fullmatch(r"[0-9a-f]{64}", platform[key]) is not None, "Invalid native digest")
    require(len(lock.get("fixture_sources", [])) == 2, "Both original Java fixture sources are required")
    return lock


def new_temporary_directory(path: Path) -> Path:
    root_value = os.environ.get("RUNNER_TEMP")
    require(bool(root_value), "RUNNER_TEMP must identify the disposable runner directory")
    root = Path(root_value).resolve(strict=True)
    absolute = path.absolute()
    require(absolute == absolute.resolve() and absolute.is_relative_to(root) and absolute != root,
            "The new fixture must be strictly inside RUNNER_TEMP without symlinks")
    require(not absolute.exists() and not absolute.is_symlink() and absolute.parent.is_dir(),
            "Refusing a preexisting fixture or missing parent directory")
    absolute.mkdir()
    return absolute


def append_environment(path: Path, values: dict[str, str]) -> None:
    for value in values.values():
        require("\n" not in value and "\r" not in value, "Unsafe environment value")
    with path.open("a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(key + "=" + value + "\n")


def checkout_identity(path: Path, pin: dict) -> dict:
    def git(*args: str) -> str:
        return subprocess.check_output(["git", "-C", str(path), *args], text=True).strip()
    source, tree = git("rev-parse", "HEAD"), git("rev-parse", "HEAD^{tree}")
    require(source == pin["source"] and tree == pin["tree"], "External checkout source/tree mismatch")
    for args in (["diff", "--quiet"], ["diff", "--cached", "--quiet"]):
        require(subprocess.run(["git", "-C", str(path), *args], check=False).returncode == 0,
                "Tracked external source was modified")
    return {"repository": pin["repository"], "source": source, "tree": tree}


def validate_jar(path: Path, name: str, version: str) -> dict:
    regular(path)
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and archive.testzip() is None, "Invalid or duplicate JAR entries")
        require(all(not PurePosixPath(n).is_absolute() and ".." not in PurePosixPath(n).parts
                    and "\\" not in n for n in names), "Unsafe JAR path")
        descriptor = archive.read("plugin.yml").decode("utf-8")
        for key, expected in (("name", name), ("version", version)):
            match = re.search(r"^" + key + r":\s*['\"]?([^'\"\s]+)['\"]?\s*$", descriptor, re.MULTILINE)
            require(match is not None and match.group(1) == expected, "JAR descriptor mismatch: " + key)
        classes = 0
        for entry in archive.infolist():
            if entry.filename.endswith(".class"):
                with archive.open(entry) as content:
                    header = content.read(8)
                require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe"
                        and int.from_bytes(header[6:8], "big") <= 65, "Non-universal Java bytecode")
                classes += 1
        require(classes > 0, "Empty plugin JAR")
    return {**file_identity(path), "plugin": name, "version": version, "classes": classes}


def validate_published_jar(path: Path, pin: dict) -> dict:
    details = validate_jar(path, pin["plugin"], pin["version"])
    published = pin["published_artifact"]
    require(all(details[field] == published[field] for field in ("bytes", "sha256")),
            "Published addon bytes differ from the reviewed artifact")
    return details


def verify_fixture_sources(directory: Path, lock: dict, packaged: bool = False) -> list[dict]:
    result = []
    for pin in lock["fixture_sources"]:
        name = "fixture/" + PurePosixPath(pin["path"]).name if packaged else pin["path"]
        source = relative_file(directory, name)
        require(digest(source) == pin["sha256"], "Original Java fixture source changed")
        result.append({"file": "fixture/" + source.name, **file_identity(source)})
    return result


def validate_classpath_receipt(path: Path, core_hash: str, core_bytes: int) -> dict:
    proof = read_json(path)
    require(proof.get("schema") == 1 and proof.get("expected_core_sha256") == core_hash,
            "Classpath proof core mismatch")
    require(set(proof.get("classpaths", {})) == {"compileClasspath", "testRuntimeClasspath"},
            "Both compile and test classpaths must be verified")
    require(all(value == {"sha256": core_hash, "bytes": core_bytes}
                for value in proof["classpaths"].values()), "Wrong core resolved for an addon build")
    return file_identity(path)


def prepare_inputs(args: argparse.Namespace, lock: dict) -> None:
    version = validate_core(args.core, read_json(args.core_metadata), args.expected_source)
    require(subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "HEAD"], text=True).strip()
            == args.expected_source, "Core checkout differs from the workflow candidate")
    checkouts = {key: checkout_identity(getattr(args, key + "_checkout"), pin)
                 for key, pin in lock["addons"].items()}
    verify_fixture_sources(args.networks_checkout, lock)
    output = new_temporary_directory(args.output_dir)
    maven = new_temporary_directory(args.maven_dir)
    shutil.copyfile(args.core, output / "core.jar")
    shutil.copyfile(args.core_metadata, output / "core-metadata.json")
    coordinate = "com.github.slimefun:Slimefun:" + version
    artifact = maven / "com/github/slimefun/Slimefun" / version
    artifact.mkdir(parents=True)
    staged = artifact / ("Slimefun-" + version + ".jar")
    shutil.copyfile(args.core, staged)
    require(digest(staged) == digest(args.core), "Local Maven core bytes changed")
    project = ElementTree.Element("project", xmlns="http://maven.apache.org/POM/4.0.0")
    for key, value in (("modelVersion", "4.0.0"), ("groupId", "com.github.slimefun"),
                       ("artifactId", "Slimefun"), ("version", version), ("packaging", "jar")):
        ElementTree.SubElement(project, key).text = value
    ElementTree.ElementTree(project).write(artifact / ("Slimefun-" + version + ".pom"),
                                         encoding="utf-8", xml_declaration=True)
    (output / "verify-core-classpath.gradle").write_text(CLASSPATH_CHECK, encoding="utf-8")
    write_json(output / "preparation.json", {
        "schema": 1, "source": args.expected_source, "core_version": version,
        "core_sha256": digest(args.core), "lock_sha256": digest(LOCK_PATH),
        "local_maven_core_sha256": digest(staged), "addon_checkouts": checkouts})
    append_environment(args.github_env, {
        "PAIRED_CORE_JAR": str(output / "core.jar"), "PAIRED_CORE_COORDINATE": coordinate,
        "PAIRED_CORE_SHA256": digest(args.core), "PAIRED_MAVEN_REPO": str(maven),
        "PAIRED_INPUTS_DIR": str(output)})


def package_inputs(args: argparse.Namespace, lock: dict) -> None:
    output = args.inputs_dir.resolve(strict=True)
    prepared = read_json(output / "preparation.json")
    version = validate_core(output / "core.jar", read_json(output / "core-metadata.json"), args.expected_source)
    require(prepared.get("source") == args.expected_source and prepared.get("lock_sha256") == digest(LOCK_PATH)
            and prepared.get("core_sha256") == digest(output / "core.jar")
            and prepared.get("local_maven_core_sha256") == digest(output / "core.jar"),
            "Prepared inputs changed")
    manifest = {"schema": 1, "purpose": lock["purpose"], "source": args.expected_source,
                "core_version": version, "lock_sha256": digest(LOCK_PATH),
                "receipt_source_sha256": digest(RECEIPT_SOURCE), "inputs": {},
                "compiled_inputs": {}, "addon_checkouts": {}, "classpath_receipts": {}}
    manifest["inputs"]["core"] = {"file": "core.jar", **validate_jar(output / "core.jar", "Slimefun", version)}
    (output / "compiled").mkdir()
    for key, pin in lock["addons"].items():
        checkout = getattr(args, key + "_checkout")
        identity = checkout_identity(checkout, pin)
        require(identity == prepared["addon_checkouts"][key], "Addon source changed during its build")
        source = relative_file(checkout, pin["jar"])
        details = validate_jar(source, pin["plugin"], pin["version"])
        compiled = output / "compiled" / (key + ".jar")
        shutil.copyfile(source, compiled)
        require(file_identity(compiled) == file_identity(source), "Copied addon differs from the build")
        manifest["compiled_inputs"][key] = {"file": "compiled/" + compiled.name, **details}
        published = relative_file(output, "published/" + key + ".jar")
        details = validate_published_jar(published, pin)
        target = output / (key + ".jar")
        require(not target.exists() and not target.is_symlink(), "Refusing an existing packaged addon")
        shutil.copyfile(published, target)
        require(file_identity(target) == file_identity(published), "Copied addon differs from the publication")
        manifest["inputs"][key] = {"file": target.name, **details}
        manifest["addon_checkouts"][key] = identity
        manifest["classpath_receipts"][key] = validate_classpath_receipt(
            output / (key + "-core-classpath.json"), prepared["core_sha256"],
            manifest["inputs"]["core"]["bytes"])
    jeg = relative_file(args.networks_checkout, lock["jeg_compile_dependency"]["jar"])
    require(digest(jeg) == lock["jeg_compile_dependency"]["sha256"], "JEG compile dependency mismatch")
    manifest["jeg_compile_dependency"] = file_identity(jeg)
    (output / "fixture").mkdir()
    for entry in lock["fixture_sources"]:
        source = relative_file(args.networks_checkout, entry["path"])
        shutil.copyfile(source, output / "fixture" / source.name)
    manifest["fixture_sources"] = verify_fixture_sources(output, lock, packaged=True)
    shutil.copyfile(LOCK_PATH, output / "fixture-lock.json")
    write_json(output / "inputs.json", manifest)


def validate_compiled_inputs(directory: Path, manifest: dict, lock: dict) -> None:
    require(set(manifest.get("compiled_inputs", {})) == {"networks", "ie2"},
            "Both compiled addon artifacts are required")
    for key, pin in lock["addons"].items():
        compiled = manifest["compiled_inputs"][key]
        compiled_name = "compiled/" + key + ".jar"
        require(isinstance(compiled, dict) and compiled.get("file") == compiled_name,
                "Unexpected compiled input name")
        require(all(type(compiled.get(field)) is int and compiled[field] > 0 for field in ("bytes", "classes")),
                "Invalid compiled input size or class count")
        require(compiled == {"file": compiled_name, **validate_jar(
            relative_file(directory, compiled_name), pin["plugin"], pin["version"])},
            "Compiled addon bytes or descriptor changed")


def validate_inputs(directory: Path, expected_source: str, lock: dict) -> dict:
    manifest = read_json(directory / "inputs.json")
    require(manifest.get("schema") == 1 and manifest.get("purpose") == lock["purpose"]
            and manifest.get("source") == expected_source, "Paired input source mismatch")
    require(manifest.get("lock_sha256") == digest(LOCK_PATH) == digest(directory / "fixture-lock.json")
            and manifest.get("receipt_source_sha256") == digest(RECEIPT_SOURCE), "Fixture code/lock mismatch")
    version = validate_core(directory / "core.jar", read_json(directory / "core-metadata.json"), expected_source)
    require(manifest.get("core_version") == version and set(manifest.get("inputs", {}))
            == {"core", "networks", "ie2"}, "Incomplete paired inputs")
    validate_compiled_inputs(directory, manifest, lock)
    for key, name, expected_version in [("core", "Slimefun", version)] + [
            (key, pin["plugin"], pin["version"]) for key, pin in lock["addons"].items()]:
        row = manifest["inputs"][key]
        require(row.get("file") == key + ".jar", "Unexpected packaged input name")
        details = (validate_jar(directory / row["file"], name, expected_version) if key == "core"
                   else validate_published_jar(directory / row["file"], lock["addons"][key]))
        require(row == {"file": key + ".jar", **details},
                "Packaged input bytes or descriptor changed")
    for key, pin in lock["addons"].items():
        require(manifest["addon_checkouts"].get(key)
                == {field: pin[field] for field in ("repository", "source", "tree")}, "Addon provenance mismatch")
        require(manifest["classpath_receipts"].get(key) == validate_classpath_receipt(
            directory / (key + "-core-classpath.json"), manifest["inputs"]["core"]["sha256"],
            manifest["inputs"]["core"]["bytes"]), "Addon classpath proof changed")
    require(manifest.get("fixture_sources") == verify_fixture_sources(directory, lock, packaged=True),
            "Packaged fixture source receipt changed")
    require(manifest.get("jeg_compile_dependency", {}).get("sha256")
            == lock["jeg_compile_dependency"]["sha256"], "Compile dependency provenance changed")
    return manifest


def download(url: str, target: Path, expected: str) -> None:
    require(not target.exists() and not target.is_symlink() and target.parent.is_dir(),
            "Download requires a new destination")
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "2",
                    "--retry-all-errors", "--retry-delay", "1", "--max-time", "240",
                    url, "--output", str(target)], check=True, timeout=750)
    require(digest(target) == expected, "Downloaded input checksum mismatch")


def compile_probes(java_home: Path, work: Path, inputs: Path, native_jars: list[Path]) -> dict:
    sources = [inputs / row["file"] for row in verify_fixture_sources(inputs, load_lock(), packaged=True)]
    classes = work / "probe-classes"
    classes.mkdir()
    classpath = [inputs / "core.jar", inputs / "networks.jar", *native_jars]
    command = [str(java_home / "bin/javac"), "--release", "21", "-encoding", "UTF-8",
               "-cp", os.pathsep.join(map(str, classpath)), "-d", str(classes),
               *map(str, sources), str(RECEIPT_SOURCE)]
    compiled = subprocess.run(command, capture_output=True, text=True, timeout=120)
    (work / "compile.log").write_text(compiled.stdout + compiled.stderr, encoding="utf-8")
    require(compiled.returncode == 0, "Paired fixture compilation failed; inspect compile.log")
    descriptors = {
        "PairedIE2StorageProbe": "name: PairedIE2StorageProbe\nversion: 1.0\n"
            "main: networksprobe.PairedIE2StorageProbe\napi-version: '1.21'\n"
            "depend: [Slimefun, Networks, InfinityExpansion2]\n",
        "PairedStorageReceipt": "name: PairedStorageReceipt\nversion: 1.0\n"
            "main: networksprobe.receipt.PairedStorageReceipt\napi-version: '1.21'\n"
            "depend: [Slimefun, Networks, InfinityExpansion2, PairedIE2StorageProbe]\n",
    }
    result = {}
    for name, descriptor in descriptors.items():
        target = work / "plugins" / (name + ".jar")
        with zipfile.ZipFile(target, "x", compression=zipfile.ZIP_DEFLATED) as archive:
            entries = {"plugin.yml": descriptor.encode("utf-8")}
            for path in sorted(classes.rglob("*.class")):
                relative = path.relative_to(classes).as_posix()
                receipt = relative.startswith("networksprobe/receipt/")
                if receipt == (name == "PairedStorageReceipt"):
                    entries[relative] = path.read_bytes()
            require(len(entries) > 1, "Compiled probe classes are missing")
            for relative, content in sorted(entries.items()):
                info = zipfile.ZipInfo(relative, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, content)
        result[target.relative_to(work).as_posix()] = file_identity(target)
    return result


def read_log(path: Path) -> str:
    regular(path)
    require(path.stat().st_size <= MAX_LOG_BYTES, "Native output exceeded its evidence bound")
    return path.read_text(encoding="utf-8", errors="replace")


def validate_phase_evidence(phase: str, token: str, pid: int, exit_code: int, timed_out: bool,
                            result: str, started: dict, stopped: dict, native: str, console: str,
                            manifest: dict, platform: dict) -> None:
    require(phase in PHASE_RESULTS and type(pid) is int and pid > 0, "Invalid completed phase identity")
    require(not timed_out and exit_code == 0, "Native phase timed out or exited abnormally")
    require(result == PHASE_RESULTS[phase], "Original paired storage assertions did not all pass")
    for proof, status in ((started, "READY"), (stopped, "STOPPED")):
        require(proof.get("schema") == 1 and proof.get("phase") == phase and proof.get("token") == token
                and proof.get("pid") == pid and proof.get("status") == status, "Native phase receipt mismatch")
        require(proof.get("core_source") == manifest["source"] == proof.get("core_commit")
                and proof.get("core_version") == manifest["core_version"]
                and proof.get("networks_version") == manifest["inputs"]["networks"]["version"]
                and proof.get("ie2_version") == manifest["inputs"]["ie2"]["version"],
                "Loaded plugin identity mismatch")
        require(proof.get("software") == platform["software"]
                and proof.get("minecraft") == platform["minecraft"]
                and proof.get("server_version", "").startswith(platform["minecraft"] + "-" + platform["build"] + "-")
                and proof.get("primary_thread") is True, "Loaded native platform or ownership mismatch")
    require(stopped.get("server_stopping") is True, "Missing normal server shutdown receipt")
    require(not BAD_LOG.search(native) and not BAD_LOG.search(console), "Unexpected native/console runtime error")
    markers = ["PAIRED_STORAGE_RECEIPT READY phase=" + phase + " token=" + token,
               "Done (", PHASE_RESULTS[phase], "Stopping server",
               "PAIRED_STORAGE_RECEIPT STOPPED phase=" + phase + " token=" + token]
    positions = [native.find(marker) for marker in markers]
    require(all(position >= 0 for position in positions) and positions == sorted(positions),
            "Fresh native lifecycle/phase markers are missing or out of order")


def validate_database_evidence(snapshot: dict, phase: str, pid: int) -> None:
    """Check the fixed fixture's committed state after its JVM has fully stopped."""
    require(phase in PHASE_RESULTS, "Unknown post-exit database phase")
    require(snapshot.get("schema") == 1 and snapshot.get("phase") == phase
            and snapshot.get("pid") == pid and snapshot.get("child_returncode") == 0
            and snapshot.get("status") == "PASS_READONLY_POST_PROCESS_DATABASE_SNAPSHOT"
            and snapshot.get("source_unchanged") is True and snapshot.get("raw_copies_unchanged") is True,
            "Finished-phase database evidence is invalid")
    require(snapshot.get("helper_sha256") == digest(HERE / "paired_database_snapshot.py")
            and snapshot.get("original_database_opened_by_sqlite") is False
            and snapshot.get("sqlite_uri_mode") == "ro" and snapshot.get("sqlite_immutable") is False,
            "Database evidence must preserve the exact read-only, WAL-aware capture")
    source = snapshot.get("source_before")
    require(isinstance(source, dict) and "block-storage.db" in source
            and all(snapshot.get(key) == source for key in ("source_after", "raw_before", "raw_after")),
            "Database source/raw copy identity changed")
    database = snapshot.get("database", {})
    require(database.get("table_counts") == {
        "block_record": 4, "block_data": 4, "block_inventory": 2 if phase == "seed" else 1,
        "chunk_data": 0, "universal_record": 0, "universal_data": 0,
        "universal_inventory": 0, "table_metadata": 1,
    }, "Post-exit database row counts violate the fixed fixture ledger")
    storage = "fixture;160:100:160"
    monitors = ("fixture;160:100:161", "fixture;161:100:160")
    records = [{"loc": loc, "chunk": "fixture;10:10", "sf_id": item} for loc, item in (
        (storage, "IE_STORAGE_UNIT_1"), (monitors[0], "NTW_MONITOR"),
        (monitors[1], "NTW_MONITOR"), ("fixture;161:100:161", "NTW_CONTROLLER"))]
    require(database.get("block_records") == records and database.get("universal_records") == [],
            "Post-exit storage/topology record identities changed")
    data = [{"loc": storage, "data_key": "stored_amount", "data_val": "MTI4" if phase == "seed" else "MA=="},
            {"loc": storage, "data_key": "void_excess", "data_val": "ZmFsc2U="},
            *[{"loc": loc, "data_key": "direction", "data_val": "U0VMRg=="} for loc in monitors]]
    require(database.get("block_data") == data, "Post-exit balance or topology data changed")
    inventory = database.get("block_inventory", [])
    require([(row.get("loc"), row.get("slot")) for row in inventory]
            == [(storage, slot) for slot in ([13, 16] if phase == "seed" else [13])],
            "Post-exit inventory slots violate the fixed fixture ledger")
    require(all(type(row.get("bytes")) is int and row["bytes"] > 4
                and row.get("prefix_hex") == "53463200"
                and re.fullmatch(r"[0-9a-f]{64}", row.get("sha256", "")) is not None for row in inventory)
            and database.get("details_truncated") == {key: False for key in (
                "block_record", "block_data", "block_inventory", "universal_record")},
            "Post-exit inventory evidence is incomplete")


def stop_timed_out(process: subprocess.Popen) -> None:
    try:
        process.communicate(input=b"stop\n", timeout=30)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=15)


def run_phase(java: Path, work: Path, phase: str, manifest: dict, platform: dict,
              bound_files: dict, timeout: float = 240) -> dict:
    result_file = work / "plugins/paired-ie2-result.txt"
    companion = work / "plugins/PairedStorageReceipt"
    for stale in [result_file, companion / "startup.json", companion / "shutdown.json", work / "logs/latest.log"]:
        require(not stale.is_symlink(), "Symlink in fresh phase evidence")
        stale.unlink(missing_ok=True)
    (work / "plugins/paired-ie2-phase.txt").write_text(phase + "\n", encoding="utf-8")
    token = str(uuid.uuid4())
    console_file = work / (phase + "-console.log")
    timed_out = False
    before = {name: file_identity(work / name) for name in bound_files}
    require(before == bound_files, "Installed inputs changed before the next JVM")
    with console_file.open("x", encoding="utf-8") as console:
        process = subprocess.Popen([str(java), "-Dnetworks.paired.probe=disposable",
            "-Dsfl.paired.phase=" + phase, "-Dsfl.paired.token=" + token,
            "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Xms256M", "-Xmx1800M",
            "-jar", "server.jar", "--nogui"], cwd=work, stdin=subprocess.PIPE,
            stdout=console, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + timeout
        try:
            process.wait(timeout=timeout)
            timed_out = time.monotonic() >= deadline
        except subprocess.TimeoutExpired:
            timed_out = True
            stop_timed_out(process)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=15)
            if process.stdin is not None and not process.stdin.closed:
                process.stdin.close()
    # No log/world/database observation happens while this JVM is alive.
    proof = {"schema": 1, "phase": phase, "token": token, "pid": process.pid,
             "exit_code": process.returncode, "timed_out": timed_out, "status": "FAIL",
             "installed_before": before}
    problem = None
    try:
        proof["database_snapshot"] = snapshot_database(work, phase, process.pid, process.returncode)
    except (Exception, SystemExit) as error:
        problem = "Post-exit snapshot: " + str(error)
    try:
        native_path = work / "logs/latest.log"
        archived_native = work / (phase + "-native.log")
        if native_path.is_file():
            shutil.copyfile(native_path, archived_native)
        for name in ("startup", "shutdown"):
            source = companion / (name + ".json")
            if source.is_file():
                shutil.copyfile(source, work / (phase + "-" + name + ".json"))
        if result_file.is_file():
            shutil.copyfile(result_file, work / (phase + "-result.txt"))
        proof["installed_after"] = {name: file_identity(work / name) for name in bound_files}
        require(proof["installed_after"] == bound_files, "Installed artifacts changed during the JVM")
        started = read_json(work / (phase + "-startup.json"))
        stopped = read_json(work / (phase + "-shutdown.json"))
        native, captured = read_log(archived_native), read_log(console_file)
        result = read_log(work / (phase + "-result.txt")).strip()
        proof.update(startup=started, shutdown=stopped, result=result,
                     native_log=file_identity(archived_native), console_log=file_identity(console_file))
        validate_phase_evidence(phase, token, process.pid, process.returncode, timed_out,
                                result, started, stopped, native, captured, manifest, platform)
        require(problem is None, problem or "Post-exit database evidence failed")
        snapshot = proof["database_snapshot"]
        require(snapshot.get("inputs_receipt_sha256") == digest(work / "inputs.json"),
                "Database snapshot input provenance mismatch")
        validate_database_evidence(snapshot, phase, process.pid)
        proof["status"] = "PASS"
    except Exception as error:
        problem = (problem + "; " if problem else "") + str(error)
    if problem:
        proof["error"] = problem
    write_json(work / (phase + "-verification.json"), proof)
    require(proof["status"] == "PASS", phase + " failed; inspect its complete phase evidence")
    return proof


def run_native(args: argparse.Namespace, lock: dict) -> None:
    require(args.allow_disposable_writes, "Explicit disposable fixture authorization is required")
    inputs = args.inputs_dir.resolve(strict=True)
    manifest = validate_inputs(inputs, args.expected_source, lock)
    require(args.platform in lock["platforms"], "Unreviewed native platform")
    platform = lock["platforms"][args.platform]
    regular(args.server_jar)
    require(digest(args.server_jar) == platform["launcher_sha256"], "Native launcher mismatch")
    java_home = args.java_home.resolve(strict=True)
    regular(java_home / "bin/java")
    regular(java_home / "bin/javac")
    work = new_temporary_directory(args.work_dir)
    receipt = {"schema": 1, "status": "FAIL", "source": args.expected_source, "platform": platform,
               "run_id": str(uuid.uuid4()), "input_manifest_sha256": digest(inputs / "inputs.json"),
               "scope": "Original physical storage/conservation assertions and two orderly JVMs; no Folia/client/crash claim"}
    write_json(work / "inputs.json", manifest)
    write_json(work / "verification.json", receipt)
    try:
        shutil.copyfile(args.server_jar, work / "server.jar")
        require(file_identity(work / "server.jar") == file_identity(args.server_jar)
                and digest(work / "server.jar") == platform["launcher_sha256"],
                "Installed native launcher differs from the pinned input")
        with zipfile.ZipFile(work / "server.jar") as archive:
            expected, url, filename = archive.read("META-INF/download-context").decode().strip().split("\t")
        require(expected == platform["mojang_sha256"] and Path(filename).name == filename
                and url.startswith("https://piston-data.mojang.com/"), "Unreviewed launcher cache contract")
        (work / "cache").mkdir()
        download(url, work / "cache" / filename, expected)
        with (work / "patch.log").open("x", encoding="utf-8") as log:
            subprocess.run([str(java_home / "bin/java"), "-Dpaperclip.patchonly=true", "-jar", "server.jar"],
                           cwd=work, stdout=log, stderr=subprocess.STDOUT, timeout=180, check=True)
        plugins = work / "plugins"
        (plugins / "bStats").mkdir(parents=True)
        (plugins / "bStats/config.yml").write_text("enabled: false\n", encoding="utf-8")
        for key, name in (("core", "Slimefun.jar"), ("networks", "Networks.jar"), ("ie2", "InfinityExpansion2.jar")):
            shutil.copyfile(inputs / manifest["inputs"][key]["file"], plugins / name)
            require(file_identity(plugins / name)
                    == {field: manifest["inputs"][key][field] for field in ("bytes", "sha256")},
                    "Installed plugin differs from the exact tested input: " + key)
        native_jars = sorted((work / "libraries").rglob("*.jar")) + sorted((work / "versions").rglob("*.jar"))
        require(len(native_jars) > 2, "Native compilation classpath is incomplete")
        compile_probes(java_home, work, inputs, native_jars)
        (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        settings = {"server-ip": "127.0.0.1", "server-port": 0, "online-mode": "false", "level-name": "fixture",
                    "level-type": "minecraft:flat", "generate-structures": "false", "view-distance": 2,
                    "simulation-distance": 2, "enable-rcon": "false", "enable-query": "false",
                    "pause-when-empty-seconds": -1,
                    "generator-settings": json.dumps({"biome": "minecraft:plains", "features": False, "lakes": False,
                        "layers": [{"block": "minecraft:bedrock", "height": 1},
                                   {"block": "minecraft:dirt", "height": 2},
                                   {"block": "minecraft:grass_block", "height": 1}], "structure_overrides": []},
                        separators=(",", ":"))}
        (work / "server.properties").write_text("".join(str(k) + "=" + str(v) + "\n" for k, v in settings.items()),
                                                encoding="utf-8")
        bound = {path.relative_to(work).as_posix(): file_identity(path)
                 for path in [work / "server.jar", *sorted(plugins.glob("*.jar"))]}
        receipt["bound_files"] = bound
        receipt["seed"] = run_phase(java_home / "bin/java", work, "seed", manifest, platform, bound)
        receipt["verify"] = run_phase(java_home / "bin/java", work, "verify", manifest, platform, bound)
        require(receipt["seed"]["pid"] != receipt["verify"]["pid"], "Separate JVM identifiers are required")
        validate_inputs(inputs, args.expected_source, lock)
        receipt["status"] = "PASS"
    except (Exception, SystemExit) as error:
        receipt["error"] = type(error).__name__ + ": " + str(error)
        raise
    finally:
        write_json(work / "verification.json", receipt)
    print("PASS: exact-core paired storage and two-JVM reload on " + args.platform, flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pins = commands.add_parser("pins")
    pins.add_argument("--github-env", type=Path, required=True)
    pins.add_argument("--github-output", type=Path, required=True)
    prepare = commands.add_parser("prepare")
    prepare.add_argument("--core", type=Path, required=True)
    prepare.add_argument("--core-metadata", type=Path, required=True)
    prepare.add_argument("--maven-dir", type=Path, required=True)
    prepare.add_argument("--output-dir", type=Path, required=True)
    prepare.add_argument("--github-env", type=Path, required=True)
    package = commands.add_parser("package")
    package.add_argument("--inputs-dir", type=Path, required=True)
    for command in (prepare, package):
        command.add_argument("--expected-source", required=True)
        command.add_argument("--networks-checkout", type=Path, required=True)
        command.add_argument("--ie2-checkout", type=Path, required=True)
    fetch = commands.add_parser("download-server")
    fetch.add_argument("--platform", required=True)
    fetch.add_argument("--destination", type=Path, required=True)
    native = commands.add_parser("run")
    native.add_argument("--inputs-dir", type=Path, required=True)
    native.add_argument("--expected-source", required=True)
    native.add_argument("--platform", required=True)
    native.add_argument("--server-jar", type=Path, required=True)
    native.add_argument("--work-dir", type=Path, required=True)
    native.add_argument("--java-home", type=Path, default=Path(os.environ.get("JAVA_HOME", "")))
    native.add_argument("--allow-disposable-writes", action="store_true")
    args = parser.parse_args()
    lock = load_lock()
    if args.command == "pins":
        values = {}
        for key, pin in lock["addons"].items():
            values[key.upper() + "_FIXTURE_REPOSITORY"] = pin["repository"]
            values[key.upper() + "_FIXTURE_HEAD"] = pin["source"]
            values[key.upper() + "_FIXTURE_VERSION"] = pin["version"]
            values[key.upper() + "_FIXTURE_JAR"] = pin["jar"]
        values["JEG_COMPILE_JAR"] = lock["jeg_compile_dependency"]["jar"]
        append_environment(args.github_env, values)
        append_environment(args.github_output, {"platforms": json.dumps(list(lock["platforms"]))})
    elif args.command == "prepare":
        prepare_inputs(args, lock)
    elif args.command == "package":
        package_inputs(args, lock)
    elif args.command == "download-server":
        require(args.platform in lock["platforms"], "Unreviewed native platform")
        platform = lock["platforms"][args.platform]
        download(platform["url"], args.destination, platform["launcher_sha256"])
    else:
        run_native(args, lock)


if __name__ == "__main__":
    main()
