#!/usr/bin/env python3
"""Publish only the independently validated revision-106 ZIP, never the core or tag."""
from __future__ import annotations
import argparse
import base64
import hashlib
import io
import json
import subprocess
import zipfile
from pathlib import Path

REPO = 'wickidcow/Slimefun-Legacy'
API = f'repos/{REPO}'
HEAD = '44b0f5bd5f53b5226ef12a4cb5c83353545c313f'
TESTED_MERGE = 'c9d91a0e29010a23cb3bfcff44ee39620ae9f723'
CORE_SOURCE = 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
CORE_SHA = '316b308139e90981ed037d82ddc57b62b354d8e0a41804d5ebf3bb2b8f896d80'
OLD_ZIP_ID = 604713027
OLD_ZIP_SHA = 'c3940c4bf4b3f9af1936656e598b7296ef0c368225652dca0e5af6e0036dbf03'
RELEASE_ID = 401503556
CORE_ID = 604656009
TAG = 'v4.1.64'
NAME = 'SF_Addons_1.21.11-26.3.zip'
CANONICAL_RUN = 36963866434
RUNTIME_RUN = 36964027857
RUNTIME_HEAD = 'e462321d43a7a4fe18f18ef5477eb0ef8ccab2cf'
NORMAL_RUNS = [36963866364,36963866424,36963866412,36963866451,36963866351,
               36963866358,36963866392,36963866428,36963866434,36963866356,36963866371]
ROOT = Path('publication')


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def command(args: list[str], *, data: str | None = None) -> bytes:
    return subprocess.run(args, input=None if data is None else data.encode(),
                          capture_output=True, check=True, timeout=180).stdout


