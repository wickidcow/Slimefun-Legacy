#!/usr/bin/env python3
"""Regression checks for direct, exact-source full-stack release triggers.

These inspect the retained workflow's simple path-list layout. They do not
claim to implement GitHub's complete workflow expression/YAML language.
"""
from pathlib import Path
import re
import unittest


class ReleaseVersionFullStackTriggerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        cls.workflow = (root / '.github/workflows/paper-26.3-full-stack.yml').read_text(encoding='utf-8')

    def paths_for(self, event):
        match = re.search(
            rf'(?ms)^  {re.escape(event)}:\n(.*?)(?=^  [a-z_]+:|^[A-Za-z]|\Z)',
            self.workflow,
        )
        self.assertIsNotNone(match, f'Missing {event} event')
        return set(re.findall(r"(?m)^      - '([^']+)'$", match.group(1)))

    def test_version_change_triggers_pull_request_stack(self):
        self.assertIn('gradle.properties', self.paths_for('pull_request'))

    def test_version_change_triggers_exact_master_stack(self):
        self.assertIn('gradle.properties', self.paths_for('push'))

    def test_existing_bundle_and_production_triggers_remain(self):
        for event in ('push', 'pull_request'):
            with self.subTest(event=event):
                self.assertTrue({'compatibility/sfl-addon-release-matrix.json',
                                 'build.gradle.kts', 'src/**'}.issubset(self.paths_for(event)))


if __name__ == '__main__':
    unittest.main()
