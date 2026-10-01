#!/usr/bin/env python3
"""Exercise the actual Bash capture loop with controlled, incrementally arriving logs."""
from pathlib import Path
import os
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ProxyIdentityCaptureTest(unittest.TestCase):
    def capture(self, initial, late='', research=''):
        source = (ROOT / 'scripts/proxy_runtime_smoke.sh').read_text()
        match = re.search(r'(?ms)^capture_player_identity\(\) \{\n.*?^\}', source)
        self.assertIsNotNone(match, 'Test must execute the production capture function')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'initial').write_text(initial)
            (root / 'late').write_text(late)
            (root / 'backend').touch()
            script = '''set -euo pipefail
WORK_DIR="$1"
BACKEND_LOG="$WORK_DIR/backend"
BACKEND_NORMALIZED="$WORK_DIR/normalized"
IDENTITY_PLAYER="FixturePlayer"
normalize_log() {
    if [[ ! -e "$WORK_DIR/started" ]]; then
        cat "$WORK_DIR/initial" >> "$BACKEND_LOG"
        touch "$WORK_DIR/started"
    fi
    cp "$1" "$2"
}
sleep() {
    if [[ ! -e "$WORK_DIR/delayed" ]]; then
        cat "$WORK_DIR/late" >> "$BACKEND_LOG"
        touch "$WORK_DIR/delayed"
    else
        SECONDS=1000000
    fi
}
exec 3>"$WORK_DIR/commands"
''' + match.group(0) + '\ncapture_player_identity test "$2"\n'
            result = subprocess.run(['bash', '-c', script, 'capture-test', temp, research],
                                    capture_output=True, text=True, timeout=5)
            return result, (root / 'commands').read_text(), (root / 'delayed').exists()

    def test_initial_capture_waits_for_the_delayed_research_candidate(self):
        result, command, waited = self.capture('UUID match: Yes\nBackpack count: 0\n',
                                             'Persistence research candidate: slimefun:walking_sticks\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(waited)
        self.assertIn('Persistence research candidate: slimefun:walking_sticks', result.stdout)
        self.assertEqual('sf doctor proxy player FixturePlayer\n', command)

    def test_missing_candidate_is_not_reported_as_complete(self):
        result, _, _ = self.capture('UUID match: Yes\nBackpack count: 0\n')
        self.assertNotEqual(0, result.returncode)

    def test_complete_initial_capture_does_not_wait(self):
        result, _, waited = self.capture('UUID match: Yes\nPersistence research candidate: slimefun:a\n')
        self.assertEqual(0, result.returncode)
        self.assertFalse(waited)

    def test_explicit_research_waits_for_its_own_result(self):
        result, command, waited = self.capture('UUID match: Yes\nResearch slimefun:other: Unlocked\n',
                                             'Research slimefun:walking_sticks: Unlocked\n', 'slimefun:walking_sticks')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(waited)
        self.assertIn('Research slimefun:walking_sticks: Unlocked', result.stdout)
        self.assertEqual('sf doctor proxy player FixturePlayer slimefun:walking_sticks\n', command)

    def test_a_candidate_is_not_an_explicit_research_result(self):
        result, _, _ = self.capture('UUID match: Yes\nPersistence research candidate: slimefun:a\n', research='slimefun:a')
        self.assertNotEqual(0, result.returncode)

    def test_uuid_mismatch_still_fails_with_a_terminal_line(self):
        result, _, _ = self.capture('UUID match: No\nPersistence research candidate: slimefun:a\n')
        self.assertNotEqual(0, result.returncode)

    def test_no_candidate_is_complete_but_the_existing_caller_rejects_it(self):
        result, _, _ = self.capture('UUID match: Yes\nPersistence research candidate: <none>\n')
        self.assertEqual(0, result.returncode)
        self.assertIn('<none>', result.stdout)
        source = (ROOT / 'scripts/proxy_runtime_smoke.sh').read_text()
        self.assertIn('"$research_key" == "<none>"', source)
        self.assertIn('Slimefun research did not persist across proxy disconnect/reconnect.', source)

    def test_offline_player_stops_without_success(self):
        result, _, waited = self.capture('FixturePlayer is not online on this backend\n')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(waited)


if __name__ == '__main__':
    unittest.main(argv=[__file__])
