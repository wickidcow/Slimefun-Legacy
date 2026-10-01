#!/usr/bin/env python3
"""Offline contract tests; generated JAR fixtures do not test Minecraft/WorldEdit gameplay."""
from __future__ import annotations

import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import prepare_worldedit_runtime as runtime


def version(identifier="buildA", channel="release", game="26.3", loader="bukkit", date="2026-09-21T12:00:00Z"):
    return {"id": identifier, "project_id": "officialProject", "version_number": "7.4.6-beta-02",
            "version_type": channel, "date_published": date,
            "game_versions": [game], "loaders": [loader], "files": []}


def jar_bytes(*, name="WorldEdit", main=runtime.MAIN_CLASS, major=65, extras=(), descriptor=None):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as jar:
        jar.writestr("plugin.yml", descriptor or f"name: {name}\nversion: '7.4.6-beta-02'\nmain: {main}\n")
        jar.writestr(runtime.MAIN_CLASS.replace(".", "/") + ".class",
                     b"\xca\xfe\xba\xbe\x00\x00" + major.to_bytes(2, "big"))
        for path, contents in extras:
            jar.writestr(path, contents)
    return output.getvalue()


def file_record(payload=None):
    payload = payload if payload is not None else jar_bytes()
    return {"primary": True, "filename": "worldedit-bukkit-7.4.6-beta-02.jar",
            "url": "https://cdn.modrinth.com/data/officialProject/versions/buildA/worldedit-bukkit.jar",
            "size": len(payload), "hashes": {"sha512": hashlib.sha512(payload).hexdigest()}}


