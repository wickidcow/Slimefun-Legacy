"""Fail-closed current published pair selector; historical test input stays immutable."""
from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.runtime.paired_addon_storage import run_probe as paired

ROOT = Path(__file__).resolve().parents[1]
HISTORICAL = paired.HERE / "fixture-lock.json"
RELEASE = paired.HERE / "release-lock.json"
DISTRIBUTION = ROOT / "scripts/published_storage_pair_lock.json"


class CurrentPublishedStoragePairTests(unittest.TestCase):
    def test_historical_default_is_unchanged_and_not_distribution(self):
        historical = json.loads(HISTORICAL.read_text(encoding="utf-8"))
        self.assertEqual("integration-test-fixture", historical["purpose"])
        self.assertIs(historical["distribution_selection"], False)
        with patch.object(paired, "PAIR_MODE", "historical"), patch.object(paired, "LOCK_PATH", HISTORICAL):
            self.assertEqual(historical, paired.load_lock())

    def test_opt_in_release_pair_exactly_matches_published_distribution_lock(self):
        distribution = json.loads(DISTRIBUTION.read_text(encoding="utf-8"))
        with patch.object(paired, "PAIR_MODE", "release"), patch.object(paired, "LOCK_PATH", RELEASE):
            selected = paired.load_lock()
        self.assertEqual("exact-published-release-storage-pair", selected["purpose"])
        self.assertIs(selected["distribution_selection"], True)
        self.assertEqual(distribution["addons"], selected["addons"])
        self.assertEqual({"networks", "ie2"}, set(selected["addons"]))

    def test_original_fixture_sources_and_purpur_platforms_are_preserved(self):
        historical = json.loads(HISTORICAL.read_text(encoding="utf-8"))
        release = json.loads(RELEASE.read_text(encoding="utf-8"))
        for key in ("fixture_sources", "jeg_compile_dependency", "platforms"):
            with self.subTest(key=key):
                self.assertEqual(historical[key], release[key])
        self.assertNotEqual(historical["addons"]["networks"]["source"],
                            release["addons"]["networks"]["source"])
        self.assertNotEqual(historical["addons"]["ie2"]["source"],
                            release["addons"]["ie2"]["source"])

    def test_invalid_mode_rejected(self):
        with patch.object(paired, "PAIR_MODE", "latest"), patch.object(paired, "LOCK_PATH", HISTORICAL):
            with self.assertRaisesRegex(RuntimeError, "Unknown paired storage source mode"):
                paired.load_lock()

    def test_release_mode_refuses_historical_lock(self):
        with patch.object(paired, "PAIR_MODE", "release"), patch.object(paired, "LOCK_PATH", HISTORICAL):
            with self.assertRaisesRegex(RuntimeError, "purpose or distribution mode"):
                paired.load_lock()

    def test_historical_mode_refuses_release_lock(self):
        with patch.object(paired, "PAIR_MODE", "historical"), patch.object(paired, "LOCK_PATH", RELEASE):
            with self.assertRaisesRegex(RuntimeError, "purpose or distribution mode"):
                paired.load_lock()

    def test_no_source_version_artifact_or_tree_substitution(self):
        original = json.loads(RELEASE.read_text(encoding="utf-8"))
        for key in ("source", "tree", "version", "jar"):
            changed = deepcopy(original)
            changed["addons"]["networks"][key] = "0" * 40 if key in {"source", "tree"} else "1.0.62"
            self._reject(changed)
        for key in ("bytes", "sha256", "url"):
            changed = deepcopy(original)
            changed["addons"]["ie2"]["published_artifact"][key] = (
                1 if key == "bytes" else "0" * 64 if key == "sha256" else "https://invalid.example/jar"
            )
            self._reject(changed)

    def test_distribution_lock_pointer_and_flags_cannot_change(self):
        original = json.loads(RELEASE.read_text(encoding="utf-8"))
        for key, value in (("distribution_lock_path", "some/other/path"),
                           ("distribution_selection", False),
                           ("purpose", "integration-test-fixture")):
            changed = deepcopy(original)
            changed[key] = value
            self._reject(changed)

    def _reject(self, candidate):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release-lock.json"
            path.write_text(json.dumps(candidate), encoding="utf-8")
            with patch.object(paired, "PAIR_MODE", "release"), patch.object(paired, "LOCK_PATH", path):
                with self.assertRaisesRegex(RuntimeError, "mismatch|differs"):
                    paired.load_lock()


if __name__ == "__main__":
    unittest.main()
