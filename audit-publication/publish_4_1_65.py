"""Publish only the explicitly approved 4.1.65 source after independent release gates.

This temporary dispatcher does not alter master, existing releases, or the release
implementation. It invokes the repository's existing reproducible publisher.
"""
from pathlib import Path
import base64
import hashlib
import io
import json
import re
import subprocess
import sys
import time
import zipfile

REPO = 'repos/wickidcow/Slimefun-Legacy'
PR_HEAD = '6325bf812db09d6fdc914d7700b8aabf09cd51c1'
SOURCE_BRANCH = 'publication-source/4.1.65'
VERSION = '4.1.65'
BUNDLE = 'SF_Addons_1.21.11-26.3.zip'
REQUIRED = {318505510, 320525617, 320525618, 340185659, 340188732,
            331523425, 353572886, 355036311, 359172427, 361328012,
            356253128, 362448329}
EVIDENCE = Path('publication-evidence')
PUBLISHED = Path('published-files')
EVIDENCE.mkdir(exist_ok=True)
PUBLISHED.mkdir(exist_ok=True)


def api(path, method='GET', payload=None, allow_missing=False):
    command = ['gh', 'api', '--method', method, path]
    if payload is not None:
        command.extend(['--input', '-'])
    result = subprocess.run(command, input=None if payload is None else json.dumps(payload).encode(),
                            capture_output=True, timeout=180)
    if result.returncode:
        try:
            body = json.loads(result.stdout)
        except (ValueError, TypeError):
            body = {}
        if allow_missing and str(body.get('status')) == '404':
            return None
        raise RuntimeError(f'GitHub request failed: {method} {path}: {result.stderr.decode()[:800]}')
    return json.loads(result.stdout) if result.stdout.strip() else {}


def raw_api(path):
    return subprocess.run(['gh', 'api', path], capture_output=True, check=True, timeout=180).stdout


def sha(data):
    return hashlib.sha256(data).hexdigest()


def save(name, value):
    (EVIDENCE / name).write_text(json.dumps(value, indent=2) + '\n')


def release_identity(release):
    return dict(id=release['id'], tag=release['tag_name'], source=release['target_commitish'],
                assets=sorted([dict(id=a['id'], name=a['name'], size=a['size'], digest=a['digest'])
                               for a in release['assets']], key=lambda a: a['id']))


# Do not merge here. The reviewed PR must first be merged through the normal connector.
for attempt in range(180):
    pr = api(f'{REPO}/pulls/311')
    assert pr['head']['sha'] == PR_HEAD, 'Release PR changed; review the new source before publishing'
    if pr['merged']:
        break
    assert pr['state'] == 'open', 'Release PR was closed without merging'
    time.sleep(10)
assert pr['merged'], 'Release PR did not merge within the guarded window'
source = pr['merge_commit_sha']
assert re.fullmatch('[0-9a-f]{40}', source)
assert api(f'{REPO}/git/ref/heads/master')['object']['sha'] == source, 'Master advanced; preserve newer work'
commit = api(f'{REPO}/git/commits/{source}')
assert any(p['sha'] == PR_HEAD for p in commit['parents']), 'Unexpected release merge parents'
save('merged-release-source.json', dict(pr=311, head=PR_HEAD, source=source, tree=commit['tree']['sha']))

# Use the exact source being published, not the temporary dispatcher's checkout.
subprocess.run(['git', 'fetch', '--depth', '1', 'origin', source], check=True)
subprocess.run(['git', 'worktree', 'add', '--detach', 'release-source', source], check=True)
root = Path('release-source')
assert f'projectVersion={VERSION}' in (root / 'gradle.properties').read_text()
manifest = json.loads((root / 'compatibility/sfl-addon-release-matrix.json').read_text())
assert manifest['bundle_revision'] == 106 and len(manifest['addons']) == 45
expected = {a['repository']: a['source_commit'] for a in manifest['addons']}
assert len(expected) == 45
baselines = json.loads((root / 'compatibility/release-baselines.json').read_text())
assert baselines['candidate']['version'] == VERSION
assert baselines['previous_stable']['source']['ref'] == 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
assert baselines['previous_stable']['version'] == '4.1.64'
publisher = api(f'{REPO}/actions/workflows/reproducible-release.yml')
assert publisher['path'] == '.github/workflows/reproducible-release.yml'
required = REQUIRED | {publisher['id']}

