#!/usr/bin/env python3
"""Download only the successful bundle for this exact head/event and tested source.

Never fall back to a release archive when a candidate build fails or is absent.
GitHub run head_sha identifies the PR head; core_source_commit identifies the
actual checkout tested by the build. Push builds use the same head/source; PR
builds retain their distinct merge source. Both identities and the event are required.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import time
import zipfile
from pathlib import Path

WORKFLOW = '.github/workflows/build-sfl-addons-compat-bundle.yml'
BUNDLE = 'SF_Addons_1.21.11-26.3.zip'


def select_run(payload: dict, head: str, event: str = 'pull_request') -> dict | None:
    if event not in {'pull_request', 'push'}:
        raise ValueError('Only pull_request and push bundle events are supported')
    matches = [row for row in payload['workflow_runs']
               if row.get('path') == WORKFLOW and row.get('head_sha') == head
               and row.get('event') == event]
    return max(matches, key=lambda row: row['id']) if matches else None


def verify_bundle(path: Path, matrix: dict, source: str) -> dict:
    expected = {row['repository']: row for row in matrix['addons']}
    if not expected or len(expected) != len(matrix['addons']):
        raise ValueError('Empty or duplicate expected addon matrix')
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError('Duplicate entries or corrupt bundle')
        manifest = json.loads(archive.read('SF_ADDON_MANIFEST.json'))
        if manifest.get('core_source_commit') != source:
            raise ValueError('Candidate bundle was built from a different core checkout')
        rows = manifest['addons']
        if len(rows) != len(expected) or {row['repository'] for row in rows} != set(expected):
            raise ValueError('Candidate bundle does not contain the exact maintained addon set')
        jars = set()
        for row in rows:
            name = row['jar']
            if Path(name).name != name or not name.endswith('.jar') or name in jars:
                raise ValueError('Invalid or duplicate plugin JAR name')
            jars.add(name)
            pinned = expected[row['repository']].get('source_commit')
            if pinned and row['commit'] != pinned:
                raise ValueError(f"Wrong addon source for {row['repository']}")
            if hashlib.sha256(archive.read(name)).hexdigest() != row['sha256']:
                raise ValueError(f'Addon checksum mismatch: {name}')
        if {name for name in names if name.endswith('.jar')} != jars:
            raise ValueError('Unlisted plugin JAR in candidate bundle')
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--head', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--event', choices=['pull_request', 'push'], default='pull_request')
    parser.add_argument('--matrix', type=Path, default=Path('compatibility/sfl-addon-release-matrix.json'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--timeout', type=int, default=1800)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
        parser.error('Invalid repository name')
    if not all(re.fullmatch(r'[0-9a-f]{40}', value) for value in (args.head, args.source)):
        parser.error('Expected full commit hashes')
    if not 1 <= args.timeout <= 3600:
        parser.error('Timeout must be between 1 and 3600 seconds')
    args.output.mkdir(parents=True, exist_ok=True)
    target = args.output / BUNDLE
    if target.exists():
        raise RuntimeError('Refusing to reuse an existing candidate bundle')
    deadline = time.monotonic() + args.timeout
    while time.monotonic() < deadline:
        result = subprocess.run(['gh', 'api',
            f'repos/{args.repository}/actions/runs?event={args.event}&head_sha={args.head}&per_page=100'],
            capture_output=True, text=True, check=True, timeout=45)
        run = select_run(json.loads(result.stdout), args.head, args.event)
        if run and run['status'] == 'completed':
            if run['conclusion'] != 'success':
                raise RuntimeError(f"Candidate bundle run {run['id']} finished with {run['conclusion']}; no release fallback")
            subprocess.run(['gh', 'run', 'download', str(run['id']), '--repo', args.repository,
                            '--name', 'SF_Addons_1.21.11-26.3', '--dir', str(args.output)],
                           check=True, timeout=180)
            manifest = verify_bundle(target, json.loads(args.matrix.read_text()), args.source)
            digest = hashlib.sha256(target.read_bytes()).hexdigest()
            source_kind = 'matching-pr-artifact' if args.event == 'pull_request' else 'matching-push-artifact'
            (args.output / 'bundle-source.txt').write_text(
                f"source={source_kind}\nrun_id={run['id']}\nhead_sha={args.head}\n"
                f'core_source_commit={args.source}\nsha256={digest}\n')
            print(f"Verified {len(manifest['addons'])} addons from exact candidate run {run['id']}", flush=True)
            return 0
        print(f"Waiting for candidate bundle head {args.head[:8]}: "
              f"{run['status'] if run else 'not yet scheduled'}", flush=True)
        time.sleep(min(15, max(0, deadline - time.monotonic())))
    raise RuntimeError('The exact candidate bundle did not complete before the timeout; no release fallback')


if __name__ == '__main__':
    raise SystemExit(main())
