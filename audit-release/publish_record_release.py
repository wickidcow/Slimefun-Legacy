"""One-shot release orchestration; no source merges or existing asset replacements."""
from pathlib import Path
import argparse
import base64
import hashlib
import io
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

REPO = 'wickidcow/Slimefun-Legacy'
ROOT = 'repos/' + REPO
VERSION = '4.1.66'
PR_HEAD = '4252c4c628568aae26dad991592f6b3f3ff38055'
PREVIOUS = '82427167200e1b349b53c8c81f2373a5b36e340f'
REQUIRED = {318505510, 320525617, 320525618, 331523425, 331873629, 340185659,
            340188732, 353572886, 355036311, 356253128, 359172427, 361328012,
            362448329, 371105252}
OUT = Path('release-evidence')
OUT.mkdir(exist_ok=True)


def api(path, payload=None, missing=False):
    command = ['gh', 'api', ROOT + '/' + path]
    if payload is not None:
        command += ['--method', 'POST', '--input', '-']
    result = subprocess.run(command, input=None if payload is None else json.dumps(payload).encode(),
                            capture_output=True, timeout=120)
    if result.returncode:
        if missing and b'HTTP 404' in result.stderr:
            return None
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return json.loads(result.stdout) if result.stdout.strip() else None


def pages(path, field):
    collected = []
    for page in range(1, 21):
        value = api(path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')
        rows = value[field]
        collected.extend(rows)
        if len(rows) < 100:
            return collected
    raise RuntimeError('Refuse a truncated paginated response: ' + path)


def source(path, sha):
    row = api(f'contents/{path}?ref={sha}')
    return base64.b64decode(row['content'])


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def save(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n')


def artifact(run, name, sha):
    matches = [a for a in pages(f'actions/runs/{run}/artifacts', 'artifacts')
               if a['name'] == name and not a['expired']]
    assert len(matches) == 1, (run, name, len(matches))
    meta = matches[0]
    assert meta['workflow_run']['head_sha'] == sha
    raw = subprocess.run(['gh', 'api', f"{ROOT}/actions/artifacts/{meta['id']}/zip"],
                         check=True, capture_output=True, timeout=180).stdout
    assert meta['digest'] == 'sha256:' + sha256(raw), (name, 'digest')
    return meta, raw


def jobs_verified(run):
    jobs = pages(f"actions/runs/{run['id']}/jobs", 'jobs')
    assert jobs and any(j['conclusion'] == 'success' for j in jobs)
    assert all(j['status'] == 'completed' and j['conclusion'] in {'success', 'skipped'} for j in jobs), run['name']
    for job in jobs:
        assert not any(step['conclusion'] in {'failure', 'cancelled', 'timed_out'} for step in job.get('steps', [])), job['name']
    return jobs


def verify_bundle(data, sha, expected):
    with zipfile.ZipFile(io.BytesIO(data)) as bundle:
        names = bundle.namelist()
        assert bundle.testzip() is None and len(names) == len(set(names))
        manifest = json.loads(bundle.read('SF_ADDON_MANIFEST.json'))
        records = manifest['addons']
        assert manifest['core_source_commit'] == sha
        assert len(records) == len(expected) == 45
        assert {r['repository']: r['commit'] for r in records} == expected
        assert {n for n in names if n.endswith('.jar')} == {r['jar'] for r in records}
        hashes = {}
        for line in bundle.read('SHA256SUMS.txt').decode().splitlines():
            if line.strip():
                digest, name = line.split(maxsplit=1)
                hashes[name.lstrip('*')] = digest
        for row in records:
            name = row['jar']
            assert Path(name).name == name
            raw = bundle.read(name)
            assert sha256(raw) == row['sha256'] == hashes[name]
            verify_jar(raw)
        return manifest


def verify_jar(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as jar:
        assert jar.testzip() is None
        names = jar.namelist()
        assert len(names) == len(set(names))
        assert any(n in names for n in ('plugin.yml', 'paper-plugin.yml', 'paper-plugin.yaml'))
        for name in names:
            assert not name.startswith(('org/junit/', 'org/mockbukkit/', 'audit/')), name
            if name.endswith('.class') and not name.startswith('META-INF/versions/'):
                header = jar.read(name)[:8]
                assert len(header) == 8 and header[:4] == b'\xca\xfe\xba\xbe'
                assert int.from_bytes(header[6:8], 'big') <= 65 and int.from_bytes(header[4:6], 'big') != 65535, name


def previous_identity():
    release = api('releases/tags/v4.1.65')
    tag = api('git/ref/tags/v4.1.65')['object']
    assert tag['type'] == 'commit' and tag['sha'] == PREVIOUS
    assets = {a['name']: dict(id=a['id'], digest=a['digest'], size=a['size']) for a in release['assets']}
    assert assets['Slimefun-Legacy4.1.65.jar']['digest'] == 'sha256:ae23eded7426ec93f4a1177c11a6f9b1593a446ddbe4cecc970305fe81fe9c0a'
    return dict(release_id=release['id'], tag=tag['sha'], assets=assets, body=release['body'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('release_sha')
    args = parser.parse_args()
    sha = args.release_sha
    assert re.fullmatch(r'[0-9a-f]{40}', sha)
    assert api('git/ref/heads/master')['object']['sha'] == sha
    pr = api('pulls/313')
    assert pr['merged'] and pr['merge_commit_sha'] == sha and pr['head']['sha'] == PR_HEAD
    assert api(f'git/commits/{sha}')['tree']['sha'] == api(f'git/commits/{PR_HEAD}')['tree']['sha']
    assert re.search(rb'^projectVersion=4\.1\.66$', source('gradle.properties', sha), re.M)
    manifest = json.loads(source('compatibility/sfl-addon-release-matrix.json', sha))
    assert manifest['bundle_revision'] == 106
    expected = {r['repository']: r['source_commit'] for r in manifest['addons']}
    old_manifest = json.loads(source('compatibility/sfl-addon-release-matrix.json', PREVIOUS))
    assert manifest == old_manifest and len(expected) == 45
    assert api('releases/tags/v4.1.66', missing=True) is None, 'Do not replace an existing release'
    before = previous_identity()
    save('previous-release-before.json', before)
    required = REQUIRED | {331100608}
    for attempt in range(91):
        assert api('git/ref/heads/master')['object']['sha'] == sha, 'Master moved; revalidate instead of publishing'
        rows = pages(f'actions/runs?head_sha={sha}&event=push', 'workflow_runs')
        latest = {}
        for run in sorted(rows, key=lambda row: row['id']):
            if run['head_sha'] == sha and run['head_branch'] == 'master':
                latest[run['workflow_id']] = run
        failed = [r for key, r in latest.items() if key in required and r['status'] == 'completed' and r['conclusion'] != 'success']
        save('observed-workflows.json', latest)
        assert not failed, [(r['name'], r['conclusion']) for r in failed]
        pending = [key for key in required if key not in latest or latest[key]['status'] != 'completed']
        if not pending:
            break
        assert attempt < 90, ('Exact master checks remain incomplete', pending)
        print('Exact master checks pending:', pending, flush=True)
        time.sleep(20)
    verified = {}
    for workflow_id in sorted(required):
        run = latest[workflow_id]
        assert run['conclusion'] == 'success'
        jobs = jobs_verified(run)
        verified[str(workflow_id)] = dict(id=run['id'], name=run['name'], path=run['path'],
                                         head=run['head_sha'], attempt=run['run_attempt'], jobs=jobs)
    compiler = verified['355036311']['jobs']
    assert {j['name'] for j in compiler if j['name'].startswith('Primary 26.3 compile - ')} == {'Primary 26.3 compile - ' + repo for repo in expected}
    assert all(j['conclusion'] == 'success' for j in compiler if j['name'].startswith('Primary 26.3 compile - '))
    stack_jobs = verified['361328012']['jobs']
    for version in ('1.21.11', '26.2', '26.3'):
        lane = [j for j in stack_jobs if j['name'] == 'Full stack Paper ' + version]
        assert len(lane) == 1 and lane[0]['conclusion'] == 'success'
    save('verified-master-workflows.json', verified)
    report_meta, raw = artifact(verified['318505510']['id'], 'deprecation-removal-report', sha)
    with zipfile.ZipFile(io.BytesIO(raw)) as report:
        roots = [ET.fromstring(report.read(n)) for n in report.namelist() if '/TEST-' in n and n.endswith('.xml')]
        counts = {k: sum(int(r.attrib[k]) for r in roots) for k in ('tests', 'failures', 'errors', 'skipped')}
        assert counts == dict(tests=592, failures=0, errors=0, skipped=1), counts
        assert [case.attrib['name'] for root in roots for case in root.findall('testcase') if case.find('skipped') is not None] == ['migratesAndDeserializesEveryInventoryItem()']
    save('master-tests.json', dict(artifact=report_meta, counts=counts, limitation='One historical fixture skip; empty external factories are not database coverage'))
    bundle_meta, raw = artifact(verified['359172427']['id'], 'SF_Addons_1.21.11-26.3', sha)
    with zipfile.ZipFile(io.BytesIO(raw)) as outer:
        bundle_data = outer.read('SF_Addons_1.21.11-26.3.zip')
    bundle_manifest = verify_bundle(bundle_data, sha, expected)
    save('canonical-bundle.json', dict(artifact=bundle_meta, sha256=sha256(bundle_data), manifest=bundle_manifest))
    assert api('git/ref/heads/master')['object']['sha'] == sha
    assert previous_identity() == before
    publication_ref = 'publication-source/' + VERSION
    existing = api('git/ref/heads/' + publication_ref, missing=True)
    if existing is None:
        api('git/refs', dict(ref='refs/heads/' + publication_ref, sha=sha))
    else:
        assert existing['object']['sha'] == sha, 'Publication branch has other work; do not reset it'
    assert api('git/ref/heads/' + publication_ref)['object']['sha'] == sha
    api('actions/workflows/reproducible-release.yml/dispatches', dict(ref=publication_ref))
    save('dispatch-receipt.json', dict(source=sha, branch=publication_ref, existing_publisher='reproducible-release.yml', dispatched=True))
    for attempt in range(121):
        runs = pages(f'actions/workflows/reproducible-release.yml/runs?head_sha={sha}&event=workflow_dispatch', 'workflow_runs')
        selected = sorted([r for r in runs if r['head_branch'] == publication_ref], key=lambda r: r['id'])
        if selected and selected[-1]['status'] == 'completed':
            published_run = selected[-1]
            assert published_run['conclusion'] == 'success', published_run['conclusion']
            break
        assert attempt < 120, 'Publisher remains incomplete; inspect before claiming release'
        time.sleep(10)
    jobs_verified(published_run)
    release = api('releases/tags/v' + VERSION)
    assert not release['draft'] and not release['prerelease']
    assert api('git/ref/tags/v' + VERSION)['object']['sha'] == sha
    assets = {a['name']: a for a in release['assets']}
    names = {f'Slimefun-Legacy{VERSION}.jar', 'SF_Addons_1.21.11-26.3.zip'}
    assert set(assets) == names
    published = {}
    for name in sorted(names):
        asset = assets[name]
        assert asset['state'] == 'uploaded'
        url = f'https://github.com/{REPO}/releases/download/v{VERSION}/{name}'
        assert asset['browser_download_url'] == url
        target = OUT / name
        subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '3', '--max-time', '180', url, '--output', str(target)], check=True)
        data = target.read_bytes()
        assert asset['digest'] == 'sha256:' + sha256(data) and len(data) == asset['size']
        if name.endswith('.jar'):
            verify_jar(data)
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                props = jar.read('git.properties').decode()
                assert 'git.commit.id=' + sha in props and 'git.source.commit=' + sha in props
                assert 'git.build.version=' + VERSION in props
                assert re.search(r'^version:\s*[\'"]?4\.1\.66[\'"]?\s*$', jar.read('plugin.yml').decode(), re.M)
                (OUT / 'published-git.properties').write_text(props)
        else:
            assert data == bundle_data
            verify_bundle(data, sha, expected)
        published[name] = dict(id=asset['id'], sha256=sha256(data), size=len(data))
    after = previous_identity()
    assert after == before
    save('previous-release-after.json', after)
    save('published-release.json', release)
    save('publication-result.json', dict(result='PASS', source=sha, publisher_run=published_run['id'],
                                        artifacts=published, addon_count=45, bundle_revision=106,
                                        previous_release_unchanged=True, tests=counts))
    print('Published release and actual downloaded assets verified:', json.dumps(published), flush=True)


if __name__ == '__main__':
    main()
