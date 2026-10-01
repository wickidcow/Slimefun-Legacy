#!/usr/bin/env python3
"""Offline metadata/stack wiring tests; no claim of a real WorldEdit or server boot."""
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import prepare_worldedit_runtime as runtime
from test_prepare_worldedit_runtime import jar_bytes, file_record, version


class BootEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.payload = jar_bytes()
        row = dict(version(channel="beta"), files=[file_record(self.payload)])
        project = {"id": "officialProject", "slug": "worldedit", "source_url": runtime.SOURCE_REPOSITORY}
        with patch.object(runtime, "request_bytes", side_effect=[json.dumps(project).encode(), json.dumps([row]).encode(), self.payload]):
            self.report = runtime.stage(self.root, "26.3", True)
        self.log = self.root / "boot.log"
        self.good = "[WorldEdit] Enabling WorldEdit v7.4.6-beta-02\n[WorldEditSlimefun] Enabling WorldEditSlimefun v1.0.6\n"

    def verify(self, text):
        self.log.write_text(text)
        return runtime.verify_boot_log(self.root, "26.3", self.log, True)

    def test_exact_provider_and_addon_enable_evidence(self):
        self.assertEqual(self.report, self.verify(self.good))

    def test_provider_absent_refused(self):
        with self.assertRaises(ValueError): self.verify(self.good.splitlines()[1])

    def test_addon_absent_refused(self):
        with self.assertRaises(ValueError): self.verify(self.good.splitlines()[0])

    def test_wrong_provider_version_refused(self):
        with self.assertRaises(ValueError): self.verify(self.good.replace("beta-02", "beta-01"))

    def test_version_prefix_is_not_a_version_match(self):
        with self.assertRaises(ValueError): self.verify(self.good.replace("beta-02\n", "beta-020\n"))

    def test_enable_failure_is_not_a_pass(self):
        with self.assertRaises(ValueError): self.verify(self.good + "Error occurred while enabling WorldEditSlimefun")

    def test_mutated_staged_file_refused(self):
        (self.root / "WorldEdit-runtime.jar").write_bytes(b"changed")
        with self.assertRaises(ValueError): self.verify(self.good)

    def test_changed_evidence_refused(self):
        self.report["sha256"] = "0" * 64
        (self.root / "worldedit-runtime.json").write_text(json.dumps(self.report))
        with self.assertRaises(ValueError): self.verify(self.good)

    def test_invalid_provenance_digest_refused(self):
        self.report["sha512"] = 42
        (self.root / "worldedit-runtime.json").write_text(json.dumps(self.report))
        with self.assertRaises(ValueError): self.verify(self.good)

    def test_missing_evidence_refused(self):
        (self.root / "worldedit-runtime.json").unlink()
        with self.assertRaises(ValueError): self.verify(self.good)

    def test_other_lane_evidence_refused(self):
        with self.assertRaises(ValueError): runtime.verify_staged(self.root, "26.2", True)

    def test_beta_requires_explicit_verification_opt_in(self):
        with self.assertRaises(ValueError): runtime.verify_staged(self.root, "26.3")

    def test_symlinked_provider_refused(self):
        original = self.root / "WorldEdit-runtime.jar"
        target = self.root / "elsewhere.jar"
        original.rename(target); original.symlink_to(target)
        with self.assertRaises(ValueError): self.verify(self.good)

    def test_second_boot_cannot_reuse_first_boot_evidence(self):
        self.verify(self.good)
        with self.assertRaises(ValueError): self.verify("Second boot did not load either plugin")


class StackWiringTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Normal repository layout, plus source-review layout for offline tests.
        root = Path(__file__).resolve().parents[1]
        review = root.parent / "modified"
        cls.root = root if (root / "scripts/full_stack_runtime_smoke.sh").is_file() else review
        cls.script = (cls.root / "scripts/full_stack_runtime_smoke.sh").read_text()
        cls.workflow = (cls.root / ".github/workflows/paper-26.3-full-stack.yml").read_text()
        cls.prepare = cls.script.split('<<\'PY\'\n', 1)[1].split('\nPY\n', 1)[0]

    def test_shell_syntax(self):
        result = subprocess.run(["bash", "-n"], input=self.script, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_all_three_lanes_retained(self):
        self.assertIn("minecraft: ['1.21.11', '26.2', '26.3']", self.workflow)
        self.assertIn("matrix.minecraft == '1.21.11' && '21' || '25'", self.workflow)

    def test_dependency_prepared_before_enable_classification(self):
        self.assertLess(self.script.index('--check-staged'), self.script.index('available_plugins ='))
        self.assertIn('--verify-log "$normalized"', self.script)
        self.assertIn('run_cycle first false\nrun_cycle second true', self.script)

    def test_source_changes_trigger_and_evidence_is_uploaded(self):
        for name in ('prepare_worldedit_runtime.py', 'test_prepare_worldedit_runtime.py', 'test_worldedit_stack_integration.py'):
            self.assertEqual(2, self.workflow.count("      - 'scripts/" + name + "'"))
        self.assertIn('/plugins/worldedit-runtime.json', self.workflow)
        self.assertIn("-p 'test_*worldedit*.py'", self.workflow)

    def test_exact_core_and_bundle_download_guards_are_retained(self):
        self.assertIn('--source "$GITHUB_SHA" --output bundle', self.workflow)
        self.assertIn('--repository "$GITHUB_REPOSITORY" --head "$PR_HEAD_SHA"', self.workflow)
        self.assertIn('verify_runtime_configuration.py', self.script)

    def run_prepare(self, *, include_dependency=True, include_addon=True, corrupt_hash=False):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); bundle = root/'bundle'; plugins = root/'plugins'
            bundle.mkdir(); plugins.mkdir()
            addons = []
            for name in (["WorldEditSlimefun", "Example"] if include_addon else ["Example"]):
                filename = name+'.jar'
                with zipfile.ZipFile(bundle/filename, 'w') as z:
                    # Exercise the real inline descriptor parser, not a substitute.
                    z.writestr('plugin.yml', f'name: {name}\nversion: 1\ndepend: [Slimefun, WorldEdit]\n')
                addons.append({'jar':filename})
            (bundle/'SF_ADDON_MANIFEST.json').write_text(json.dumps({'addons':addons}))
            if include_dependency:
                payload = jar_bytes()
                (plugins/'WorldEdit-runtime.jar').write_bytes(payload)
                report=runtime.validate_jar(payload,file_record(payload),25)
                if corrupt_hash: report['sha256']='0'*64
                (plugins/'worldedit-runtime.json').write_text(json.dumps(report))
            expected=root/'expected'; gated=root/'gated'
            result=subprocess.run([sys.executable,'-',str(bundle),str(plugins),str(expected),str(gated)],
                                  input=self.prepare,text=True,capture_output=True)
            return result, expected.read_text() if expected.exists() else '', gated.read_text() if gated.exists() else ''

    def test_worldedit_addon_becomes_required_not_excluded(self):
        result, expected, gated = self.run_prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('WorldEditSlimefun.jar\tWorldEditSlimefun\n', expected)
        self.assertEqual('', gated)
        self.assertNotIn('WorldEdit-runtime.jar',expected)

    def test_missing_dependency_cannot_be_called_dependency_gated_success(self):
        result, expected, gated = self.run_prepare(include_dependency=False)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual('',expected)

    def test_missing_worldedit_addon_refused(self):
        result, _, _ = self.run_prepare(include_addon=False)
        self.assertNotEqual(0,result.returncode)
        self.assertIn('exactly one WorldEditSlimefun',result.stderr)

    def test_changed_dependency_digest_refused(self):
        result,_,_=self.run_prepare(corrupt_hash=True)
        self.assertNotEqual(0,result.returncode)
        self.assertIn('does not match',result.stderr)


if __name__=='__main__': unittest.main(verbosity=2)