# Check all required exact-master groups, including the broader full-maintained compiler.
for attempt in range(150):
    response = api(f'{REPO}/actions/runs?head_sha={source}&event=push&per_page=100')
    assert response['total_count'] <= 100, 'Run pagination needs explicit review'
    rows = [r for r in response['workflow_runs'] if r['head_branch'] == 'master']
    latest = {}
    for row in sorted(rows, key=lambda r: r['id']):
        assert row['head_sha'] == source
        latest[row['workflow_id']] = row
    failure = [r for r in latest.values() if r['status'] == 'completed' and r['conclusion'] != 'success']
    save('master-gates.json', [dict(id=r['id'], workflow_id=r['workflow_id'], name=r['name'], path=r['path'],
                                  head=r['head_sha'], status=r['status'], conclusion=r['conclusion'])
                             for r in latest.values()])
    assert not failure, [(r['id'], r['name'], r['conclusion']) for r in failure]
    if required <= set(latest) and all(r['status'] == 'completed' and r['conclusion'] == 'success' for r in latest.values()):
        break
    print(f'Exact-master checks pending: {len(required - set(latest))} missing, '
          f'{sum(r["status"] != "completed" for r in latest.values())} unfinished', flush=True)
    time.sleep(10)
assert required <= set(latest) and all(r['conclusion'] == 'success' for r in latest.values())
assert api(f'{REPO}/git/ref/heads/master')['object']['sha'] == source, 'Master changed during validation'
assert api(f'{REPO}/releases/tags/v{VERSION}', allow_missing=True) is None, 'Release already exists; do not overwrite it'
assert api(f'{REPO}/git/ref/tags/v{VERSION}', allow_missing=True) is None, 'Tag already exists; do not move it'
previous = release_identity(api(f'{REPO}/releases/tags/v4.1.64'))
save('previous-release-before.json', previous)

# A pinned branch selects the exact validated master commit even if master advances later.
reference = api(f'{REPO}/git/ref/heads/{SOURCE_BRANCH}', allow_missing=True)
if reference is None:
    reference = api(f'{REPO}/git/refs', 'POST', dict(ref='refs/heads/' + SOURCE_BRANCH, sha=source))
assert reference['object']['sha'] == source
before = api(f'{REPO}/actions/runs?head_sha={source}&event=workflow_dispatch&per_page=100')
assert not any(r['workflow_id'] == publisher['id'] for r in before['workflow_runs']), 'Publisher was already dispatched'
assert api(f'{REPO}/git/ref/heads/master')['object']['sha'] == source
response = api(f'{REPO}/actions/workflows/reproducible-release.yml/dispatches', 'POST', dict(ref=SOURCE_BRANCH))
save('dispatch.json', dict(source=source, pinned_branch=SOURCE_BRANCH, response=response))

for attempt in range(180):
    rows = api(f'{REPO}/actions/runs?head_sha={source}&event=workflow_dispatch&per_page=100')['workflow_runs']
    found = [r for r in rows if r['workflow_id'] == publisher['id'] and r['head_branch'] == SOURCE_BRANCH]
    assert len(found) <= 1, 'Ambiguous publisher runs'
    if found and found[0]['status'] == 'completed':
        break
    time.sleep(10)
assert found and found[0]['status'] == 'completed' and found[0]['conclusion'] == 'success', found
run = found[0]
save('publisher-run.json', {k: run[k] for k in ['id', 'head_sha', 'head_branch', 'path', 'event', 'status', 'conclusion']})

# Independently fetch the newly published bytes; never relabel the PR artifacts as a release.
release = api(f'{REPO}/releases/tags/v{VERSION}')
assert not release['draft'] and not release['prerelease'] and release['target_commitish'] == source
assert api(f'{REPO}/git/ref/tags/v{VERSION}')['object']['sha'] == source
names = {a['name'] for a in release['assets']}
assert names == {f'Slimefun-Legacy{VERSION}.jar', BUNDLE} and len(release['assets']) == 2
for asset in release['assets']:
    assert asset['state'] == 'uploaded'
    path = PUBLISHED / asset['name']
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '3',
                    '--max-time', '180', asset['browser_download_url'], '--output', str(path)], check=True)
    assert asset['digest'] == 'sha256:' + sha(path.read_bytes()), asset['name']
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
core_path = PUBLISHED / f'Slimefun-Legacy{VERSION}.jar'
with zipfile.ZipFile(core_path) as z:
    properties = z.read('git.properties').decode()
    assert f'git.commit.id={source}' in properties and f'git.source.commit={source}' in properties
    assert f'git.build.version={VERSION}' in properties
    assert re.search(rf'(?m)^version:\s*[\'"]?{re.escape(VERSION)}[\'"]?\s*$', z.read('plugin.yml').decode())
    (EVIDENCE / 'published-git.properties').write_text(properties)

