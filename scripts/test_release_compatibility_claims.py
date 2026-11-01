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
