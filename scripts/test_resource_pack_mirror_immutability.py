"""Regression contracts: pinned pack validation must never rewrite existing releases."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MIRROR = ROOT / ".github/workflows/mirror-resource-pack-release.yml"
RELEASE = ROOT / ".github/workflows/reproducible-release.yml"
VERIFIER = ROOT / "scripts/verify_release_resource_pack.py"


class ResourcePackMirrorImmutabilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mirror = MIRROR.read_text(encoding="utf-8")
        cls.release = RELEASE.read_text(encoding="utf-8")
        cls.verifier = VERIFIER.read_text(encoding="utf-8")

    def test_mirror_only_reads_source_and_uploads_ci_artifact(self):
        self.assertIn('gh release download "$PACK_TAG"', self.mirror)
        self.assertIn('gh api "repos/${PACK_REPO}/releases/tags/${PACK_TAG}"', self.mirror)
        self.assertIn("python3 scripts/verify_release_resource_pack.py .", self.mirror)
        self.assertIn("pinned-slimefun-resource-pack", self.mirror)
        self.assertIn("actions/upload-artifact@", self.mirror)

    def test_no_event_can_modify_a_published_release(self):
        self.assertIn("contents: read", self.mirror)
        self.assertNotIn("contents: write", self.mirror)
        self.assertNotIn("publish-current-release:", self.mirror)
        self.assertNotRegex(
            self.mirror,
            r"\bgh\s+release\s+(?:upload|create|edit|delete(?:-asset)?)\b",
        )
        self.assertNotIn("--clobber", self.mirror)
        self.assertIn("scripts.test_resource_pack_mirror_immutability -v", self.mirror)

    def test_new_release_still_contains_approved_pack(self):
        self.assertIn('gh release create "$TAG" "$JAR" "$BUNDLE" "$RESOURCE_PACK"', self.release)
        self.assertNotIn("--clobber", self.release)
        self.assertIn("mirror never mutates published releases", self.verifier)
        self.assertIn("mirror workflow verifies pinned source bytes", self.verifier)


if __name__ == "__main__":
    unittest.main()
