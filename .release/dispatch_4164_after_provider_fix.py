#!/usr/bin/env python3
"""Final 4.1.64 dispatcher after the reviewed CI-only provider setup correction.

The existing reproducible publisher performs the builds and publication.
This dispatcher never writes refs, tags, release metadata or release assets.
"""
from __future__ import annotations
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

REPO = 'wickidcow/Slimefun-Legacy'
BASE = 'd1ac11a1c6891ac6768dff935bcc8de7d149b7a5'
FIX_HEAD = '5ebeedbac5f9b91322d55635c515b41361b2b895'
MATRIX_HASH = '314d6a83931f887d7dbe17271c44327d3d39f5feb7792ab48c38a40f7a7e1f36'
BUNDLE_PATH = '.github/workflows/build-sfl-addons-compat-bundle.yml'
STACK_PATH = '.github/workflows/paper-26.3-full-stack.yml'
COMPILE_PATH = '.github/workflows/paper-26.3-addon-compile.yml'
CORE_PATH = '.github/workflows/build-ci.yml'
PUBLISHER_PATH = '.github/workflows/reproducible-release.yml'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(args):
    return subprocess.check_output(args, text=True, timeout=180)


def api(path):
    return json.loads(command(['gh', 'api', '--method', 'GET', f'repos/{REPO}/{path}']))


def jobs(number):
    result = []
    page = 1
    while True:
        data = api(f'actions/runs/{number}/jobs?filter=latest&per_page=100&page={page}')
        result.extend(data['jobs'])
        if len(result) >= data['total_count']:
            require(len(result) == data['total_count'], 'Unexpected job pagination')
            return result
        require(bool(data['jobs']), 'Missing job page')
        page += 1


def checked_runs(source, event, branch=None):
    endpoint = f'actions/runs?head_sha={source}&event={event}&per_page=100'
    if branch is not None:
        endpoint += f'&branch={branch}'
    data = api(endpoint)
    require(data['total_count'] == len(data['workflow_runs']), 'Run pagination needs explicit review')
    latest = {}
    for run in data['workflow_runs']:
        require(run['head_sha'] == source and run['event'] == event, 'Wrong filtered run')
        if branch is not None:
            require(run['head_branch'] == branch, 'Wrong run branch')
        if run['path'] not in latest or run['id'] > latest[run['path']]['id']:
            latest[run['path']] = run
    require(bool(latest), 'No source workflows found')
    for run in latest.values():
        require(run['status'] == 'completed' and run['conclusion'] == 'success', f"Workflow not passed: {run['id']} {run['name']}")
        children = jobs(run['id'])
        require(bool(children), f"No jobs for run {run['id']}")
        require(all(j['status'] == 'completed' and j['conclusion'] in ('success', 'skipped') for j in children), f"Unsuccessful child job in {run['id']}")
        run['verified_jobs'] = [{k: j.get(k) for k in ('id', 'name', 'status', 'conclusion')} for j in children]
    for path in (BUNDLE_PATH, STACK_PATH, COMPILE_PATH, CORE_PATH):
        require(path in latest, 'Missing required workflow: ' + path)
    stack = latest[STACK_PATH]['verified_jobs']
    for version in ('1.21.11', '26.2', '26.3'):
        matching = [j for j in stack if j['name'] == 'Full stack Paper ' + version]
        require(len(matching) == 1 and matching[0]['conclusion'] == 'success', 'Missing required restart lane ' + version)
    return latest


