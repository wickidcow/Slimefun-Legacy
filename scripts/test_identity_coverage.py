import copy
import hashlib
import unittest

from scripts.identity_coverage import BACKPACK_PHASES, ENDER_PHASES, PRODUCERS, build_report


class IdentityCoverageTest(unittest.TestCase):
    def setUp(self):
        self.registry = {"schema": 1, "software": "Paper", "minecraft": "26.3",
                         "server_version": "26.3-145", "items": [
                             {"item_id": "SMALL_URANIUM", "plugin": "Slimefun", "plugin_version": "4.1.79"}]}

    def report(self):
        return build_report(self.registry, "a" * 40, "b" * 64, "c" * 64)

    def test_registry_and_synthetic_success_never_imply_producer_success(self):
        self.registry["result"] = "PASS"
        self.registry["items"][0]["status"] = "PASS"
        report = self.report()
        self.assertEqual(0, report["counts"]["PASS"])
        self.assertEqual(len(PRODUCERS), report["counts"]["UNTESTED"])
        self.assertTrue(all(row["evidence"] == [] and row["applicability"] == "UNKNOWN"
                            for row in report["rows"]))
        self.assertIn("unclassified", {row["producer"] for row in report["rows"]})

    def test_duplicate_item_id_even_across_plugins_is_rejected(self):
        self.registry["items"].append({**self.registry["items"][0], "plugin": "Other"})
        with self.assertRaises(ValueError):
            self.report()

    def test_empty_registry_is_rejected(self):
        self.registry["items"] = []
        with self.assertRaises(ValueError):
            self.report()

    def test_missing_plugin_version_is_rejected(self):
        del self.registry["items"][0]["plugin_version"]
        with self.assertRaises(ValueError):
            self.report()

    def test_invalid_provenance_is_rejected(self):
        for source, core, server in [("master", "b" * 64, "c" * 64),
                                      ("a" * 40, "", "c" * 64), ("a" * 40, "b" * 64, "")]:
            with self.subTest(source=source, core=core, server=server), self.assertRaises(ValueError):
                build_report(self.registry, source, core, server)

    def test_runtime_is_required(self):
        del self.registry["minecraft"]
        with self.assertRaises(ValueError):
            self.report()

    def test_report_is_deterministic_and_does_not_mutate_registry(self):
        before = copy.deepcopy(self.registry)
        self.assertEqual(self.report(), self.report())
        self.assertEqual(before, self.registry)

    def test_payload_values_are_not_copied(self):
        self.registry["items"][0]["pdc"] = {"owner": "private"}
        self.assertNotIn("private", str(self.report()))


