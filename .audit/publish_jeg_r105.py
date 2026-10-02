"""One-shot revision105 publisher; the workflow runs this only after independent approval."""
from __future__ import annotations

import base64
import hashlib
import io
import json
import subprocess
import sys
import zipfile
from pathlib import Path

REPO = 'wickidcow/Slimefun-Legacy'
API = f'repos/{REPO}'
TAG = 'v4.1.64'
CORE = 'Slimefun-Legacy4.1.64.jar'
BUNDLE = 'SF_Addons_1.21.11-26.3.zip'
SOURCE = 'e25e395e216d0fbee3da5e1cf64f005d88abf756'
RELEASE_SOURCE = 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
TEST_MERGE = '0f521697310b494135eced045daf5d241b6f7105'
CORE_SHA = '316b308139e90981ed037d82ddc57b62b354d8e0a41804d5ebf3bb2b8f896d80'
OLD_ZIP_SHA = 'ce28b417b2632e99a7b7699e5e0b22e8cc677bb610f65114b3e631d7625b574f'
CANONICAL_RUN = 36957523399
RUNTIME_RUN = 36957734010
RUNTIME_HEAD = 'd1e35f4da6025542433a8dbbc41f1a3e7149b227'
JEG_SOURCE = '89ce9a8b0e8ef9d1939256f1d80917af63e439e2'
STAGE = Path('publication')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(*args):
    return subprocess.run(args, check=True, capture_output=True, timeout=180).stdout


def api(path):
    return json.loads(command('gh', 'api', path))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def source_manifest(ref):
    record = api(f'{API}/contents/compatibility/sfl-addon-release-matrix.json?ref={ref}')
    return json.loads(base64.b64decode(record['content']))


def checked_run(run_id, head, workflow):
    record = api(f'{API}/actions/runs/{run_id}')
    require(record['head_sha'] == head and record['path'] == workflow, 'Wrong validation run source')
    require(record['status'] == 'completed' and record['conclusion'] == 'success', 'Required validation has not succeeded')
    return {k: record[k] for k in ('id', 'head_sha', 'path', 'status', 'conclusion', 'run_attempt')}