def main():
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Wrong repository')
    source = os.environ['RELEASE_SHA']
    require(re.fullmatch(r'[0-9a-f]{40}', source) is not None, 'Invalid release source')
    frozen = 'release/verified-4.1.64'
    require(api('git/ref/heads/master')['object']['sha'] == source, 'Master advanced; reconcile before publishing')
    require(api('git/ref/heads/' + frozen)['object']['sha'] == source, 'Frozen ref has wrong source')
    pr = api('pulls/303')
    require(pr['merged'] and pr['head']['sha'] == FIX_HEAD and pr['merge_commit_sha'] == source, 'Wrong merged provider-fix PR')
    original = api('pulls/300')
    require(original['merged'] and original['merge_commit_sha'] == BASE, 'Original release merge changed')
    require(api(f'git/commits/{source}')['tree']['sha'] == api(f'git/commits/{FIX_HEAD}')['tree']['sha'], 'Merged tree differs from reviewed follow-up head')
    diff = api(f'compare/{BASE}...{source}')
    require(diff['status'] == 'ahead' and diff['behind_by'] == 0, 'The original release history is not retained')
    require({f['filename'] for f in diff['files']} == {COMPILE_PATH, 'scripts/test_addon_provider_bootstrap.py', 'gradle.properties'}, 'Unexpected source change after the reviewed release')
    matrix_bytes = base64.b64decode(api(f'contents/compatibility/sfl-addon-release-matrix.json?ref={source}')['content'])
    require(hashlib.sha256(matrix_bytes).hexdigest() == MATRIX_HASH, 'Addon matrix changed')
    matrix = json.loads(matrix_bytes)
    require(matrix['bundle_revision'] == 104 and len(matrix['addons']) == 45, 'Wrong addon membership')
    properties = base64.b64decode(api(f'contents/gradle.properties?ref={source}')['content']).decode()
    require('projectVersion=4.1.64\n' in properties, 'Wrong version')
    require(api(f'contents/{PUBLISHER_PATH}?ref={source}')['sha'] == '5928b5bd190c7005931abd75b111576aac0e4d4c', 'Existing publisher changed')
    pr_runs = checked_runs(FIX_HEAD, 'pull_request')
    master_runs = checked_runs(source, 'push', 'master')
    require(PUBLISHER_PATH in master_runs, 'Master two-build reproducibility check missing')
    primary = master_runs[COMPILE_PATH]['verified_jobs']
    compiler_jobs = [j for j in primary if j['name'].startswith('Primary 26.3 compile - ')]
    require(len(compiler_jobs) == 45 and {j['name'].split(' - ', 1)[1] for j in compiler_jobs} == {r['repository'] for r in matrix['addons']}, 'Incomplete maintained compiler coverage')
    require(all(j['conclusion'] == 'success' for j in compiler_jobs), 'A maintained addon compile failed or was skipped')
    bundles = master_runs[BUNDLE_PATH]['verified_jobs']
    addon_jobs = [j for j in bundles if j['name'].startswith('Verify wickidcow/')]
    require(len(addon_jobs) == 45 and all(j['conclusion'] == 'success' for j in addon_jobs), 'Incomplete canonical addon build')
    previous = api('releases/tags/v4.1.63')
    assets = {a['name']: a for a in previous['assets']}
    require(len(previous['assets']) == len(assets) == 2, 'Previous asset set changed')
    core = assets['Slimefun-Legacy4.1.63.jar']
    bundle = assets['SF_Addons_1.21.11-26.3.zip']
    require(core['id'] == 603654347 and core['digest'] == 'sha256:993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42', 'Previous core changed')
    require(bundle['id'] == 604494714 and bundle['digest'] == 'sha256:e6ba58c00d843f445d8292435645fc3bda0902cfa38fd986fc2e6a9e3ca87ca8', 'Previous bundle changed')
    require(api('git/ref/tags/v4.1.63')['object']['sha'] == '2703e3a500b426849f3eb7806862bb4343bb9d6a', 'Previous tag changed')
    exists = subprocess.run(['gh', 'api', '--method', 'GET', f'repos/{REPO}/releases/tags/v4.1.64'], text=True, capture_output=True, timeout=45)
    require(exists.returncode != 0 and 'HTTP 404' in exists.stderr, 'Release already exists or cannot be checked')
    require(api('git/ref/heads/master')['object']['sha'] == source and api('git/ref/heads/' + frozen)['object']['sha'] == source, 'Release refs moved during validation')
    root = Path('dispatch-evidence'); root.mkdir(exist_ok=True)
    (root / 'gates.json').write_text(json.dumps({'source': source, 'original_release_merge': BASE, 'followup_head': FIX_HEAD,
        'pr_runs': list(pr_runs.values()), 'master_runs': list(master_runs.values()), 'previous_release': previous}, indent=2) + '\n')
    output = command(['gh', 'workflow', 'run', 'reproducible-release.yml', '--repo', REPO, '--ref', frozen])
    (root / 'dispatch.txt').write_text(output + '\nExisting reproducible publisher requested at ' + source + '\n')
    print('Exact-source publisher requested. Its completion and uploaded bytes require separate verification.')


if __name__ == '__main__':
    main()
