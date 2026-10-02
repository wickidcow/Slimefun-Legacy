#!/usr/bin/env python3
"""Offline canonical-workflow regressions; the retained Java probe checks actual linkage."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class JegBundleFloorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / '.github/workflows/build-sfl-addons-compat-bundle.yml').read_text()
        marker = '      - name: Rebuild JEG against the supported API floor\n'
        _, found, remainder = cls.workflow.partition(marker)
        cls.step = remainder.split('      - name:', 1)[0] if found else ''

    def test_floor_rebuild_exists(self):
        self.assertIn('Rebuild JEG against the supported API floor', self.workflow)

    def test_only_jeg_uses_this_specific_rebuild(self):
        self.assertIn("if: matrix.slug == 'justenoughguide'", self.step)

    def test_candidate_probe_precedes_floor_and_collection_follows(self):
        candidate = self.workflow.find('      - name: Compile against Paper 26.3 candidate')
        floor = self.workflow.find('      - name: Rebuild JEG against the supported API floor')
        collect = self.workflow.find('      - name: Collect and canonicalize distributable plugin JAR')
        self.assertTrue(0 <= candidate < floor < collect)

    def test_floor_build_keeps_the_same_candidate_core(self):
        self.assertIn('tools/scripts/compile_addon_paper_26_3.py', self.step)
        self.assertIn('candidate-core/Slimefun-Legacy-candidate.jar', self.step)
        self.assertIn('1.21.11-R0.1-SNAPSHOT --report-dir', self.step)

    def test_project_verification_is_not_skipped(self):
        self.assertIn('-DskipTests=false verify', self.step)
        self.assertNotIn('-DskipTests=true', self.step)

    def test_packaged_methods_are_executed_with_the_floor_classpath(self):
        self.assertIn('dependency:build-classpath', self.step)
        self.assertIn('javac --release 21', self.step)
        self.assertIn('${JEG_JARS[0]}:$(cat report-native-floor/classpath.txt)', self.step)
        self.assertIn('JegClipboardLinkageProbe', self.step)
        self.assertIn('JEG_CLIPBOARD_FLOOR_PASS methods=2 scenarios=6', self.step)
        self.assertIn('set -euo pipefail', self.step)

    def test_manifest_reports_the_actual_distributable_api(self):
        self.assertIn("if slug in ('slimeeasy', 'dracfunreborn', 'justenoughguide')", self.workflow)

    def test_nonshipped_probe_exercises_both_real_overloads(self):
        source = (ROOT / 'tests/runtime/JegClipboardLinkageProbe.java').read_text()
        self.assertEqual(2, source.count('getMethod("makeComponentPaper"'))
        self.assertIn('ClickEvent.Action.COPY_TO_CLIPBOARD', source)
        self.assertIn('component.hoverEvent() == null', source)
        self.assertIn('Class.forName("com.balugaq.jeg.utils.ClipboardUtil")', source)


if __name__ == '__main__':
    unittest.main(argv=[__file__])