class MetadataTests(unittest.TestCase):
    def test_official_project_identity(self):
        self.assertEqual("officialProject", runtime.validate_project({"id": "officialProject", "slug": "worldedit", "source_url": runtime.SOURCE_REPOSITORY}))

    def test_foreign_project_rejected(self):
        for project in ({}, None, {"id": "x", "slug": "worldedit", "source_url": "https://github.com/other/project"},
                        {"id": "../x", "slug": "worldedit", "source_url": runtime.SOURCE_REPOSITORY}):
            with self.subTest(project=project), self.assertRaises(ValueError):
                runtime.validate_project(project)

    def test_exact_minecraft_match(self):
        with self.assertRaises(ValueError):
            runtime.select_version([version(game="26.2")], "officialProject", "26.3")

    def test_fabric_jar_not_substituted(self):
        with self.assertRaises(ValueError):
            runtime.select_version([version(loader="fabric")], "officialProject", "26.3")

    def test_beta_requires_explicit_opt_in(self):
        with self.assertRaises(ValueError):
            runtime.select_version([version(channel="beta")], "officialProject", "26.3")
        self.assertEqual("buildA", runtime.select_version([version(channel="beta")], "officialProject", "26.3", True)["id"])

    def test_stable_preferred_even_over_newer_beta(self):
        releases = [version("stable", date="2026-09-01T00:00:00Z"), version("beta", "beta", date="2026-09-30T00:00:00Z")]
        self.assertEqual("stable", runtime.select_version(releases, "officialProject", "26.3", True)["id"])

    def test_latest_publication_is_deterministic(self):
        releases = [version("A", date="2026-09-22T00:00:00Z"), version("B", date="2026-09-21T00:00:00Z")]
        self.assertEqual("A", runtime.select_version(releases[::-1], "officialProject", "26.3")["id"])

    def test_explicit_version_replays_the_selected_build(self):
        releases = [version("A"), version("B", date="2026-09-22T00:00:00Z")]
        self.assertEqual("A", runtime.select_version(releases, "officialProject", "26.3", version_id="A")["id"])

    def test_absent_requested_version_does_not_fall_back(self):
        with self.assertRaises(ValueError):
            runtime.select_version([version()], "officialProject", "26.3", version_id="missing")

    def test_duplicate_ids_are_not_silently_chosen(self):
        with self.assertRaises(ValueError):
            runtime.select_version([version(), version()], "officialProject", "26.3")

    def test_versions_cannot_cross_projects(self):
        row = version(); row["project_id"] = "foreign"
        with self.assertRaises(ValueError):
            runtime.select_version([row], "officialProject", "26.3")

    def test_invalid_timestamp_or_channel_rejected(self):
        for key, value in (("date_published", "not-a-date"), ("date_published", "2026-09-21T00:00:00"), ("version_type", "unknown")):
            row = version(); row[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                runtime.select_version([row], "officialProject", "26.3")

    def test_malformed_lists_rejected(self):
        for key, value in (("loaders", "bukkit"), ("game_versions", "26.3"), ("loaders", [42]), ("game_versions", [None])):
            row = version(); row[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                runtime.select_version([row], "officialProject", "26.3")

    def test_primary_file_required_exactly_once(self):
        for files in ([], [dict(file_record(), primary=False)], [file_record(), file_record()]):
            with self.subTest(count=len(files)), self.assertRaises(ValueError):
                runtime.select_file(dict(version(), files=files))

    def test_wrong_artifact_type_rejected(self):
        for name in ("worldedit-fabric.jar", "worldedit-bukkit-sources.jar", "../worldedit-bukkit.jar", "worldedit-bukkit.zip"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                runtime.select_file(dict(version(), files=[dict(file_record(), filename=name)]))

    def test_primary_artifact_and_hash_accepted(self):
        file = file_record()
        self.assertIs(file, runtime.select_file(dict(version(), files=[file])))

    def test_digest_and_size_required(self):
        for field, value in (("hashes", {}), ("size", 0), ("size", True), ("size", runtime.MAX_DOWNLOAD + 1), ("hashes", {"sha512": "0" * 127})):
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                runtime.select_file(dict(version(), files=[dict(file_record(), **{field: value})]))

    def test_official_download_transport_required(self):
        for url in ("http://cdn.modrinth.com/f.jar", "https://cdn.modrinth.com.evil.test/f.jar", "https://other.test/f.jar",
                    "https://user:pass@cdn.modrinth.com/f.jar", "https://cdn.modrinth.com:444/f.jar", "file:///tmp/x"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                runtime.select_file(dict(version(), files=[dict(file_record(), url=url)]))

    def test_duplicate_json_fields_rejected(self):
        with self.assertRaises(ValueError):
            runtime.object_json(b'{"id":"a","id":"b"}', "test")

    def test_valid_json_preserves_values(self):
        self.assertEqual({"id": "a", "value": 12}, runtime.object_json(b'{"id":"a","value":12}', "test"))


class JarTests(unittest.TestCase):
    def test_runtime_identity_and_hash(self):
        payload = jar_bytes()
        result = runtime.validate_jar(payload, file_record(payload), 21)
        self.assertEqual("WorldEdit", result["name"])
        self.assertEqual(65, result["max_class_major"])
        self.assertEqual(hashlib.sha256(payload).hexdigest(), result["sha256"])

    def test_changed_payload_rejected(self):
        payload = jar_bytes()
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload + b"x", file_record(payload), 21)

    def test_wrong_digest_rejected(self):
        payload = jar_bytes(); record = file_record(payload); record["hashes"]["sha512"] = "0" * 128
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, record, 21)

    def test_wrong_plugin_name_rejected(self):
        payload = jar_bytes(name="AnotherPlugin")
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_wrong_plugin_main_rejected(self):
        payload = jar_bytes(main="another.Main")
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_floor_cannot_use_java25_dependency(self):
        payload = jar_bytes(major=69)
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)
        self.assertEqual(69, runtime.validate_jar(payload, file_record(payload), 25)["max_class_major"])

    def test_future_overlay_ignored_at_floor_but_checked_when_applicable(self):
        overlay = "META-INF/versions/25/test/Extra.class"
        payload = jar_bytes(extras=[(overlay, b"\xca\xfe\xba\xbe\x00\x00\x00\x45")])
        self.assertEqual(1, runtime.validate_jar(payload, file_record(payload), 21)["applicable_classes"])
        self.assertEqual(2, runtime.validate_jar(payload, file_record(payload), 25)["applicable_classes"])

    def test_invalid_low_class_version_refused(self):
        payload = jar_bytes(major=0)
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_invalid_class_header_refused(self):
        payload = jar_bytes(extras=[("other/Broken.class", b"not-a-class")])
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_preview_class_refused(self):
        payload = jar_bytes(extras=[("other/Preview.class", b"\xca\xfe\xba\xbe\xff\xff\x00\x41")])
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_path_traversal_refused(self):
        payload = jar_bytes(extras=[("../server.properties", b"bad")])
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_plugin_yaml_missing_refused(self):
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, "w") as jar: jar.writestr("README", "not a plugin")
        data = payload.getvalue()
        with self.assertRaises(ValueError):
            runtime.validate_jar(data, file_record(data), 21)

    def test_duplicate_descriptor_field_refused(self):
        payload = jar_bytes(descriptor=f"name: WorldEdit\nname: Another\nmain: {runtime.MAIN_CLASS}\nversion: 1\n")
        with self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_compressed_expansion_bound(self):
        payload = jar_bytes()
        with patch.object(runtime, "MAX_EXPANDED", 2), self.assertRaises(ValueError):
            runtime.validate_jar(payload, file_record(payload), 21)

    def test_non_jar_is_refused(self):
        payload = b"server error"
        with self.assertRaises(zipfile.BadZipFile):
            runtime.validate_jar(payload, file_record(payload), 21)


class StagingTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.addCleanup(self.directory.cleanup)
        self.payload = jar_bytes()
        self.project = {"id": "officialProject", "slug": "worldedit", "source_url": runtime.SOURCE_REPOSITORY}
        self.row = dict(version(), files=[file_record(self.payload)])

    def fetch(self, url, limit, *, allowed_host):
        if url.endswith("/project/worldedit"): return json.dumps(self.project).encode()
        if "/version?" in url: return json.dumps([self.row]).encode()
        return self.payload

    def test_valid_dependency_is_staged_with_provenance(self):
        with patch.object(runtime, "request_bytes", self.fetch):
            report = runtime.stage(self.root, "26.3")
        self.assertEqual(self.payload, (self.root / "WorldEdit-runtime.jar").read_bytes())
        self.assertEqual(report, json.loads((self.root / "worldedit-runtime.json").read_text()))
        self.assertIn("runtime activation must still be tested", report["scope"])

    def test_existing_runtime_never_replaced(self):
        file = self.root / "WorldEdit-runtime.jar"; file.write_bytes(b"old")
        with patch.object(runtime, "request_bytes") as request, self.assertRaises(ValueError):
            runtime.stage(self.root, "26.3")
        request.assert_not_called(); self.assertEqual(b"old", file.read_bytes())

    def test_bad_download_leaves_no_runtime_or_clean_report(self):
        self.row["files"][0]["hashes"]["sha512"] = "0" * 128
        with patch.object(runtime, "request_bytes", self.fetch), self.assertRaises(ValueError):
            runtime.stage(self.root, "26.3")
        self.assertEqual([], list(self.root.iterdir()))

    def test_network_failure_is_not_missing_dependency_skip(self):
        with patch.object(runtime, "request_bytes", side_effect=OSError("offline")), self.assertRaises(OSError):
            runtime.stage(self.root, "26.3")
        self.assertEqual([], list(self.root.iterdir()))

    def test_unknown_lane_refused_before_network(self):
        with patch.object(runtime, "request_bytes") as request, self.assertRaises(ValueError):
            runtime.stage(self.root, "1.20.4")
        request.assert_not_called()

    def test_stale_metadata_not_reused(self):
        (self.root / "worldedit-runtime.json").write_text("old report")
        with patch.object(runtime, "request_bytes") as request, self.assertRaises(ValueError):
            runtime.stage(self.root, "26.3")
        request.assert_not_called()
        self.assertEqual("old report", (self.root / "worldedit-runtime.json").read_text())

    def test_pinned_version_still_requires_declared_game_support(self):
        self.row["game_versions"] = ["26.2"]
        with patch.object(runtime, "request_bytes", self.fetch), self.assertRaises(ValueError):
            runtime.stage(self.root, "26.3", version_id="buildA")
        self.assertEqual([], list(self.root.iterdir()))

    def test_output_write_failure_removes_partially_staged_files(self):
        original = Path.write_text
        def fail_evidence(path, *args, **kwargs):
            if path.name == "worldedit-runtime.json": raise OSError("injected disk error")
            return original(path, *args, **kwargs)
        with patch.object(runtime, "request_bytes", self.fetch), patch.object(Path, "write_text", fail_evidence), self.assertRaises(OSError):
            runtime.stage(self.root, "26.3")
        self.assertEqual([], list(self.root.iterdir()))

    def test_prerelease_channel_is_recorded_not_relabelled_stable(self):
        self.row["version_type"] = "beta"
        with patch.object(runtime, "request_bytes", self.fetch):
            report = runtime.stage(self.root, "26.3", True)
        self.assertEqual("beta", report["channel"])
        self.assertTrue(report["prerelease_allowed"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
