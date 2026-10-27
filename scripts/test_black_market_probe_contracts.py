"""Offline admission tests only; these do not execute Paper or plugin transactions."""
from __future__ import annotations
import contextlib
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

SOURCE = Path(__file__).parent / "runtime/black_market/run_probe.py"
SPEC = importlib.util.spec_from_file_location("black_market_probe", SOURCE)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class AdmissionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.home = Path(self.temporary.name).resolve()
        self.previous = Path.cwd()
        os.chdir(self.home)
        self.addCleanup(os.chdir, self.previous)
        self.work = self.home / "build/full-stack-26.3"
        (self.work / "plugins").mkdir(parents=True)
        for name, content in {
            "smoke-result.txt": "Paper runtime smoke: PASS\n",
            "server.jar": "server", "eula.txt": "eula=true\n", "runtime-build.txt": "Paper 26.3 fixture\n",
            "server.properties": "online-mode=false\nlevel-name=full-stack-smoke-world\nenable-rcon=false\nenable-query=false\n",
            "plugins/Slimefun-Legacy-full-stack.jar": "core",
        }.items():
            (self.work / name).write_text(content)
        self.rows = []
        self.blobs = {}
        for i in range(45):
            commit, jar = probe.MAGIC_RELEASES[139]
            name = jar if i == 0 else f"Addon{i}.jar"
            blob = f"synthetic-jar-{i}".encode()
            self.blobs[name] = blob
            (self.work / "plugins" / name).write_bytes(blob)
            self.rows.append({"repository": probe.MAGIC if i == 0 else f"fixture/Addon{i}", "jar": name,
                              "commit": commit if i == 0 else "a" * 40,
                              "sha256": hashlib.sha256(blob).hexdigest()})
        self.manifest = {"bundle_revision": 139, "addons": self.rows}
        self.bundle = self.home / "candidate.zip"
        self.write_bundle()

    def write_bundle(self):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("SF_ADDON_MANIFEST.json", json.dumps(self.manifest))
            for name, blob in self.blobs.items():
                archive.writestr(name, blob)

    def validate(self):
        return probe.validate_fixture(self.work, self.bundle)

    def rejects(self):
        with self.assertRaises((RuntimeError, KeyError, ValueError)):
            self.validate()
        self.assertFalse((self.work / "bm-probe-classes").exists())

    def test_exact_fixture_admitted_without_writes(self):
        before = {p.relative_to(self.work): p.read_bytes() for p in self.work.rglob("*") if p.is_file()}
        evidence = self.validate()
        self.assertEqual(len(evidence["tested_addons"]), 45)
        self.assertEqual(evidence["result"], "STARTED")
        self.assertEqual(evidence["magic_version"], "1.1.15")
        self.assertEqual(before, {p.relative_to(self.work): p.read_bytes() for p in self.work.rglob("*") if p.is_file()})

    def test_all_four_runtime_names_supported(self):
        for name in probe.DIRECTORIES:
            with self.subTest(name=name):
                destination = self.work.with_name(name)
                if destination != self.work:
                    self.work.rename(destination)
                    self.work = destination
                self.validate()

    def test_production_directory_refused(self):
        destination = self.home / "production"
        self.work.rename(destination)
        self.work = destination
        self.rejects()

    def test_linked_runtime_refused(self):
        destination = self.work.with_name("original")
        self.work.rename(destination)
        self.work.symlink_to(destination, target_is_directory=True)
        self.rejects()

    def test_r137_is_still_supported(self):
        old_commit, old_jar = probe.MAGIC_RELEASES[137]
        new_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(new_jar)
        (self.work / "plugins" / new_jar).unlink()
        self.blobs[old_jar] = blob
        (self.work / "plugins" / old_jar).write_bytes(blob)
        self.rows[0]["jar"] = old_jar
        self.rows[0]["commit"] = old_commit
        self.manifest["bundle_revision"] = 137
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 137)
        self.assertEqual(evidence["magic_version"], "1.1.13")

    def test_r138_is_still_supported(self):
        self.manifest["bundle_revision"] = 138
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 138)
        self.assertEqual(evidence["magic_version"], "1.1.15")

    def test_r140_is_supported(self):
        self.manifest["bundle_revision"] = 140
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 140)
        self.assertEqual(evidence["magic_version"], "1.1.15")

    def test_bad_or_noninteger_revision_refused(self):
        for revision in (136, 154, "141", 141.0, "142", 142.0, "143", 143.0, "144", 144.0, "145", 145.0, "146", 146.0, "147", 147.0, "148", 148.0, "149", 149.0, "150", 150.0, "151", 151.0, "152", 152.0, "153", 153.0, True):
            with self.subTest(revision=revision):
                self.manifest["bundle_revision"] = revision
                self.write_bundle()
                self.rejects()

    def test_r141_requires_magic_1_1_16_and_its_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[141]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 141
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 141)
        self.assertEqual(evidence["magic_version"], "1.1.16")
        self.assertEqual(jar, "SF_MagicExpansion1.1.16.jar")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[140][0]
        self.write_bundle()
        self.rejects()

    def test_wrong_magic_source_refused(self):
        self.rows[0]["commit"] = "b" * 40
        self.write_bundle()
        self.rejects()

    def test_r142_requires_retained_magic_1_1_16_and_exact_source(self):
        commit = "1f7f6fed1e4b7b8cda9331cedc8a963332808101"
        jar = "SF_MagicExpansion1.1.16.jar"
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 142
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 142)
        self.assertEqual(evidence["magic_version"], "1.1.16")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[140][0]
        self.write_bundle()
        self.rejects()

    def test_r143_through_r145_require_unchanged_magic_1_1_16_and_exact_source(self):
        commit = "1f7f6fed1e4b7b8cda9331cedc8a963332808101"
        jar = "SF_MagicExpansion1.1.16.jar"
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        for revision in (143, 144, 145):
            with self.subTest(revision=revision):
                self.rows[0].update(jar=jar, commit=commit)
                self.manifest["bundle_revision"] = revision
                self.write_bundle()
                evidence = self.validate()
                self.assertEqual(evidence["bundle_revision"], revision)
                self.assertEqual(evidence["magic_version"], "1.1.16")
                self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
                self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
                self.assertEqual(evidence["result"], "STARTED")
                self.rows[0]["commit"] = probe.MAGIC_RELEASES[140][0]
                self.write_bundle()
                self.rejects()

    def test_r146_requires_magic_1_1_17_and_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[146]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 146
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 146)
        self.assertEqual(evidence["magic_version"], "1.1.17")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.assertEqual(evidence["result"], "STARTED")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[145][0]
        self.write_bundle()
        self.rejects()

    def test_r147_requires_magic_1_1_17_and_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[147]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 147
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 147)
        self.assertEqual(evidence["magic_version"], "1.1.17")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.assertEqual(evidence["result"], "STARTED")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[145][0]
        self.write_bundle()
        self.rejects()

    def test_r148_requires_magic_1_1_17_and_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[148]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 148
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 148)
        self.assertEqual(evidence["magic_version"], "1.1.17")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.assertEqual(evidence["result"], "STARTED")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[145][0]
        self.write_bundle()
        self.rejects()

    def test_r149_requires_magic_1_1_17_and_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[149]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 149
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 149)
        self.assertEqual(evidence["magic_version"], "1.1.17")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.assertEqual(evidence["result"], "STARTED")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[145][0]
        self.write_bundle()
        self.rejects()

    def test_r150_requires_magic_1_1_18_and_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[150]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 150
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 150)
        self.assertEqual(evidence["magic_version"], "1.1.18")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.assertEqual(evidence["result"], "STARTED")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[149][0]
        self.write_bundle()
        self.rejects()

    def test_r151_preview_requires_magic_1_1_19_and_exact_source(self):
        commit, jar = probe.MAGIC_RELEASES[151]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 151
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 151)
        self.assertEqual(evidence["magic_version"], "1.1.19")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.assertEqual(evidence["result"], "STARTED")
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[150][0]
        self.write_bundle()
        self.rejects()

    def test_r151_preview_rejects_the_published_magic_1_1_18_pair(self):
        # An official r151 source is not the unpublished 1.1.19 test fixture.
        published_commit, published_jar = probe.MAGIC_RELEASES[150]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[published_jar] = blob
        (self.work / "plugins" / published_jar).write_bytes(blob)
        self.rows[0].update(jar=published_jar, commit=published_commit)
        self.manifest["bundle_revision"] = 151
        self.write_bundle()
        self.rejects()

    def test_r152_requires_final_merged_magic_1_1_19_source(self):
        commit, jar = probe.MAGIC_RELEASES[152]
        old_jar = self.rows[0]["jar"]
        blob = self.blobs.pop(old_jar)
        (self.work / "plugins" / old_jar).unlink()
        self.blobs[jar] = blob
        (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        self.manifest["bundle_revision"] = 152
        self.write_bundle()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 152)
        self.assertEqual(evidence["magic_version"], "1.1.19")
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"], commit)
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"], jar)
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[151][0]
        self.write_bundle()
        self.rejects()


    def select_r153_roster(self):
        """Use the exact reviewed 44-addon roster and merged Magic 1.1.19 source."""
        commit, jar = probe.MAGIC_RELEASES[153]
        old_jar = self.rows[0]["jar"]
        if old_jar != jar:
            blob = self.blobs.pop(old_jar)
            (self.work / "plugins" / old_jar).unlink()
            self.blobs[jar] = blob
            (self.work / "plugins" / jar).write_bytes(blob)
        self.rows[0].update(jar=jar, commit=commit)
        retired = self.rows.pop()
        self.blobs.pop(retired["jar"])
        (self.work / "plugins" / retired["jar"]).unlink()
        self.manifest["bundle_revision"] = 153
        self.write_bundle()

    def test_r153_admits_exact_44_addon_roster_without_buildingstaff(self):
        self.select_r153_roster()
        evidence = self.validate()
        self.assertEqual(evidence["bundle_revision"], 153)
        self.assertEqual(evidence["magic_version"], "1.1.19")
        self.assertEqual(len(evidence["tested_addons"]), 44)
        self.assertNotIn("wickidcow/SF_BuildingStaff", evidence["tested_addons"])
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["commit"],
                         probe.MAGIC_RELEASES[153][0])
        self.assertEqual(evidence["tested_addons"][probe.MAGIC]["jar"],
                         "SF_MagicExpansion1.1.19.jar")
        self.assertEqual(evidence["result"], "STARTED")

    def test_r153_rejects_retired_45_addon_roster(self):
        self.select_r153_roster()
        self.rows.append({"repository": "fixture/Addon44", "jar": "Addon44.jar",
                          "commit": "a" * 40, "sha256": "b" * 64})
        self.write_bundle()
        self.rejects()

    def test_r153_rejects_buildingstaff_reintroduced_in_44_rows(self):
        self.select_r153_roster()
        self.rows[1]["repository"] = "wickidcow/SF_BuildingStaff"
        self.write_bundle()
        self.rejects()

    def test_r153_rejects_obsolete_magic_1_1_18_source(self):
        self.select_r153_roster()
        self.rows[0]["commit"] = probe.MAGIC_RELEASES[150][0]
        self.write_bundle()
        self.rejects()

    def test_r153_rejects_obsolete_magic_jar_filename(self):
        self.select_r153_roster()
        self.rows[0]["jar"] = probe.MAGIC_RELEASES[150][1]
        self.write_bundle()
        self.rejects()

    def test_r153_rejects_missing_addon(self):
        self.select_r153_roster()
        self.rows.pop()
        self.write_bundle()
        self.rejects()

    def test_r153_rejects_checksum_drift(self):
        self.select_r153_roster()
        self.rows[2]["sha256"] = "0" * 64
        self.write_bundle()
        self.rejects()

    def test_missing_checksum_refused(self):
        del self.rows[0]["sha256"]
        self.write_bundle()
        self.rejects()

    def test_other_addon_changed_refused(self):
        (self.work / "plugins/Addon44.jar").write_bytes(b"changed")
        self.rejects()

    def test_bundle_bytes_changed_refused(self):
        self.blobs[probe.MAGIC_RELEASES[139][1]] = b"changed"
        self.write_bundle()
        self.rejects()

    def test_missing_addon_refused(self):
        (self.work / "plugins/Addon44.jar").unlink()
        self.rejects()

    def test_linked_addon_refused(self):
        path = self.work / "plugins" / probe.MAGIC_RELEASES[139][1]
        original = self.home / "outside.jar"
        path.rename(original)
        path.symlink_to(original)
        self.rejects()

    def test_duplicate_repository_refused(self):
        self.rows[44]["repository"] = self.rows[43]["repository"]
        self.write_bundle()
        self.rejects()

    def test_incomplete_roster_refused(self):
        self.rows.pop()
        self.write_bundle()
        self.rejects()

    def test_failed_baseline_refused(self):
        (self.work / "smoke-result.txt").write_text("runtime smoke: FAIL")
        self.rejects()

    def test_wrong_world_or_remote_admin_refused(self):
        path = self.work / "server.properties"
        before = path.read_text()
        for old, new in (("full-stack-smoke-world", "survival"), ("enable-rcon=false", "enable-rcon=true"),
                         ("online-mode=false", "online-mode=true"), ("enable-query=false", "enable-query=true")):
            with self.subTest(new=new):
                path.write_text(before.replace(old, new))
                self.rejects()
        path.write_text(before)

    def test_stale_probe_refused(self):
        path = self.work / "plugins" / probe.PLUGIN
        path.mkdir()
        self.rejects()

    def test_core_missing_refused(self):
        (self.work / "plugins/Slimefun-Legacy-full-stack.jar").unlink()
        self.rejects()

    def test_four_phase_order_requires_missing_then_restoration(self):
        self.assertEqual(probe.PHASES, ("seed", "verify", "missing", "restored"))


if __name__ == "__main__":
    unittest.main()
