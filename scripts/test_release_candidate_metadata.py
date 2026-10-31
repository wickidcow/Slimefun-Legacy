"""Exact candidate metadata regressions for distinct Slimefun Legacy 4.1.79."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PUBLISHED_V4178_SOURCE = "b14a3bcc5ef221c013f93d49b6b3a1bbdb3101e9"


class ReleaseCandidateMetadataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        cls.support = json.loads((ROOT / "compatibility/support-contract.json").read_text(encoding="utf-8"))
        cls.baselines = json.loads((ROOT / "compatibility/release-baselines.json").read_text(encoding="utf-8"))
        cls.readme = (ROOT / "README.md").read_text(encoding="utf-8")
        cls.notes = (ROOT / "docs/releases/4.1.79.md").read_text(encoding="utf-8")

    def test_candidate_version_matches_both_release_registries(self):
        match = re.search(r"^projectVersion=(\d+\.\d+\.\d+)$", self.properties, re.M)
        self.assertIsNotNone(match)
        self.assertEqual("4.1.79", match.group(1))
        self.assertEqual(match.group(1), self.support["release"])
        self.assertEqual(match.group(1), self.baselines["candidate"]["version"])

    def test_previous_stable_is_exact_published_v4178_source(self):
        previous = self.baselines["previous_stable"]
        self.assertEqual("4.1.78", previous["version"])
        self.assertEqual("git-ref", previous["source"]["mode"])
        self.assertEqual(PUBLISHED_V4178_SOURCE, previous["source"]["ref"])
        self.assertIs(True, previous["release_blocking"])
        self.assertEqual("4.1.15", self.baselines["legacy_floor"]["version"])
        self.assertIs(False, self.baselines["legacy_floor"]["release_blocking"])

    def test_readme_and_notes_never_claim_candidate_was_published(self):
        self.assertIn("Slimefun Legacy 4.1.79 is undergoing exact-source validation primarily", self.readme)
        self.assertIn("4.1.78 was published on 2026-10-27", self.readme)
        self.assertIn("unpublished candidate", self.readme)
        self.assertIn("unreleased candidate", self.notes)
        self.assertIn("Status: candidate only", self.notes)
        self.assertNotIn("4.1.79 was published", self.readme)


if __name__ == "__main__":
    unittest.main()
