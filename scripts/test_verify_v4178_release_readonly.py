"""Prevent a historical published release from being modified by CI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/restore-unchanged-geyser-release.yml"


class HistoricalReleaseReadOnlyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_only_read_permissions_and_verification_job(self):
        self.assertIn("permissions:\n  contents: read", self.workflow)
        self.assertNotIn("contents: write", self.workflow)
        self.assertIn("\n  verify:\n", self.workflow)
        self.assertNotIn("\n  append-only:\n", self.workflow)

    def test_no_historical_release_mutation_commands(self):
        self.assertNotRegex(self.workflow, r"gh\s+release\s+(?:upload|delete|delete-asset|edit|create)\b")
        self.assertNotRegex(self.workflow, r"gh\s+api\s+(?:--method|-[XP])\s*(?:POST|PATCH|DELETE)")
        self.assertIn("--require-present", self.workflow)

    def test_preserve_exact_original_zip_and_read_only_receipts(self):
        self.assertIn("gh release download v4.1.77", self.workflow)
        self.assertIn('gh api "repos/$GITHUB_REPOSITORY/releases/tags/v4.1.78"', self.workflow)
        self.assertIn("e11e5f8e6ede75a17421039e50178e83e11e9cf7f020e99596cfc078801b8c58", self.workflow)
        self.assertIn("sha256sum --check --strict", self.workflow)
        self.assertIn("unchanged-geyser-zip-carry-preflight", self.workflow)


if __name__ == "__main__":
    unittest.main()
