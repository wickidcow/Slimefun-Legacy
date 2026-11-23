"""Fail-closed controls for the core-owned paired storage runner; no server starts."""
from __future__ import annotations

from copy import deepcopy
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.runtime.paired_addon_storage import run_probe as probe


SOURCE = "1a9c4adb9b6f27d387bfefe41ec8f16b91aa56b2"
TOKEN = "e912965e-cde8-40d8-90ad-b7699cdbad52"
PID = 12345
MANIFEST = {
    "source": SOURCE, "core_version": "4.1.76",
    "inputs": {"networks": {"version": "1.0.60"}, "ie2": {"version": "2.0.15"}},
}
PLATFORM = {"software": "Purpur", "minecraft": "26.3", "build": "2652"}
VALID_ADDON_STYLE = {
    "schema": 1,
    "scope": "actual_loaded_published_addons_reconstructed_legacy_setters",
    "genuine_old_saved_addon_items": "UNTESTED",
    "registered": 10,
    "strict_style_repairable": 3,
    "addons": {
        "Networks": {
            "registered": 4, "already_matching": 2,
            "strict_style_repairable": 1, "refused": 1,
            "unsupported": 0, "typed_pdc_guards": 1,
        },
        "InfinityExpansion2": {
            "registered": 6, "already_matching": 2,
            "strict_style_repairable": 2, "refused": 1,
            "unsupported": 1, "typed_pdc_guards": 2,
        },
    },
}


def phase_receipts(phase="seed", token=TOKEN, pid=PID):
    started = {
        "schema": 1, "phase": phase, "token": token, "pid": pid, "status": "READY",
        "software": "Purpur", "minecraft": "26.3", "server_version": "26.3-2652-candidate",
        "core_source": SOURCE, "core_commit": SOURCE, "core_version": "4.1.76",
        "networks_version": "1.0.60", "ie2_version": "2.0.15", "primary_thread": True,
        "registered_style_audit": deepcopy(VALID_ADDON_STYLE),
    }
    stopped = dict(started, status="STOPPED", server_stopping=True)
    native = "\n".join((
        "PAIRED_ADDON_REGISTERED_STYLE_AUDIT_PASS plugins=2 registered=10 repairable=3",
        "PAIRED_STORAGE_RECEIPT READY phase=" + phase + " token=" + token,
        'Done (1.0s)! For help, type "help"',
        probe.PHASE_RESULTS[phase],
        "Stopping server",
        "PAIRED_STORAGE_RECEIPT STOPPED phase=" + phase + " token=" + token,
    )) + "\n"
    return started, stopped, native


def database_receipt(phase="seed", pid=PID):
    storage = "fixture;160:100:160"
    source = {"block-storage.db": {"bytes": 73728, "sha256": "a" * 64}}
    return {
        "schema": 1, "phase": phase, "pid": pid, "child_returncode": 0,
        "status": "PASS_READONLY_POST_PROCESS_DATABASE_SNAPSHOT",
        "source_unchanged": True, "raw_copies_unchanged": True,
        "helper_sha256": probe.digest(probe.HERE / "paired_database_snapshot.py"),
        "original_database_opened_by_sqlite": False, "sqlite_uri_mode": "ro", "sqlite_immutable": False,
        "source_before": deepcopy(source), "source_after": deepcopy(source),
        "raw_before": deepcopy(source), "raw_after": deepcopy(source),
        "database": {
            "table_counts": {
                "block_record": 4, "block_data": 4, "block_inventory": 2 if phase == "seed" else 1,
                "chunk_data": 0, "universal_record": 0, "universal_data": 0,
                "universal_inventory": 0, "table_metadata": 1,
            },
            "block_records": [
                {"loc": storage, "chunk": "fixture;10:10", "sf_id": "IE_STORAGE_UNIT_1"},
                {"loc": "fixture;160:100:161", "chunk": "fixture;10:10", "sf_id": "NTW_MONITOR"},
                {"loc": "fixture;161:100:160", "chunk": "fixture;10:10", "sf_id": "NTW_MONITOR"},
                {"loc": "fixture;161:100:161", "chunk": "fixture;10:10", "sf_id": "NTW_CONTROLLER"},
            ],
            "block_data": [
                {"loc": storage, "data_key": "stored_amount", "data_val": "MTI4" if phase == "seed" else "MA=="},
                {"loc": storage, "data_key": "void_excess", "data_val": "ZmFsc2U="},
                {"loc": "fixture;160:100:161", "data_key": "direction", "data_val": "U0VMRg=="},
                {"loc": "fixture;161:100:160", "data_key": "direction", "data_val": "U0VMRg=="},
            ],
            "block_inventory": [
                {"loc": storage, "slot": slot, "bytes": 128, "sha256": "b" * 64, "prefix_hex": "53463200"}
                for slot in ([13, 16] if phase == "seed" else [13])
            ],
            "universal_records": [],
            "details_truncated": {
                "block_record": False, "block_data": False, "block_inventory": False, "universal_record": False,
            },
        },
    }


