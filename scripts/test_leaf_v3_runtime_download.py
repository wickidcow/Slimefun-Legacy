#!/usr/bin/env python3
"""Fail-closed Leaf Fill v3 runtime metadata selection regression tests."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/paper_family_26_2_runtime_smoke.sh"
BEGIN = 'elif [[ "$SOFTWARE" == "leaf" ]]; then'
END = '\nelse\n    BUILDS_URL="https://fill.papermc.io/v3/projects/folia/versions/' + '$' + '{MC_VERSION}/builds"'


def run(builds: object) -> subprocess.CompletedProcess[str]:
    source = SCRIPT.read_text(encoding="utf-8")
    if source.count(BEGIN) != 1 or source.count(END) != 1:
        raise AssertionError("Expected one Leaf v3 switch block")
    excerpt = source[source.index(BEGIN):source.index(END)]
    excerpt = excerpt.replace("elif [[", "if [[", 1) + "\nfi\n"
    shell = (
        'set -euo pipefail\nMC_VERSION="26.2"\nSOFTWARE="leaf"\n'
        'runtime_download() { printf "%s" "$LEAF_TEST_BUILDS"; }\n'
        + excerpt
        + 'printf "%s\\t%s\\t%s\\t%s\\n" '
          '"$SERVER_BUILD" "$SERVER_CHANNEL" "$SERVER_URL" "$LEAF_SHA256"\n'
    )
    return subprocess.run(
        ["bash", "-c", shell], text=True, capture_output=True,
        env={"PATH": "/usr/bin:/bin", "LEAF_TEST_BUILDS": json.dumps(builds)},
        timeout=15, check=False)


def build(number: int, *, url: str | None = None,
          sha: str | None = None, name: str | None = None) -> dict:
    name = name or f"leaf-26.2-{number}.jar"
    return {"id": number, "channel": "BETA", "time": "2026-11-23T00:00:00Z",
            "commits": [], "downloads": {"server:default": {
                "name": name, "size": 124,
                "url": url or f"https://dl-data.leafmc.one/leaf/26.2/{number}/{name}",
                "checksums": {"sha256": sha or hashlib.sha256(str(number).encode()).hexdigest()}}}}


class LeafV3RuntimeSelection(unittest.TestCase):
    def test_exact_latest_server_selected(self) -> None:
        older, latest = build(134), build(135)
        result = run([latest, older])
        self.assertEqual(result.returncode, 0, result.stderr)
        selected, channel, url, digest = result.stdout.strip().split("\t")
        self.assertEqual(selected, "135")
        self.assertEqual(channel, "BETA")
        self.assertEqual(url, latest["downloads"]["server:default"]["url"])
        self.assertEqual(digest, latest["downloads"]["server:default"]["checksums"]["sha256"])

    def test_bad_build_lists_refused(self) -> None:
        for candidate in ([], {"error": "unavailable"}, [{"channel": "BETA"}]):
            with self.subTest(candidate=candidate):
                self.assertNotEqual(run(candidate).returncode, 0)

    def test_unsafe_downloads_refused(self) -> None:
        for candidate in (
                build(135, sha="not-sha256"),
                build(135, url="https://attacker.example/leaf-26.2-135.jar"),
                build(135, url="http://dl-data.leafmc.one/leaf/26.2/135/leaf-26.2-135.jar"),
                build(135, url="https://dl-data.leafmc.one/leaf/26.2/134/leaf-26.2-135.jar"),
                build(135, name="leaf.jar/../evil.jar")):
            with self.subTest(candidate=candidate):
                self.assertNotEqual(run([candidate]).returncode, 0)

    def test_no_other_download_distribution(self) -> None:
        candidate = build(135)
        candidate["downloads"] = {"server:experimental": candidate["downloads"]["server:default"]}
        self.assertNotEqual(run([candidate]).returncode, 0)

    def test_shell_and_digest_policy(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn('sha256sum --check --strict', source)
        self.assertIn('LEAF_SHA256', source)
        self.assertNotIn('LEAF_VERSION_URL="https://api.leafmc.one/v2', source)
        syntax = subprocess.run(["bash", "-n", str(SCRIPT)], capture_output=True, text=True)
        self.assertEqual(syntax.returncode, 0, syntax.stderr)


if __name__ == "__main__":
    unittest.main()
