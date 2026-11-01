"""Protect automatic validation triggers for release-affecting source edits.

The push job validates release gates but cannot publish; publication continues to
require workflow_dispatch with an explicit publish=true on master.
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/reproducible-release.yml"
RELEASE_INPUTS = (
    "gradle.properties",
    "docs/releases/**",
    ".github/workflows/reproducible-release.yml",
    "compatibility/sfl-addon-release-matrix.json",
    "compatibility/release-baselines.json",
    "compatibility/support-contract.json",
    "compatibility/resource-pack-release.json",
    "scripts/published_storage_pair_lock.json",
    "scripts/stage_published_pair.py",
    "scripts/stage_unpublished_pair_candidate.py",
    "scripts/collect_release_runtime_evidence.py",
    "scripts/verify_release_resource_pack.py",
    "scripts/verify_sfl_release_assets.py",
)


def push_paths(source: str) -> set[str]:
    match = re.search(r"(?ms)^  push:\n(.*?)(?=^[a-z][^\n]*:|^permissions:)", source)
    if not match:
        raise AssertionError("Expected an explicit push trigger")
    paths = set()
    in_paths = False
    for line in match.group(1).splitlines():
        if line == "    paths:":
            in_paths = True
        elif in_paths and line.startswith("      - "):
            paths.add(line.removeprefix("      - ").strip().strip("'\\\""))
        elif in_paths and line.strip():
            in_paths = False
    return paths


class ReleaseValidationTriggerTest(unittest.TestCase):
    def test_all_release_affecting_changes_trigger_candidate_validation(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertEqual(set(), set(RELEASE_INPUTS) - push_paths(source))

    def test_push_cannot_publish_and_manual_publish_remains_explicit(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("default: false", source)
        self.assertIn("github.event_name == 'workflow_dispatch'", source)
        self.assertIn("inputs.publish == true", source)
        self.assertNotIn("gh release upload v4.1.78", source)
        self.assertIn("scripts.test_release_validation_triggers -v", source)

    def test_release_evidence_paths_exist(self):
        for name in RELEASE_INPUTS:
            if not any(part in name for part in ("**",)):
                with self.subTest(path=name):
                    self.assertTrue((ROOT / name).is_file(), name)


if __name__ == "__main__":
    unittest.main()
