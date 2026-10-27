#!/usr/bin/env python3

from __future__ import annotations

import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

import audit_folia_addon_admission as audit
import yaml


def plugin_jar(descriptor_name: str, folia_line: str | None) -> bytes:
    buffer = io.BytesIO()
    descriptor = "name: Fixture\nversion: 1.0\n"
    if folia_line is not None:
        descriptor += folia_line + "\n"
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(descriptor_name, descriptor)
    return buffer.getvalue()


class TestFoliaAddonAdmissionAudit(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bundle = self.root / "bundle.zip"

    def write_bundle(self, rows, blobs, *, revision=137, duplicate_entry=False, source="a" * 40):
        manifest = {
            "bundle_revision": revision,
            "core_source_commit": source,
            "addons": [dict(row, sha256=row.get("sha256", audit.sha256(blobs[row["jar"]]))) for row in rows],
        }
        with zipfile.ZipFile(self.bundle, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("SF_ADDON_MANIFEST.json", json.dumps(manifest))
            for name, blob in blobs.items():
                zf.writestr(name, blob)
            if duplicate_entry:
                first = next(iter(blobs.items()))
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    zf.writestr(first[0], first[1])

    def write_single(self, descriptor=None, *, row=None, extra_entries=None):
        row = row or {"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"}
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w", zipfile.ZIP_DEFLATED) as jar:
            jar.writestr("plugin.yml", descriptor or "name: Fixture\nfolia-supported: true\n")
            for name, content in extra_entries or []:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    jar.writestr(name, content)
        self.write_bundle([row], {row["jar"]: raw.getvalue()})

    def test_cli_supports_variable_bundle_rosters(self):
        self.write_single()
        args = [sys.executable, str(Path(audit.__file__)), str(self.bundle)]
        okay = subprocess.run(args, text=True, capture_output=True, timeout=15)
        self.assertEqual(0, okay.returncode, okay.stderr)
        self.assertEqual(1, json.loads(okay.stdout)["addon_count"])
        failed = subprocess.run(args + ["--expect-addons", "45"], text=True, capture_output=True, timeout=15)
        self.assertEqual(1, failed.returncode)
        self.assertIn("Expected 45 addons, found 1", failed.stderr)

    def test_classifies_true_false_and_undeclared(self):
        rows = [
            {"repository": "example/true", "commit": "1" * 40, "jar": "True.jar"},
            {"repository": "example/false", "commit": "2" * 40, "jar": "False.jar"},
            {"repository": "example/missing", "commit": "3" * 40, "jar": "Missing.jar"},
        ]
        blobs = {
            "True.jar": plugin_jar("plugin.yml", "folia-supported: true"),
            "False.jar": plugin_jar("plugin.yml", "folia-supported: FALSE"),
            "Missing.jar": plugin_jar("paper-plugin.yml", None),
        }
        self.write_bundle(rows, blobs)
        report = audit.audit(self.bundle)
        self.assertEqual(report["counts"], {"declared_true": 1, "declared_false": 1, "undeclared": 1})
        by_repo = {row["repository"]: row for row in report["addons"]}
        self.assertEqual(by_repo["example/true"]["admission"], "declared-true")
        self.assertEqual(by_repo["example/false"]["admission"], "declared-false")
        self.assertEqual(by_repo["example/missing"]["admission"], "undeclared")
        self.assertEqual(report["scope"], "descriptor-admission-only")

    def test_nested_keys_and_description_text_do_not_opt_in(self):
        for descriptor in (
            "name: Fixture\ncommands:\n  test:\n    folia-supported: true\n",
            "name: Fixture\ndescription: |\n  folia-supported: true\n",
        ):
            with self.subTest(descriptor=descriptor):
                self.write_single(descriptor)
                self.assertEqual(audit.audit(self.bundle)["addons"][0]["admission"], "undeclared")

    def test_top_level_false_overrides_unrelated_nested_true(self):
        self.write_single("name: Fixture\ncommands:\n  test:\n    folia-supported: true\nfolia-supported: false\n")
        self.assertEqual(audit.audit(self.bundle)["addons"][0]["admission"], "declared-false")

    def test_comments_quoted_keys_and_uppercase_booleans_are_parsed(self):
        self.write_single('name: Fixture\n"folia-supported": TRUE # explicit opt-in\n')
        self.assertEqual(audit.audit(self.bundle)["addons"][0]["admission"], "declared-true")

    def test_nonliteral_boolean_values_fail_instead_of_claiming_admission(self):
        for value in ('"true"', "'false'", "yes", "on", "null", "1", "[true]", "{value: true}"):
            with self.subTest(value=value):
                self.write_single(f"name: Fixture\nfolia-supported: {value}\n")
                with self.assertRaisesRegex(ValueError, "must be the Boolean true or false"):
                    audit.audit(self.bundle)

    def test_duplicate_top_level_keys_are_refused(self):
        for descriptor in (
            "folia-supported: true\nfolia-supported: false\n",
            "folia-supported: true\n'folia-supported': true\n",
            "name: First\nname: Second\nfolia-supported: true\n",
        ):
            with self.subTest(descriptor=descriptor):
                self.write_single(descriptor)
                with self.assertRaisesRegex(ValueError, "Duplicate descriptor key"):
                    audit.audit(self.bundle)

    def test_yaml_merge_is_refused_as_ambiguous_descriptor_metadata(self):
        self.write_single("defaults: &defaults {folia-supported: true}\n<<: *defaults\n")
        with self.assertRaisesRegex(ValueError, "YAML merge keys"):
            audit.audit(self.bundle)

    def test_invalid_utf8_yaml_or_nonmapping_descriptors_are_refused(self):
        for descriptor in (b"name: Fixture\n\xff", "name: [broken\n", "- folia-supported: true\n",
                           "folia-supported: true\n---\nfolia-supported: false\n"):
            with self.subTest(descriptor=descriptor):
                self.write_single(descriptor)
                with self.assertRaises(ValueError):
                    audit.audit(self.bundle)

    def test_paper_descriptor_takes_precedence_over_bukkit_fallback(self):
        self.write_single(extra_entries=[("paper-plugin.yml", "name: Fixture\nfolia-supported: false\n")])
        report = audit.audit(self.bundle)
        self.assertEqual(report["addons"][0]["descriptor"], "paper-plugin.yml")
        self.assertEqual(report["addons"][0]["admission"], "declared-false")

    def test_duplicate_descriptor_zip_members_are_refused(self):
        self.write_single(extra_entries=[("plugin.yml", "name: Fixture\nfolia-supported: false\n")])
        with self.assertRaisesRegex(ValueError, "Addon JAR contains duplicate ZIP entries"):
            audit.audit(self.bundle)

    def test_duplicate_nondescriptor_jar_members_are_also_refused(self):
        self.write_single(extra_entries=[("Fixture.class", b"one"), ("Fixture.class", b"two")])
        with self.assertRaisesRegex(ValueError, "Addon JAR contains duplicate ZIP entries"):
            audit.audit(self.bundle)

    def test_manifest_hash_must_match_the_actual_jar_bytes(self):
        self.write_single(row={"repository": "example/a", "commit": "1" * 40, "jar": "A.jar", "sha256": "0" * 64})
        with self.assertRaisesRegex(ValueError, "Addon checksum mismatch"):
            audit.audit(self.bundle)

    def test_invalid_or_missing_manifest_digest_is_refused(self):
        for digest in (None, "", "a" * 63, "g" * 64, 123):
            with self.subTest(digest=digest):
                self.write_single(row={"repository": "example/a", "commit": "1" * 40,
                                       "jar": "A.jar", "sha256": digest})
                with self.assertRaisesRegex(ValueError, "Missing or invalid addon SHA-256"):
                    audit.audit(self.bundle)

    def test_exact_core_source_is_checked_and_bytes_are_not_changed(self):
        self.write_single()
        before = self.bundle.read_bytes()
        report = audit.audit(self.bundle, "a" * 40)
        self.assertEqual(report["expected_core_source_commit"], "a" * 40)
        self.assertTrue(report["manifest_hashes_verified"])
        self.assertEqual(before, self.bundle.read_bytes())
        with self.assertRaisesRegex(ValueError, "different core checkout"):
            audit.audit(self.bundle, "b" * 40)

    def test_missing_or_invalid_core_source_is_refused(self):
        for source in (None, "", "a" * 39, "g" * 40):
            with self.subTest(source=source):
                self.write_bundle([{"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"}],
                                  {"A.jar": plugin_jar("plugin.yml", None)}, source=source)
                with self.assertRaisesRegex(ValueError, "full core source commit"):
                    audit.audit(self.bundle)

    def test_duplicate_manifest_repository_is_refused(self):
        rows = [
            {"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"},
            {"repository": "EXAMPLE/A", "commit": "2" * 40, "jar": "B.jar"},
        ]
        self.write_bundle(rows, {name: plugin_jar("plugin.yml", None) for name in ("A.jar", "B.jar")})
        with self.assertRaisesRegex(ValueError, "duplicate addon repository"):
            audit.audit(self.bundle)

    def test_nonroot_or_ambiguous_manifest_jar_names_are_refused(self):
        for name in ("nested/A.jar", "../A.jar", "nested\\A.jar", " A.jar", "A.jar "):
            with self.subTest(name=name):
                self.write_bundle([{"repository": "example/a", "commit": "1" * 40, "jar": name}],
                                  {name: plugin_jar("plugin.yml", None)})
                with self.assertRaisesRegex(ValueError, "Invalid addon JAR name"):
                    audit.audit(self.bundle)

    def test_duplicate_json_fields_are_refused(self):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("SF_ADDON_MANIFEST.json", '{"addons": [], "addons": []}')
        with self.assertRaisesRegex(ValueError, "Duplicate manifest field"):
            audit.audit(self.bundle)

    def test_bundle_manifest_mismatch_refused(self):
        rows = [{"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"}]
        blobs = {
            "A.jar": plugin_jar("plugin.yml", "folia-supported: true"),
            "Extra.jar": plugin_jar("plugin.yml", "folia-supported: true"),
        }
        self.write_bundle(rows, blobs)
        with self.assertRaisesRegex(ValueError, "Bundle/manifest JAR mismatch"):
            audit.audit(self.bundle)

    def test_nested_unlisted_jar_is_not_ignored(self):
        rows = [{"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"}]
        self.write_bundle(rows, {"A.jar": plugin_jar("plugin.yml", None),
                                 "nested/Extra.jar": plugin_jar("plugin.yml", None)})
        with self.assertRaisesRegex(ValueError, "Bundle/manifest JAR mismatch"):
            audit.audit(self.bundle)

    def test_missing_descriptor_refused(self):
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr("README.txt", "no descriptor")
        rows = [{"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"}]
        self.write_bundle(rows, {"A.jar": raw.getvalue()})
        with self.assertRaisesRegex(ValueError, "No plugin descriptor"):
            audit.audit(self.bundle)

    def test_duplicate_manifest_jar_refused(self):
        rows = [
            {"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"},
            {"repository": "example/b", "commit": "2" * 40, "jar": "A.jar"},
        ]
        self.write_bundle(rows, {"A.jar": plugin_jar("plugin.yml", "folia-supported: true")})
        with self.assertRaisesRegex(ValueError, "duplicate addon JAR"):
            audit.audit(self.bundle)

    def test_duplicate_zip_entry_refused(self):
        rows = [{"repository": "example/a", "commit": "1" * 40, "jar": "A.jar"}]
        self.write_bundle(
            rows,
            {"A.jar": plugin_jar("plugin.yml", "folia-supported: true")},
            duplicate_entry=True,
        )
        with self.assertRaisesRegex(ValueError, "duplicate ZIP entries"):
            audit.audit(self.bundle)


class TestFoliaAdmissionWorkflow(unittest.TestCase):
    """Execute the real workflow fetch step against a recording downloader."""

    def setUp(self):
        workflow = Path(__file__).resolve().parents[1] / ".github/workflows/folia-26.3-addon-admission.yml"
        config = yaml.safe_load(workflow.read_text(encoding="utf-8"))
        self.fetch = next(step["run"] for step in config["jobs"]["audit"]["steps"]
                          if step.get("name") == "Wait for exact-source canonical addon bundle")
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "scripts").mkdir()
        (self.root / "scripts/download_candidate_bundle.py").write_text(
            "import json, os, sys\nfrom pathlib import Path\n"
            "Path('recorded.json').write_text(json.dumps(sys.argv[1:]))\n"
            "sys.exit(int(os.environ.get('FIXTURE_EXIT', '0')))\n", encoding="utf-8")

    def run_fetch(self, event, *, exit_code=0):
        return subprocess.run(["bash", "-c", self.fetch], cwd=self.root, capture_output=True, text=True,
                              env={**os.environ, "GITHUB_EVENT_NAME": event,
                                   "GITHUB_REPOSITORY": "example/core", "GITHUB_SHA": "2" * 40,
                                   "PR_HEAD_SHA": "1" * 40, "FIXTURE_EXIT": str(exit_code)}, timeout=10)

    def test_pr_keeps_head_distinct_from_tested_merge_source(self):
        result = self.run_fetch("pull_request")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads((self.root / "recorded.json").read_text()), [
            "--repository", "example/core", "--event", "pull_request", "--head", "1" * 40,
            "--source", "2" * 40, "--output", "build/bundle", "--timeout", "4800"])

    def test_push_and_manual_audit_require_matching_push_source(self):
        for event in ("push", "workflow_dispatch"):
            with self.subTest(event=event):
                result = self.run_fetch(event)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads((self.root / "recorded.json").read_text()), [
                    "--repository", "example/core", "--event", "push", "--head", "2" * 40,
                    "--source", "2" * 40, "--output", "build/bundle", "--timeout", "4800"])

    def test_unexpected_event_is_refused_before_downloading(self):
        result = self.run_fetch("pull_request_target")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / "recorded.json").exists())

    def test_failed_exact_download_is_not_replaced_by_a_release(self):
        result = self.run_fetch("pull_request", exit_code=7)
        self.assertEqual(result.returncode, 7)
        self.assertTrue((self.root / "recorded.json").exists())


if __name__ == "__main__":
    unittest.main()