def api(path: str) -> dict:
    return json.loads(command(['gh', 'api', f'{API}/{path}']))


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def save_json(path: Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')


def download(url: str, path: Path, expected: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    command(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '3',
             '--max-time', '150', url, '--output', str(path)])
    require(digest(path.read_bytes()) == expected, f'Download checksum mismatch: {path.name}')


def archive_for(meta: dict) -> zipfile.ZipFile:
    require(not meta['expired'], 'Required evidence expired')
    raw = command(['gh', 'api', f"{API}/actions/artifacts/{meta['id']}/zip"])
    require(meta['digest'] == 'sha256:' + digest(raw), 'Artifact checksum mismatch')
    archive = zipfile.ZipFile(io.BytesIO(raw))
    require(archive.testzip() is None, 'Artifact archive is corrupt')
    return archive


def artifacts(run_id: int) -> dict:
    result = api(f'actions/runs/{run_id}/artifacts?per_page=100')
    require(result['total_count'] == len(result['artifacts']), 'Incomplete artifact listing')
    found = {row['name']: row for row in result['artifacts']}
    require(len(found) == len(result['artifacts']), 'Ambiguous artifact names')
    return found


def require_run(run_id: int, head: str, path: str | None = None) -> dict:
    run = api(f'actions/runs/{run_id}')
    require(run['head_sha'] == head and run['status'] == 'completed'
            and run['conclusion'] == 'success', f'Required run is not successful for exact head: {run_id}')
    if path is not None:
        require(run['path'] == path, 'Unexpected workflow path')
    return {key: run[key] for key in ('id', 'head_sha', 'path', 'event', 'conclusion', 'run_attempt')}


def manifest_at(ref: str) -> dict:
    row = api(f'contents/compatibility/sfl-addon-release-matrix.json?ref={ref}')
    return json.loads(base64.b64decode(row['content']))


def pinned_records(manifest: dict, field: str) -> dict:
    records = {row['repository']: row[field] for row in manifest['addons']}
    require(len(records) == len(manifest['addons']) == 45, 'Expected exactly 45 distinct addon sources')
    return records


def current_release() -> tuple[dict, dict, dict, dict]:
    release = api(f'releases/{RELEASE_ID}')
    require(api('releases/latest')['id'] == RELEASE_ID, 'A newer core release now exists; stop and reconcile')
    require(release['tag_name'] == TAG and not release['draft'] and not release['prerelease'], 'Unexpected release identity')
    tag = api(f'git/ref/tags/{TAG}')
    require(tag['object']['type'] == 'commit' and tag['object']['sha'] == CORE_SOURCE, 'Published tag changed')
    cores = [a for a in release['assets'] if a['name'] == 'Slimefun-Legacy4.1.64.jar']
    bundles = [a for a in release['assets'] if a['name'] == NAME]
    require(len(cores) == len(bundles) == 1, 'Missing or ambiguous release assets')
    core, bundle = cores[0], bundles[0]
    require(core['id'] == CORE_ID and core['digest'] == 'sha256:' + CORE_SHA
            and core['state'] == 'uploaded', 'Published core asset changed')
    return release, tag, core, bundle


def require_merged_selection() -> dict:
    pr = api('pulls/308')
    require(pr['merged'] and pr['head']['sha'] == HEAD, 'The reviewed combined source is not merged')
    compared = api(f'compare/{HEAD}...master')
    require(compared['status'] in ('ahead', 'identical') and not compared['files'],
            'Master has different source content; do not overwrite newer work')
    selected = manifest_at(HEAD)
    require(selected['bundle_revision'] == 106 and manifest_at('master') == selected,
            'Master no longer selects this exact bundle')
    return pr


def prepare(expected_zip: str) -> None:
    pr = require_merged_selection()
    checks = [require_run(run_id, HEAD) for run_id in NORMAL_RUNS]
    require_run(RUNTIME_RUN, RUNTIME_HEAD, '.github/workflows/r106-final-published-stack.yml')
    require_run(CANONICAL_RUN, HEAD, '.github/workflows/build-sfl-addons-compat-bundle.yml')
    canonical = artifacts(CANONICAL_RUN)['SF_Addons_1.21.11-26.3']
    with archive_for(canonical) as outer:
        data = outer.read(NAME)
    require(digest(data) == expected_zip, 'Canonical ZIP differs from independently checked bytes')
    with zipfile.ZipFile(io.BytesIO(data)) as bundle:
        require(bundle.testzip() is None and len(bundle.namelist()) == len(set(bundle.namelist())), 'Corrupt or duplicate ZIP entries')
        manifest = json.loads(bundle.read('SF_ADDON_MANIFEST.json'))
        require(manifest['core_source_commit'] == TESTED_MERGE, 'Wrong canonical checkout')
        require(pinned_records(manifest, 'commit') == pinned_records(manifest_at(HEAD), 'source_commit'), 'Addon source drift')
        expected_names = {row['jar'] for row in manifest['addons']}
        require({n for n in bundle.namelist() if n.endswith('.jar')} == expected_names, 'Extra or missing plugin JAR')
        for row in manifest['addons']:
            require(Path(row['jar']).name == row['jar'], 'Unexpected nested addon path')
            payload = bundle.read(row['jar'])
            require(digest(payload) == row['sha256'], 'Addon checksum mismatch')
            with zipfile.ZipFile(io.BytesIO(payload)) as jar:
                require(jar.testzip() is None and 'plugin.yml' in jar.namelist(), 'Unusable addon archive')
                require(not any(n.startswith(('org/junit/', 'org/mockbukkit/', 'audit/')) for n in jar.namelist()), 'Test classes shipped')
            if row['repository'] == 'wickidcow/SF_JustEnoughGuide':
                require(row['version'] == '2.1.70' and row['distributable_api'] == '1.21.11-R0.1-SNAPSHOT', 'Wrong JEG build baseline')
    runtime_artifacts = artifacts(RUNTIME_RUN)
    runtime_checks = []
    for version in ('1.21.11', '26.2', '26.3'):
        meta = runtime_artifacts[f'revision106-corrected-stack-{version}']
        with archive_for(meta) as archive:
            provenance = json.loads(archive.read('provenance.json'))
            require(provenance['head'] == HEAD and provenance['canonical_run'] == CANONICAL_RUN
                    and provenance['bundle_sha256'] == expected_zip and provenance['core_sha256'] == CORE_SHA,
                    'Runtime did not test the exact release candidates')
            require(archive.read('result.txt').decode().startswith('PASS: corrected revision106'), 'Missing runtime completion')
            require(not archive.read('runtime/dependency-gated-addons.txt').strip(), 'An addon was omitted from runtime')
            require(len(archive.read('runtime/expected-addons.txt').decode().splitlines()) == 45, 'Incomplete runtime membership')
            for cycle, phase in (('first', 'initial'), ('second', 'restart')):
                log = archive.read(f'runtime/{cycle}.normalized.log').decode()
                require('R106_PLUGIN_STATE_PASS count=45 core=4.1.64 guideRenderer=linked clipboard=6' in log,
                        'Actual guide or enabled-state verification is missing')
                require(f'CRAFTER_NATIVE_PASS mode=updated phase={phase}' in log, 'Native crafter phase missing')
                require(not any(token in log for token in ('ERROR', 'SEVERE', 'CRAFTER_NATIVE_FAIL', 'R106_PLUGIN_STATE_FAIL')),
                        'Runtime contains an unapproved failure')
            runtime_checks.append(dict(version=version, artifact=meta, build=archive.read('runtime/runtime-build.txt').decode()))
    release, tag, core, old = current_release()
    require(old['id'] == OLD_ZIP_ID and old['digest'] == 'sha256:' + OLD_ZIP_SHA, 'The release ZIP changed concurrently')
    download(old['browser_download_url'], ROOT / 'backup' / NAME, OLD_ZIP_SHA)
    download(core['browser_download_url'], ROOT / 'backup' / core['name'], CORE_SHA)
    with zipfile.ZipFile(ROOT / 'backup' / NAME) as archive:
        previous = json.loads(archive.read('SF_ADDON_MANIFEST.json'))
    before, after = pinned_records(previous, 'commit'), pinned_records(manifest, 'commit')
    require(before.keys() == after.keys(), 'Membership changed from released bundle')
    require({repo for repo in before if before[repo] != after[repo]}
            == {'wickidcow/SF_JustEnoughGuide', 'wickidcow/SF_FluffyMachines'}, 'Unexpected change from released revision105')
    save_json(ROOT / 'backup' / 'release-before.json', release)
    save_json(ROOT / 'backup' / 'tag-before.json', tag)
    (ROOT / 'new').mkdir(parents=True, exist_ok=True)
    (ROOT / 'new' / NAME).write_bytes(data)
    state = dict(release=release, tag=tag, core=core, old_bundle=old, new_sha256=expected_zip,
                 canonical=canonical, normal_checks=checks, runtime_checks=runtime_checks,
                 selected_head=HEAD, tested_merge=TESTED_MERGE, merged_commit=pr['merge_commit_sha'])
    save_json(ROOT / 'prepared.json', state)
    save_json(ROOT / 'backup' / 'validation.json', state)
    print('Prepared verified addon-only replacement and recovery data; no release asset changed.')


def publish(expected_zip: str, backup_artifact: int) -> None:
    state = json.loads((ROOT / 'prepared.json').read_text())
    require(state['new_sha256'] == expected_zip and state['selected_head'] == HEAD, 'Prepared source changed')
    require(digest((ROOT / 'new' / NAME).read_bytes()) == expected_zip, 'Prepared ZIP changed')
    require(digest((ROOT / 'backup' / NAME).read_bytes()) == OLD_ZIP_SHA, 'Recovery ZIP changed')
    backup = api(f'actions/artifacts/{backup_artifact}')
    require(not backup['expired'] and backup['name'] == 'revision106-release-recovery'
            and backup['size_in_bytes'] > 1000000, 'Durable recovery artifact was not uploaded')
    require_merged_selection()
    release, tag, core, old = current_release()
    require(release['body'] == state['release']['body'] and release['updated_at'] == state['release']['updated_at'],
            'Release notes changed after backup; stop rather than overwrite concurrent work')
    fingerprint = lambda items: sorted((a['id'], a['name'], a.get('digest'), a['size']) for a in items)
    require(fingerprint(release['assets']) == fingerprint(state['release']['assets']), 'Assets changed after backup')
    require(old['id'] == OLD_ZIP_ID and old['digest'] == 'sha256:' + OLD_ZIP_SHA and tag == state['tag'], 'Core/tag/ZIP changed after backup')
    phase = 'before-upload'
    try:
        command(['gh', 'release', 'upload', TAG, str(ROOT / 'new' / NAME), '--repo', REPO, '--clobber'])
        phase = 'addon-uploaded'
        after, after_tag, after_core, uploaded = current_release()
        require(uploaded['digest'] == 'sha256:' + expected_zip and uploaded['state'] == 'uploaded', 'Uploaded asset metadata mismatch')
        download(uploaded['browser_download_url'], ROOT / 'verified' / NAME, expected_zip)
        require(after['body'] == release['body'], 'Concurrent note change detected after upload')
        notes = f'''\n\n## Addon-only refresh: combined revision 106\n\nThis ZIP combines JustEnoughGuide 2.1.70, ExtraHeads 1.0.4 and FluffyMachines 26.2.14. All 45 maintained addons are retained; the other 42 revision-104 source selections remain unchanged. The original Slimefun Legacy 4.1.64 core JAR and tag are unchanged.\n\nJEG is rebuilt against the supported 1.21.11 API after its separate 26.3 compile probe, preventing the reproduced bundle-only clipboard linkage error. The permanent check executes the actual packaged methods. FluffyMachines retains the corrected auto-crafter cache lifecycle without changing recipes, machine output, energy costs or stored identities. ExtraHeads retains the already released missing-head update.\n\n- Selection head: `{HEAD}` (PR308).\n- Actual canonical build checkout: `{TESTED_MERGE}`.\n- Addon ZIP SHA-256: `{expected_zip}`.\n- Previous revision-105 ZIP SHA-256: `{OLD_ZIP_SHA}`.\n- Unchanged core SHA-256: `{CORE_SHA}`.\n\n[Canonical build](https://github.com/{REPO}/actions/runs/{CANONICAL_RUN}) and [published-core validation](https://github.com/{REPO}/actions/runs/{RUNTIME_RUN}) passed. The exact ZIP passed two boots on Paper 1.21.11, 26.2 and 26.3 with all 45 actual enabled states/versions, real WorldEdit, guide-renderer linkage, both clipboard methods and native crafter checks. The tested 26.3 channel is recorded in the retained runtime evidence, not assumed stable. These generated-server checks do not certify every customer world, machine, live-client interaction or Folia concurrency case.\n\nThe previous ZIP/core and release metadata were preserved before replacement. The uploaded ZIP was downloaded and checksum-verified. Standalone raw JAR releases remain available in each addon repository. Stop the server, back up worlds and plugin data, and replace existing JARs rather than leaving duplicate versions. Do not install unwanted addons merely because they are included in the aggregate archive.\n'''
        require('## Addon-only refresh: combined revision 106' not in after['body'], 'Revision106 notes already exist')
        command(['gh', 'api', '--method', 'PATCH', f'{API}/releases/{RELEASE_ID}', '--input', '-'],
                data=json.dumps({'body': after['body'] + notes}))
        phase = 'notes-updated'
        final, final_tag, final_core, final_bundle = current_release()
        require(final['body'] == after['body'] + notes and final_bundle['digest'] == 'sha256:' + expected_zip,
                'Final release verification failed')
        require(final_tag == tag and fingerprint([final_core]) == fingerprint([core]), 'Published core or tag changed')
        other_before = [a for a in release['assets'] if a['name'] != NAME]
        other_after = [a for a in final['assets'] if a['name'] != NAME]
        require(fingerprint(other_before) == fingerprint(other_after), 'An unrelated release asset changed')
        save_json(ROOT / 'receipt.json', dict(result='published-and-verified', phase=phase, release_id=RELEASE_ID,
                  old_bundle=old, new_bundle=final_bundle, core=final_core, tag=final_tag,
                  backup_artifact=backup_artifact, selection=HEAD, tested_merge=TESTED_MERGE,
                  canonical_run=CANONICAL_RUN, runtime_run=RUNTIME_RUN))
        print('Published only the verified revision106 addon ZIP; original core, tag and other assets unchanged.')
    except Exception as error:
        save_json(ROOT / 'failure.json', dict(phase=phase, error=str(error), backup_artifact=backup_artifact,
                  recovery='Backup is retained. Do not blindly overwrite a concurrent release change.'))
        raise


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('prepare', 'publish'))
    parser.add_argument('--expected-zip-sha256', required=True)
    parser.add_argument('--backup-artifact-id', type=int)
    args = parser.parse_args()
    require(len(args.expected_zip_sha256) == 64 and all(c in '0123456789abcdef' for c in args.expected_zip_sha256), 'Invalid checksum')
    if args.phase == 'prepare':
        prepare(args.expected_zip_sha256)
    else:
        require(args.backup_artifact_id is not None, 'Recovery artifact is required before publication')
        publish(args.expected_zip_sha256, args.backup_artifact_id)


if __name__ == '__main__':
    main()
