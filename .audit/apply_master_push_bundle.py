from pathlib import Path
import hashlib
import subprocess


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def replace(path, expected, transform):
    path = Path(path)
    data = path.read_bytes()
    assert blob(data) == expected, str(path)
    path.write_text(transform(data.decode('utf-8')), encoding='utf-8')


def selector(text):
    text = text.replace('Download only the successful bundle for this PR head and verify its tested source.',
                        'Download only the successful bundle for this exact head/event and tested source.')
    text = text.replace('actual merge checkout tested by the build. Both identities are required.',
                        'actual checkout tested by the build. Push builds use the same head/source; PR\nbuilds retain their distinct merge source. Both identities and the event are required.')
    old = 'def select_run(payload: dict, head: str) -> dict | None:\n    matches = '
    new = '''def select_run(payload: dict, head: str, event: str = 'pull_request') -> dict | None:
    if event not in {'pull_request', 'push'}:
        raise ValueError('Only pull_request and push bundle events are supported')
    matches = '''
    assert text.count(old) == 1
    text = text.replace(old, new).replace("and row.get('event') == 'pull_request']", "and row.get('event') == event]")
    old = "    parser.add_argument('--source', required=True)\n"
    assert text.count(old) == 1
    text = text.replace(old, old + "    parser.add_argument('--event', choices=['pull_request', 'push'], default='pull_request')\n")
    old = 'actions/runs?event=pull_request&head_sha={args.head}&per_page=100'
    assert text.count(old) == 1
    text = text.replace(old, 'actions/runs?event={args.event}&head_sha={args.head}&per_page=100')
    old = 'run = select_run(json.loads(result.stdout), args.head)'
    assert text.count(old) == 1
    text = text.replace(old, 'run = select_run(json.loads(result.stdout), args.head, args.event)')
    old = "            (args.output / 'bundle-source.txt').write_text(\n                f\"source=matching-pr-artifact\\nrun_id={run['id']}\\nhead_sha={args.head}\\n\""
    new = "            source_kind = 'matching-pr-artifact' if args.event == 'pull_request' else 'matching-push-artifact'\n            (args.output / 'bundle-source.txt').write_text(\n                f\"source={source_kind}\\nrun_id={run['id']}\\nhead_sha={args.head}\\n\""
    assert text.count(old) == 1
    return text.replace(old, new)


def tests(text):
    needle = "\n\nif __name__ == '__main__':"
    addition = '''
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
'''
    assert text.count(needle) == 1
    return text.replace(needle, '\n' + addition + needle)


def workflow(text):
    old = '          elif [[ "$GITHUB_EVENT_NAME" == "workflow_run" ]]; then\n'
    new = '''          elif [[ "$GITHUB_EVENT_NAME" == "push" ]]; then
            python3 scripts/download_candidate_bundle.py \\
              --repository "$GITHUB_REPOSITORY" --event push --head "$GITHUB_SHA" \\
              --source "$GITHUB_SHA" --output bundle
'''
    assert text.count(old) == 1
    text = text.replace(old, new + old)
    old = '''          chmod +x gradlew
          ./gradlew clean shadowJar -PpaperApiVersion="$PAPER_API_VERSION" --no-daemon'''
    new = '''          chmod +x gradlew
          export SOURCE_COMMIT="$(git rev-parse HEAD)"
          export SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"
          ./gradlew clean shadowJar -PpaperApiVersion="$PAPER_API_VERSION" --no-daemon --no-build-cache --no-configuration-cache'''
    assert text.count(old) == 1
    text = text.replace(old, new)
    old = '          python3 scripts/check_bytecode_target.py "$JAR" --expected-java 21\n'
    assert text.count(old) == 1
    return text.replace(old, old + '          python3 scripts/verify_release_artifact.py "$JAR" --root .\n')


replace('scripts/test_download_candidate_bundle.py', 'a2db1c31956fce41fe81d7b54b4a4355a8f83501', tests)
probe = subprocess.run(['python3', '-m', 'unittest', 'test_download_candidate_bundle.CandidateBundleTest.test_master_push_requires_same_source_bundle'], cwd='scripts', capture_output=True, text=True)
assert probe.returncode == 1 and 'AssertionError' in probe.stderr and 'FAILED (failures=1)' in probe.stderr, probe.stderr
Path('evidence').mkdir(exist_ok=True)
Path('evidence/original-push-negative-control.txt').write_text(probe.stdout + probe.stderr)
replace('scripts/download_candidate_bundle.py', '3a749f6b33dff0e7145531bdeda6949a3fbe6c35', selector)
replace('.github/workflows/paper-26.3-full-stack.yml', '0ae0d9fcb2f8fba0ff5a9f925fb7c413c25d4884', workflow)
expected = {'scripts/download_candidate_bundle.py': '94e857edbaa730bdcdc238d7eb9f2889e6018293', 'scripts/test_download_candidate_bundle.py': '820c548295c9ed1b13418fd9d3a2d44e539f4f2b', '.github/workflows/paper-26.3-full-stack.yml': '7b88058ab5099533b00193df5ee7a4d47d4952e7'}
for name, sha in expected.items():
    assert blob(Path(name).read_bytes()) == sha, name