def artifact(run_id, name, head):
    records = api(f'{API}/actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    found = [r for r in records if r['name'] == name and not r['expired']]
    require(len(found) == 1, f'Missing or ambiguous artifact: {name}')
    meta = found[0]
    require(meta['workflow_run']['head_sha'] == head, 'Artifact source mismatch')
    data = command('gh', 'api', f"{API}/actions/artifacts/{meta['id']}/zip")
    require(meta['digest'] == 'sha256:' + digest(data), 'Artifact digest mismatch')
    return meta, data


def release_snapshot():
    release = api(f'{API}/releases/tags/{TAG}')
    require(not release['draft'] and not release['prerelease'], 'Unexpected release state')
    assets = {a['name']: a for a in release['assets']}
    require(CORE in assets and BUNDLE in assets, 'Missing required release assets')
    core = assets[CORE]
    require(core['id'] == 604656009 and core['digest'] == 'sha256:' + CORE_SHA, 'Published core changed')
    tag = api(f'{API}/git/ref/tags/{TAG}')
    obj = tag['object']
    for _ in range(5):
        if obj['type'] == 'commit':
            break
        require(obj['type'] == 'tag', 'Unsupported tag type')
        obj = api(f"{API}/git/tags/{obj['sha']}")['object']
    require(obj['type'] == 'commit' and obj['sha'] == RELEASE_SOURCE, 'Published core tag moved')
    return release, assets, tag


def require_merged_source():
    pr = api(f'{API}/pulls/304')
    require(pr['merged'] and pr['head']['sha'] == SOURCE, 'Expected addon selection PR is not merged')
    current = api(f'{API}/git/ref/heads/master')['object']['sha']
    require(current == pr['merge_commit_sha'], 'Master advanced; reconcile rather than publish an older selection')
    return current


def prepare():
    require(not STAGE.exists(), 'Refusing to reuse a stale publication directory')
    master = require_merged_source()
    canonical = checked_run(CANONICAL_RUN, SOURCE, '.github/workflows/build-sfl-addons-compat-bundle.yml')
    runtime = checked_run(RUNTIME_RUN, RUNTIME_HEAD, '.github/workflows/jeg-r105-published-core-validation.yml')
    before = source_manifest(RELEASE_SOURCE)
    after = source_manifest(SOURCE)
    require(before['bundle_revision'] == 104 and after['bundle_revision'] == 105, 'Unexpected source revision')
    expected = {a['repository']: a['source_commit'] for a in after['addons']}
    require(len(after['addons']) == len(expected) == 45, 'Incomplete addon membership')
    require(expected['wickidcow/SF_JustEnoughGuide'] == JEG_SOURCE, 'Wrong JEG source')
    reverted = json.loads(json.dumps(after))
    reverted['bundle_revision'] = 104
    for row in reverted['addons']:
        if row['repository'] == 'wickidcow/SF_JustEnoughGuide':
            row['source_commit'] = next(a['source_commit'] for a in before['addons'] if a['repository'] == row['repository'])
    require(reverted == before, 'Unexpected non-JEG source selection change')
    meta, outer = artifact(CANONICAL_RUN, 'SF_Addons_1.21.11-26.3', SOURCE)
    with zipfile.ZipFile(io.BytesIO(outer)) as archive:
        require(archive.testzip() is None, 'Canonical wrapper CRC failure')
        data = archive.read(BUNDLE)
    _, evidence = artifact(RUNTIME_RUN, 'jeg-r105-published-core-preparation', RUNTIME_HEAD)
    with zipfile.ZipFile(io.BytesIO(evidence)) as archive:
        proof = json.loads(archive.read('bundle-verification.json'))
    require(proof['head'] == SOURCE and proof['tested_merge'] == TEST_MERGE, 'Wrong runtime source evidence')
    require(proof['bundle_sha256'] == digest(data), 'Runtime did not test these exact addon bytes')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        require(archive.testzip() is None and len(archive.namelist()) == len(set(archive.namelist())), 'Corrupt or duplicate bundle entries')
        manifest = json.loads(archive.read('SF_ADDON_MANIFEST.json'))
        require(manifest['core_source_commit'] == TEST_MERGE, 'Wrong canonical checkout')
        require(len(manifest['addons']) == 45 and {a['repository']: a['commit'] for a in manifest['addons']} == expected, 'Wrong addon manifest')
        require({n for n in archive.namelist() if n.endswith('.jar')} == {a['jar'] for a in manifest['addons']}, 'Extra or missing plugin JARs')
        for row in manifest['addons']:
            require(Path(row['jar']).name == row['jar'], 'Unsafe plugin filename')
            jar = archive.read(row['jar'])
            require(digest(jar) == row['sha256'], 'Plugin checksum mismatch')
            with zipfile.ZipFile(io.BytesIO(jar)) as plugin:
                require(plugin.testzip() is None, 'Plugin CRC failure')
                if row['repository'] == 'wickidcow/SF_JustEnoughGuide':
                    require(row['jar'] == 'SF_JustEnoughGuide2.1.70.jar', 'Wrong guide version')
                    require('com/balugaq/jeg/utils/clickhandler/OnDisplay$ItemGroup.class' in plugin.namelist(), 'Missing guide renderer')
                    require('com/balugaq/jeg/utils/GuideRuntimeClasses.class' in plugin.namelist(), 'Missing early runtime guard')
    release, assets, tag = release_snapshot()
    require(assets[BUNDLE]['id'] == 604656013 and assets[BUNDLE]['digest'] == 'sha256:' + OLD_ZIP_SHA, 'Published addon ZIP advanced; do not overwrite concurrent work')
    backup = STAGE / 'backup'
    backup.mkdir(parents=True)
    for name, expected_hash in ((CORE, CORE_SHA), (BUNDLE, OLD_ZIP_SHA)):
        command('gh', 'release', 'download', TAG, '--repo', REPO, '--pattern', name, '--dir', str(backup))
        require(digest((backup / name).read_bytes()) == expected_hash, 'Recovery asset hash mismatch')
    write_json(backup / 'release-before.json', release)
    write_json(backup / 'tag-before.json', tag)
    (STAGE / 'new').mkdir()
    (STAGE / 'new' / BUNDLE).write_bytes(data)
    receipt = dict(source=SOURCE, master=master, tag=TAG, canonical=canonical, runtime=runtime,
                   core_sha256=CORE_SHA, old_bundle_sha256=OLD_ZIP_SHA, new_bundle_sha256=digest(data),
                   canonical_artifact=meta, release_id=release['id'], old_zip_asset_id=assets[BUNDLE]['id'])
    write_json(STAGE / 'prepared.json', receipt)
    write_json(backup / 'prepared.json', receipt)
    print('Prepared exact tested addon ZIP and verified recovery assets; nothing published.')


def publish():
    prepared = json.loads((STAGE / 'prepared.json').read_text())
    require(require_merged_source() == prepared['master'], 'Master changed after preparation')
    prior = json.loads((STAGE / 'backup' / 'release-before.json').read_text())
    release, assets, tag = release_snapshot()
    require(release['id'] == prior['id'] and release['body'] == prior['body'], 'Release notes changed concurrently')
    require(assets[BUNDLE]['id'] == prepared['old_zip_asset_id'] and assets[BUNDLE]['digest'] == 'sha256:' + OLD_ZIP_SHA, 'Addon release changed concurrently')
    data = (STAGE / 'new' / BUNDLE).read_bytes()
    require(digest(data) == prepared['new_bundle_sha256'], 'Prepared file changed')
    require(digest((STAGE / 'backup' / BUNDLE).read_bytes()) == OLD_ZIP_SHA, 'Recovery ZIP changed')
    command('gh', 'release', 'upload', TAG, str(STAGE / 'new' / BUNDLE), '--repo', REPO, '--clobber')
    release, assets, final_tag = release_snapshot()
    require(final_tag['object'] == tag['object'], 'Core tag changed during addon upload')
    require(assets[BUNDLE]['digest'] == 'sha256:' + prepared['new_bundle_sha256'], 'Uploaded addon digest mismatch')
    destination = STAGE / 'downloaded'
    destination.mkdir()
    command('gh', 'release', 'download', TAG, '--repo', REPO, '--pattern', BUNDLE, '--dir', str(destination))
    require(digest((destination / BUNDLE).read_bytes()) == prepared['new_bundle_sha256'], 'Downloaded published ZIP mismatch')
    require(release['body'] == prior['body'], 'Release notes changed during upload; do not overwrite')
    notes = (prior.get('body') or '') + f'''\n\n## Addon-only refresh: revision 105\n\nThe complete 45-addon ZIP now includes JustEnoughGuide 2.1.70. All 44 other revision-104 selections remain unchanged. The published Slimefun-Legacy4.1.64.jar and v4.1.64 tag are unchanged.\n\nJEG now verifies its renderer classes before guide takeover and checks complete compiled-class membership when packaging. The original complete 2.1.69 release contained the class from the reported error and also passed menu tests; the affected server's actual installed/remapped bytes were not supplied, so no specific installation cause is claimed. Install the new raw JAR only with a full stop/replace/start and retain plugin data. No item IDs, bookmarks, data formats or guide layouts were migrated.\n\n- Selection: `{SOURCE}` (PR304); actual canonical checkout: `{TEST_MERGE}`.\n- New addon ZIP SHA-256: `{prepared['new_bundle_sha256']}`.\n- Previous addon ZIP SHA-256: `{OLD_ZIP_SHA}`.\n- Unchanged core SHA-256: `{CORE_SHA}`.\n\n[Canonical build](https://github.com/{REPO}/actions/runs/{CANONICAL_RUN}) and [published-core validation](https://github.com/{REPO}/actions/runs/{RUNTIME_RUN}) passed. The exact ZIP was tested with the actual published core: all 45 addons required for two full starts on each supported Paper line, followed by actual survival/cheat history and category rendering. Synthetic player interfaces and generated servers are not exhaustive live-player, historical-world or Folia-concurrency certification.\n\nThe prior core, ZIP and release metadata were retained in the publication workflow's recovery artifact before replacement. The uploaded ZIP was downloaded again and hash-verified; the core asset identity and tag were rechecked unchanged. Standalone SF_JustEnoughGuide2.1.70.jar is available from its addon release.\n'''
    (STAGE / 'release-notes.md').write_text(notes, encoding='utf-8')
    command('gh', 'release', 'edit', TAG, '--repo', REPO, '--notes-file', str(STAGE / 'release-notes.md'))
    final, final_assets, final_tag = release_snapshot()
    require(final['body'] == notes and final_assets[BUNDLE]['digest'] == 'sha256:' + prepared['new_bundle_sha256'], 'Final publication readback mismatch')
    require(final_tag['object'] == tag['object'], 'Tag changed during note update')
    write_json(STAGE / 'published-receipt.json', dict(**prepared, core_asset_id=final_assets[CORE]['id'],
        new_zip_asset_id=final_assets[BUNDLE]['id'], release_url=final['html_url'], published_verified=True))
    print('Published only the exact tested addon ZIP; core asset and tag remain unchanged.')


if __name__ == '__main__':
    require(len(sys.argv) == 2 and sys.argv[1] in ('prepare', 'publish'), 'Use prepare or publish explicitly')
    (prepare if sys.argv[1] == 'prepare' else publish)()
