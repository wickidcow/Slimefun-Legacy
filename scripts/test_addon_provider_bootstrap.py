#!/usr/bin/env python3
"""Regression tests for provider-aware addon compiler workflow setup.

Execute the actual embedded matrix generator with small temporary JSON
inputs. Static checks apply only to this workflow's retained YAML layout;
this is not a general GitHub Actions/YAML interpreter.
"""
from pathlib import Path
import json
import os
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / '.github/workflows/paper-26.3-addon-compile.yml'
STAFF = 'wickidcow/SF_BuildingStaff'


class AddonProviderBootstrapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding='utf-8')
        section = cls.workflow.split('      - name: Generate compile targets\n', 1)[1].split('      - name:', 1)[0]
        match = re.search(r"(?ms)^          python3 - <<'PY'\n(.*?)^          PY\n", section)
        if match is None:
            raise AssertionError('Cannot locate the actual matrix generator')
        cls.matrix_code = textwrap.dedent(match.group(1))

    def generate(self, event, required, release):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'compatibility').mkdir()
            (root / 'compatibility/addon-compatibility-matrix.json').write_text(json.dumps({'addons': required}), encoding='utf-8')
            (root / 'compatibility/sfl-addon-release-matrix.json').write_text(json.dumps({'addons': release}), encoding='utf-8')
            output = root / 'outputs.txt'
            result = subprocess.run([sys.executable, '-c', self.matrix_code], cwd=root,
                                    env={**os.environ, 'EVENT_NAME': event, 'GITHUB_OUTPUT': str(output)},
                                    text=True, capture_output=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stderr)
            values = dict(line.split('=', 1) for line in output.read_text(encoding='utf-8').splitlines())
            rows = json.loads(values['matrix'])['include']
            self.assertEqual(len(rows), int(values['count']))
            return rows

    def bootstrap(self):
        title = '      - name: Install BuildingStaff optional compile-only provider API\n'
        self.assertEqual(1, self.workflow.count(title), 'Provider bootstrap missing or duplicated')
        return self.workflow.split(title, 1)[1].split('      - name:', 1)[0]

    def test_bootstrap_is_restricted_and_failure_is_not_ignored(self):
        step = self.bootstrap()
        self.assertIn("        if: matrix.repository == 'wickidcow/SF_BuildingStaff'\n", step)
        self.assertIn('        run: bash addon/scripts/install_rebar_api.sh\n', step)
        self.assertNotIn('continue-on-error', step)
        self.assertNotIn('|| true', step)

    def test_provider_setup_precedes_compilation_after_clone(self):
        self.bootstrap()
        self.assertLess(self.workflow.index('      - name: Clone maintained addon\n'),
                        self.workflow.index('      - name: Install BuildingStaff optional compile-only provider API\n'))
        self.assertLess(self.workflow.index('      - name: Install BuildingStaff optional compile-only provider API\n'),
                        self.workflow.index('      - name: Compile addon against candidate stack\n'))

    def test_required_pr_targets_and_optional_provider_target_are_retained(self):
        rows = self.generate('pull_request', [
            {'repository': 'wickidcow/SF_DynaTech', 'enabled': True, 'tier': 'required'},
            {'repository': 'wickidcow/SF_Disabled', 'enabled': False, 'tier': 'required'},
            {'repository': STAFF, 'enabled': True, 'tier': 'optional'},
        ], [{'repository': STAFF, 'slug': 'buildingstaff'}])
        self.assertEqual({'wickidcow/SF_DynaTech', STAFF}, {row['repository'] for row in rows})

    def test_provider_already_required_is_not_duplicated(self):
        rows = self.generate('pull_request', [{'repository': STAFF, 'enabled': True, 'tier': 'required'}],
                             [{'repository': STAFF, 'slug': 'buildingstaff'}])
        self.assertEqual([STAFF], [row['repository'] for row in rows])

    def test_unbundled_provider_is_not_invented(self):
        rows = self.generate('pull_request', [{'repository': 'wickidcow/SF_DynaTech', 'enabled': True, 'tier': 'required'}], [])
        self.assertEqual(['wickidcow/SF_DynaTech'], [row['repository'] for row in rows])

    def test_push_still_compiles_the_complete_canonical_set(self):
        release = [{'repository': STAFF}, {'repository': 'wickidcow/SF_DynaTech'}, {'repository': 'wickidcow/WorldEditSlimefun'}]
        rows = self.generate('push', [], release)
        self.assertEqual({row['repository'] for row in release}, {row['repository'] for row in rows})

    def test_manual_and_scheduled_runs_keep_full_membership(self):
        release = [{'repository': STAFF}, {'repository': 'wickidcow/SF_DynaTech'}]
        for event in ('workflow_dispatch', 'schedule'):
            with self.subTest(event=event):
                self.assertEqual(2, len(self.generate(event, [], release)))

    def test_regressions_are_executed_and_path_filtered(self):
        self.assertIn('          python3 scripts/test_addon_provider_bootstrap.py\n', self.workflow)
        self.assertEqual(2, self.workflow.count("      - 'scripts/test_addon_provider_bootstrap.py'\n"))


if __name__ == '__main__':
    unittest.main()
