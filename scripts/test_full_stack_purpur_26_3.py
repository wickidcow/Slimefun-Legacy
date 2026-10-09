#!/usr/bin/env python3
"""Offline regression checks for pinned Purpur 26.3 with the canonical addon bundle.

The tests do not download server builds, mutate worlds, or claim runtime success.
Actual compatibility requires the separate GitHub Actions double-boot lane.
"""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
HARNESS = ROOT / "scripts" / "full_stack_runtime_smoke.sh"
WORKFLOW = ROOT / ".github" / "workflows" / "paper-26.3-full-stack.yml"


def probe(software: str, version: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update({
        "SERVER_SOFTWARE": software,
        "SERVER_MINECRAFT_VERSION": version,
        "PURPUR_SMOKE_BUILD": "2646",
    })
    # Existing/unsupported lanes exit before attempting any downloads because
    # the supplied JAR and addon bundle do not exist.
    return subprocess.run(
        [
            "bash", str(HARNESS),
            str(ROOT / "missing-purpur-full-stack.jar"),
            str(ROOT / "missing-purpur-addon-bundle.zip"),
        ],
        env=env,
        capture_output=True,
        text=True,
        check=False,
        timeout=10,
    )


class PurpurFullStackContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = HARNESS.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_bash_syntax(self) -> None:
        result = subprocess.run(
            ["bash", "-n", str(HARNESS)], capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_all_existing_paper_versions_remain_accepted(self) -> None:
        for version in ("1.21.11", "26.2", "26.3"):
            with self.subTest(version=version):
                result = probe("paper", version)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("Unsupported full-stack", result.stderr)

    def test_purpur_26_3_is_the_only_new_software_version_pair(self) -> None:
        result = probe("purpur", "26.3")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("Unsupported full-stack", result.stderr)
        for software, version in (
            ("purpur", "26.2"), ("purpur", "26.4"),
            ("folia", "26.3"), ("leaf", "26.3"), ("paper", "26.4"),
        ):
            with self.subTest(software=software, version=version):
                invalid = probe(software, version)
                self.assertNotEqual(0, invalid.returncode)
                self.assertIn("Unsupported full-stack", invalid.stderr)

    def test_purpur_build_is_explicitly_pinned_and_api_verified(self) -> None:
        self.assertIn('PURPUR_SMOKE_BUILD: \'2646\'', self.workflow)
        self.assertIn("PURPUR_SMOKE_BUILD:?", self.source)
        self.assertIn("any(.builds.all[]?; tostring == $build)", self.source)
        self.assertIn('https://api.purpurmc.org/v2/purpur/26.3', self.source)
        self.assertIn("SERVER_CHANNEL=\"pinned-experimental\"", self.source)
        self.assertIn('Software: ${SOFTWARE_NAME}', self.source)

    def test_reuses_exact_addon_artifacts_without_release_fallback(self) -> None:
        self.assertIn("needs: [build-core, fetch-bundle]", self.workflow)
        self.assertIn("name: full-stack-core", self.workflow)
        self.assertIn("name: full-stack-addon-bundle", self.workflow)
        self.assertIn("bundle/SF_Addons_1.21.11-26.3.zip", self.workflow)
        self.assertIn('SF_ADDON_MANIFEST.json', self.source)
        self.assertIn('expected-addons.txt', self.source)
        self.assertIn('dependency-gated-addons.txt', self.source)
        self.assertIn('run_cycle first false\nrun_cycle second true', self.source)

    def test_retains_existing_paper_matrix_and_purpur_evidence(self) -> None:
        self.assertIn("minecraft: ['1.21.11', '26.2', '26.3']", self.workflow)
        self.assertIn("Full stack experimental Purpur 26.3 build 2646", self.workflow)
        self.assertIn("name: full-stack-runtime-purpur-26.3", self.workflow)
        self.assertIn('verify_runtime_configuration.py', self.source)
        self.assertIn('--verify-log "$normalized"', self.source)
        self.assertIn("Clean shutdown persistence: observed on second boot", self.source)
        self.assertIn('bundle/bundle-source.txt', self.workflow)


if __name__ == "__main__":
    unittest.main()
