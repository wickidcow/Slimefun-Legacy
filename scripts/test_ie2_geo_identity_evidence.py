"""Fail-closed import contracts for source-specific native IE2 GEO Quarry evidence."""
import copy
import hashlib
import unittest

from scripts.identity_coverage import attach_ie2_geo_evidence, build_report


class Ie2GeoIdentityEvidenceTest(unittest.TestCase):
    def setUp(self):
        registry = {"schema": 1, "software": "Paper", "minecraft": "26.3",
                    "server_version": "26.3-145", "items": [
                        {"item_id": "SMALL_URANIUM", "plugin": "Slimefun",
                         "plugin_version": "4.1.80"}]}
        self.report = build_report(registry, "a" * 40, "b" * 64, "c" * 64)
        self.item = b"actual native GEO output fixture"
        self.addon = b"exact IE2 CI addon JAR bytes"
        self.probe_source = b"exact original IE2 NativeModernizationProbe.java bytes"
        self.receipt = {
            "filename": "native-geo-uranium.bin",
            "sha256": hashlib.sha256(self.item).hexdigest(),
            "size_bytes": len(self.item),
        }
        self.evidence = {
            "core_sha256": self.report["core_sha256"],
            "server_sha256": self.report["server_sha256"],
            "ie2_sha256": hashlib.sha256(self.addon).hexdigest(),
            "probe_sha256": hashlib.sha256(self.probe_source).hexdigest(),
            "phases": {
                phase: ("PASS " + phase + ": real SMALL_URANIUM cheat, GEO guide display, "
                        "GeoQuarry.process, world entity and inventory transfer "
                        "preserve metadata across restart\nfailures: 0\n")
                for phase in ("seed", "verify")
            },
            "geo_quarry": {
                "schema": 1, "item_id": "SMALL_URANIUM",
                "producer": "IE2_GEO_QUARRY",
                "scope": "actual registered machine output; disposable fixture only",
                "status": "PASS", "phases": ["seed", "verify"],
                "capture": self.receipt,
            },
        }

    def attach(self, evidence=None, item=None, addon=None, report=None, probe_source=None):
        return attach_ie2_geo_evidence(
            self.report if report is None else report,
            self.evidence if evidence is None else evidence,
            self.item if item is None else item,
            self.addon if addon is None else addon,
            self.probe_source if probe_source is None else probe_source)

    def test_native_producer_case_does_not_promote_all_machine_rows(self):
        before = copy.deepcopy(self.report)
        original_evidence = copy.deepcopy(self.evidence)
        result = self.attach()
        self.assertEqual(before, self.report)
        self.assertEqual(original_evidence, self.evidence)
        self.assertEqual(1, result["actual_case_counts"]["PASS"])
        self.assertEqual(0, result["counts"]["PASS"])
        self.assertEqual(len(result["rows"]), result["counts"]["UNTESTED"])
        self.assertTrue(all(row["status"] == "UNTESTED" for row in result["rows"]))
        case = result["actual_cases"][0]
        self.assertEqual("ie2_geo_quarry_actual_output_restart", case["case"])
        self.assertEqual("SMALL_URANIUM", case["item_id"])
        self.assertEqual(["seed", "verify"], case["required_phases"])
        self.assertEqual({"native-geo-uranium.bin": self.receipt["sha256"]},
                         case["captured_sha256"])
        self.assertNotIn(self.item.decode(), str(result))

    def test_reject_mismatched_core_or_server_artifact(self):
        for key in ("core_sha256", "server_sha256"):
            evidence = copy.deepcopy(self.evidence)
            evidence[key] = "f" * 64
            with self.subTest(field=key), self.assertRaisesRegex(ValueError, key):
                self.attach(evidence=evidence)

    def test_reject_swapped_addon_jar(self):
        with self.assertRaisesRegex(ValueError, "addon artifact mismatch"):
            self.attach(addon=b"another IE2 JAR")
        with self.assertRaisesRegex(ValueError, "Missing exact IE2 addon"):
            self.attach(addon=b"")

    def test_reject_different_native_probe_source_bytes(self):
        with self.assertRaisesRegex(ValueError, "probe source digest mismatch"):
            self.attach(probe_source=b"modified Java native probe source")
        with self.assertRaisesRegex(ValueError, "probe source digest mismatch"):
            self.attach(probe_source=b"other file with a plausible source name")

    def test_reject_empty_or_oversized_native_probe_source(self):
        for payload in (b"", b"!" * (1024 * 1024 + 1)):
            with self.subTest(length=len(payload)), self.assertRaisesRegex(
                    ValueError, "Missing or oversized IE2 GEO native probe source"):
                self.attach(probe_source=payload)

    def test_reject_wrong_capture_bytes_length_or_checksum(self):
        for item in (b"changed native output", b"", b"!" * (1024 * 1024 + 1)):
            with self.subTest(item_length=len(item)), self.assertRaisesRegex(
                    ValueError, "captured output"):
                self.attach(item=item)
        for key, value in (("size_bytes", True), ("size_bytes", len(self.item) + 1),
                           ("sha256", "0" * 64), ("filename", "template.bin")):
            evidence = copy.deepcopy(self.evidence)
            evidence["geo_quarry"]["capture"][key] = value
            with self.subTest(field=key), self.assertRaises(ValueError):
                self.attach(evidence=evidence)

    def test_reject_missing_or_fabricated_native_producer_phases(self):
        variants = (
            {"seed": self.evidence["phases"]["seed"]},
            {"seed": self.evidence["phases"]["verify"],
             "verify": self.evidence["phases"]["verify"]},
            {"seed": "PASS seed: generic template equality\nfailures: 0",
             "verify": self.evidence["phases"]["verify"]},
            {"seed": self.evidence["phases"]["seed"] * 2,
             "verify": self.evidence["phases"]["verify"]},
        )
        for phases in variants:
            evidence = copy.deepcopy(self.evidence)
            evidence["phases"] = phases
            with self.subTest(phases=phases), self.assertRaises(ValueError):
                self.attach(evidence=evidence)

    def test_reject_false_or_broadened_producer_claims(self):
        for field, value in (("schema", 2), ("item_id", "CARBONADO"),
                             ("producer", "ALL_MACHINES"), ("status", "UNTESTED"),
                             ("phases", ["seed"]), ("scope", "all production validated")):
            evidence = copy.deepcopy(self.evidence)
            evidence["geo_quarry"][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(
                    ValueError, "producer claim"):
                self.attach(evidence=evidence)

    def test_reject_unknown_producer_fields_or_missing_capture(self):
        for bad in ({"other": "all passed"}, {}):
            evidence = copy.deepcopy(self.evidence)
            evidence["geo_quarry"].update(bad)
            if not bad:
                del evidence["geo_quarry"]["capture"]
            with self.assertRaisesRegex(ValueError, "producer evidence fields"):
                self.attach(evidence=evidence)

    def test_reject_missing_native_probe_digest(self):
        for digest in (None, "unverified", "D" * 64):
            evidence = copy.deepcopy(self.evidence)
            evidence["probe_sha256"] = digest
            with self.subTest(digest=digest), self.assertRaisesRegex(
                    ValueError, "probe source"):
                self.attach(evidence=evidence)

    def test_reject_missing_registry_item_and_duplicate_fixture(self):
        missing = copy.deepcopy(self.report)
        missing["rows"] = []
        with self.assertRaisesRegex(ValueError, "absent or ambiguous"):
            self.attach(report=missing)
        once = self.attach()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.attach(report=once)

    def test_import_is_additive_to_existing_native_cases(self):
        baseline = copy.deepcopy(self.report)
        baseline["actual_cases"] = [{"case": "unrelated_real_compressor", "status": "PASS"}]
        baseline["actual_case_counts"] = {"PASS": 1}
        result = self.attach(report=baseline)
        self.assertEqual(2, result["actual_case_counts"]["PASS"])
        self.assertEqual("unrelated_real_compressor", result["actual_cases"][0]["case"])
        self.assertEqual(0, result["counts"]["PASS"])


if __name__ == "__main__":
    unittest.main()
