"""Protect exact-source validation triggers for release-affecting inputs.

The release workflow runs pre-publication validation on selected master pushes,
but publication remains a separate explicit workflow_dispatch publish=true.
Every release-triggering push must also generate exact-source canonical bundle
and native recovery evidence, or the release check cannot succeed.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/reproducible-release.yml"
BUNDLE = ROOT / ".github/workflows/build-sfl-addons-compat-bundle.yml"
NATIVE = ROOT / ".github/workflows/native-entity-retirement.yml"
RELEASE_INPUTS = (
    "gradle.properties",
    "docs/releases/**",
    ".github/workflows/reproducible-release.yml",
    "scripts/runtime_download.sh",
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


def event_paths(source: str, event: str) -> set[str]:
    lines = source.splitlines()
    marker = "  " + event + ":"
    if marker not in lines:
        raise AssertionError("Missing workflow trigger: " + event)
    start = lines.index(marker) + 1
    inside_paths = False
    paths = set()
    for line in lines[start:]:
        # The first next event or top-level YAML key ends this event's block.
        if line and not line.startswith("    "):
            break
        if line == "    paths:":
            inside_paths = True
        elif inside_paths and line.startswith("      - "):
            paths.add(line[8:].strip().strip("'\""))
        elif inside_paths and line.strip():
            inside_paths = False
    return paths


def covered_by(paths: set[str], required: str) -> bool:
    return any(
        path == required or
        (path.endswith("/**") and required.startswith(path[:-2]))
        for path in paths
    )


class ReleaseValidationTriggerTest(unittest.TestCase):
    def test_source_locks_trigger_release_validation(self):
        release = event_paths(WORKFLOW.read_text(encoding="utf-8"), "push")
        for item in RELEASE_INPUTS:
            with self.subTest(item=item):
                self.assertTrue(covered_by(release, item), item)

    def test_each_release_push_generates_bundle_and_native_evidence(self):
        for workflow in (BUNDLE, NATIVE):
            source = workflow.read_text(encoding="utf-8")
            for event in ("push", "pull_request"):
                paths = event_paths(source, event)
                for item in RELEASE_INPUTS:
                    with self.subTest(workflow=workflow.name, event=event, item=item):
                        self.assertTrue(covered_by(paths, item),
                                        f"{workflow.name} {event} does not cover {item}")

    def test_missing_push_path_is_not_hidden_by_a_pull_request_trigger(self):
        source = BUNDLE.read_text(encoding="utf-8")
        required = ".github/workflows/reproducible-release.yml"
        self.assertTrue(covered_by(event_paths(source, "push"), required))
        source = source.replace("      - '" + required + "'", "", 1)
        self.assertFalse(covered_by(event_paths(source, "push"), required))
        self.assertTrue(covered_by(event_paths(source, "pull_request"), required))

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
            if "**" not in name:
                with self.subTest(path=name):
                    self.assertTrue((ROOT / name).is_file(), name)


if __name__ == "__main__":
    unittest.main()
