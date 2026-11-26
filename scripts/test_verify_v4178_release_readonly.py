"""Historical Geyser release immutability checks owned by current release workflows."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MIRROR = ROOT / ".github/workflows/mirror-resource-pack-release.yml"
RELEASE = ROOT / ".github/workflows/reproducible-release.yml"
RETIRED = ROOT / ".github/workflows/restore-unchanged-geyser-release.yml"


class HistoricalReleaseReadOnlyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mirror = MIRROR.read_text(encoding="utf-8")
        cls.release = RELEASE.read_text(encoding="utf-8")

    def test_obsolete_v4178_only_workflow_is_retired(self):
        self.assertFalse(RETIRED.exists())
        self.assertIn("scripts.test_verify_v4178_release_readonly -v", self.mirror)
        self.assertIn("scripts.test_verify_v4178_bedrock_carry -v", self.mirror)

    def test_historical_verifier_changes_trigger_its_new_test_owner(self):
        for event in ("pull_request", "push"):
            with self.subTest(event=event):
                block = re.search(
                    rf"(?ms)^  {event}:\n(.*?)(?=^  \S|\Z)", self.mirror
                )
                self.assertIsNotNone(block, f"Missing {event} trigger")
                for path in (
                    "scripts/verify_v4178_bedrock_carry.py",
                    "scripts/test_verify_v4178_bedrock_carry.py",
                    "scripts/test_verify_v4178_release_readonly.py",
                ):
                    self.assertIn(f"      - '{path}'", block.group(1))

    def test_current_mirror_has_read_only_release_permissions(self):
        self.assertIn("permissions:\n  contents: read", self.mirror)
        self.assertNotIn("contents: write", self.mirror)
        self.assertNotRegex(
            self.mirror,
            r"\bgh\s+release\s+(?:upload|delete|delete-asset|edit|create)\b",
        )
        self.assertNotRegex(
            self.mirror,
            r"\bgh\s+api\s+(?:--method|-[XP])\s*(?:POST|PATCH|DELETE)",
        )

    def test_unchanged_geyser_source_is_byte_pinned_for_new_releases(self):
        for required in (
            "GEYSER_SOURCE_TAG: v4.1.78",
            'GEYSER_SOURCE_RELEASE_ID: "425012427"',
            'GEYSER_SOURCE_ASSET_ID: "682983055"',
            'GEYSER_SOURCE_SIZE: "1339867"',
            "GEYSER_SOURCE_SHA256: e11e5f8e6ede75a17421039e50178e83e11e9cf7f020e99596cfc078801b8c58",
            'gh release download "$SOURCE"',
            "sha256sum --check --strict",
        ):
            with self.subTest(required=required):
                self.assertIn(required, self.release)

    def test_historical_release_assets_are_never_overwritten(self):
        self.assertIn("Refusing to overwrite an existing release at another source", self.release)
        self.assertIn("Release $TAG already exists at identical source; leave all asset bytes unchanged", self.release)
        self.assertNotRegex(
            self.release,
            r"\bgh\s+release\s+(?:upload|delete|delete-asset|edit)\b",
        )
        self.assertNotIn("--clobber", self.release)


if __name__ == "__main__":
    unittest.main()
