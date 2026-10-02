import hashlib, io, json, os, re, subprocess, sys, time, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
from urllib.parse import quote

REPO = 'wickidcow/Slimefun-Legacy'
API = 'repos/' + REPO
SHA = os.environ['RELEASE_SOURCE']
assert re.fullmatch(r'[0-9a-f]{40}', SHA)
VERSION = '4.1.67'
REF = 'publication-source/' + VERSION
BUNDLE_NAME = 'SF_Addons_1.21.11-26.3.zip'
CORE_NAME = 'Slimefun-Legacy' + VERSION + '.jar'
evidence = Path('evidence')
output = Path('release-files')

def api(path, body=None, missing=False):
    cmd = ['gh', 'api', '--method', 'GET' if body is None else 'POST', path]
    if body is not None:
        cmd += ['--input', '-']
    result = subprocess.run(cmd, input=None if body is None else json.dumps(body).encode(),
        capture_output=True, timeout=120)
    if result.returncode:
        try:
            error = json.loads(result.stdout)
        except (ValueError, UnicodeError):
            error = {}
        if missing and str(error.get('status')) == '404':
            return None
        raise RuntimeError(f'GitHub request failed for {path}: {result.stderr.decode(errors="replace")}')
    return json.loads(result.stdout) if result.stdout.strip() else None

