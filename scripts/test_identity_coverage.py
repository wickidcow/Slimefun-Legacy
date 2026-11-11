import copy
import unittest

from scripts.identity_coverage import PRODUCERS, build_report


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


if __name__ == "__main__":
    unittest.main()
