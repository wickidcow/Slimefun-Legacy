"""Actions cleanup regressions: preserve protected release checks, reduce idle CI.

The standalone published-archive workflow is retired: GITHUB_TOKEN-created
GitHub releases do not reliably emit a separate workflow trigger. The protected
reproducible release job now validates the same archive and published bytes.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / ".github/workflows/build-sfl-addons-compat-bundle.yml"
RELEASE = ROOT / ".github/workflows/reproducible-release.yml"
NATIVE = ROOT / ".github/workflows/native-entity-retirement.yml"
RETIRED = ROOT / ".github/workflows/publish-sfl-addon-release-jars.yml"


class ActionsReleaseTriggerPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = BUNDLE.read_text(encoding="utf-8")
        cls.release = RELEASE.read_text(encoding="utf-8")
        cls.native = NATIVE.read_text(encoding="utf-8")

    def test_bundle_still_compiles_on_source_and_manual_dispatch(self):
        for trigger in ("  push:\n", "  pull_request:\n", "  workflow_dispatch:\n"):
            self.assertIn(trigger, self.bundle)
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

    def test_redundant_published_archive_workflow_is_retired(self):
        self.assertFalse(RETIRED.exists())
        self.assertIn("Verify canonical addon ZIP with independent published-archive integrity validator", self.release)
        self.assertIn('python3 scripts/verify_published_addon_bundle.py "dist/$' + '{CANONICAL_ADDON_BUNDLE}"', self.release)
        self.assertIn("scripts.test_actions_release_trigger_policy -v", self.release)
        self.assertIn("scripts.test_actions_release_trigger_policy -v", self.bundle)
        self.assertIn("result['addon_count'] != 45", self.release)
        self.assertIn("> build/release-verification/published-addon-validation.json", self.release)

    def test_new_source_validator_changes_retrigger_exact_release_evidence(self):
        for required in (
            "scripts/verify_published_addon_bundle.py",
            "scripts/test_verify_published_addon_bundle.py",
            "scripts/test_actions_release_trigger_policy.py",
        ):
            with self.subTest(path=required):
                self.assertIn(f"      - '{required}'", self.release)
                self.assertEqual(2, self.native.count(f"      - '{required}'"))
                self.assertIn("      - 'scripts/**'", self.bundle)

    def test_published_assets_still_verified_and_immutable(self):
        self.assertIn("  workflow_dispatch:\n", self.release)
        self.assertIn("inputs.publish == true", self.release)
        self.assertIn("github.ref == 'refs/heads/master'", self.release)
        self.assertIn("Refusing to overwrite an existing release at another source", self.release)
        self.assertIn('gh release download "$TAG"', self.release)
        self.assertIn('cmp "dist/$file" "verify-release-bytes/$file"', self.release)
        self.assertNotIn("--clobber", self.release)


if __name__ == "__main__":
    unittest.main()