class NativeEvidenceTest(unittest.TestCase):
    def setUp(self):
        from scripts.identity_coverage import attach_native_evidence
        self.attach = attach_native_evidence
        registry = {"schema": 1, "software": "Paper", "minecraft": "26.2", "server_version": "26.2-129",
                    "items": [{"item_id": item, "plugin": "Slimefun", "plugin_version": "test"}
                              for item in ("SMALL_URANIUM", "COPPER_DUST", "CARBONADO", "COPPER_WIRE", "CARBON", "COMPRESSED_CARBON")]}
        self.report = build_report(registry, "a" * 40, "b" * 64, "c" * 64)
        self.evidence = {"result": "PASS", "boots": 3, "connected_player_checks": True,
                         "core_sha256": "b" * 64, "server_sha256": "c" * 64,
                         "phases": {phase: "RESOURCE_PACK_DOCTOR_PROBE_PASS " + phase + " | 26.2-129"
                                    for phase in ("player-identity-give", "player-identity-craft", "player-identity-compressor", "player-identity-compressor-rejected", "player-identity-compressor-chain",
                                                  "identity-output-restart") + ENDER_PHASES + BACKPACK_PHASES}}
        self.captures = {"identity-crafted-wire.bin": b"crafted", "identity-given-wire.bin": b"given",
                         "identity-produced-carbon.bin": b"real carbon output",
                         "identity-produced-compressed-carbon.bin": b"real compressed output"}
        self.evidence["captures_sha256"] = {
            name: hashlib.sha256(data).hexdigest() for name, data in self.captures.items()
        }

    def test_missing_malformed_or_extra_hash_receipts_fail_closed(self):
        for invalid in (None, {}, {"identity-crafted-wire.bin": "0" * 64},
                        {**self.evidence["captures_sha256"], "extra.bin": "a" * 64},
                        {**self.evidence["captures_sha256"], "identity-given-wire.bin": "NOT_SHA256"}):
            evidence = copy.deepcopy(self.evidence)
            if invalid is None:
                del evidence["captures_sha256"]
            else:
                evidence["captures_sha256"] = invalid
            with self.subTest(receipts=invalid), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)

    def test_swapped_or_tampered_capture_bytes_are_never_marked_pass(self):
        swapped = {
            "identity-crafted-wire.bin": self.captures["identity-given-wire.bin"],
            "identity-given-wire.bin": self.captures["identity-crafted-wire.bin"],
        }
        with self.assertRaises(ValueError):
            self.attach(self.report, self.evidence, swapped)
        changed = dict(self.captures)
        changed["identity-crafted-wire.bin"] += b"-tampered"
        with self.assertRaises(ValueError):
            self.attach(self.report, self.evidence, changed)
        for filename in ("identity-produced-carbon.bin", "identity-produced-compressed-carbon.bin"):
            changed = dict(self.captures)
            changed[filename] += b"-tampered"
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                self.attach(self.report, self.evidence, changed)

    def test_specific_cases_do_not_promote_broad_coverage_or_mutate_input(self):
        before = copy.deepcopy(self.report)
        result = self.attach(self.report, self.evidence, self.captures)
        self.assertEqual(10, result["actual_case_counts"]["PASS"])
        self.assertEqual(0, result["counts"]["PASS"])
        self.assertEqual(before, self.report)
        self.assertEqual(before["rows"], result["rows"])
        self.assertEqual(64, len(result["actual_cases"][-3]["captured_sha256"]["identity-crafted-wire.bin"]))

    def test_wrong_source_artifacts_and_runtime_rejected(self):
        for key in ("core_sha256", "server_sha256"):
            evidence = copy.deepcopy(self.evidence)
            evidence[key] = "d" * 64
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)
        self.evidence["phases"]["identity-output-restart"] += " other runtime"
        with self.assertRaises(ValueError):
            self.attach(self.report, self.evidence, self.captures)

    def test_ender_storage_requires_all_stages_from_same_runtime(self):
        result = self.attach(self.report, self.evidence, self.captures)
        storage = result["actual_cases"][-5]
        self.assertEqual("player_ender_chest_reconnect_and_restart", storage["case"])
        self.assertEqual(list(ENDER_PHASES), storage["required_phases"])
        for phase in ENDER_PHASES:
            evidence = copy.deepcopy(self.evidence)
            evidence["phases"][phase] += " different runtime"
            with self.subTest(phase=phase), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)

    def test_backpack_storage_requires_all_stages_from_same_runtime(self):
        result = self.attach(self.report, self.evidence, self.captures)
        storage = result["actual_cases"][-4]
        self.assertEqual("backpack_database_save_and_restart", storage["case"])
        self.assertEqual(list(BACKPACK_PHASES), storage["required_phases"])
        for phase in BACKPACK_PHASES:
            evidence = copy.deepcopy(self.evidence)
            evidence["phases"][phase] += " different runtime"
            with self.subTest(phase=phase), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)

    def test_loaded_backpack_case_requires_restart_and_load_receipts(self):
        result = self.attach(self.report, self.evidence, self.captures)
        loaded = result["actual_cases"][-3]
        self.assertEqual("backpack_public_api_reload_after_restart", loaded["case"])
        self.assertEqual(list(BACKPACK_PHASES), loaded["required_phases"])
        self.assertIn("player-identity-backpack-restart", loaded["required_phases"])
        self.assertIn("player-identity-backpack-loaded", loaded["required_phases"])

    def test_compressor_case_is_separate_from_wire_storage(self):
        result = self.attach(self.report, self.evidence, self.captures)
        compressor = result["actual_cases"][-2]
        self.assertEqual("CARBON", compressor["item_id"])
        self.assertEqual("compressor_handler_coal_blocks_delayed_output", compressor["case"])
        self.assertEqual(["player-identity-compressor-rejected", "player-identity-compressor",
                          "identity-output-restart"], compressor["required_phases"])
        self.assertEqual(
            {"identity-produced-carbon.bin": self.evidence["captures_sha256"]["identity-produced-carbon.bin"]},
            compressor["captured_sha256"])

    def test_chain_requires_actual_upstream_producer_receipt(self):
        result = self.attach(self.report, self.evidence, self.captures)
        chain = result["actual_cases"][-1]
        self.assertEqual("COMPRESSED_CARBON", chain["item_id"])
        self.assertEqual(["player-identity-compressor", "player-identity-compressor-chain",
                          "identity-output-restart"], chain["required_phases"])
        self.assertEqual(
            {"identity-produced-compressed-carbon.bin":
                self.evidence["captures_sha256"]["identity-produced-compressed-carbon.bin"]},
            chain["captured_sha256"])

    def test_each_native_phase_rejects_wrong_runtime(self):
        for phase in self.evidence["phases"]:
            evidence = copy.deepcopy(self.evidence)
            evidence["phases"][phase] += " other runtime"
            with self.subTest(phase=phase), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)

    def test_missing_phase_capture_or_registry_item_rejected(self):
        for phase in self.evidence["phases"]:
            evidence = copy.deepcopy(self.evidence)
            del evidence["phases"][phase]
            with self.subTest(phase=phase), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)
        with self.assertRaises(ValueError):
            self.attach(self.report, self.evidence, {})
        self.report["rows"] = [row for row in self.report["rows"] if row["item_id"] != "COPPER_WIRE"]
        with self.assertRaises(ValueError):
            self.attach(self.report, self.evidence, self.captures)

    def test_nonconnected_runtime_has_no_actual_cases(self):
        self.evidence["connected_player_checks"] = False
        result = self.attach(self.report, self.evidence, {})
        self.assertEqual([], result["actual_cases"])
        self.assertEqual(0, result["actual_case_counts"]["PASS"])

    def test_incomplete_run_rejected(self):
        for key, value in (("result", "FAIL"), ("boots", 2), ("connected_player_checks", None)):
            evidence = copy.deepcopy(self.evidence)
            evidence[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.attach(self.report, evidence, self.captures)


if __name__ == "__main__":
    unittest.main()