def pages(path, key):
    collected = []
    for page in range(1, 21):
        row = api(path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')
        items = row[key]
        collected += items
        if len(items) < 100:
            assert len(collected) == row['total_count'], (path, len(collected), row['total_count'])
            return collected
    raise RuntimeError('Unexpected pagination overflow: ' + path)

def write(name, data):
    (evidence / name).write_text(json.dumps(data, indent=2) + '\n')

def sha(data):
    return hashlib.sha256(data).hexdigest()

def artifact(run_id, name):
    matches = [a for a in pages(f'{API}/actions/runs/{run_id}/artifacts', 'artifacts')
        if a['name'] == name and not a['expired']]
    assert len(matches) == 1, (name, len(matches))
    item = matches[0]
    assert item['workflow_run']['head_sha'] == SHA
    raw = subprocess.run(['gh', 'api', f"{API}/actions/artifacts/{item['id']}/zip"],
        capture_output=True, check=True, timeout=120).stdout
    assert item['digest'] == 'sha256:' + sha(raw), name
    archive = zipfile.ZipFile(io.BytesIO(raw))
    assert archive.testzip() is None
    assert len(archive.namelist()) == len(set(archive.namelist()))
    return item, archive

source = json.loads(Path('compatibility/sfl-addon-release-matrix.json').read_text())
assert source['bundle_revision'] == 107 and len(source['addons']) == 45
expected = {a['repository']: a['source_commit'] for a in source['addons']}
assert len(expected) == 45
assert 'projectVersion=' + VERSION in Path('gradle.properties').read_text()
assert subprocess.check_output(['git', 'rev-parse', 'HEAD:.github/workflows/reproducible-release.yml'],
    text=True).strip() == '5928b5bd190c7005931abd75b111576aac0e4d4c'
assert api(f'{API}/git/ref/heads/master')['object']['sha'] == SHA
assert api(f'{API}/git/ref/heads/{REF}')['object']['sha'] == SHA

previous = api(f'{API}/releases/tags/v4.1.66')
previous_tag = api(f'{API}/git/ref/tags/v4.1.66')
asset_identity = lambda release: sorted((a['id'], a['name'], a['size'], a.get('digest'), a['state'])
    for a in release['assets'])
previous_identity = asset_identity(previous)
write('previous-release-before.json', previous)
write('previous-tag-before.json', previous_tag)
assert previous['target_commitish'] == '22ae22c4e32fd3b086565922f4427206d0f4b065'
assert previous_tag['object']['sha'] == '22ae22c4e32fd3b086565922f4427206d0f4b065'
old_assets = {a['name']: a for a in previous['assets']}
assert old_assets['Slimefun-Legacy4.1.66.jar']['digest'] == 'sha256:514730d71bd276b5ddcd6f46a665605be122e568f838458f66527ad396c22649'
assert old_assets[BUNDLE_NAME]['digest'] == 'sha256:e5715441fea9d47e80dc20a649effb9e5107ec686bfa16afe42ee2c1ae261afb'
assert hashlib.sha256(Path('compatibility/sfl-addon-release-matrix.json').read_bytes()).hexdigest() == '9f55ffcc4a7b32b58a522772a067080a5974341e4ec4cdb2c7046518fea93da2'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() == SHA


required_names = {
    'Build Slimefun Legacy', 'Public API Compatibility', 'Slimefun Compatibility',
    'Paper 26.3 Primary Preflight', 'Paper 26.3 Maintained Addon Compile',
    'Build SF Addons 1.21.11-26.3 Bundle', 'Paper 26.2 / 26.3 Full Stack Smoke',
    'Reproducible Release'}
MANUAL_WORKFLOWS = {
    '.github/workflows/api-compatibility.yml': 'Public API Compatibility',
    '.github/workflows/compatibility-ci.yml': 'Slimefun Compatibility',
    '.github/workflows/paper-26.3-preflight.yml': 'Paper 26.3 Primary Preflight',
}
# Reuse existing exact-source runs; dispatch each missing API gate at most once.
manual_before = pages(f'{API}/actions/runs?head_sha={SHA}&branch={quote(REF, safe="")}&event=workflow_dispatch', 'workflow_runs')
manual_requests = []
for workflow in MANUAL_WORKFLOWS:
    matches = [r for r in manual_before if r['path'] == workflow]
    if matches:
        chosen = max(matches, key=lambda r: r['id'])
        assert chosen['head_sha'] == SHA and chosen['head_branch'] == REF
        assert chosen['event'] == 'workflow_dispatch'
        assert chosen['status'] != 'completed' or chosen['conclusion'] == 'success', chosen
        manual_requests.append(dict(workflow=workflow, reused_run=chosen['id']))
    else:
        api(f"{API}/actions/workflows/{workflow.rsplit('/',1)[-1]}/dispatches", {'ref': REF})
        manual_requests.append(dict(workflow=workflow, requested_source=SHA))
write('manual-gates.json', manual_requests)

latest = {}
for attempt in range(120):
    runs = pages(f'{API}/actions/runs?head_sha={SHA}&branch=master&event=push', 'workflow_runs')
    manual = pages(f'{API}/actions/runs?head_sha={SHA}&branch={quote(REF, safe="")}&event=workflow_dispatch', 'workflow_runs')
    runs += [r for r in manual if r['path'] in MANUAL_WORKFLOWS]
    latest = {}
    for run in runs:
        assert run['head_sha'] == SHA and ((run['head_branch'] == 'master' and run['event'] == 'push') or (run['head_branch'] == REF and run['event'] == 'workflow_dispatch' and run['path'] in MANUAL_WORKFLOWS))
        if run['name'] not in latest or run['id'] > latest[run['name']]['id']:
            latest[run['name']] = run
    state = [{k: run.get(k) for k in ('id', 'name', 'path', 'head_sha', 'status', 'conclusion', 'run_attempt', 'event', 'head_branch')}
        for run in latest.values()]
    write('master-gates.json', state)
    failed = [r for r in state if r['status'] == 'completed' and r['conclusion'] != 'success']
    assert not failed, failed
    if len(latest) >= 15 and required_names <= set(latest) and all(
            run['status'] == 'completed' and run['conclusion'] == 'success' for run in latest.values()):
        break
    if attempt % 4 == 0:
        print('Master gates:', {r['name']: r['status'] for r in state}, flush=True)
    time.sleep(15)
else:
    raise RuntimeError('Exact-master release gates did not complete within the bounded check window')

jobs = {}
for name, run in latest.items():
    items = pages(f"{API}/actions/runs/{run['id']}/jobs?filter=latest", 'jobs')
    assert items and all(j['status'] == 'completed' and j['conclusion'] in ('success', 'skipped') for j in items), name
    jobs[name] = [{k: j.get(k) for k in ('id', 'name', 'status', 'conclusion')} for j in items]
write('master-child-jobs.json', jobs)
compiler = {j['name']: j['conclusion'] for j in jobs['Paper 26.3 Maintained Addon Compile']}
canonical = {j['name']: j['conclusion'] for j in jobs['Build SF Addons 1.21.11-26.3 Bundle']}
for repo in expected:
    assert compiler.get('Primary 26.3 compile - ' + repo) == 'success', repo
    assert canonical.get('Verify ' + repo) == 'success', repo
assert sum(n.startswith('Primary 26.3 compile - ') for n in compiler) == 45
stack_jobs = {j['name']: j['conclusion'] for j in jobs['Paper 26.2 / 26.3 Full Stack Smoke']}
for version in ('1.21.11', '26.2', '26.3'):
    assert stack_jobs.get('Full stack Paper ' + version) == 'success', version

report_meta, report = artifact(latest['Build Slimefun Legacy']['id'], 'deprecation-removal-report')
counts = dict(tests=0, failures=0, errors=0, skipped=0)
skipped = []
for name in report.namelist():
    if name.endswith('.xml') and 'TEST-' in name:
        root = ET.fromstring(report.read(name))
        for key in counts:
            counts[key] += int(root.attrib.get(key, 0))
        skipped += [c.attrib for c in root.findall('.//testcase') if c.find('skipped') is not None]
assert counts == dict(tests=604, failures=0, errors=0, skipped=1), counts
assert len(skipped) == 1 and skipped[0]['classname'].endswith('DatabasePatchV3RealDatabaseTest')
lock_reports = [ET.fromstring(report.read(name)) for name in report.namelist()
    if name.endswith('ScopedLockConcurrencyTest.xml')]
assert len(lock_reports) == 1
assert int(lock_reports[0].attrib['tests']) == 12
assert all(int(lock_reports[0].attrib.get(k, 0)) == 0 for k in ('failures', 'errors', 'skipped'))

write('actual-core-test-summary.json', dict(artifact=report_meta, counts=counts, skipped=skipped))

bundle_meta, outer = artifact(latest['Build SF Addons 1.21.11-26.3 Bundle']['id'], BUNDLE_NAME[:-4])
bundle = outer.read(BUNDLE_NAME)
with zipfile.ZipFile(io.BytesIO(bundle)) as z:
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    manifest = json.loads(z.read('SF_ADDON_MANIFEST.json'))
    assert manifest['core_source_commit'] == SHA and len(manifest['addons']) == 45
    assert {a['repository']: a['commit'] for a in manifest['addons']} == expected
    assert {n for n in z.namelist() if n.endswith('.jar')} == {a['jar'] for a in manifest['addons']}
    for a in manifest['addons']:
        assert Path(a['jar']).name == a['jar'] and sha(z.read(a['jar'])) == a['sha256']
        with zipfile.ZipFile(io.BytesIO(z.read(a['jar']))) as jar:
            assert jar.testzip() is None and len(jar.namelist()) == len(set(jar.namelist()))
            assert not any(n.startswith(('org/junit/', 'org/mockbukkit/', 'audit/')) for n in jar.namelist())
            descriptors = [n for n in ('plugin.yml', 'paper-plugin.yml', 'paper-plugin.yaml') if n in jar.namelist()]
            assert descriptors
            for descriptor in descriptors:
                match = re.search(r"(?m)^version:\s*['\"]?([^'\"\r\n]+)", jar.read(descriptor).decode())
                assert match and match.group(1).strip() == a['version']
            for name in jar.namelist():
                if name.endswith('.class') and not name.startswith('META-INF/versions/'):
                    header = jar.read(name)[:8]
                    assert header[:4] == b'\xca\xfe\xba\xbe'
                    assert int.from_bytes(header[6:8], 'big') <= 65 and int.from_bytes(header[4:6], 'big') != 65535
            if a['repository'] == 'wickidcow/SF_JustEnoughGuide':
                assert 'com/balugaq/jeg/utils/clickhandler/OnDisplay$ItemGroup.class' in jar.namelist()

    for line in z.read('SHA256SUMS.txt').decode().splitlines():
        if line.strip():
            digest, name = line.split(None, 1)
            assert sha(z.read(name.lstrip('*').strip())) == digest
    write('canonical-manifest.json', manifest)
bundle_digest = sha(bundle)
write('canonical-bundle.json', dict(artifact=bundle_meta, inner_sha256=bundle_digest))

for version in ('1.21.11', '26.2', '26.3'):
    meta, archive = artifact(latest['Paper 26.2 / 26.3 Full Stack Smoke']['id'], 'full-stack-runtime-' + version)
    names = [n for n in archive.namelist() if n.endswith('smoke-result.txt')]
    assert len(names) == 1
    result = archive.read(names[0]).decode()
    for text in ('PASS', 'Slimefun Legacy: ' + VERSION, 'Required-enable addon JARs: 45',
            'Dependency-gated addon JARs: 0', 'Cycles: 2', 'Clean shutdown persistence: observed on second boot'):
        assert text in result, (version, text)
    logs = [n for n in archive.namelist() if n.endswith('.normalized.log')]
    assert len(logs) == 2
    for name in logs:
        text = archive.read(name).decode()
        errors = re.findall(r'^.*(?: ERROR\]|SEVERE|Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|InvalidConfigurationException).*$', text, re.M)
        assert not errors, (version, errors[:5])
        (evidence / (version + '-' + Path(name).name)).write_text(text)
    write('runtime-' + version + '.json', dict(artifact=meta, result=result))

# Dispatch only the pre-existing publisher at the verified exact-source ref.
# This branch never changes the release source, release notes or publishing workflow.
assert api(f'{API}/git/ref/heads/master')['object']['sha'] == SHA
assert api(f'{API}/git/ref/heads/{REF}')['object']['sha'] == SHA
existing = api(f'{API}/releases/tags/v{VERSION}', missing=True)
assert existing is None, 'Release already exists; refuse an automatic replacement'
dispatch_time = time.time()
api(f'{API}/actions/workflows/reproducible-release.yml/dispatches', {'ref': REF})
write('dispatch.json', dict(source=SHA, ref=REF, dispatched_at=dispatch_time,
    workflow='reproducible-release.yml', previous_assets_unchanged=True))
publisher = None
for attempt in range(120):
    runs = pages(f'{API}/actions/runs?head_sha={SHA}&branch={quote(REF, safe="")}&event=workflow_dispatch', 'workflow_runs')
    matching = [r for r in runs if r['path'] == '.github/workflows/reproducible-release.yml']
    if matching:
        publisher = max(matching, key=lambda r: r['id'])
        if publisher['status'] == 'completed':
            assert publisher['conclusion'] == 'success', publisher['conclusion']
            break
    time.sleep(10)
else:
    raise RuntimeError('Publisher has not completed; no publication success is asserted')
write('publisher.json', {k: publisher[k] for k in ('id', 'head_sha', 'head_branch', 'status', 'conclusion', 'path')})
release = api(f'{API}/releases/tags/v{VERSION}')
assert not release['draft'] and not release['prerelease'] and release['target_commitish'] == SHA
assert {a['name'] for a in release['assets']} == {CORE_NAME, BUNDLE_NAME}
subprocess.run(['gh', 'release', 'download', 'v' + VERSION, '--repo', REPO,
    '--dir', str(output), '--pattern', CORE_NAME, '--pattern', BUNDLE_NAME], check=True, timeout=180)
for a in release['assets']:
    raw = (output / a['name']).read_bytes()
    assert len(raw) == a['size'] and a['digest'] == 'sha256:' + sha(raw), a['name']
assert sha((output / BUNDLE_NAME).read_bytes()) == bundle_digest
with zipfile.ZipFile(output / CORE_NAME) as z:
    assert z.testzip() is None
    assert re.search(r'^version:\s*' + re.escape(VERSION) + r'\s*$', z.read('plugin.yml').decode(), re.M)
    props = z.read('git.properties').decode()
    assert 'git.commit.id=' + SHA in props and 'git.source.commit=' + SHA in props
    assert 'com/xzavier0722/mc/plugin/slimefun4/storage/controller/ScopedLock$LockEntry.class' in z.namelist()

    for name in z.namelist():
        assert not name.startswith(('org/junit/', 'org/mockbukkit/', 'audit/'))
        if name.endswith('.class') and not name.startswith('META-INF/versions/'):
            data = z.read(name)
            assert data[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(data[6:8], 'big') <= 65
            assert int.from_bytes(data[4:6], 'big') != 65535
    (evidence / 'published-git.properties').write_text(props)
after = api(f'{API}/releases/tags/v4.1.66')
assert asset_identity(after) == previous_identity
assert api(f'{API}/git/ref/tags/v4.1.66')['object'] == previous_tag['object']
write('previous-release-after.json', after)
write('published-release.json', release)
result = dict(version=VERSION, source=SHA, source_workflows=len(latest), master_push_workflows=sum(r['event']=='push' for r in latest.values()), extra_manual_workflows=sum(r['event']=='workflow_dispatch' for r in latest.values()),
    primary_compiler_addons=45, canonical_addons=45, core_tests=counts,
    publisher_run=publisher['id'], previous_release_unchanged=True,
    core_sha256=sha((output / CORE_NAME).read_bytes()), bundle_sha256=bundle_digest,
    result='PASS: published assets downloaded and verified')
write('publication-result.json', result)
print(json.dumps(result, indent=2), flush=True)
