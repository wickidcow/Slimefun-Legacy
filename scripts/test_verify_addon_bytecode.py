#!/usr/bin/env python3
"""Header/CLI regressions; synthetic class headers are not JVM gameplay tests."""
from __future__ import annotations

import contextlib
import hashlib
import io
import json
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

from verify_addon_bytecode import inspect_jar, main, multi_release


def header(major=65, minor=0):
    return b'\xca\xfe\xba\xbe' + minor.to_bytes(2, 'big') + major.to_bytes(2, 'big')


class AddonBytecodeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / 'addon.jar'

    def write_jar(self, entries, manifest=None):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.jar, 'w') as archive:
                if manifest is not None:
                    archive.writestr('META-INF/MANIFEST.MF', manifest)
                for name, value in entries:
                    archive.writestr(name, value)

    def run_cli(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            result = main([str(self.jar), *args])
        return result, out.getvalue(), err.getvalue()

    def test_accepts_java21_and_older_shaded_classes(self):
        self.write_jar([('addon/Main.class', header()), ('lib/Old.class', header(52))])
        result = inspect_jar(self.jar, 21)
        self.assertEqual('PASS', result['result'])
        self.assertEqual({52: 1, 65: 1}, result['class_major_histogram'])

    def test_rejects_java25_base_classes(self):
        self.write_jar([('addon/Main.class', header(69))])
        self.assertEqual('FAIL', inspect_jar(self.jar, 21)['result'])

    def test_rejects_newer_shaded_classes_not_just_owned_names(self):
        self.write_jar([('addon/Main.class', header()), ('any/shaded/Library.class', header(69))])
        failures = inspect_jar(self.jar, 21)['incompatible_classes']
        self.assertEqual(['any/shaded/Library.class'], [r['class_name'] for r in failures])

    def test_rejects_preview_mode_at_the_allowed_major(self):
        self.write_jar([('addon/Main.class', header(65, 65535))])
        self.assertEqual('FAIL', inspect_jar(self.jar, 21)['result'])

    def test_truncated_wrong_magic_and_invalid_version_fail(self):
        for value in (b'', b'\xca\xfe\xba\xbe', b'NOTCLASS', header(0)):
            with self.subTest(value=value):
                self.write_jar([('addon/Main.class', value)])
                with self.assertRaises(ValueError):
                    inspect_jar(self.jar, 21)

    def test_empty_and_versioned_only_jars_fail(self):
        for entries in ([], [('META-INF/versions/25/addon/Main.class', header(69))]):
            with self.subTest(entries=entries):
                self.write_jar(entries, 'Multi-Release: true\n')
                with self.assertRaises(ValueError):
                    inspect_jar(self.jar, 21)

    def test_duplicate_entries_fail(self):
        self.write_jar([('addon/Main.class', header()), ('addon/Main.class', header(69))])
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            inspect_jar(self.jar, 21)

    def test_applicable_multi_release_overlay_is_checked(self):
        self.write_jar([('addon/Main.class', header()), ('META-INF/versions/21/addon/Main.class', header(69))], 'Multi-Release: true\n')
        self.assertEqual('FAIL', inspect_jar(self.jar, 21)['result'])

    def test_higher_multi_release_overlay_is_reported_separately(self):
        self.write_jar([('addon/Main.class', header()), ('META-INF/versions/25/addon/Main.class', header(69))], 'Multi-Release: true\n')
        result = inspect_jar(self.jar, 21)
        self.assertEqual('PASS', result['result'])
        self.assertEqual(1, result['ignored_versioned_classes'])
        self.assertEqual(1, result['checked_classes'])

    def test_non_multi_release_overlays_are_not_loaded(self):
        self.write_jar([('addon/Main.class', header()), ('META-INF/versions/21/addon/Main.class', header(69))])
        self.assertEqual('PASS', inspect_jar(self.jar, 21)['result'])

    def test_manifest_case_continuations_and_main_section(self):
        self.assertTrue(multi_release('Manifest-Version: 1.0\r\nMulti-ReLEase: tr\r\n ue\r\n\r\nName: x\r\n'))
        self.assertFalse(multi_release('Manifest-Version: 1.0\n\nName: x\nMulti-Release: true\n'))
        self.assertFalse(multi_release('Multi-Release: false\n'))

    def test_invalid_encoding_and_zip_fail_closed(self):
        self.write_jar([('addon/Main.class', header())], b'\xff')
        self.assertEqual(1, self.run_cli()[0])
        self.jar.write_bytes(b'not a zip')
        self.assertEqual(1, self.run_cli()[0])

    def test_missing_input_replaces_stale_success_report(self):
        report = self.root / 'report.json'
        report.write_text('{"result":"PASS"}')
        self.assertEqual(1, self.run_cli('--report', str(report))[0])
        self.assertEqual('FAIL', json.loads(report.read_text())['result'])

    def test_success_and_failure_do_not_modify_jar(self):
        for major in (65, 69):
            with self.subTest(major=major):
                self.write_jar([('addon/Main.class', header(major))])
                before = hashlib.sha256(self.jar.read_bytes()).digest()
                result, out, err = self.run_cli()
                self.assertEqual(int(major > 65), result)
                self.assertEqual(before, hashlib.sha256(self.jar.read_bytes()).digest())

    def test_reports_success_and_original_histogram(self):
        self.write_jar([('addon/Main.class', header())])
        report = self.root / 'subdir/report.json'
        status, out, err = self.run_cli('--report', str(report))
        self.assertEqual(0, status)
        self.assertFalse(err)
        self.assertIn('passed', out)
        self.assertEqual({'65': 1}, json.loads(report.read_text())['class_major_histogram'])

    def test_unwritable_report_fails_even_for_valid_jar(self):
        self.write_jar([('addon/Main.class', header())])
        self.assertEqual(1, self.run_cli('--report', str(self.root))[0])

    def test_requested_target_is_honored(self):
        self.write_jar([('addon/Main.class', header(69))])
        self.assertEqual('PASS', inspect_jar(self.jar, 25)['result'])
        with self.assertRaises(ValueError):
            inspect_jar(self.jar, 7)


if __name__ == '__main__':
    unittest.main(argv=[__file__])