class PairedReceiptTests(unittest.TestCase):
    def accept(self, phase="seed", started=None, stopped=None, native=None, console="Early bootstrap only\n",
               result=None, timed_out=False, exit_code=0):
        normal_start, normal_stop, normal_log = phase_receipts(phase)
        probe.validate_phase_evidence(
            phase, TOKEN, PID, exit_code, timed_out,
            probe.PHASE_RESULTS[phase] if result is None else result,
            normal_start if started is None else started, normal_stop if stopped is None else stopped,
            normal_log if native is None else native, console, MANIFEST, PLATFORM)

    def test_complete_native_log_can_replace_missing_stdout_lifecycle(self):
        for phase in ("seed", "verify"):
            with self.subTest(phase=phase):
                self.accept(phase)

    def test_registered_addon_style_proof_must_be_complete_and_fail_closed(self):
        # This is a *synthetic* completed native startup receipt. Every
        # missing/forged addon partition and overstated historical claim
        # must be rejected while the original storage DB fixture remains.
        changes = [
            lambda x: x.pop("registered_style_audit"),
            lambda x: x["registered_style_audit"].update(
                {"genuine_old_saved_addon_items": "VERIFIED"}),
            lambda x: x["registered_style_audit"].update({"registered": 9}),
            lambda x: x["registered_style_audit"].update({"strict_style_repairable": 4}),
            lambda x: x["registered_style_audit"]["addons"].pop("Networks"),
            lambda x: x["registered_style_audit"]["addons"]["Networks"].update({"registered": 0}),
            lambda x: x["registered_style_audit"]["addons"]["Networks"].update({"refused": 8}),
            lambda x: x["registered_style_audit"]["addons"]["Networks"].update({"typed_pdc_guards": 0}),
            lambda x: x["registered_style_audit"]["addons"]["InfinityExpansion2"].update(
                {"strict_style_repairable": -1}),
            lambda x: x["registered_style_audit"]["addons"]["InfinityExpansion2"].update(
                {"already_matching": True}),
        ]
        for role in ("started", "stopped"):
            for i, mutation in enumerate(changes):
                altered = phase_receipts()[0 if role == "started" else 1]
                mutation(altered)
                with self.subTest(role=role, change=i), self.assertRaises(RuntimeError):
                    self.accept(**{role: altered})

    def test_error_in_either_stream_rejects_complete_proof(self):
        for diagnostic in (
            "[Server thread/ERROR] storage failure", "[SEVERE] failure",
            "[03:46:01 ERROR]: storage failure", "[03:46:01 SEVERE]: storage failure",
            "java.lang.IllegalStateException: lost state", "PAIRED_STORAGE_RECEIPT FAIL invalid",
            "Error occurred while disabling Networks", "java.lang.NoSuchMethodError: method",
        ):
            for stream in ("native", "console"):
                with self.subTest(diagnostic=diagnostic, stream=stream), self.assertRaises(RuntimeError):
                    self.accept(**{stream: phase_receipts()[2] + diagnostic})

    def test_exact_result_is_required_in_each_phase(self):
        for phase in ("seed", "verify"):
            for result in ("PASS", "PASS: partial", probe.PHASE_RESULTS[phase] + " ignored failure", "FAIL: reload"):
                with self.subTest(phase=phase, result=result), self.assertRaises(RuntimeError):
                    self.accept(phase, result=result)

    def test_missing_or_mismatched_receipts_fail(self):
        mutations = {
            "schema": 2, "phase": "other", "token": "previous-boot", "pid": PID + 1, "status": "PASS",
            "core_source": "0" * 40, "core_commit": "0" * 40, "core_version": "4.1.75",
            "networks_version": "1.0.59", "ie2_version": "2.0.14", "software": "Paper",
            "minecraft": "1.21.11", "server_version": "26.3-2646-wrong", "primary_thread": False,
        }
        for field, value in mutations.items():
            for role in ("started", "stopped"):
                for remove in (False, True):
                    altered = phase_receipts()[0 if role == "started" else 1]
                    if remove:
                        altered.pop(field)
                    else:
                        altered[field] = value
                    with self.subTest(field=field, role=role, remove=remove), self.assertRaises(RuntimeError):
                        self.accept(**{role: altered})

    def test_abnormal_shutdown_and_late_success_are_rejected(self):
        for stopped in (dict(phase_receipts()[1], server_stopping=False),
                        {k: v for k, v in phase_receipts()[1].items() if k != "server_stopping"}):
            with self.assertRaises(RuntimeError):
                self.accept(stopped=stopped)
        for args in ({"timed_out": True}, {"exit_code": 1}, {"exit_code": -9}):
            with self.subTest(args=args), self.assertRaises(RuntimeError):
                self.accept(**args)

    def test_stale_missing_and_reordered_native_markers_are_rejected(self):
        lines = phase_receipts()[2].splitlines()
        for index in range(len(lines)):
            with self.subTest(missing=index), self.assertRaises(RuntimeError):
                self.accept(native="\n".join(lines[:index] + lines[index + 1:]))
        with self.assertRaises(RuntimeError):
            self.accept(native=phase_receipts()[2].replace(TOKEN, "previous-token"))
        with self.assertRaises(RuntimeError):
            self.accept(native="\n".join([lines[2], lines[0], lines[1], *lines[3:]]))


