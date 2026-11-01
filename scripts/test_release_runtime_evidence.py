"""Offline controls for exact release/native source and byte binding."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import io
import json
from pathlib import Path
import shutil
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid
import zipfile

from scripts.runtime.paired_addon_storage import run_probe as paired
from scripts.test_paired_addon_storage_probe import database_receipt
from scripts.verify_release_runtime_evidence import (
    ADDONS, FIXED_JOBS, REPOSITORY, WORKFLOW, ReleaseRuntimeEvidenceError, verify_native_evidence,
)

SOURCE = "66ef9d066eb051d4101b49d67ae3b982d40bda2c"
# Historical test fixtures and canonical distribution at SOURCE. Keep this mismatch
# as a regression control even when the current fixture lock advances.
TEST_SOURCES = {
    "networks": "4e954b87a03ac84fc5357c49c315d155e8435e78",
    "ie2": "1ee9e3a5566886d14f2cc1a9a0ea0ce55b400a27",
}
CANONICAL_SOURCES = {
    "networks": "560d590095206a8159d82c70e7cabe6d16aaa0d8",
    "ie2": "8ce79a3fac44424b3fca3d7b3fe7c48c2767fe12",
}
HISTORICAL_PINS = {
    "networks": {"version": "1.0.59", "tree": "6c04aba17d48fd8397d5c071f60f80807069565c",
                 "jar": "build/libs/SF_Networks1.0.59.jar"},
    "ie2": {"version": "2.0.14", "tree": "e96502ae27f99f585c2cdb134d377a1cb51ab291",
            "jar": "build/libs/SF_InfinityExpansion2.0.14.jar"},
}


def encoded(value):
    return (json.dumps(value, indent=2) + "\n").encode()


def identity(value):
    return {"bytes": len(value), "sha256": hashlib.sha256(value).hexdigest()}


def write_json(path, value):
    path.write_bytes(encoded(value))


def plugin_jar(name, version, marker="test", source=None):
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.writestr("plugin.yml", f"name: {name}\nversion: {version}\n")
        jar.writestr("test/Marker.class", bytes.fromhex("cafebabe00000041") + marker.encode())
        if source is not None:
            jar.writestr("git.properties", f"git.source.commit={source}\ngit.commit.id={source}\ngit.build.version={version}\n")
    return data.getvalue()


class Fixture:
    def __init__(self, root, *, historical_pair=False):
        self.root = root
        root.mkdir(parents=True, exist_ok=True)
        self.evidence = root / "evidence"
        self.native = self.evidence / "native"
        self.inputs = self.native / "paired-storage-inputs"
        self.inputs.mkdir(parents=True)
        self.core = root / "core.jar"
        self.bundle = root / "bundle.zip"
        self.matrix_path = root / "matrix.json"
        self.lock_path = root / "fixture-lock.json"
        self.lock = json.loads(paired.LOCK_PATH.read_text())
        if historical_pair:
            for key, pin in self.lock["addons"].items():
                pin.update(HISTORICAL_PINS[key], source=TEST_SOURCES[key])
                pin["published_artifact"]["url"] = ("https://github.com/" + pin["repository"]
                    + "/releases/download/v" + pin["version"] + "/" + Path(pin["jar"]).name)
        self.jars = {"core": plugin_jar("Slimefun", "4.1.76", source=SOURCE), **{
            key: plugin_jar(pin["plugin"], pin["version"])
            for key, pin in self.lock["addons"].items()}}
        self.compiled_jars = {
            key: plugin_jar(pin["plugin"], pin["version"], marker="compiled against candidate core")
            for key, pin in self.lock["addons"].items()}
        self.core.write_bytes(self.jars["core"])
        for key, value in self.jars.items():
            (self.inputs / (key + ".jar")).write_bytes(value)
        (self.inputs / "compiled").mkdir()
        for key, value in self.compiled_jars.items():
            (self.inputs / "compiled" / (key + ".jar")).write_bytes(value)
            self.lock["addons"][key]["published_artifact"].update(identity(self.jars[key]))
        (self.inputs / "fixture").mkdir()
        for index, row in enumerate(self.lock["fixture_sources"]):
            value = ("// synthetic original fixture " + str(index) + "\n").encode()
            row["sha256"] = identity(value)["sha256"]
            (self.inputs / "fixture" / Path(row["path"]).name).write_bytes(value)
        write_json(self.lock_path, self.lock)
        (self.inputs / "fixture-lock.json").write_bytes(self.lock_path.read_bytes())
        metadata = {"schema": 1, "source_commit": SOURCE, "core_version": "4.1.76",
                    "core_sha256": identity(self.jars["core"])["sha256"]}
        write_json(self.inputs / "core-metadata.json", metadata)
        self.manifest = {
            "schema": 1, "purpose": "integration-test-fixture", "source": SOURCE,
            "core_version": "4.1.76", "lock_sha256": identity(self.lock_path.read_bytes())["sha256"],
            "receipt_source_sha256": paired.digest(paired.RECEIPT_SOURCE),
            "inputs": {}, "compiled_inputs": {}, "addon_checkouts": {}, "classpath_receipts": {},
        }
        for key, name, version in [("core", "Slimefun", "4.1.76")] + [
                (key, pin["plugin"], pin["version"]) for key, pin in self.lock["addons"].items()]:
            self.manifest["inputs"][key] = {"file": key + ".jar", **identity(self.jars[key]),
                                            "plugin": name, "version": version, "classes": 1}
        for key, pin in self.lock["addons"].items():
            self.manifest["compiled_inputs"][key] = {
                "file": "compiled/" + key + ".jar", **identity(self.compiled_jars[key]),
                "plugin": pin["plugin"], "version": pin["version"], "classes": 1}
            self.manifest["addon_checkouts"][key] = {field: pin[field] for field in ("repository", "source", "tree")}
            proof = {"schema": 1, "expected_core_sha256": metadata["core_sha256"],
                     "classpaths": {"compileClasspath": identity(self.jars["core"]),
                                    "testRuntimeClasspath": identity(self.jars["core"])}}
            path = self.inputs / (key + "-core-classpath.json")
            write_json(path, proof)
            self.manifest["classpath_receipts"][key] = identity(path.read_bytes())
        self.manifest["fixture_sources"] = paired.verify_fixture_sources(self.inputs, self.lock, packaged=True)
        self.manifest["jeg_compile_dependency"] = {"bytes": 1, "sha256": self.lock["jeg_compile_dependency"]["sha256"]}
        write_json(self.inputs / "inputs.json", self.manifest)
        self.input_bytes = (self.inputs / "inputs.json").read_bytes()

        # Synthetic *second* published-release pair. Both fake releases share
        # source identity only in this happy-path fixture; negative tests vary
        # the release source and installed bytes independently.
        self.release_lock = deepcopy(self.lock)
        self.release_lock.update(
            purpose="exact-published-release-storage-pair",
            distribution_selection=True,
            distribution_lock_path="scripts/published_storage_pair_lock.json",
        )
        self.release_lock_path = root / "release-lock.json"
        self.distribution_path = root / "published-storage-pair-lock.json"
        write_json(self.release_lock_path, self.release_lock)
        write_json(self.distribution_path, {
            "schema": 1, "purpose": "published-distribution",
            "distribution_selection": True, "addons": deepcopy(self.release_lock["addons"]),
        })
        self.release_inputs = self.native / "paired-storage-release-inputs"
        shutil.copytree(self.inputs, self.release_inputs)
        (self.release_inputs / "fixture-lock.json").write_bytes(self.release_lock_path.read_bytes())
        self.release_manifest = deepcopy(self.manifest)
        self.release_manifest["purpose"] = self.release_lock["purpose"]
        self.release_manifest["lock_sha256"] = identity(self.release_lock_path.read_bytes())["sha256"]
        write_json(self.release_inputs / "inputs.json", self.release_manifest)
        self.release_input_bytes = (self.release_inputs / "inputs.json").read_bytes()

        self.matrix = {"addons": [{"repository": repository, "source_commit": self.lock["addons"][key]["source"]}
                                  for key, (repository, _) in ADDONS.items()]}
        self.bundle_manifest = {
            "bundle_revision": "r142" if historical_pair else "r143", "core_source_commit": SOURCE,
            "addons": [{"repository": repository, "commit": self.lock["addons"][key]["source"],
                        "version": self.lock["addons"][key]["version"], "jar": key + ".jar",
                        "sha256": identity(self.jars[key])["sha256"]}
                       for key, (repository, _) in ADDONS.items()],
        }
        self.write_bundle()
        self.write_api_metadata()
        for index, (key, platform) in enumerate(self.lock["platforms"].items()):
            self.write_lane(key, platform, 1000 + index * 10)
            self.write_lane(key, platform, 3000 + index * 10, release=True)

    def write_bundle(self):
        write_json(self.matrix_path, self.matrix)
        with zipfile.ZipFile(self.bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("SF_ADDON_MANIFEST.json", encoded(self.bundle_manifest))
            for key in ADDONS:
                archive.writestr(key + ".jar", self.jars[key])

    def write_api_metadata(self):
        run = {"id": 12345, "run_attempt": 1, "path": WORKFLOW, "head_sha": SOURCE,
               "head_branch": "master", "event": "push", "status": "completed", "conclusion": "success",
               "repository": {"id": 100, "full_name": REPOSITORY},
               "head_repository": {"id": 100, "full_name": REPOSITORY}}
        write_json(self.native / "run.json", run)
        names = sorted(FIXED_JOBS
                       | {"Paired storage and restart on " + key for key in self.lock["platforms"]}
                       | {"Current published storage and restart on " + key
                          for key in self.lock["platforms"]})
        jobs = [{"id": 200 + index, "run_id": run["id"], "run_attempt": 1, "head_sha": SOURCE,
                 "name": name, "status": "completed", "conclusion": "success",
                 "started_at": "2026-10-18T04:00:00Z", "completed_at": "2026-10-18T04:10:00Z"}
                for index, name in enumerate(names)]
        write_json(self.native / "jobs.json", {"total_count": len(jobs), "jobs": jobs})
        names = ["paired-storage-inputs", "paired-storage-release-inputs",
                 *["paired-storage-" + key for key in self.lock["platforms"]],
                 *["paired-storage-release-" + key for key in self.lock["platforms"]]]
        artifacts = [{"id": 300 + index, "name": name, "size_in_bytes": 100, "digest": "sha256:" + "a" * 64,
                      "expired": False, "created_at": "2026-10-18T04:05:00Z",
                      "workflow_run": {"id": run["id"], "head_sha": SOURCE, "repository_id": 100, "head_repository_id": 100}}
                     for index, name in enumerate(names)]
        write_json(self.native / "artifacts.json", {"total_count": len(artifacts), "artifacts": artifacts})

    def write_lane(self, key, platform, pid, release=False):
        directory = self.native / (("paired-storage-release-" if release else "paired-storage-") + key)
        directory.mkdir()
        manifest = self.release_manifest if release else self.manifest
        source_bytes = self.release_input_bytes if release else self.input_bytes
        (directory / "inputs.json").write_bytes(source_bytes)
        bound = {"server.jar": {"bytes": 100, "sha256": platform["launcher_sha256"]},
                 "plugins/Slimefun.jar": identity(self.jars["core"]),
                 "plugins/Networks.jar": identity(self.jars["networks"]),
                 "plugins/InfinityExpansion2.jar": identity(self.jars["ie2"]),
                 "plugins/PairedIE2StorageProbe.jar": {"bytes": 100, "sha256": "c" * 64},
                 "plugins/PairedStorageReceipt.jar": {"bytes": 100, "sha256": "d" * 64}}
        proof = {"schema": 1, "status": "PASS", "source": SOURCE, "platform": platform,
                 "run_id": str(uuid.uuid4()), "input_manifest_sha256": identity(source_bytes)["sha256"],
                 "bound_files": bound}
        for index, phase in enumerate(("seed", "verify")):
            token = str(uuid.uuid4())
            started = {"schema": 1, "phase": phase, "token": token, "pid": pid + index, "status": "READY",
                       "software": "Purpur", "minecraft": platform["minecraft"],
                       "server_version": platform["minecraft"] + "-" + platform["build"] + "-test",
                       "core_source": SOURCE, "core_commit": SOURCE, "core_version": "4.1.76",
                       "networks_version": self.lock["addons"]["networks"]["version"],
                       "ie2_version": self.lock["addons"]["ie2"]["version"], "primary_thread": True}
            stopped = dict(started, status="STOPPED", server_stopping=True)
            native = ("PAIRED_STORAGE_RECEIPT READY phase=" + phase + " token=" + token + "\nDone (1.0s)!\n"
                      + paired.PHASE_RESULTS[phase] + "\nStopping server\nPAIRED_STORAGE_RECEIPT STOPPED phase="
                      + phase + " token=" + token + "\n").encode()
            console = b"Bootstrap output\n"
            snapshot = database_receipt(phase, pid + index)
            snapshot["inputs_receipt_sha256"] = identity(source_bytes)["sha256"]
            result = {"schema": 1, "phase": phase, "token": token, "pid": pid + index, "exit_code": 0,
                      "timed_out": False, "status": "PASS", "installed_before": deepcopy(bound),
                      "installed_after": deepcopy(bound), "startup": started, "shutdown": stopped,
                      "result": paired.PHASE_RESULTS[phase], "native_log": identity(native),
                      "console_log": identity(console), "database_snapshot": snapshot}
            for suffix, data in (("startup.json", started), ("shutdown.json", stopped), ("verification.json", result)):
                write_json(directory / (phase + "-" + suffix), data)
            (directory / (phase + "-native.log")).write_bytes(native)
            (directory / (phase + "-console.log")).write_bytes(console)
            (directory / (phase + "-result.txt")).write_text(paired.PHASE_RESULTS[phase] + "\n")
            proof[phase] = result
        write_json(directory / "verification.json", proof)

    def verify(self):
        return verify_native_evidence(self.evidence, self.core, self.bundle, self.matrix_path,
                                      self.lock_path, SOURCE,
                                      release_lock=self.release_lock_path,
                                      distribution_lock=self.distribution_path)

    def alter_json(self, path, change):
        value = json.loads(path.read_text())
        change(value)
        write_json(path, value)

    def alter_phase(self, change, phase="seed"):
        directory = self.native / "paired-storage-purpur-1.21.11-2568"
        proof = json.loads((directory / "verification.json").read_text())
        change(proof[phase])
        write_json(directory / "verification.json", proof)
        write_json(directory / (phase + "-verification.json"), proof[phase])
        return directory, proof


class ReleaseNativeEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture = Fixture(Path(self.temp.name) / "case")

    def fresh(self, name):
        return Fixture(Path(self.temp.name) / name)

    def test_complete_matching_candidate_is_accepted(self):
        result = self.fixture.verify()
        self.assertEqual(result["status"], "PASS_EXACT_RELEASE_NATIVE_PAIR")
        self.assertEqual(result["required_jobs"], 9)
        self.assertEqual(len(result["lanes"]), 2)
        self.assertEqual(len(result["historical_lanes"]), 2)
        self.assertEqual(result["core"], identity(self.fixture.core.read_bytes()))
        self.assertEqual(result["bundle"], identity(self.fixture.bundle.read_bytes()))
        for key in ADDONS:
            self.assertNotEqual(self.fixture.manifest["inputs"][key]["sha256"],
                                self.fixture.manifest["compiled_inputs"][key]["sha256"])

    def test_packaging_keeps_published_runtime_separate_from_candidate_compilation(self):
        f = self.fixture
        output = f.root / "packaged"
        output.mkdir()
        (output / "published").mkdir()
        for name in ("core.jar", "core-metadata.json"):
            shutil.copyfile(f.inputs / name, output / name)
        lock = deepcopy(f.lock)
        checkouts = {}
        for key, pin in lock["addons"].items():
            checkout = f.root / (key + "-checkout")
            checkouts[key + "_checkout"] = checkout
            built = checkout / pin["jar"]
            built.parent.mkdir(parents=True)
            built.write_bytes(f.compiled_jars[key])
            (output / "published" / (key + ".jar")).write_bytes(f.jars[key])
            shutil.copyfile(f.inputs / (key + "-core-classpath.json"), output / (key + "-core-classpath.json"))
        for row in lock["fixture_sources"]:
            target = checkouts["networks_checkout"] / row["path"]
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(f.inputs / "fixture" / target.name, target)
        jeg = checkouts["networks_checkout"] / lock["jeg_compile_dependency"]["jar"]
        jeg.parent.mkdir(parents=True, exist_ok=True)
        jeg.write_bytes(b"synthetic pinned compilation dependency")
        lock["jeg_compile_dependency"]["sha256"] = paired.digest(jeg)
        lock_path = f.root / "packaging-lock.json"
        write_json(lock_path, lock)
        write_json(output / "preparation.json", {
            "schema": 1, "source": SOURCE, "core_version": "4.1.76",
            "core_sha256": identity(f.jars["core"])["sha256"], "lock_sha256": paired.digest(lock_path),
            "local_maven_core_sha256": identity(f.jars["core"])["sha256"],
            "addon_checkouts": f.manifest["addon_checkouts"],
        })
        args = SimpleNamespace(inputs_dir=output, expected_source=SOURCE, **checkouts)
        def source_identity(checkout, pin):
            return {field: pin[field] for field in ("repository", "source", "tree")}
        with patch.object(paired, "LOCK_PATH", lock_path), \
                patch.object(paired, "checkout_identity", side_effect=source_identity):
            paired.package_inputs(args, lock)
            manifest = paired.validate_inputs(output, SOURCE, lock)
        for key in ADDONS:
            self.assertEqual((output / (key + ".jar")).read_bytes(), f.jars[key])
            self.assertEqual((output / "compiled" / (key + ".jar")).read_bytes(), f.compiled_jars[key])
            self.assertNotEqual(manifest["inputs"][key]["sha256"], manifest["compiled_inputs"][key]["sha256"])
            self.assertEqual(manifest["classpath_receipts"][key], f.manifest["classpath_receipts"][key])

    def test_self_consistent_runtime_manifest_cannot_replace_pinned_publication(self):
        f = self.fixture
        pin = f.lock["addons"]["networks"]
        changed = plugin_jar(pin["plugin"], pin["version"], marker="different publication bytes")
        f.jars["networks"] = changed
        (f.inputs / "networks.jar").write_bytes(changed)
        f.alter_json(f.inputs / "inputs.json", lambda row: row["inputs"]["networks"].update(identity(changed)))
        f.input_bytes = (f.inputs / "inputs.json").read_bytes()
        f.bundle_manifest["addons"][0]["sha256"] = identity(changed)["sha256"]
        f.write_bundle()
        for index, (key, platform) in enumerate(f.lock["platforms"].items()):
            shutil.rmtree(f.native / ("paired-storage-" + key))
            f.write_lane(key, platform, 1000 + index * 10)
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "Published addon bytes differ"):
            f.verify()

    def test_missing_or_changed_compiled_artifacts_are_rejected(self):
        for key in ADDONS:
            for mode in ("missing-receipt", "missing-file", "changed-bytes", "wrong-path"):
                f = self.fresh(key + "-" + mode)
                path = f.inputs / "compiled" / (key + ".jar")
                if mode == "missing-receipt":
                    f.alter_json(f.inputs / "inputs.json", lambda row: row["compiled_inputs"].pop(key))
                elif mode == "missing-file":
                    path.unlink()
                elif mode == "changed-bytes":
                    pin = f.lock["addons"][key]
                    path.write_bytes(plugin_jar(pin["plugin"], pin["version"], marker="changed compiled output"))
                else:
                    f.alter_json(f.inputs / "inputs.json", lambda row: row["compiled_inputs"][key].update(file=key + ".jar"))
                with self.subTest(addon=key, mode=mode), self.assertRaises(ReleaseRuntimeEvidenceError):
                    f.verify()

    def test_recorded_real_pair_source_mismatch_rejects_equal_versions_and_core(self):
        f = Fixture(Path(self.temp.name) / "historical-pair", historical_pair=True)
        for row in f.matrix["addons"]:
            key = next(key for key, (repo, _) in ADDONS.items() if repo == row["repository"])
            row["source_commit"] = CANONICAL_SOURCES[key]
        for row in f.bundle_manifest["addons"]:
            key = next(key for key, (repo, _) in ADDONS.items() if repo == row["repository"])
            row["commit"] = CANONICAL_SOURCES[key]
        f.write_bundle()
        self.assertEqual(f.manifest["core_version"], "4.1.76")
        self.assertEqual([row["version"] for row in f.bundle_manifest["addons"]], ["1.0.59", "2.0.14"])
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "tested addon source differs"):
            f.verify()

    def test_same_source_version_but_different_whole_jar_is_rejected(self):
        f = self.fixture
        f.jars["networks"] = plugin_jar("Networks", f.lock["addons"]["networks"]["version"],
                                       marker="changed artifact bytes")
        f.bundle_manifest["addons"][0]["sha256"] = identity(f.jars["networks"])["sha256"]
        f.write_bundle()
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "tested addon bytes differ"):
            f.verify()

    def test_wrong_release_core_or_embedded_source_is_rejected(self):
        for marker, source in (("different build", SOURCE), ("test", "0" * 40)):
            f = self.fresh(source + marker.replace(" ", "-"))
            f.core.write_bytes(plugin_jar("Slimefun", "4.1.76", marker=marker, source=source))
            with self.subTest(marker=marker, source=source), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()

    def test_all_nine_required_jobs_must_succeed(self):
        for index in range(9):
            f = self.fresh("job-" + str(index))
            f.alter_json(f.native / "jobs.json", lambda value: value["jobs"][index].update(conclusion="skipped"))
            with self.subTest(index=index), self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "did not succeed"):
                f.verify()

    def test_retained_successful_jobs_from_an_earlier_attempt_are_valid(self):
        f = self.fixture
        f.alter_json(f.native / "run.json", lambda value: value.update(run_attempt=2))
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "no job from the current attempt"):
            f.verify()
        f.alter_json(f.native / "jobs.json", lambda value: value["jobs"][0].update(run_attempt=2))
        self.assertEqual(f.verify()["native_run"]["attempt"], 2)
        f.alter_json(f.native / "jobs.json", lambda value: value["jobs"][0].update(run_attempt=3))
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "source or attempt mismatch"):
            f.verify()

    def test_stale_foreign_or_incomplete_run_is_rejected(self):
        for field, value in (("head_sha", "0" * 40), ("head_branch", "repair"), ("event", "pull_request"),
                             ("conclusion", "failure"), ("status", "in_progress"), ("path", "other.yml")):
            f = self.fresh("run-" + field)
            f.alter_json(f.native / "run.json", lambda row: row.update({field: value}))
            with self.subTest(field=field), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()

    def test_artifact_provenance_expiry_and_successful_job_interval_are_required(self):
        mutations = (
            lambda row: row.update(expired=True),
            lambda row: row.update(digest=None),
            lambda row: row.update(created_at="2026-10-18T03:59:59Z"),
            lambda row: row["workflow_run"].update(head_sha="0" * 40),
            lambda row: row["workflow_run"].update(id=999),
        )
        for index, change in enumerate(mutations):
            f = self.fresh("artifact-" + str(index))
            f.alter_json(f.native / "artifacts.json", lambda data: change(data["artifacts"][0]))
            with self.subTest(index=index), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()

    def test_incomplete_pages_and_duplicate_metadata_are_rejected(self):
        for file, rows_key in (("jobs.json", "jobs"), ("artifacts.json", "artifacts")):
            f = self.fresh(file)
            f.alter_json(f.native / file, lambda value: value[rows_key].pop())
            with self.subTest(file=file), self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "Incomplete"):
                f.verify()
        f = self.fixture
        f.alter_json(f.native / "artifacts.json", lambda value: value["artifacts"][1].update(name="paired-storage-inputs"))
        with self.assertRaises(ReleaseRuntimeEvidenceError):
            f.verify()

    def test_missing_lane_wrong_platform_and_stale_input_manifest_are_rejected(self):
        for index in range(3):
            f = self.fresh("lane-" + str(index))
            directory = f.native / "paired-storage-purpur-26.3-2652"
            if index == 0:
                shutil.rmtree(directory)
            elif index == 1:
                f.alter_json(directory / "verification.json", lambda row: row["platform"].update(build="2646"))
            else:
                (directory / "inputs.json").write_bytes(f.input_bytes + b" ")
            with self.subTest(index=index), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()

    def test_incomplete_timeout_and_changed_installed_phase_bytes_are_rejected(self):
        changes = (
            lambda row: row.update(status="FAIL"),
            lambda row: row.update(timed_out=True),
            lambda row: row.update(exit_code=1),
            lambda row: row["installed_after"]["plugins/Networks.jar"].update(sha256="0" * 64),
            lambda row: row["database_snapshot"].update(inputs_receipt_sha256="0" * 64),
        )
        for index, change in enumerate(changes):
            f = self.fresh("phase-" + str(index))
            f.alter_phase(change)
            with self.subTest(index=index), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()

    def test_complete_receipts_do_not_hide_errors_in_either_log_stream(self):
        for stream in ("native", "console"):
            f = self.fresh("log-" + stream)
            directory = f.native / "paired-storage-purpur-1.21.11-2568"
            path = directory / ("seed-" + stream + ".log")
            path.write_bytes(path.read_bytes() + b"[17:17:17 ERROR]: failed storage operation\n")
            f.alter_phase(lambda row: row.update({stream + "_log": identity(path.read_bytes())}))
            with self.subTest(stream=stream), self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "Unexpected native/console"):
                f.verify()

    def test_separate_phase_summary_cannot_be_substituted(self):
        f = self.fixture
        directory = f.native / "paired-storage-purpur-1.21.11-2568"
        f.alter_json(directory / "seed-verification.json", lambda row: row.update(pid=999999))
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "phase summary differs"):
            f.verify()

    def test_classpath_proof_must_match_both_current_core_classpaths(self):
        f = self.fixture
        path = f.inputs / "networks-core-classpath.json"
        f.alter_json(path, lambda value: value["classpaths"]["testRuntimeClasspath"].update(sha256="0" * 64))
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "Wrong core resolved"):
            f.verify()

    def test_matrix_and_bundle_source_membership_cannot_diverge(self):
        f = self.fixture
        f.alter_json(f.matrix_path, lambda value: value["addons"][0].update(source_commit="0" * 40))
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "differs from the reviewed matrix"):
            f.verify()

    def test_duplicate_json_and_missing_fixture_evidence_are_rejected(self):
        f = self.fixture
        (f.native / "run.json").write_text('{"id": 1, "id": 2}')
        with self.assertRaisesRegex(ReleaseRuntimeEvidenceError, "Duplicate JSON key"):
            f.verify()
        f = self.fresh("missing-fixture")
        next((f.inputs / "fixture").iterdir()).unlink()
        with self.assertRaises(ReleaseRuntimeEvidenceError):
            f.verify()

    def test_malformed_nested_objects_raise_the_public_error_type(self):
        cases = (
            ("run.json", lambda value: value.update(repository=[])),
            ("artifacts.json", lambda value: value["artifacts"][0].update(workflow_run=[])),
            ("paired-storage-inputs/inputs.json", lambda value: value["inputs"].update(core=[])),
            ("paired-storage-purpur-1.21.11-2568/verification.json", lambda value: value.update(seed=[])),
        )
        for index, (path, change) in enumerate(cases):
            f = self.fresh("malformed-" + str(index))
            f.alter_json(f.native / path, change)
            with self.subTest(path=path), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()

    def test_booleans_cannot_substitute_for_numeric_evidence(self):
        cases = (
            ("paired-storage-inputs/inputs.json", lambda value: value.update(schema=True)),
            ("paired-storage-inputs/core-metadata.json", lambda value: value.update(schema=True)),
            ("paired-storage-inputs/inputs.json", lambda value: value["inputs"]["core"].update(classes=True)),
            ("paired-storage-inputs/inputs.json", lambda value: value["compiled_inputs"]["networks"].update(classes=True)),
            ("paired-storage-inputs/inputs.json", lambda value: value["compiled_inputs"]["ie2"].update(bytes=True)),
            ("paired-storage-inputs/networks-core-classpath.json", lambda value: value.update(schema=True)),
            ("paired-storage-purpur-1.21.11-2568/verification.json", lambda value: value.update(schema=True)),
        )
        for index, (path, change) in enumerate(cases):
            f = self.fresh("boolean-" + str(index))
            f.alter_json(f.native / path, change)
            with self.subTest(path=path), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()
        for field, value in (("schema", True), ("exit_code", False)):
            f = self.fresh("boolean-phase-" + field)
            f.alter_phase(lambda row: row.update({field: value}))
            with self.subTest(field=field), self.assertRaises(ReleaseRuntimeEvidenceError):
                f.verify()


if __name__ == "__main__":
    unittest.main()

