#!/usr/bin/env python3
"""Pure fixture tests for candidate selection and byte/source identity enforcement."""
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from download_candidate_bundle import WORKFLOW, select_run, verify_bundle


class CandidateBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'bundle.zip'
        self.head, self.source = '1' * 40, '2' * 40
        self.matrix = {'addons': [{'repository': 'wickidcow/SF_Test', 'source_commit': self.head}]}
        self.payload = b'fixture bytes, not an executable JAR'
        self.manifest = {'core_source_commit': self.source, 'addons': [{
            'repository': 'wickidcow/SF_Test', 'commit': self.head,
            'jar': 'SF_Test1.0.jar', 'sha256': hashlib.sha256(self.payload).hexdigest()}]}

    def write(self, extra=None):
        with zipfile.ZipFile(self.path, 'w') as archive:
            archive.writestr('SF_ADDON_MANIFEST.json', json.dumps(self.manifest))
            archive.writestr('SF_Test1.0.jar', self.payload)
            for name, content in (extra or {}).items():
                archive.writestr(name, content)

    def test_selects_only_the_exact_head_and_workflow_and_event(self):
        correct = dict(id=1, path=WORKFLOW, head_sha=self.head, event='pull_request')
        rows = [correct, dict(correct, id=2, head_sha=self.source),
                dict(correct, id=3, event='push'), dict(correct, id=4, path='other.yml')]
        self.assertEqual(correct, select_run({'workflow_runs': rows}, self.head))

    def test_selects_latest_attempt_even_when_it_failed(self):
        good = dict(id=1, path=WORKFLOW, head_sha=self.head, event='pull_request', conclusion='success')
        failed = dict(good, id=2, conclusion='failure')
        self.assertEqual(failed, select_run({'workflow_runs': [good, failed]}, self.head))

    def test_no_matching_run_is_not_a_release_fallback(self):
        self.assertIsNone(select_run({'workflow_runs': []}, self.head))

    def test_exact_artifact_passes_without_modifying_its_bytes(self):
        self.write()
        before = self.path.read_bytes()
        self.assertEqual(self.manifest, verify_bundle(self.path, self.matrix, self.source))
        self.assertEqual(before, self.path.read_bytes())

    def test_wrong_core_or_addon_commit_fails(self):
        self.write()
        with self.assertRaises(ValueError): verify_bundle(self.path, self.matrix, self.head)
        self.manifest['addons'][0]['commit'] = self.source
        self.write()
        with self.assertRaises(ValueError): verify_bundle(self.path, self.matrix, self.source)

    def test_missing_or_duplicate_addon_records_fail(self):
        for rows in ([], self.manifest['addons'] * 2):
            with self.subTest(rows=rows):
                self.manifest['addons'] = rows
                self.write()
                with self.assertRaises(ValueError): verify_bundle(self.path, self.matrix, self.source)

    def test_altered_plugin_checksum_fails(self):
        self.manifest['addons'][0]['sha256'] = '0' * 64
        self.write()
        with self.assertRaises(ValueError): verify_bundle(self.path, self.matrix, self.source)

    def test_path_traversal_is_rejected_before_reading_plugin(self):
        self.manifest['addons'][0]['jar'] = '../escape.jar'
        self.write()
        with self.assertRaises(ValueError): verify_bundle(self.path, self.matrix, self.source)

    def test_extra_plugin_is_not_silently_installed(self):
        self.write({'unexpected.jar': b'x'})
        with self.assertRaises(ValueError): verify_bundle(self.path, self.matrix, self.source)

    def test_missing_or_corrupt_archives_fail(self):
        with self.assertRaises(FileNotFoundError): verify_bundle(self.path, self.matrix, self.source)
        self.path.write_bytes(b'not a zip')
        with self.assertRaises(zipfile.BadZipFile): verify_bundle(self.path, self.matrix, self.source)


    def test_push_selection_cannot_reuse_a_pr_or_other_head(self):
        correct = dict(id=1, path=WORKFLOW, head_sha=self.head, event='push')
        rows = [correct, dict(correct, id=2, event='pull_request'),
                dict(correct, id=3, head_sha=self.source), dict(correct, id=4, path='wrong.yml')]
        self.assertEqual(correct, select_run({'workflow_runs': rows}, self.head, 'push'))
        self.assertEqual(rows[1], select_run({'workflow_runs': rows}, self.head))

    def test_latest_failed_push_cannot_fall_back_to_earlier_success(self):
        old = dict(id=1, path=WORKFLOW, head_sha=self.head, event='push', conclusion='success')
        latest = dict(old, id=2, conclusion='failure')
        self.assertEqual(latest, select_run({'workflow_runs': [old, latest]}, self.head, 'push'))

    def test_missing_push_does_not_select_a_successful_pr(self):
        pr = dict(id=1, path=WORKFLOW, head_sha=self.head, event='pull_request', conclusion='success')
        self.assertIsNone(select_run({'workflow_runs': [pr]}, self.head, 'push'))

    def test_unknown_event_is_rejected_not_broadened(self):
        for event in ('', 'schedule', 'workflow_dispatch', 'pull_request_target', None):
            with self.subTest(event=event), self.assertRaises(ValueError):
                select_run({'workflow_runs': []}, self.head, event)

    def test_master_push_requires_same_source_bundle(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/paper-26.3-full-stack.yml').read_text()
        marker = 'elif [[ "$GITHUB_EVENT_NAME" == "push" ]]; then'
        self.assertIn(marker, workflow)
        push = workflow.split(marker, 1)[1].split('elif ', 1)[0]
        for required in ('scripts/download_candidate_bundle.py', '--event push',
                         '--head "$GITHUB_SHA"', '--source "$GITHUB_SHA"'):
            self.assertIn(required, push)
        self.assertNotIn('gh release', push)

    def test_full_stack_core_pins_and_verifies_actual_checkout(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/paper-26.3-full-stack.yml').read_text()
        core = workflow.split('  build-core:', 1)[1].split('  fetch-bundle:', 1)[0]
        pin = core.index('export SOURCE_COMMIT="$(git rev-parse HEAD)"')
        build = core.index('./gradlew clean shadowJar')
        check = core.index('python3 scripts/verify_release_artifact.py')
        upload = core.index('uses: actions/upload-artifact')
        self.assertLess(pin, build)
        self.assertLess(build, check)
        self.assertLess(check, upload)
        self.assertIn('export SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"', core)


if __name__ == '__main__':
    unittest.main(argv=[__file__])
