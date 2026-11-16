"""Negative controls for immutable r156 addon release-source provenance."""
import copy
import json
import unittest
from scripts.verify_r156_addon_publications import EXPECTED, LOCK, MATRIX, validate_local


class OfficialReleaseProvenanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
        cls.lock = json.loads(LOCK.read_text(encoding="utf-8"))

    def test_exact_published_tag_sources_are_pinned(self):
        rows = validate_local(self.matrix, self.lock)
        self.assertEqual(set(EXPECTED), {r["repository"] for r in rows})
        self.assertEqual(44, len(self.matrix["addons"]))

    def test_stale_prerelease_source_is_rejected(self):
        matrix = copy.deepcopy(self.matrix)
        for row in matrix["addons"]:
            if row["repository"] == "wickidcow/SF_CrystamaeHistoria":
                row["source_commit"] = "d04f6b147580bc31426a96e894d65a72c5088f48"
        with self.assertRaisesRegex(ValueError, "does not match"):
            validate_local(matrix, self.lock)

    def test_wrong_versioned_raw_asset_is_rejected(self):
        lock = copy.deepcopy(self.lock)
        lock["releases"][0]["asset"] = "SF_CrystamaeHistoria1.0.2.jar"
        with self.assertRaisesRegex(ValueError, "Wrong official version"):
            validate_local(self.matrix, lock)

    def test_missing_unverified_and_duplicate_release_are_rejected(self):
        for mode in ("missing", "duplicate", "invalid_sha", "invalid_size"):
            with self.subTest(mode=mode):
                lock = copy.deepcopy(self.lock)
                if mode == "missing":
                    lock["releases"].pop()
                elif mode == "duplicate":
                    lock["releases"][1] = copy.deepcopy(lock["releases"][0])
                elif mode == "invalid_sha":
                    lock["releases"][0]["sha256"] = "0"
                else:
                    lock["releases"][0]["bytes"] = 0
                with self.assertRaises(ValueError):
                    validate_local(self.matrix, lock)

    def test_unreviewed_bundle_revision_is_rejected(self):
        lock = copy.deepcopy(self.lock)
        lock["bundle_revision"] = 157
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            validate_local(self.matrix, lock)


if __name__ == "__main__":
    unittest.main()
