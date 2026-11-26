"""Release-safe, low-noise Actions schedule and event trigger regressions.

Every release-affecting source push and PR still builds the canonical 45-addon
bundle. Unchanged default branch does not compile 45 addon JARs each night.
Published-asset verification belongs to release publication, not a build.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / ".github/workflows/build-sfl-addons-compat-bundle.yml"
PUBLISHED = ROOT / ".github/workflows/publish-sfl-addon-release-jars.yml"
RELEASE = ROOT / ".github/workflows/reproducible-release.yml"


class ActionsReleaseTriggerPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = BUNDLE.read_text(encoding="utf-8")
        cls.published = PUBLISHED.read_text(encoding="utf-8")
        cls.release = RELEASE.read_text(encoding="utf-8")

    def test_bundle_still_compiles_on_source_and_manual_dispatch(self):
        self.assertIn("  push:\n", self.bundle)
        self.assertIn("  pull_request:\n", self.bundle)
        self.assertIn("  workflow_dispatch:\n", self.bundle)
        for required in (
            "      - 'src/**'",
            "      - 'scripts/**'",
            "      - 'gradle.properties'",
            "      - '.github/workflows/reproducible-release.yml'",
            "      - 'compatibility/sfl-addon-release-matrix.json'",
        ):
            with self.subTest(trigger=required):
                self.assertIn(required, self.bundle)

    def test_idle_45_addon_rebuild_is_weekly_not_daily(self):
        self.assertIn("  schedule:\n", self.bundle)
        self.assertIn("cron: '17 6 * * 0'", self.bundle)
        self.assertNotIn("cron: '17 6 * * *'", self.bundle)

    def test_published_validator_checks_publication_not_bundle_completion(self):
        self.assertIn("  release:\n    types: [published]\n", self.published)
        self.assertNotIn("  workflow_run:\n", self.published)
        self.assertNotIn("github.event.workflow_run.", self.published)
        self.assertIn("  pull_request:\n", self.published)
        self.assertIn("  push:\n", self.published)
        self.assertIn("  workflow_dispatch:\n", self.published)
        self.assertIn('gh api "repos/$GITHUB_REPOSITORY/releases/$RELEASE_EVENT_ID"', self.published)
        self.assertIn('gh api "repos/$GITHUB_REPOSITORY/releases/latest"', self.published)
        self.assertIn("sha256:", self.published)
        self.assertIn("scripts/verify_published_addon_bundle.py", self.published)
        self.assertIn("permissions:\n  contents: read", self.published)
        self.assertNotIn("contents: write", self.published)

    def test_exact_source_publication_remains_explicit_and_immutable(self):
        self.assertIn("  workflow_dispatch:\n", self.release)
        self.assertIn("inputs.publish == true", self.release)
        self.assertIn("github.ref == 'refs/heads/master'", self.release)
        self.assertIn("Refusing to overwrite an existing release at another source", self.release)
        self.assertNotIn("--clobber", self.release)


if __name__ == "__main__":
    unittest.main()
