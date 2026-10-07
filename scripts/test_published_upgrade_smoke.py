#!/usr/bin/env python3
"""Offline regression checks for the published-upgrade driver (not server gameplay)."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import published_upgrade_smoke as subject


def bundle_bytes(change=None):
    rows = []
    jars = {}
    for i in range(45):
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as jar:
            jar.writestr("plugin.yml", f"name: Fixture{i}\nversion: '1.0.0'\n")
        name = f"SF_Fixture{i}1.0.0.jar"
        jars[name] = output.getvalue()
        rows.append({"jar": name, "version": "1.0.0", "sha256": hashlib.sha256(jars[name]).hexdigest()})
    manifest = {"bundle_revision": 125, "core_source_commit": subject.SOURCE, "addons": rows}
    if change:
        change(manifest, jars)
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("SF_ADDON_MANIFEST.json", json.dumps(manifest))
        for name, data in jars.items():
            archive.writestr(name, data)
    return output.getvalue()


def build(n, channel):
    return {"id": n, "channel": channel,
            "downloads": {"server:default": {"checksums": {"sha256": "a" * 64}}}}


class DriverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def inspect(self, modify=None):
        file = self.root / "bundle.zip"
        file.write_bytes(bundle_bytes(modify))
        return subject.inspect_bundle(file, subject.SOURCE)

    def test_exact_synthetic_bundle_is_accepted(self):
        manifest, jars, plugins = self.inspect()
        self.assertEqual(45, len(jars))
        self.assertEqual(45, len(plugins.splitlines()))

    def test_wrong_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "source"):
            self.inspect(lambda m, j: m.update(core_source_commit="b" * 40))

    def test_wrong_revision_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "revision"):
            self.inspect(lambda m, j: m.update(bundle_revision=124))

    def test_missing_addon_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "45 addons"):
            self.inspect(lambda m, j: m["addons"].pop())

    def test_missing_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "JAR set"):
            self.inspect(lambda m, j: j.pop(next(iter(j))))

    def test_extra_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "JAR set"):
            self.inspect(lambda m, j: j.update({"unexpected.jar": b"not a plugin"}))

    def test_corrupted_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "hash"):
            self.inspect(lambda m, j: j.update({next(iter(j)): b"bad bytes"}))

    def test_changed_manifest_version_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "version"):
            self.inspect(lambda m, j: m["addons"][0].update(version="1.0.1"))

    def test_duplicate_manifest_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate manifest"):
            self.inspect(lambda m, j: m["addons"].__setitem__(1, m["addons"][0]))

    def test_path_traversal_is_rejected(self):
        def change(m, j):
            old = m["addons"][0]["jar"]
            m["addons"][0]["jar"] = "../" + old
            j["../" + old] = j.pop(old)
        with self.assertRaisesRegex(ValueError, "Unsafe JAR"):
            self.inspect(change)

    def test_duplicate_plugin_name_is_rejected(self):
        def change(m, j):
            row = m["addons"][1]
            j[row["jar"]] = j[m["addons"][0]["jar"]]
            row["sha256"] = hashlib.sha256(j[row["jar"]]).hexdigest()
        with self.assertRaisesRegex(ValueError, "Duplicate plugin"):
            self.inspect(change)

    def test_stable_selection_is_not_list_order_dependent(self):
        result = subject.paper_build("26.2", [build(2, "STABLE"), build(9, "BETA"), build(5, "STABLE")])
        self.assertEqual(5, result["id"])

    def test_beta_is_not_silently_used_on_supported_floor(self):
        with self.assertRaisesRegex(ValueError, "No permitted"):
            subject.paper_build("1.21.11", [build(3, "BETA")])

    def test_explicit_26_3_beta_lane_is_supported(self):
        self.assertEqual("BETA", subject.paper_build("26.3", [build(3, "BETA")])["channel"])

    def test_stable_is_preferred_over_beta_on_26_3(self):
        self.assertEqual("STABLE", subject.paper_build("26.3", [build(3, "BETA"), build(2, "STABLE")])["channel"])

    def test_alpha_is_not_accepted(self):
        with self.assertRaisesRegex(ValueError, "No permitted"):
            subject.paper_build("26.3", [build(3, "ALPHA")])

    def test_server_checksum_is_required(self):
        value = build(3, "STABLE")
        value["downloads"]["server:default"]["checksums"] = {}
        with self.assertRaisesRegex(ValueError, "checksum"):
            subject.paper_build("26.2", [value])

    def test_failure_report_is_not_a_pass(self):
        path = self.root / "report.txt"
        path.write_text("status=FAIL\nphase=baseline\n")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            subject.parse_result(path)

    def test_empty_report_is_not_a_pass(self):
        path = self.root / "report.txt"
        path.write_text("")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            subject.parse_result(path)

    def test_download_checks_hash_before_persisting(self):
        path = self.root / "asset.jar"
        with patch.object(subject.urllib.request, "urlopen", return_value=io.BytesIO(b"bad")):
            with self.assertRaisesRegex(ValueError, "hash"):
                subject.download("https://example.invalid/artifact", path, "a" * 64, 3)
        self.assertFalse(path.exists())

    def test_download_rejects_oversized_payload(self):
        path = self.root / "asset.jar"
        with patch.object(subject.urllib.request, "urlopen", return_value=io.BytesIO(b"too big")):
            with self.assertRaisesRegex(ValueError, "size"):
                subject.download("https://example.invalid/artifact", path, "a" * 64, 3)
        self.assertFalse(path.exists())

    def test_driver_requires_disposable_ci_context(self):
        with patch.dict(subject.os.environ, {"GITHUB_ACTIONS": "false"}):
            with patch("sys.argv", ["driver", "--minecraft", "1.21.11", "--work-dir", str(self.root / "server")]):
                with self.assertRaisesRegex(ValueError, "GitHub Actions"):
                    subject.main()
        self.assertFalse((self.root / "server").exists())


if __name__ == "__main__":
    unittest.main()
