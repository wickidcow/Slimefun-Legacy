#!/usr/bin/env python3
"""Static CI guard: workflow_run must not execute untrusted fork PR code."""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def pull_request_paths(source):
    section = source.split('\n  pull_request:\n', 1)[1]
    paths = []
    for line in section.splitlines():
        if line and not line.startswith('    '):
            break
        match = re.match(r"^\s+- '([^']+)'", line)
        if match:
            paths.append(match.group(1))
    return paths


class WorkflowRunForkIsolationTest(unittest.TestCase):
    def test_full_stack_does_not_recheckout_pr_from_privileged_workflow_run(self):
        source = (ROOT / '.github/workflows/paper-26.3-full-stack.yml').read_text()
        self.assertEqual(source.count("github.event.workflow_run.event != 'pull_request'"), 2)
        self.assertNotIn('allow-unsafe-pr-checkout', source)

    def test_direct_pr_full_stack_covers_every_bundle_pr_path(self):
        bundle = (ROOT / '.github/workflows/build-sfl-addons-compat-bundle.yml').read_text()
        full = (ROOT / '.github/workflows/paper-26.3-full-stack.yml').read_text()
        covered = pull_request_paths(full)
        for path in pull_request_paths(bundle):
            matches = path in covered or any(
                pattern.endswith('/**') and path.startswith(pattern[:-3] + '/')
                for pattern in covered
            )
            self.assertTrue(matches, f'Full-stack pull_request missing bundle trigger: {path}')

    def test_audit_executes_trusted_classifier_and_only_reads_pr_matrix_data(self):
        source = (ROOT / '.github/workflows/addon-compatibility-audit.yml').read_text()
        self.assertIn("steps.source.outputs.source_event != 'pull_request'", source)
        self.assertIn('repos/$GITHUB_REPOSITORY/contents/scripts/summarize_addon_compatibility_artifacts.py', source)
        self.assertIn('repos/$SOURCE_REPO/contents/compatibility/addon-compatibility-matrix.json', source)
        self.assertNotIn('allow-unsafe-pr-checkout', source)


if __name__ == '__main__':
    unittest.main()
