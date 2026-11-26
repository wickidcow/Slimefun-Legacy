#!/usr/bin/env python3
"""Protect the canonical bundle-first full-stack trigger topology.

A master source change already triggers the complete 45-addon release bundle.
Its trusted successful workflow_run is the exact-source full-stack release
evidence required by the protected release gate. Do not launch a duplicate
full-stack runtime by direct master push, and never lose source path coverage.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FULL = ROOT / '.github/workflows/paper-26.3-full-stack.yml'
BUNDLE = ROOT / '.github/workflows/build-sfl-addons-compat-bundle.yml'
RELEASE = ROOT / '.github/workflows/reproducible-release.yml'

# All original direct-master full-stack source triggers must still reach the
# canonical source-pinned 45-addon bundle (then full-stack via workflow_run).
REQUIRED_DIRECT_SOURCE_PATHS = {
    '.github/workflows/paper-26.3-full-stack.yml',
    'scripts/full_stack_runtime_smoke.sh',
    'scripts/runtime/black_market/**',
    'scripts/test_black_market_probe_contracts.py',
    'scripts/test_full_stack_purpur_26_3.py',
    'scripts/prepare_worldedit_runtime.py',
    'scripts/test_prepare_worldedit_runtime.py',
    'scripts/test_worldedit_stack_integration.py',
    'scripts/download_candidate_bundle.py',
    'scripts/test_download_candidate_bundle.py',
    'scripts/test_workflow_run_trust_boundary.py',
    'scripts/paper_candidate_versions.py',
    'compatibility/sfl-addon-release-matrix.json',
    'build.gradle.kts',
    'gradle.properties',
    'gradle/**',
    'src/**',
}


def paths_for(source: str, event: str) -> set[str]:
    match = re.search(
        rf'(?ms)^  {re.escape(event)}:\n(.*?)(?=^  [a-z_]+:|^[A-Za-z]|\Z)',
        source,
    )
    if match is None:
        raise AssertionError(f'Missing {event} event')
    return set(re.findall(r"(?m)^      - '([^']+)'$", match.group(1)))


def covered_by(paths: set[str], target: str) -> bool:
    return any(p == target or (p.endswith('/**') and target.startswith(p[:-2])) for p in paths)


class ReleaseVersionFullStackTriggerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = FULL.read_text(encoding='utf-8')
        cls.bundle = BUNDLE.read_text(encoding='utf-8')
        cls.release = RELEASE.read_text(encoding='utf-8')

    def test_full_stack_keeps_independent_pr_manual_and_scheduled_modes(self):
        self.assertIn('  pull_request:\n', self.workflow)
        self.assertIn('  workflow_dispatch:\n', self.workflow)
        self.assertIn('  schedule:\n', self.workflow)
        self.assertIn('gradle.properties', paths_for(self.workflow, 'pull_request'))

    def test_no_duplicate_direct_master_full_stack_run(self):
        self.assertNotIn('  push:\n', self.workflow)
        self.assertNotIn('elif [[ "$GITHUB_EVENT_NAME" == "push" ]]; then', self.workflow)
        self.assertIn(
            "  workflow_run:\n    workflows: ['Build SF Addons 1.21.11-26.3 Bundle']\n"
            "    types: [completed]\n", self.workflow
        )
        self.assertIn('    branches: [master]', self.workflow)
        self.assertIn("github.event.workflow_run.event == 'push'", self.workflow)
        self.assertIn('github.event.workflow_run.head_repository.full_name == github.repository', self.workflow)
        self.assertIn("github.event.workflow_run.conclusion == 'success'", self.workflow)

    def test_old_direct_master_paths_still_build_exact_source_bundle(self):
        self.assertIn('    branches: [master]', self.bundle)
        push = paths_for(self.bundle, 'push')
        pr = paths_for(self.workflow, 'pull_request')
        for path in REQUIRED_DIRECT_SOURCE_PATHS:
            with self.subTest(path=path):
                self.assertIn(path, pr, 'Full-stack PR gate missing original source path')
                self.assertTrue(covered_by(push, path),
                                f'Canonical source-pinned bundle does not cover {path}')
        self.assertTrue(covered_by(push, 'scripts/test_worldedit_release_version_trigger.py'))

    def test_release_requires_workflow_run_full_stack_exact_source(self):
        self.assertIn(
            "require_complete paper-26.3-full-stack.yml workflow_run",
            self.release
        )
        self.assertIn('gh run download "$TRIGGER_RUN_ID"', self.workflow)
        self.assertIn('github.event.workflow_run.head_sha || github.sha', self.workflow)


if __name__ == '__main__':
    unittest.main()
