#!/usr/bin/env python3
"""Regression tests for the supplementary configuration smoke gate."""
from __future__ import annotations

import contextlib
import io
import re
import tempfile
import unittest
from pathlib import Path

from verify_runtime_configuration import configuration_failures, main


class RuntimeConfigurationTest(unittest.TestCase):
    def test_bukkit_configuration_exception_is_not_a_successful_enable(self):
        text = ('[INFO]: [FinalTECH-Changed] Enabling FinalTECH-Changed v3.0.2\n'
                '[ERROR]: org.bukkit.configuration.InvalidConfigurationException: invalid YAML\n'
                '[INFO]: Done (4.2s)! For help, type "help"\n')
        old = r'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError'
        self.assertIsNone(re.search(old, text))
        self.assertEqual([(2, text.splitlines()[1])], configuration_failures(text))

    def test_yaml_exception_variants_and_relocated_packages(self):
        for exception in ('parser.ParserException', 'scanner.ScannerException', 'constructor.ConstructorException', 'error.YAMLException'):
            with self.subTest(exception=exception):
                self.assertEqual(1, len(configuration_failures(f'Caused by: plugin.shaded.org.yaml.snakeyaml.{exception}: broken')))

    def test_yaml_path_failures_without_a_printed_exception(self):
        for message in ('Cannot load plugins/FinalTECH/en-US.yml', 'Could not load "plugins/A/config.yaml"',
                        'Unable to parse plugins/A/config.yml', 'Failed to read settings.yml',
                        'Error loading en-US.yml', 'ERROR parsing config.yaml'):
            with self.subTest(message=message):
                self.assertEqual(1, len(configuration_failures(message)))

    def test_normal_startup_and_unrelated_warnings_are_not_rejected(self):
        text = '\n'.join(('Loaded config.yml', 'Created default en-US.yml', 'InvalidConfigurationExceptionHandler enabled',
                          'WARNING: Unsafe is deprecated', 'Failed to load optional texture.png',
                          'Previous clean shutdown: Yes', 'No configuration errors detected',
                          'Missing optional integration; continuing', 'Done (3.0s)!'))
        self.assertEqual([], configuration_failures(text))

    def test_matching_lines_are_unique_and_ordered(self):
        text = 'header\r\nCannot load en-US.yml: InvalidConfigurationException\r\nseparator\r\nFailed to parse config.yaml\r\n'
        self.assertEqual([(2, text.splitlines()[1]), (4, text.splitlines()[3])], configuration_failures(text))

    def run_cli(self, path):
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            status = main([str(path)])
        return status, output.getvalue(), errors.getvalue()

    def test_missing_empty_directory_and_invalid_encoding_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            missing, blank, invalid = root / 'missing.log', root / 'blank.log', root / 'invalid.log'
            blank.write_text(' \n\t')
            invalid.write_bytes(b'\xff\xfe')
            for path in (missing, blank, invalid, root):
                with self.subTest(path=path):
                    status, out, err = self.run_cli(path)
                    self.assertEqual(1, status)
                    self.assertFalse(out)
                    self.assertIn('failed', err)

    def test_cli_accepts_clean_log_without_mutating_it(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'first.log'
            original = b'[INFO] Loaded config.yml\r\n[INFO] Done (2s)!\r\n'
            path.write_bytes(original)
            status, out, err = self.run_cli(path)
            self.assertEqual(0, status)
            self.assertIn('passed', out)
            self.assertFalse(err)
            self.assertEqual(original, path.read_bytes())

    def test_each_restart_log_is_checked_independently(self):
        with tempfile.TemporaryDirectory() as directory:
            first, second = Path(directory) / 'first.log', Path(directory) / 'second.log'
            first.write_text('Enabling FinalTECH v3.0.2\nDone (2s)!\n')
            second.write_text('Enabling FinalTECH v3.0.2\nInvalidConfigurationException\nDone (2s)!\n')
            self.assertEqual(0, self.run_cli(first)[0])
            self.assertEqual(1, self.run_cli(second)[0])

    def test_diagnostics_are_bounded_but_full_count_is_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'errors.log'
            original = ('InvalidConfigurationException\n' * 100).encode()
            path.write_bytes(original)
            status, out, err = self.run_cli(path)
            self.assertEqual(1, status)
            self.assertFalse(out)
            self.assertIn('100 matching lines', err)
            self.assertIn('40: InvalidConfigurationException', err)
            self.assertNotIn('41: InvalidConfigurationException', err)
            self.assertEqual(original, path.read_bytes())


if __name__ == '__main__':
    unittest.main(argv=[__file__])
