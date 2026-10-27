#!/usr/bin/env python3
"""Candidate-only source preview never substitutes for immutable published-pair evidence."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from scripts.stage_unpublished_pair_candidate import preview_mode


class UnpublishedPairPreviewTest(unittest.TestCase):
    OLD_SOURCE = "a" * 40
    NEW_SOURCE = "b" * 40
    REPO = "wickidcow/SF_InfinityExpansion2"
    SLUG = "infinityexpansion2"

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / "InfinityExpansion2.0.16.jar"
        self.write_jar()
        self.lock = {"addons": {
            "ie2": {"repository": self.REPO, "source": self.OLD_SOURCE,
                    "plugin": "InfinityExpansion2", "version": "2.0.15"},
            "networks": {"repository": "wickidcow/SF_NetworksExp", "source": "c" * 40,
                         "plugin": "Networks", "version": "1.0.61"},
        }}
        self.manifest = {"addons": [
            {"repository": self.REPO, "slug": self.SLUG, "source_commit": self.NEW_SOURCE}
        ]}

    def write_jar(self, *, plugin="InfinityExpansion2", version="2.0.16",
                  api="1.21", bytecode=65, folia=False, folia_key="folia-supported"):
        descriptor = f"name: {plugin}\nversion: {version}\napi-version: '{api}'\n"
        if folia:
            descriptor += folia_key + ": " + (folia if isinstance(folia, str) else "true") + "\n"
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("plugin.yml", descriptor)
            archive.writestr("com/example/Addon.class",
                             b"\xca\xfe\xba\xbe\0\0" + bytecode.to_bytes(2, "big"))

    def preview(self, **updates):
        args = dict(manifest=self.manifest, lock=self.lock,
                    slug=self.SLUG, source=self.NEW_SOURCE, repository=self.REPO,
                    event="pull_request", jar=self.jar)
        args.update(updates)
        return preview_mode(**args)

    def test_only_exact_pinned_pr_candidate_is_previewable(self):
        mode, proof = self.preview()
        self.assertEqual("unpublished-pr-preview", mode)
        self.assertFalse(proof["eligible_for_release"])
        self.assertEqual(self.NEW_SOURCE, proof["source"])
        self.assertEqual(self.REPO, proof["repository"])
        self.assertEqual("InfinityExpansion2", proof["plugin"])
        self.assertEqual("2.0.16", proof["version"])
        self.assertEqual(self.jar.stat().st_size, proof["bytes"])
        self.assertEqual(hashlib.sha256(self.jar.read_bytes()).hexdigest(), proof["sha256"])

    def test_reviewed_published_pair_stays_on_original_immutable_path(self):
        self.manifest["addons"][0]["source_commit"] = self.OLD_SOURCE
        mode, proof = self.preview(source=self.OLD_SOURCE, event="push", jar=None)
        self.assertEqual("published", mode)
        self.assertIsNone(proof)

    def test_unpublished_source_is_rejected_on_push_dispatch_and_schedule(self):
        for event in ("push", "workflow_dispatch", "schedule", "workflow_run"):
            with self.subTest(event=event), self.assertRaisesRegex(RuntimeError, "master or release"):
                self.preview(event=event)

    def test_unreviewed_source_and_cross_repositories_are_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "exact addon manifest"):
            self.preview(source="d" * 40)
        with self.assertRaisesRegex(RuntimeError, "reviewed pair"):
            self.preview(repository="wickidcow/OtherPlugin")
        with self.assertRaisesRegex(RuntimeError, "Exact source SHA"):
            self.preview(source="latest")
        self.manifest["addons"].append(dict(self.manifest["addons"][0]))
        with self.assertRaisesRegex(RuntimeError, "exact addon manifest"):
            self.preview()

    def test_missing_and_symlinked_candidate_jar_is_rejected(self):
        missing = self.root / "missing.jar"
        with self.assertRaisesRegex(RuntimeError, "regular"):
            self.preview(jar=missing)
        link = self.root / "symlink.jar"
        link.symlink_to(self.jar)
        with self.assertRaisesRegex(RuntimeError, "regular"):
            self.preview(jar=link)

    def test_old_version_and_wrong_plugin_are_rejected(self):
        self.write_jar(version="2.0.15")
        with self.assertRaisesRegex(RuntimeError, "newer version"):
            self.preview()
        self.write_jar(version="2.0.16", plugin="OtherPlugin")
        with self.assertRaisesRegex(RuntimeError, "plugin identity mismatch"):
            self.preview()
        self.write_jar(version="candidate")
        with self.assertRaisesRegex(RuntimeError, "numeric"):
            self.preview()

    def test_modern_api_and_unaccepted_folia_claims_are_rejected(self):
        self.write_jar(api="26.3")
        with self.assertRaisesRegex(RuntimeError, "API floor"):
            self.preview()
        self.write_jar(folia=True)
        with self.assertRaisesRegex(RuntimeError, "Folia"):
            self.preview()

    def test_folia_yaml_boolean_variants_cannot_claim_unreviewed_support(self):
        for declaration in ("true", "'true'", '"TRUE"', "yes", "true # enabled", "false"):
            with self.subTest(declaration=declaration):
                self.write_jar(folia=declaration)
                with self.assertRaisesRegex(RuntimeError, "Folia"):
                    self.preview()

    def test_folia_quoted_yaml_keys_and_case_variants_are_rejected(self):
        for key in ("'folia-supported'", '"folia-supported"', "FoLiA-SuPpOrTeD"):
            with self.subTest(key=key):
                self.write_jar(folia="true", folia_key=key)
                with self.assertRaisesRegex(RuntimeError, "Folia"):
                    self.preview()

    def test_bytecode_above_java21_is_rejected(self):
        self.write_jar(bytecode=69)
        with self.assertRaisesRegex(RuntimeError, "Non-universal Java bytecode"):
            self.preview()

    def test_no_release_or_published_receipt_is_written(self):
        original = self.jar.read_bytes()
        mode, proof = self.preview()
        self.assertEqual("unpublished-pr-preview", mode)
        self.assertFalse(proof["eligible_for_release"])
        self.assertEqual(original, self.jar.read_bytes())
        self.assertEqual(["InfinityExpansion2.0.16.jar"], [p.name for p in self.root.iterdir()])


if __name__ == "__main__":
    unittest.main()
