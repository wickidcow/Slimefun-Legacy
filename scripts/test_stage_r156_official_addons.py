"""Fail closed on r156 published raw-asset replacement and source drift."""
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from scripts.stage_r156_official_addons import SLUGS, stage, verify_staged

RELEASES = {
    "wickidcow/SF_CrystamaeHistoria": ("crystamaehistoria", "CrystamaeHistoria", "1.0.3"),
    "wickidcow/SF_Galactifun": ("galactifun", "Galactifun", "1.0.8"),
}


def jar(name, version, marker):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("plugin.yml", f"name: {name}\nversion: '{version}'\napi-version: '1.21.11'\n")
        archive.writestr("META-INF/build-marker", marker)
    return buffer.getvalue()


class PublishedR156JarStageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.jars = self.root / "jars"
        self.meta = self.root / "metadata"
        self.jars.mkdir()
        self.meta.mkdir()
        self.rows = []
        self.official = {}
        for repo, (slug, name, version) in RELEASES.items():
            asset = f"SF_{name}{version}.jar"
            compiled = jar(name, version, "rebuilt-candidate-26.3")
            published = jar(name, version, "official-1.21.11-binary")
            source = ("a" if slug == "crystamaehistoria" else "b") * 40
            (self.jars / asset).write_bytes(compiled)
            record = {
                "repository": repo,
                "slug": slug,
                "jar": asset,
                "commit": source,
                "version": version,
                "sha256": hashlib.sha256(compiled).hexdigest(),
                "distributable_api": "26.3.build.253-stable",
            }
            (self.meta / f"{slug}.json").write_text(json.dumps(record), encoding="utf-8")
            self.rows.append({
                "repository": repo, "tag": f"v{version}", "source_commit": source,
                "asset": asset, "bytes": len(published),
                "sha256": hashlib.sha256(published).hexdigest(),
            })
            self.official[repo] = published

    def snapshot(self):
        return {p.name: p.read_bytes() for folder in (self.jars, self.meta) for p in folder.iterdir()}

    def test_includes_byte_identical_published_jar_and_correct_floor(self):
        stage(self.jars, self.meta, self.rows, self.official)
        verify_staged(self.jars, self.meta, self.rows)
        for row in self.rows:
            self.assertEqual(self.official[row["repository"]], (self.jars / row["asset"]).read_bytes())
            slug = SLUGS[row["repository"]][0]
            rec = json.loads((self.meta / f"{slug}.json").read_text())
            self.assertEqual(rec["distributable_api"], "1.21.11-R0.1-SNAPSHOT")
            self.assertEqual(rec["sha256"], row["sha256"])
            self.assertTrue(rec["published_artifact"]["verified_download"])
            self.assertNotEqual(rec["source_build_sha256"], row["sha256"])

    def test_rejects_missing_or_tampered_official_jar_without_writing(self):
        old = self.snapshot()
        for mode in ("missing", "tampered", "wrong-bundled-name"):
            with self.subTest(mode=mode):
                official = dict(self.official)
                rows = copy.deepcopy(self.rows)
                repo = rows[0]["repository"]
                if mode == "missing":
                    del official[repo]
                elif mode == "tampered":
                    official[repo] = official[repo] + b"bad"
                else:
                    rows[0]["asset"] = "SF_CrystamaeHistoria1.0.2.jar"
                with self.assertRaises(ValueError):
                    stage(self.jars, self.meta, rows, official)
                self.assertEqual(self.snapshot(), old)

    def test_rejects_stale_source_or_changed_rebuilt_metadata(self):
        for mode in ("stale-source", "bad-hash", "bad-version"):
            with self.subTest(mode=mode):
                slug = self.rows[0]["repository"]
                file = self.meta / f"{SLUGS[slug][0]}.json"
                original = file.read_bytes()
                rec = json.loads(original)
                rec[{"stale-source": "commit", "bad-hash": "sha256", "bad-version": "version"}[mode]] = "bad"
                file.write_text(json.dumps(rec))
                with self.assertRaises(ValueError):
                    stage(self.jars, self.meta, self.rows, self.official)
                file.write_bytes(original)

    def test_packager_rejects_mutated_actual_jar_or_forged_receipt(self):
        stage(self.jars, self.meta, self.rows, self.official)
        self.jars.joinpath(self.rows[0]["asset"]).write_bytes(b"invalid")
        with self.assertRaises(ValueError):
            verify_staged(self.jars, self.meta, self.rows)

    def test_workflow_replaces_before_packaging_and_rechecks(self):
        workflow = (Path(__file__).resolve().parents[1]
                    / ".github/workflows/build-sfl-addons-compat-bundle.yml").read_text(encoding="utf-8")
        stage_step = workflow.index("python3 scripts/stage_r156_official_addons.py --jars collected/jars --metadata collected/metadata")
        final_check = workflow.index("python3 scripts/stage_r156_official_addons.py --jars collected/jars --metadata collected/metadata --verify-only")
        bundle_step = workflow.index("name: Verify complete maintained set and canonical names")
        self.assertLess(stage_step, final_check)
        self.assertLess(final_check, bundle_step)
        packed = workflow.index('(cd bundle && zip -9 -q "../${BUNDLE_NAME}" ./*)')
        verified = workflow.index('python3 scripts/verify_published_addon_bundle.py "$BUNDLE_NAME"')
        uploaded = workflow.index('name: SF_Addons_1.21.11-26.3', verified)
        self.assertLess(packed, verified)
        self.assertLess(verified, uploaded)


if __name__ == "__main__":
    unittest.main()
