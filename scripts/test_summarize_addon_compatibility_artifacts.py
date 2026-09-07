#!/usr/bin/env python3
"""Self-tests for the aggregate addon compatibility artifact classifier."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

import summarize_addon_compatibility_artifacts as audit


class AggregateCompatibilityAuditTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.matrix = self.root / "matrix.json"
        self.artifacts = self.root / "artifacts"
        self.summary = self.root / "summary.md"
        self.matrix.write_text(
            json.dumps(
                {
                    "addons": [
                        {
                            "slug": "required-addon",
                            "repository": "example/RequiredAddon",
                            "tier": "required",
                            "advisory": False,
                            "enabled": True,
                        },
                        {
                            "slug": "advisory-addon",
                            "repository": "example/AdvisoryAddon",
                            "tier": "fork-advisory",
                            "advisory": True,
                            "enabled": True,
                        },
                    ]
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_status(self, slug: str, status: str) -> None:
        report = (
            self.artifacts
            / f"addon-compatibility-{slug}"
            / f"{slug}-report"
        )
        report.mkdir(parents=True, exist_ok=True)
        (report / "status.txt").write_text(status + "\n", encoding="utf-8")
        linkage = report / "binary-linkage"
        linkage.mkdir()
        (linkage / "status.txt").write_text("PASS\n", encoding="utf-8")

    def run_audit(self) -> int:
        old_argv = sys.argv
        try:
            sys.argv = [
                "summarize_addon_compatibility_artifacts.py",
                str(self.matrix),
                str(self.artifacts),
                "--summary",
                str(self.summary),
            ]
            return audit.main()
        finally:
            sys.argv = old_argv

    def test_all_pass(self) -> None:
        self.write_status("required-addon", audit.PASS)
        self.write_status("advisory-addon", audit.PASS)
        self.assertEqual(0, self.run_audit())
        self.assertIn("**PASS:**", self.summary.read_text(encoding="utf-8"))

    def test_advisory_baseline_failure_does_not_block(self) -> None:
        self.write_status("required-addon", audit.PASS)
        self.write_status("advisory-addon", audit.BASELINE_BUILD_FAILED)
        self.assertEqual(0, self.run_audit())

    def test_candidate_regression_blocks_even_for_advisory(self) -> None:
        self.write_status("required-addon", audit.PASS)
        self.write_status("advisory-addon", audit.LEGACY_COMPATIBILITY_FAILED)
        self.assertEqual(4, self.run_audit())

    def test_missing_report_is_instrumentation_error(self) -> None:
        self.write_status("required-addon", audit.PASS)
        self.assertEqual(3, self.run_audit())
        self.assertIn("INSTRUMENTATION_ERROR", self.summary.read_text(encoding="utf-8"))

    def test_required_baseline_failure_blocks(self) -> None:
        self.write_status("required-addon", audit.BASELINE_BUILD_FAILED)
        self.write_status("advisory-addon", audit.PASS)
        self.assertEqual(5, self.run_audit())


if __name__ == "__main__":
    unittest.main()