# The final publisher's raw JAR artifact must match the public asset exactly.
artifacts = api(f"{REPO}/actions/runs/{run['id']}/artifacts?per_page=100")['artifacts']
artifact = next(a for a in artifacts if a['name'] == 'Slimefun-Legacy' and not a['expired'])
raw = raw_api(f"{REPO}/actions/artifacts/{artifact['id']}/zip")
assert artifact['digest'] == 'sha256:' + sha(raw)
with zipfile.ZipFile(io.BytesIO(raw)) as z:
    if 'plugin.yml' in z.namelist():
        jar_bytes = raw
    else:
        jars = [n for n in z.namelist() if n.endswith('.jar')]
        assert len(jars) == 1
        jar_bytes = z.read(jars[0])
assert sha(jar_bytes) == sha(core_path.read_bytes())

sys.path.insert(0, str((root / 'scripts').resolve()))
from verify_addon_bytecode import inspect_jar
reports = []
classes = 0
with zipfile.ZipFile(PUBLISHED / BUNDLE) as z:
    bundle_manifest = json.loads(z.read('SF_ADDON_MANIFEST.json'))
    assert bundle_manifest['core_source_commit'] == source
    assert len(bundle_manifest['addons']) == 45
    assert {a['repository']: a['commit'] for a in bundle_manifest['addons']} == expected
    assert {n for n in z.namelist() if n.endswith('.jar')} == {a['jar'] for a in bundle_manifest['addons']}
    checks = {line.split(None, 1)[1].strip().removeprefix('*'): line.split()[0]
              for line in z.read('SHA256SUMS.txt').decode().splitlines()}
    unpacked = Path('verified-addon-jars')
    unpacked.mkdir()
    for row in bundle_manifest['addons']:
        name = row['jar']
        assert Path(name).name == name
        data = z.read(name)
        assert sha(data) == row['sha256'] == checks[name]
        path = unpacked / name
        path.write_bytes(data)
        report = inspect_jar(path, 21)
        assert report['result'] == 'PASS', report
        classes += report['checked_classes']
        with zipfile.ZipFile(path) as jar:
            assert jar.testzip() is None
            entries = jar.namelist()
            assert len(entries) == len(set(entries))
            descriptor = next(n for n in ['plugin.yml', 'paper-plugin.yml', 'paper-plugin.yaml'] if n in entries)
            text = jar.read(descriptor).decode()
            match = re.search(r'''(?m)^version:\s*['"]?([^'"\r\n]+)''', text)
            assert match and match.group(1).strip() == row['version']
            assert not any(n.startswith(('org/junit/', 'org/mockbukkit/', 'audit/')) for n in entries)
        reports.append(dict(repository=row['repository'], commit=row['commit'], version=row['version'],
                            jar=name, sha256=sha(data), bytecode=report))
    save('published-addon-manifest.json', bundle_manifest)

subprocess.run(['python3', str(root / 'scripts/check_bytecode_target.py'), str(core_path), '--expected-java', '21'], check=True)
subprocess.run(['python3', str(root / 'scripts/verify_release_artifact.py'), str(core_path), '--root', str(root)], check=True)
after = release_identity(api(f'{REPO}/releases/tags/v4.1.64'))
assert after == previous, 'An older release changed during publication; investigate without overwriting it'
save('previous-release-after.json', after)
save('published-release.json', release)
save('verification.json', dict(result='PASS', version=VERSION, source=source, publisher_run=run['id'],
     core_sha256=sha(core_path.read_bytes()), bundle_sha256=sha((PUBLISHED / BUNDLE).read_bytes()),
     addon_revision=106, addons=reports, applicable_addon_classes=classes,
     existing_release_unchanged=True, published_bytes_downloaded_and_verified=True))
print(f'Published and independently verified {VERSION}, exact source {source}, all 45 addons; old release untouched.')
