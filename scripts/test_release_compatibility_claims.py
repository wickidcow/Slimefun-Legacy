import copy
import json
from pathlib import Path
import unittest

from scripts.release_compatibility_policy import NOTICE_261, validate_notice, validate_scope

ROOT = Path(__file__).resolve().parents[1]


class CompatibilityClaimsTest(unittest.TestCase):
    def setUp(self):
        self.matrix = json.loads((ROOT / "compatibility/sfl-addon-release-matrix.json").read_text())

    def test_live_matrix_preserves_runtime_floor_and_separate_advisory(self):
        validate_scope(self.matrix)

    def test_r153_source_pins_match_reviewed_merged_mains(self):
        self.assertEqual(153, self.matrix["bundle_revision"])
        rows = self.matrix["addons"]
        self.assertEqual(44, len(rows))
        pins = {row["repository"]: row["source_commit"] for row in rows}
        self.assertEqual(len(rows), len(pins), "Duplicate addon repository source lock")
        expected = {
            "wickidcow/SF_FlowerPower": "d8c3f2c8ec66b7c08e0ceb8b23f710f020b5c36f",
            "wickidcow/SF_ExtraHeads": "b1e8a1f11692b44d359eec863f3be5be0f061516",
            "wickidcow/SF_InfinityExpansion2": "fdadfa67bc68fb18e34ccfed773a23ba2efbd4ef",
        }
        # Do not accept unmerged candidate branches as canonical bundle provenance.
        self.assertNotEqual("f74950f9c901ffd4b866a58fa5ca6591aa9d00e8", pins["wickidcow/SF_FlowerPower"])
        self.assertNotEqual("26861f9e1ca5cab305678c39f10630a61d6f38f0", pins["wickidcow/SF_ExtraHeads"])
        for repository, commit in expected.items():
            with self.subTest(repository=repository):
                self.assertEqual(commit, pins[repository])
        self.assertNotEqual("b1b691c45138065dfc6688a43f6af4a26709745e",
                            pins["wickidcow/SF_FlowerPower"])
        self.assertNotEqual("bc0ea212f4889e494462ba22f1604206079cbd3d",
                            pins["wickidcow/SF_ExtraHeads"])

    def test_ie2_2017_release_pin_keeps_existing_bundle_jar_identity(self):
        matches = [row for row in self.matrix["addons"]
                   if row["repository"] == "wickidcow/SF_InfinityExpansion2"]
        self.assertEqual(1, len(matches), "InfinityExpansion2 must appear exactly once")
        self.assertEqual("infinityexpansion2", matches[0]["slug"])
        self.assertEqual("InfinityExpansion", matches[0]["artifact_base"])
        self.assertEqual("fdadfa67bc68fb18e34ccfed773a23ba2efbd4ef", matches[0]["source_commit"])
        self.assertNotEqual("a9b105e5cf3e8c69b3edb135c615eb3710f72158", matches[0]["source_commit"],
                            "Do not rebuild the 2.0.16 source in the 2.0.17 bundle preview")

    def test_generated_notice_carries_the_same_explicit_scope(self):
        text = (ROOT / ".github/workflows/build-sfl-addons-compat-bundle.yml").read_text()
        start = text.index("cat > bundle/COMPATIBILITY.txt <<EOF")
        end = text.index("\n          EOF", start)
        validate_notice(text[start:end])
        self.assertIn("python3 -m unittest scripts.test_release_compatibility_claims -v", text[:start])

    def test_broad_or_specific_261_runtime_claims_are_rejected(self):
        for version in ("26.1", "26.1.x", "26.1.2"):
            candidate = copy.deepcopy(self.matrix)
            candidate["compatibility"]["minecraft_versions"]["supported"].append(version)
            with self.subTest(version=version), self.assertRaises(ValueError):
                validate_scope(candidate)

    def test_runtime_or_world_claims_cannot_be_inferred_from_compile(self):
        for field in ("runtime_support", "world_migration_or_rollback_verified"):
            for value in (True, "false", None, 0):
                candidate = copy.deepcopy(self.matrix)
                candidate["compatibility"]["minecraft_versions"]["advisory"]["26.1.2"][field] = value
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    validate_scope(candidate)

    def test_missing_advisory_evidence_is_not_accepted(self):
        candidate = copy.deepcopy(self.matrix)
        del candidate["compatibility"]["minecraft_versions"]["advisory"]["26.1.2"]["validation"]
        with self.assertRaises(ValueError):
            validate_scope(candidate)

    def test_lost_runtime_floor_or_primary_line_is_rejected(self):
        for field, value in (("minimum", "1.21.10"), ("primary", "26.1.2")):
            candidate = copy.deepcopy(self.matrix)
            candidate["compatibility"]["minecraft_versions"][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_scope(candidate)

    def test_mutated_generated_notice_is_rejected(self):
        for text in ("", "- 26.1.x: supported", "- 26.1.2: supported", NOTICE_261 + "\n- 26.1.x: supported"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_notice(text)

    def test_validation_does_not_rewrite_addon_pins_or_manifest(self):
        before = copy.deepcopy(self.matrix)
        validate_scope(self.matrix)
        self.assertEqual(before, self.matrix)


if __name__ == "__main__":
    unittest.main()
