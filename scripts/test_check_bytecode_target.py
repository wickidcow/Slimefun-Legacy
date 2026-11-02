"""Exercise the real bytecode-gate CLI with synthetic class-header JARs, not gameplay."""
from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

CHECKER = Path(__file__).with_name("check_bytecode_target.py")
OWNED = "io/github/thebusybiscuit/slimefun4/Probe.class"


def header(major: int, minor: int = 0) -> bytes:
    # The checker intentionally inspects only these eight bytes, not JVM verification.
    return struct.pack(">IHH", 0xCAFEBABE, minor, major)


class BytecodeTargetTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.jar = self.root / "candidate.jar"
        self.report = self.root / "reports" / "bytecode.txt"

    def run_gate(self, members: dict[str, bytes], java: int = 21, *, all_classes: bool = False):
        with zipfile.ZipFile(self.jar, "w") as archive:
            for name, data in members.items():
                archive.writestr(name, data)
        before = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        command = [sys.executable]
        if sys.flags.optimize:
            command.append("-O")
        gate_args = [str(CHECKER), str(self.jar), "--expected-java", str(java),
                     "--report", str(self.report)]
        if all_classes:
            gate_args.append("--all-classes")
        result = subprocess.run(
            command + gate_args,
            capture_output=True, text=True, timeout=10, check=False,
        )
        self.assertEqual(before, hashlib.sha256(self.jar.read_bytes()).hexdigest(),
                         "Validation must not rewrite the candidate artifact")
        text = self.report.read_text(encoding="utf-8") if self.report.exists() else ""
        return result, text

    def test_java21_nonpreview_and_older_classes_pass(self):
        result, report = self.run_gate({OWNED: header(65), "me/mrCookieSlime/Old.class": header(52)})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("2 Slimefun-owned classes", result.stdout)
        self.assertIn("PASS:", report)

    def test_java21_preview_is_rejected_without_a_false_pass(self):
        result, report = self.run_gate({OWNED: header(65, 65535)})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("65.65535", report)
        self.assertIn("preview", report)
        self.assertIn(OWNED, report)
        self.assertNotIn("PASS:", report)

    def test_earlier_preview_is_not_compatible_with_a_newer_runtime(self):
        for major in (56, 61, 64):
            with self.subTest(major=major):
                result, report = self.run_gate({OWNED: header(major, 65535)})
                self.assertNotEqual(0, result.returncode)
                self.assertIn(f"{major}.65535", report)
                self.assertNotIn("PASS:", report)

    def test_other_modern_nonzero_minors_are_invalid(self):
        for minor in (1, 42, 65534):
            with self.subTest(minor=minor):
                result, report = self.run_gate({OWNED: header(65, minor)})
                self.assertNotEqual(0, result.returncode)
                self.assertIn(f"65.{minor}", report)
                self.assertIn("invalid minor", report)
                self.assertNotIn("PASS:", report)

    def test_historical_major45_through55_allow_nonzero_minor(self):
        # JVMS 4.1 permits any minor for these pre-preview major versions.
        for major, minor in ((45, 3), (45, 65535), (52, 1), (55, 65535)):
            with self.subTest(major=major, minor=minor):
                result, report = self.run_gate({OWNED: header(major, minor)})
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("PASS:", report)

    def test_above_target_still_fails_and_retains_diagnostic(self):
        result, report = self.run_gate({OWNED: header(66)})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Classes above target:", report)
        self.assertIn("major 66", report)
        self.assertNotIn("PASS:", report)

    def test_above_target_preview_reports_both_reasons(self):
        result, report = self.run_gate({OWNED: header(69, 65535)})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Classes above target:", report)
        self.assertIn("69.65535", report)
        self.assertIn("preview", report)

    def test_stable_class_does_not_hide_preview_in_another_owned_namespace(self):
        other = "com/xzavier0722/mc/plugin/slimefun4/Preview.class"
        result, report = self.run_gate({OWNED: header(65), other: header(65, 65535)})
        self.assertNotEqual(0, result.returncode)
        self.assertIn(other, report)
        self.assertNotIn("PASS:", report)

    def test_expected_target_remains_configurable_without_allowing_preview(self):
        result, _ = self.run_gate({OWNED: header(61)}, java=17)
        self.assertEqual(0, result.returncode, result.stderr)
        result, report = self.run_gate({OWNED: header(61, 65535)}, java=17)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("61.65535", report)

    def test_malformed_magic_is_rejected(self):
        result, _ = self.run_gate({OWNED: b"notclass"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid class header", result.stderr)

    def test_truncated_header_is_rejected(self):
        result, _ = self.run_gate({OWNED: header(65)[:7]})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid class header", result.stderr)

    def test_no_owned_classes_cannot_pass(self):
        result, _ = self.run_gate({"external/Other.class": header(65)})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("No Slimefun-owned classes", result.stderr)

    def test_all_classes_opt_in_rejects_preview_in_shaded_and_external(self):
        for path in ("io/github/thebusybiscuit/slimefun4/libraries/Preview.class",
                     "external/Preview.class"):
            with self.subTest(path=path):
                result, report = self.run_gate({OWNED: header(65), path: header(65, 65535)},
                                               all_classes=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(path, report)
                self.assertIn("65.65535", report)
                self.assertNotIn("PASS:", report)

    def test_all_classes_opt_in_rejects_unsupported_shaded_major(self):
        other = "io/github/thebusybiscuit/slimefun4/libraries/New.class"
        result, report = self.run_gate({OWNED: header(65), other: header(69)}, all_classes=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(other, report)
        self.assertIn("major 69", report)

    def test_all_classes_opt_in_rejects_malformed_external_header(self):
        result, _ = self.run_gate({OWNED: header(65), "external/Corrupt.class": b"bad"}, all_classes=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("external/Corrupt.class", result.stderr)

    def test_all_classes_opt_in_allows_stable_mixed_legacy_dependencies(self):
        result, report = self.run_gate({
            OWNED: header(65),
            "io/github/thebusybiscuit/slimefun4/libraries/Old.class": header(52),
            "external/Modern.class": header(61),
        }, all_classes=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("3 packaged classes", result.stdout)
        self.assertIn("every packaged class", report)

    def test_all_classes_opt_in_accepts_historical_valid_nonzero_minor(self):
        result, report = self.run_gate({OWNED: header(65), "external/Old.class": header(45, 3)},
                                       all_classes=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("PASS:", report)

    def test_rejects_invalid_below_jvm_floor_in_both_scopes(self):
        for use_all in (False, True):
            with self.subTest(all_classes=use_all):
                result, report = self.run_gate({OWNED: header(44)}, all_classes=use_all)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("below 45", report)
                self.assertNotIn("PASS:", report)

    def test_existing_shaded_and_external_scope_exclusions_are_unchanged(self):
        result, report = self.run_gate({
            OWNED: header(65),
            "external/Other.class": header(69, 65535),
            "io/github/thebusybiscuit/slimefun4/libraries/Other.class": header(69, 65535),
            "plugin.yml": b"name: Slimefun\n",
        })
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("1 Slimefun-owned classes", result.stdout)
        self.assertIn("PASS:", report)


if __name__ == "__main__":
    unittest.main()
