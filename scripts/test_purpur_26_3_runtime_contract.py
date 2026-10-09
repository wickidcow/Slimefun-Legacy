#!/usr/bin/env python3
"""Offline compatibility contracts for the shared 26.2/Purpur-26.3 runtime smoke harness."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "paper_family_26_2_runtime_smoke.sh"


def probe(software: str, minecraft: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["SERVER_SOFTWARE"] = software
    env["SERVER_MINECRAFT_VERSION"] = minecraft
    env["SLIMEFUN_SMOKE_VERSION"] = next(
        row.split("=", 1)[1]
        for row in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if row.startswith("projectVersion=")
    )
    # The fake JAR fails before any download or world creation is attempted.
    return subprocess.run(
        ["bash", str(SCRIPT), str(ROOT / "missing-purpur-milestone-test.jar")],
        env=env,
        capture_output=True,
        text=True,
        check=False,
        timeout=10,
    )


class TestPurpur263RuntimeContract(unittest.TestCase):
    def test_supported_existing_26_2_targets_unchanged(self) -> None:
        for software in ("purpur", "folia", "leaf"):
            with self.subTest(software=software):
                result = probe(software, "26.2")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Slimefun JAR not found or empty", result.stderr)
                self.assertNotIn("Unsupported server/version combination", result.stderr)

    def test_purpur_26_3_is_accepted_as_a_candidate(self) -> None:
        result = probe("purpur", "26.3")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Slimefun JAR not found or empty", result.stderr)
        self.assertNotIn("Unsupported server/version combination", result.stderr)

    def test_folia_and_leaf_26_3_not_prematurely_claimed(self) -> None:
        for software in ("folia", "leaf"):
            with self.subTest(software=software):
                result = probe(software, "26.3")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Unsupported server/version combination", result.stderr)

    def test_future_purpur_versions_are_not_implicitly_certified(self) -> None:
        result = probe("purpur", "26.4")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unsupported server/version combination", result.stderr)

    def test_build_pin_is_validated_against_official_manifest(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("PURPUR_SMOKE_BUILD", source)
        self.assertIn("any(.builds.all[]?; tostring == $build)", source)
        self.assertIn("SERVER_BUILD=\"$PURPUR_SMOKE_BUILD\"", source)
        self.assertIn("${MC_VERSION} runtime smoke: PASS", source)


if __name__ == "__main__":
    unittest.main()
