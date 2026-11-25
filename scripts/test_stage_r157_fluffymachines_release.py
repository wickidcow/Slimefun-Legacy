"""Regression coverage for byte-exact official FluffyMachines bundle staging."""
from __future__ import annotations

import copy
import hashlib
import json
import tempfile
import unittest
import zipfile
from io import BytesIO
from pathlib import Path

from scripts.stage_r157_fluffymachines_release import (
    SLUG,
    plugin_descriptor,
    stage,
    validate_lock,
)

REPO = "wickidcow/SF_FluffyMachines"
SOURCE = "7d6f363d55b96e5a043ea20627f68b208a48e448"
JAR = "SF_FluffyMachines26.2.24.jar"


def create_jar(extra: bytes = b"", major: int = 65) -> bytes:
    stream = BytesIO()
    with zipfile.ZipFile(stream, "w") as out:
        out.writestr("plugin.yml", "name: FluffyMachines\nversion: 26.2.24\napi-version: '1.21.11'\n")
        out.writestr("io/ncbpfluffybear/fluffymachines/FluffyMachines.class", bytes.fromhex("cafebabe0000") + major.to_bytes(2, "big") + extra)
    return stream.getvalue()


class FluffyMachinesReleaseStagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jars = self.root / "jars"
        self.jars.mkdir()
        self.metadata = self.root / "metadata"
        self.metadata.mkdir()
        self.official = self.root / "official.jar"
        self.raw = create_jar(b"official")
        self.source = create_jar(b"source")
        self.official.write_bytes(self.raw)
        self.jar = self.jars / JAR
        self.jar.write_bytes(self.source)
        self.row = {
            "repository": REPO, "slug": SLUG, "commit": SOURCE,
            "jar": JAR, "version": "26.2.24", "artifact_base": "FluffyMachines",
            "sha256": hashlib.sha256(self.source).hexdigest(),
            "distributable_api": "26.3-R0.1-SNAPSHOT",
            "secondary_platforms": ["Folia", "Leaf"],
        }
        self.meta = self.metadata / (SLUG + ".json")
        self.write_meta()
        self.matrix = {
            "bundle_revision": 157,
            "addons": [{"repository": REPO, "slug": SLUG, "source_commit": SOURCE}],
        }
        self.lock = {
            "schema": 1, "bundle_revision": 157, "repository": REPO,
            "tag": "v26.2.24", "source_commit": SOURCE, "asset": JAR,
            "bytes": len(self.raw), "sha256": hashlib.sha256(self.raw).hexdigest(),
            "plugin_name": "FluffyMachines", "plugin_version": "26.2.24",
            "api_version": "1.21.11", "java_class_major": 65,
        }

    def write_meta(self):
        self.meta.write_text(json.dumps(self.row), encoding="utf-8")

    def test_source_build_is_replaced_with_exact_official_bytes(self):
        staged = stage(self.jars, self.metadata, self.official, self.lock, self.matrix, False)
        self.assertEqual(self.raw, self.jar.read_bytes())
        self.assertEqual(hashlib.sha256(self.source).hexdigest(), staged["source_build_sha256"])
        self.assertEqual(self.lock["sha256"], staged["sha256"])
        self.assertTrue(staged["published_artifact"]["verified_download"])
        self.assertEqual(staged, stage(self.jars, self.metadata, self.official, self.lock, self.matrix, True))

    def test_source_manifest_must_pin_official_commit(self):
        wrong = copy.deepcopy(self.matrix)
        wrong["addons"][0]["source_commit"] = "a" * 40
        with self.assertRaisesRegex(ValueError, "source commit"):
            validate_lock(self.lock, wrong)

    def test_wrong_release_digest_is_rejected_without_writing(self):
        self.official.write_bytes(create_jar(b"tampered"))
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            stage(self.jars, self.metadata, self.official, self.lock, self.matrix, False)
        self.assertEqual(self.source, self.jar.read_bytes())

    def test_source_provenance_mismatch_is_rejected(self):
        self.row["commit"] = "f" * 40
        self.write_meta()
        with self.assertRaisesRegex(ValueError, "metadata identity"):
            stage(self.jars, self.metadata, self.official, self.lock, self.matrix, False)

    def test_corrupt_or_wrong_java_class_is_rejected(self):
        bad = create_jar(b"wrong-java", major=69)
        with self.assertRaisesRegex(ValueError, "Java 21"):
            plugin_descriptor(bad)


if __name__ == "__main__":
    unittest.main()