class DatabaseReceiptTests(unittest.TestCase):
    def test_exact_committed_ledgers_pass(self):
        for phase in ("seed", "verify"):
            with self.subTest(phase=phase):
                probe.validate_database_evidence(database_receipt(phase), phase, PID)

    def test_lost_wrong_or_unexpected_records_reject_healthy_phase(self):
        for phase in ("seed", "verify"):
            for table in database_receipt(phase)["database"]["table_counts"]:
                value = database_receipt(phase)
                value["database"]["table_counts"][table] += 1
                with self.subTest(phase=phase, table=table), self.assertRaises(RuntimeError):
                    probe.validate_database_evidence(value, phase, PID)
            for mutation in ("lost", "wrong_id", "wrong_world", "wrong_chunk", "duplicate"):
                value = database_receipt(phase)
                rows = value["database"]["block_records"]
                if mutation == "lost":
                    rows.pop()
                elif mutation == "wrong_id":
                    rows[0]["sf_id"] = "MISSING_STORAGE"
                elif mutation == "wrong_world":
                    rows[0]["loc"] = "fixture_nether;160:100:160"
                elif mutation == "wrong_chunk":
                    rows[0]["chunk"] = "fixture;11:10"
                else:
                    rows[3] = deepcopy(rows[0])
                with self.subTest(phase=phase, mutation=mutation), self.assertRaises(RuntimeError):
                    probe.validate_database_evidence(value, phase, PID)

    def test_lost_or_wrong_balance_data_and_inventory_reject(self):
        for phase in ("seed", "verify"):
            for field in ("block_data", "block_inventory"):
                value = database_receipt(phase)
                value["database"][field].pop()
                with self.subTest(phase=phase, missing=field), self.assertRaises(RuntimeError):
                    probe.validate_database_evidence(value, phase, PID)
            value = database_receipt(phase)
            value["database"]["block_data"][0]["data_val"] = "MA==" if phase == "seed" else "MTI4"
            with self.subTest(phase=phase, bad_balance=True), self.assertRaises(RuntimeError):
                probe.validate_database_evidence(value, phase, PID)
            for field, wrong in (("slot", 17), ("loc", "fixture;161:100:160"),
                                 ("bytes", 0), ("sha256", "missing"), ("prefix_hex", "00000000")):
                value = database_receipt(phase)
                value["database"]["block_inventory"][0][field] = wrong
                with self.subTest(phase=phase, inventory=field), self.assertRaises(RuntimeError):
                    probe.validate_database_evidence(value, phase, PID)

    def test_missing_capture_identity_or_mutated_raw_bytes_reject(self):
        changes = {
            "schema": 2, "phase": "verify", "pid": PID + 1, "child_returncode": 1,
            "status": "PASS", "source_unchanged": False, "raw_copies_unchanged": False,
            "helper_sha256": "0" * 64, "original_database_opened_by_sqlite": True,
            "sqlite_uri_mode": "rw", "sqlite_immutable": True, "source_after": {},
            "raw_before": {}, "raw_after": {},
        }
        for field, wrong in changes.items():
            for remove in (False, True):
                value = database_receipt()
                if remove:
                    value.pop(field)
                else:
                    value[field] = wrong
                with self.subTest(field=field, remove=remove), self.assertRaises(RuntimeError):
                    probe.validate_database_evidence(value, "seed", PID)
        value = database_receipt()
        value["database"]["details_truncated"]["block_data"] = True
        with self.assertRaises(RuntimeError):
            probe.validate_database_evidence(value, "seed", PID)


