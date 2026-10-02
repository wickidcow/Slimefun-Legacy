"""Guarded addon-only publication, executed after separately verified release and runtime tests."""
import hashlib
import io
import json
import os
import subprocess
import zipfile
from pathlib import Path

REPO = 'wickidcow/Slimefun-Legacy'
API = 'repos/' + REPO
VERSION = '4.1.66'
CORE_SOURCE = '22ae22c4e32fd3b086565922f4427206d0f4b065'
HEAD = '974e55dc763aa3e3e55fc2c4547abeebcd0dff16'
MERGE_UNDER_TEST = '72aea0f45c434f43d23774f92597c60bfbe90fc8'
NEW_ZIP_SHA = 'e5715441fea9d47e80dc20a649effb9e5107ec686bfa16afe42ee2c1ae261afb'
CORE_NAME = 'Slimefun-Legacy4.1.66.jar'
ZIP_NAME = 'SF_Addons_1.21.11-26.3.zip'
CANONICAL_RUN = 37064167645
FINALIZER_RUN = 37064718699
RUNTIME_RUN = int(os.environ['PUBLISHED_CORE_RUNTIME_RUN'])
ROOT = Path('publication')
BACKUP = ROOT / 'backup'
EVIDENCE = ROOT / 'evidence'
NEW = ROOT / 'new'
for p in [BACKUP, EVIDENCE, NEW]:
    p.mkdir(parents=True, exist_ok=True)


def digest(b):
    return hashlib.sha256(b).hexdigest()


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n')


def api(path, method='GET', body=None):
    command = ['gh', 'api', '--method', method, path]
    if body is not None:
        command += ['--input', '-']
    result = subprocess.run(command, input=json.dumps(body).encode() if body is not None else None,
                            capture_output=True, check=True, timeout=120)
    return json.loads(result.stdout) if result.stdout.strip() else None


