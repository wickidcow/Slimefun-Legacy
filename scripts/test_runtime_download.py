#!/usr/bin/env python3
"""Exercise the real shell/curl helper against a disposable loopback HTTP server."""
from __future__ import annotations
import http.server
import pathlib
import subprocess
import sys
import tempfile
import threading
import unittest

ROOT = pathlib.Path(sys.argv.pop(1)).resolve() if len(sys.argv) > 1 and not sys.argv[1].startswith('-') else pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/runtime_download.sh'


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.server.requests.append((self.path, self.headers.get('User-Agent')))
        sequence = self.server.responses
        code, data = sequence.pop(0) if len(sequence) > 1 else sequence[0]
        self.send_response(code)
        self.send_header('Content-Length', str(len(data) + (9 if self.path == '/partial' else 0)))
        self.end_headers()
        self.wfile.write(data)
        self.close_connection = True

    def log_message(self, *args):
        pass


class DownloadTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def setUp(self):
        self.server.requests = []
        self.server.responses = [(200, b'{"build":132}\n')]
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.target = pathlib.Path(self.temp.name) / 'server.jar'

    def fetch(self, output=False, path='/metadata'):
        args = ['bash', '-c', 'source "$1"; USER_AGENT="Slimefun-Legacy-Test/1.0 (https://github.com/wickidcow/Slimefun-Legacy)"; runtime_download "${@:2}"', 'test', str(SCRIPT), f'http://127.0.0.1:{self.server.server_port}{path}']
        if output:
            args.append(str(self.target))
        return subprocess.run(args, capture_output=True, timeout=35)

    def test_metadata_success_is_exact(self):
        result = self.fetch()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b'{"build":132}\n', result.stdout)
        self.assertEqual(1, len(self.server.requests))

    def test_503_then_success_excludes_error_body(self):
        self.server.responses = [(503, b'<html>unavailable</html>'), (200, b'{"ok":true}')]
        result = self.fetch()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b'{"ok":true}', result.stdout)
        self.assertEqual(2, len(self.server.requests))

    def test_429_then_success_uses_same_resource(self):
        self.server.responses = [(429, b'too many requests'), (200, b'correct bytes')]
        result = self.fetch(output=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b'correct bytes', self.target.read_bytes())
        self.assertEqual(['/metadata', '/metadata'], [path for path, _ in self.server.requests])

    def test_exhausted_retries_keep_previous_artifact(self):
        self.target.write_bytes(b'old verified artifact')
        self.server.responses = [(503, b'not a jar')]
        result = self.fetch(output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'old verified artifact', self.target.read_bytes())
        self.assertEqual(5, len(self.server.requests))
        self.assertEqual([], list(self.target.parent.glob('*.partial.*')))
        self.assertEqual(b'', result.stdout)

    def test_404_is_not_retried_or_returned_as_json(self):
        self.server.responses = [(404, b'{"error":"not found"}')]
        result = self.fetch()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'', result.stdout)
        self.assertEqual(1, len(self.server.requests))

    def test_403_does_not_disable_http_failure_checks(self):
        self.server.responses = [(403, b'access denied')]
        result = self.fetch(output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.target.exists())
        self.assertEqual(1, len(self.server.requests))

    def test_truncated_transfer_never_publishes_partial_file(self):
        self.target.write_bytes(b'previous')
        result = self.fetch(output=True, path='/partial')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'previous', self.target.read_bytes())
        self.assertEqual([], list(self.target.parent.glob('*.partial.*')))

    def test_empty_success_is_refused(self):
        self.server.responses = [(200, b'')]
        result = self.fetch(output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.target.exists())

    def test_binary_bytes_preserved(self):
        value = bytes(range(256)) * 128
        self.server.responses = [(200, value)]
        result = self.fetch(output=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(value, self.target.read_bytes())
        self.assertEqual(b'', result.stdout)

    def test_informative_user_agent_is_sent(self):
        self.fetch()
        self.assertIn('https://github.com/wickidcow/Slimefun-Legacy', self.server.requests[0][1])

    def test_usage_and_unavailable_destination_fail_before_network(self):
        result = subprocess.run(['bash', '-c', 'source "$1"; runtime_download', 'test', str(SCRIPT)], capture_output=True)
        self.assertEqual(2, result.returncode)
        self.target = self.target / 'missing-parent.jar'
        result = self.fetch(output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.server.requests)

    def test_all_runtime_callers_use_shared_helper(self):
        for stem in ('paper_runtime_smoke', 'legacy_1_21_11_runtime_smoke', 'paper_family_26_2_runtime_smoke', 'paper_26_3_runtime_smoke', 'full_stack_runtime_smoke', 'addon_runtime_smoke', 'proxy_runtime_smoke'):
            source = (ROOT / 'scripts' / f'{stem}.sh').read_text()
            self.assertIn('source "$REPO_ROOT/scripts/runtime_download.sh"', source, stem)
            self.assertIn('runtime_download ', source, stem)
            self.assertNotIn('curl --fail-with-body', source, stem)
            subprocess.run(['bash', '-n', str(ROOT / 'scripts' / f'{stem}.sh')], check=True)


if __name__ == '__main__':
    unittest.main()