class InputGuardTests(unittest.TestCase):
    def test_only_new_directories_inside_runner_temp_are_allowed(self):
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            root = parent / "runner"
            root.mkdir()
            with patch.dict(os.environ, {"RUNNER_TEMP": str(root)}):
                fresh = probe.new_temporary_directory(root / "fresh")
                self.assertEqual(fresh, root / "fresh")
                for bad in (fresh, root, parent / "outside", root / "missing" / "child"):
                    with self.subTest(path=bad), self.assertRaises(RuntimeError):
                        probe.new_temporary_directory(bad)
                (root / "alias").symlink_to(fresh, target_is_directory=True)
                with self.assertRaises(RuntimeError):
                    probe.new_temporary_directory(root / "alias" / "child")
                (root / "dangling").symlink_to(root / "absent", target_is_directory=True)
                with self.assertRaises(RuntimeError):
                    probe.new_temporary_directory(root / "dangling")
            with patch.dict(os.environ, {}, clear=True), self.assertRaises(RuntimeError):
                probe.new_temporary_directory(root / "no-environment")

    def test_classpath_proof_requires_the_same_core_in_compile_and_test(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "classpath.json"
            complete = {
                "schema": 1, "expected_core_sha256": "a" * 64,
                "classpaths": {name: {"sha256": "a" * 64, "bytes": 123} for name in (
                    "compileClasspath", "testRuntimeClasspath")},
            }
            probe.write_json(path, complete)
            probe.validate_classpath_receipt(path, "a" * 64, 123)
            for name in complete["classpaths"]:
                for mode in ("wrong_hash", "wrong_size", "missing"):
                    value = deepcopy(complete)
                    if mode == "missing":
                        value["classpaths"].pop(name)
                    else:
                        value["classpaths"][name]["sha256" if mode == "wrong_hash" else "bytes"] = "b" * 64
                    probe.write_json(path, value)
                    with self.subTest(name=name, mode=mode), self.assertRaises(RuntimeError):
                        probe.validate_classpath_receipt(path, "a" * 64, 123)

    def test_lock_is_test_only_and_keeps_original_fixture_sources(self):
        lock = probe.load_lock()
        self.assertFalse(lock["distribution_selection"])
        self.assertEqual(set(lock["addons"]), {"networks", "ie2"})
        self.assertEqual(len(lock["fixture_sources"]), 2)

    def test_published_lock_requires_exact_release_url_size_and_digest(self):
        mutations = (
            lambda pin: pin.pop("published_artifact"),
            lambda pin: pin["published_artifact"].update(url="https://example.com/other.jar"),
            lambda pin: pin["published_artifact"].update(bytes=0),
            lambda pin: pin["published_artifact"].update(bytes=True),
            lambda pin: pin["published_artifact"].update(sha256="missing"),
            lambda pin: pin["published_artifact"].update(extra="unreviewed"),
        )
        original = probe.load_lock()
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fixture-lock.json"
            for change in mutations:
                for key in original["addons"]:
                    lock = deepcopy(original)
                    change(lock["addons"][key])
                    probe.write_json(path, lock)
                    with self.subTest(addon=key, change=change), patch.object(probe, "LOCK_PATH", path), \
                            self.assertRaises(RuntimeError):
                        probe.load_lock()

    def test_published_jar_rejects_equal_version_with_different_size_or_bytes(self):
        pin = deepcopy(probe.load_lock()["addons"]["networks"])
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "networks.jar"
            def write_jar(marker):
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("plugin.yml", "name: " + pin["plugin"] + "\nversion: " + pin["version"] + "\n")
                    archive.writestr("test/Marker.class", bytes.fromhex("cafebabe00000041") + marker)
            write_jar(b"test")
            pin["published_artifact"].update(probe.file_identity(path))
            self.assertEqual(probe.validate_published_jar(path, pin)["sha256"], pin["published_artifact"]["sha256"])
            wrong_size = deepcopy(pin)
            wrong_size["published_artifact"]["bytes"] += 1
            with self.assertRaisesRegex(RuntimeError, "Published addon bytes differ"):
                probe.validate_published_jar(path, wrong_size)
            write_jar(b"evil")
            self.assertEqual(path.stat().st_size, pin["published_artifact"]["bytes"])
            with self.assertRaisesRegex(RuntimeError, "Published addon bytes differ"):
                probe.validate_published_jar(path, pin)


class FinishedProcessBoundaryTests(unittest.TestCase):
    def run_mock_phase(self, *, late=False, lose_rows=False, changed_input=False):
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            (work / "plugins/PairedStorageReceipt").mkdir(parents=True)
            (work / "logs").mkdir()
            (work / "server.jar").write_bytes(b"fixed launcher")
            probe.write_json(work / "inputs.json", MANIFEST)
            bound = {"server.jar": probe.file_identity(work / "server.jar")}
            observed = {"waited": False, "snapshot": False}
            process_holder = {}

            class FinishedProcess:
                def __init__(self, args, **kwargs):
                    self.pid, self.returncode, self.stdin = PID, None, io.BytesIO()
                    self.console = kwargs["stdout"]
                    self.token = next(arg.split("=", 1)[1] for arg in args if arg.startswith("-Dsfl.paired.token="))
                    process_holder["process"] = self

                def wait(self, timeout):
                    observed["waited"] = True
                    started, stopped, native = phase_receipts(token=self.token)
                    probe.write_json(work / "plugins/PairedStorageReceipt/startup.json", started)
                    probe.write_json(work / "plugins/PairedStorageReceipt/shutdown.json", stopped)
                    (work / "logs/latest.log").write_text(native)
                    (work / "plugins/paired-ie2-result.txt").write_text(probe.PHASE_RESULTS["seed"] + "\n")
                    self.console.write("Bootstrap capture only\n")
                    if changed_input:
                        (work / "server.jar").write_bytes(b"altered launcher")
                    self.returncode = 0
                    return 0

                def poll(self):
                    return self.returncode

            def snapshot(path, phase, pid, exit_code):
                self.assertTrue(observed["waited"])
                self.assertTrue(process_holder["process"].console.closed)
                self.assertEqual(exit_code, 0)
                self.assertEqual((path, phase, pid), (work, "seed", PID))
                observed["snapshot"] = True
                value = database_receipt()
                value["inputs_receipt_sha256"] = probe.digest(work / "inputs.json")
                if lose_rows:
                    value["database"]["table_counts"]["block_record"] = 0
                    value["database"]["block_records"] = []
                return value

            with patch.object(probe.subprocess, "Popen", FinishedProcess), \
                    patch.object(probe.time, "monotonic", side_effect=[0.0, 2.0 if late else 0.5]), \
                    patch.object(probe, "snapshot_database", side_effect=snapshot):
                if late or lose_rows or changed_input:
                    with self.assertRaises(RuntimeError):
                        probe.run_phase(Path("/unused/java"), work, "seed", MANIFEST, PLATFORM, bound, timeout=1)
                else:
                    probe.run_phase(Path("/unused/java"), work, "seed", MANIFEST, PLATFORM, bound, timeout=1)
            self.assertTrue(observed["snapshot"])
            result = probe.read_json(work / "seed-verification.json")
            self.assertEqual(result["status"], "FAIL" if late or lose_rows or changed_input else "PASS")
            self.assertEqual(result["timed_out"], late)
            return result

    def test_snapshot_only_follows_process_exit_and_closed_console(self):
        self.run_mock_phase()

    def test_late_complete_proof_and_exit_zero_still_fail(self):
        self.assertIn("timed out", self.run_mock_phase(late=True)["error"])

    def test_successful_phase_cannot_hide_post_shutdown_record_loss(self):
        self.assertIn("row counts", self.run_mock_phase(lose_rows=True)["error"])

    def test_successful_phase_cannot_hide_installed_jar_mutation(self):
        self.assertIn("artifacts changed", self.run_mock_phase(changed_input=True)["error"])


if __name__ == "__main__":
    unittest.main()

