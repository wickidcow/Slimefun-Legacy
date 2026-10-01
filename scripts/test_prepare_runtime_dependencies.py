#!/usr/bin/env python3
"""No-network contract tests for required runtime dependency provenance."""
import copy
import hashlib
import io
import tempfile
import unittest
import zipfile
from pathlib import Path
from prepare_runtime_dependencies import prepare


def archive(name="WorldEdit"):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as jar:
        jar.writestr("plugin.yml", "name: " + name + "\nversion: test\n")
    return output.getvalue()


class RuntimeDependenciesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.data = archive()
        self.lock = {"schema": 1, "dependencies": [{
            "project": "WorldEdit", "plugin_name": "WorldEdit", "filename": "worldedit-test.jar",
            "version": "test", "version_id": "Test1234", "minecraft_versions": ["1.21.11", "26.2", "26.3"],
            "url": "https://cdn.modrinth.com/data/project/versions/Test1234/worldedit-test.jar",
            "sha512": hashlib.sha512(self.data).hexdigest()
        }]}

    def run_prepare(self, lock=None, data=None, version="26.3"):
        return prepare(self.lock if lock is None else lock, version, self.root/"plugins", self.root/"report.json",
                       lambda _: self.data if data is None else data)

    def rejected(self, lock=None, data=None, version="26.3"):
        with self.assertRaises((ValueError, KeyError, zipfile.BadZipFile)):
            self.run_prepare(lock, data, version)
        self.assertFalse((self.root/"report.json").exists())

    def test_exact_download_is_recorded(self):
        report = self.run_prepare()
        self.assertEqual(self.data, (self.root/"plugins/worldedit-test.jar").read_bytes())
        self.assertEqual(hashlib.sha256(self.data).hexdigest(), report["dependencies"][0]["sha256"])

    def test_floor_uses_same_exact_artifact(self):
        self.run_prepare(version="1.21.11")
        self.assertEqual(self.data, (self.root/"plugins/worldedit-test.jar").read_bytes())

    def test_idempotent_install(self):
        self.assertEqual(self.run_prepare(), self.run_prepare())

    def test_checksum_failure_is_not_a_fallback(self):
        self.rejected(data=self.data+b"changed")
        self.assertFalse((self.root/"plugins").exists())

    def test_unsupported_server_refuses(self):
        self.rejected(version="1.20.6")

    def test_path_traversal_refuses(self):
        self.lock["dependencies"][0]["filename"] = "../worldedit.jar"
        self.rejected()

    def test_duplicate_identity_refuses(self):
        self.lock["dependencies"].append(copy.deepcopy(self.lock["dependencies"][0]))
        self.rejected()
        self.assertFalse((self.root/"plugins").exists())

    def test_wrong_plugin_identity_refuses(self):
        data = archive("NotWorldEdit")
        self.lock["dependencies"][0]["sha512"] = hashlib.sha512(data).hexdigest()
        self.rejected(data=data)

    def test_client_or_nonjar_refuses(self):
        data = b"not-a-plugin"
        self.lock["dependencies"][0]["sha512"] = hashlib.sha512(data).hexdigest()
        self.rejected(data=data)

    def test_unpinned_or_http_url_refuses(self):
        for url in ("http://cdn.modrinth.com/test.jar", "https://cdn.modrinth.com/latest.jar",
                    "https://other.example/versions/Test1234/worldedit-test.jar"):
            with self.subTest(url=url):
                lock = copy.deepcopy(self.lock)
                lock["dependencies"][0]["url"] = url
                self.rejected(lock)

    def test_missing_dependency_list_refuses(self):
        self.rejected({"schema": 1, "dependencies": []})

    def test_existing_different_jar_is_not_overwritten(self):
        path = self.root/"plugins/worldedit-test.jar"
        path.parent.mkdir()
        path.write_bytes(b"existing")
        self.rejected()
        self.assertEqual(b"existing", path.read_bytes())

    def test_missing_hash_refuses(self):
        del self.lock["dependencies"][0]["sha512"]
        self.rejected()


if __name__ == "__main__":
    unittest.main()