def pages(path, key):
    result = []
    for page in range(1, 21):
        body = api(path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')
        result += body[key]
        if len(body[key]) < 100:
            assert len(result) == body['total_count'], path
            return result
    raise AssertionError('Refusing incomplete pagination: ' + path)


def artifact(run_id, name):
    run = api(f'{API}/actions/runs/{run_id}')
    assert run['status'] == 'completed' and run['conclusion'] == 'success', (run_id, run['conclusion'])
    matching = [a for a in pages(f'{API}/actions/runs/{run_id}/artifacts', 'artifacts')
                if a['name'] == name and not a['expired']]
    assert len(matching) == 1, (run_id, name, len(matching))
    meta = matching[0]
    raw = subprocess.run(['gh', 'api', f"{API}/actions/artifacts/{meta['id']}/zip"],
                         capture_output=True, check=True, timeout=120).stdout
    assert meta['digest'] == 'sha256:' + digest(raw), name
    archive = zipfile.ZipFile(io.BytesIO(raw))
    assert archive.testzip() is None
    assert len(archive.namelist()) == len(set(archive.namelist()))
    return run, meta, archive


def assets(release):
    assert not release['draft'] and not release['prerelease']
    assert release['target_commitish'] == CORE_SOURCE
    assert {a['name'] for a in release['assets']} == {CORE_NAME, ZIP_NAME}
    return {a['name']: a for a in release['assets']}


def identity(a):
    return [a['id'], a['name'], a['digest'], a['size'], a['state']]


def download_release(destination):
    subprocess.run(['gh', 'release', 'download', 'v' + VERSION, '--repo', REPO,
                    '--dir', str(destination), '--pattern', CORE_NAME, '--pattern', ZIP_NAME],
                   check=True, timeout=180)


def unpack_manifest(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
        m = json.loads(z.read('SF_ADDON_MANIFEST.json'))
        assert len(m['addons']) == 45
        assert {n for n in z.namelist() if n.endswith('.jar')} == {a['jar'] for a in m['addons']}
        assert len({a['repository'] for a in m['addons']}) == 45
        for a in m['addons']:
            assert Path(a['jar']).name == a['jar'] and digest(z.read(a['jar'])) == a['sha256']
        for line in z.read('SHA256SUMS.txt').decode().splitlines():
            if line.strip():
                expected, name = line.split(None, 1)
                assert digest(z.read(name.lstrip('*').strip())) == expected
        return m


def prepare():
    pr = api(f'{API}/pulls/314')
    assert pr['merged'] and pr['state'] == 'closed' and pr['head']['sha'] == HEAD
    release = api(f'{API}/releases/tags/v{VERSION}')
    old_assets = assets(release)
    tag = api(f'{API}/git/ref/tags/v{VERSION}')
    write(BACKUP / 'release-before.json', release)
    write(BACKUP / 'tag-before.json', tag)
    download_release(BACKUP)
    for name, asset in old_assets.items():
        b = (BACKUP / name).read_bytes()
        assert len(b) == asset['size'] and asset['digest'] == 'sha256:' + digest(b)
    run, meta, receipt_archive = artifact(FINALIZER_RUN, 'release-4.1.66-exact-source-verification')
    receipt = json.loads(receipt_archive.read('publication-result.json'))
    assert receipt['source'] == CORE_SOURCE and receipt['version'] == VERSION
    assert receipt['core_sha256'] == digest((BACKUP / CORE_NAME).read_bytes())
    assert receipt['bundle_sha256'] == digest((BACKUP / ZIP_NAME).read_bytes())
    write(EVIDENCE / 'core-publication-receipt.json', receipt)
    run, meta, outer = artifact(CANONICAL_RUN, ZIP_NAME[:-4])
    assert run['head_sha'] == HEAD and run['path'] == '.github/workflows/build-sfl-addons-compat-bundle.yml'
    raw = outer.read(ZIP_NAME)
    assert digest(raw) == NEW_ZIP_SHA
    (NEW / ZIP_NAME).write_bytes(raw)
    new_manifest = unpack_manifest(raw)
    old_manifest = unpack_manifest((BACKUP / ZIP_NAME).read_bytes())
    assert new_manifest['core_source_commit'] == MERGE_UNDER_TEST
    assert old_manifest['core_source_commit'] == CORE_SOURCE
    before = {a['repository']: a for a in old_manifest['addons']}
    after = {a['repository']: a for a in new_manifest['addons']}
    assert before.keys() == after.keys()
    changed = {name for name in before if before[name]['commit'] != after[name]['commit']}
    assert changed == {'wickidcow/SF_HotbarPets', 'wickidcow/SF_SMG'}
    assert after['wickidcow/SF_HotbarPets']['commit'] == 'ac1e2213686a83acd52d82c01434ab12b48e64f3'
    assert after['wickidcow/SF_SMG']['commit'] == '014a7e55a832255e6dd7fc4677b05480624a0fd7'
    assert after['wickidcow/SF_HotbarPets']['version'] == '1.0.3'
    assert after['wickidcow/SF_SMG']['version'] == '1.0.5'
    checks = pages(f'{API}/actions/runs?head_sha={HEAD}&event=pull_request', 'workflow_runs')
    latest = {}
    for r in checks:
        if r['path'] not in latest or r['id'] > latest[r['path']]['id']:
            latest[r['path']] = r
    assert len(latest) >= 11
    assert all(r['status'] == 'completed' and r['conclusion'] == 'success' for r in latest.values())
    write(EVIDENCE / 'pr-workflows.json', [{k:r[k] for k in ('id','path','head_sha','status','conclusion')}
                                        for r in latest.values()])
    runtime_evidence = []
    for version in ('1.21.11', '26.2', '26.3'):
        runtime, item, archive = artifact(RUNTIME_RUN, 'r107-published-core-' + version)
        assert runtime['path'] == '.github/workflows/r107-published-core-runtime.yml'
        provenance = json.loads(archive.read('provenance.json'))
        assert provenance['candidate_head'] == HEAD
        assert provenance['published_core_source'] == CORE_SOURCE
        assert provenance['bundle_sha256'] == NEW_ZIP_SHA
        assert provenance['core_sha256'] == receipt['core_sha256']
        assert 'PASS:' in archive.read('result.txt').decode()
        smoke = archive.read('runtime/smoke-result.txt').decode()
        for required in ('PASS', 'Slimefun Legacy: ' + VERSION, 'Cycles: 2',
                         'Required-enable addon JARs: 45', 'Dependency-gated addon JARs: 0',
                         'Clean shutdown persistence: observed on second boot'):
            assert required in smoke, (version, required)
        runtime_evidence.append({'minecraft': version, 'artifact': item, 'result': smoke})
    write(EVIDENCE / 'runtime-verification.json', runtime_evidence)
    (BACKUP / 'release-notes-before.md').write_text(release['body'] or '')
    write(EVIDENCE / 'prepared.json', dict(version=VERSION, pr_head=HEAD, pr_merge=pr['merge_commit_sha'],
        core_source=CORE_SOURCE, core_sha256=receipt['core_sha256'], canonical_run=CANONICAL_RUN,
        canonical_checkout=MERGE_UNDER_TEST, runtime_run=RUNTIME_RUN, new_zip_sha256=NEW_ZIP_SHA,
        old_zip_sha256=digest((BACKUP / ZIP_NAME).read_bytes()), preserved_members=45, changed_addons=sorted(changed),
        release_id=release['id']))
    print('All prerequisites and backups verified; no release assets changed yet.', flush=True)


def publish():
    prepared = json.loads((EVIDENCE / 'prepared.json').read_text())
    prior = json.loads((BACKUP / 'release-before.json').read_text())
    old = assets(prior)
    tag_before = json.loads((BACKUP / 'tag-before.json').read_text())
    current = api(f'{API}/releases/tags/v{VERSION}')
    current_assets = assets(current)
    assert current['id'] == prior['id'] and current['body'] == prior['body']
    assert {k:identity(a) for k,a in current_assets.items()} == {k:identity(a) for k,a in old.items()}
    assert api(f'{API}/git/ref/tags/v{VERSION}')['object'] == tag_before['object']
    assert digest((NEW / ZIP_NAME).read_bytes()) == NEW_ZIP_SHA
    assert os.environ.get('BACKUP_UPLOAD_ID'), 'Backup upload did not report an artifact ID'
    write(EVIDENCE / 'backup-receipt.json', dict(artifact_id=os.environ['BACKUP_UPLOAD_ID']))
    subprocess.run(['gh','release','upload','v'+VERSION,str(NEW/ZIP_NAME),'--repo',REPO,'--clobber'],
                   check=True,timeout=180)
    after_upload = api(f'{API}/releases/tags/v{VERSION}')
    updated = assets(after_upload)
    assert identity(updated[CORE_NAME]) == identity(old[CORE_NAME])
    assert updated[ZIP_NAME]['digest'] == 'sha256:' + NEW_ZIP_SHA
    assert api(f'{API}/git/ref/tags/v{VERSION}')['object'] == tag_before['object']
    assert after_upload['body'] == prior['body'], 'Notes changed concurrently; refusing to overwrite them'
    note = f'''\n\n## Addon-only refresh: revision 107\n\nThe complete 45-addon ZIP now includes HotbarPets 1.0.3 and SimpleMaterialGenerators 1.0.5. All 43 other source selections and all exclusions are unchanged. The original Slimefun Legacy 4.1.66 core JAR and release tag remain unchanged. Both standalone raw JAR releases already exist.\n\nThe two selected sources passed 24 project tests (13 HotbarPets, 11 SMG), separate API compilation on 1.21.11/26.2/26.3 and byte-identical independent baseline builds against the 4.1.66 core. Baseline rebuilt JARs match their published standalone files. Canonical bundle JARs differ only in embedded compile-dependency POM metadata; classes and runtime resources are unchanged.\n\n- Selection head: `{HEAD}` (PR314).\n- Canonical build checkout: `{MERGE_UNDER_TEST}`.\n- Current addon ZIP SHA-256: `{NEW_ZIP_SHA}`.\n- Previous revision-106 ZIP SHA-256: `{prepared['old_zip_sha256']}`.\n- Unchanged core SHA-256: `{prepared['core_sha256']}`.\n\n[Canonical build](https://github.com/{REPO}/actions/runs/{CANONICAL_RUN}) and [published-core validation](https://github.com/{REPO}/actions/runs/{RUNTIME_RUN}) passed. The identical archive was tested with the actual published 4.1.66 core on two boots each of Paper 1.21.11, 26.2 and 26.3, with all 45 addons required and real WorldEdit/WorldEditSlimefun providers. These are generated startup/restart and project fixtures, not exhaustive customer-world, connected-player, crash or Folia-concurrency certification.\n\nThe previous ZIP, core and release metadata were backed up before replacement; uploaded bytes were downloaded again and verified. Back up world/plugin data, test the matching core and intended addons on a disposable copy, and replace existing JARs without leaving duplicate old/new versions.\n'''
    (EVIDENCE / 'release-note-append.md').write_text(note)
    api(f"{API}/releases/{prior['id']}", method='PATCH', body={'body':(prior['body'] or '')+note})
    verified = ROOT / 'downloaded'
    verified.mkdir()
    download_release(verified)
    result_release = api(f'{API}/releases/tags/v{VERSION}')
    result_assets = assets(result_release)
    for name,a in result_assets.items():
        data = (verified/name).read_bytes()
        assert len(data) == a['size'] and a['digest'] == 'sha256:' + digest(data)
    assert digest((verified/ZIP_NAME).read_bytes()) == NEW_ZIP_SHA
    assert digest((verified/CORE_NAME).read_bytes()) == prepared['core_sha256']
    assert identity(result_assets[CORE_NAME]) == identity(old[CORE_NAME])
    assert api(f'{API}/git/ref/tags/v{VERSION}')['object'] == tag_before['object']
    write(EVIDENCE / 'release-after.json', result_release)
    write(EVIDENCE / 'publication-result.json', dict(prepared, result='PASS: addon ZIP replaced and downloaded/verified',
        backup_artifact_id=os.environ['BACKUP_UPLOAD_ID'], published_zip_asset=result_assets[ZIP_NAME],
        core_asset_unchanged=True,release_tag_unchanged=True))
    print('Addon revision107 publication and downloaded artifacts verified; core/tag unchanged.',flush=True)

if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['prepare']:
        prepare()
    elif sys.argv[1:] == ['publish']:
        publish()
    else:
        raise SystemExit('Expected prepare or publish')
