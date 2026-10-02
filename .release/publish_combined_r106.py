#!/usr/bin/env python3
"""One-shot addon-only publication. Never uploads a core or modifies a Git ref."""
from __future__ import annotations
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

REPO = 'wickidcow/Slimefun-Legacy'
HEAD = '44b0f5bd5f53b5226ef12a4cb5c83353545c313f'
CHECKOUT = 'c9d91a0e29010a23cb3bfcff44ee39620ae9f723'
TAG = 'v4.1.64'
TAG_COMMIT = 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
RELEASE_ID = 401503556
CORE = 'Slimefun-Legacy4.1.64.jar'
ZIP = 'SF_Addons_1.21.11-26.3.zip'
CORE_SHA = '316b308139e90981ed037d82ddc57b62b354d8e0a41804d5ebf3bb2b8f896d80'
OLD_ZIP_SHA = 'c394b68f13cdaf67f1011d90c89f04adbaed3749ab9eacea9d6f39415e4c207e'
NEW_ZIP_SHA = '65a4f9ac5d05b97be58f032d8e7c09cd2ac4f15f004f75274965b7323f243f3d'
FINAL_RUN = 36999969800
FINAL_HEAD = '03fefea528fc8a1923fef60a010438f9c61922dc'
INPUT_ARTIFACT = 11223880431
INPUT_SHA = '4c8b226c588f3fe9a437e6c631c2ab5a30cc5c4af011b198a359e1975e93a8de'
PR_RUNS = (36963866434, 36963866356, 36963866371, 36963866451,
           36963866364, 36963866412, 36963866424, 36963866392,
           36963866428, 36963866351, 36963866358)
SELECTIONS = {
    'wickidcow/SF_JustEnoughGuide': ('2.1.70', '89ce9a8b0e8ef9d1939256f1d80917af63e439e2'),
    'wickidcow/SF_ExtraHeads': ('1.0.4', '72e7d9bc3ee2ac01bd75b147df39f018bffe544d'),
    'wickidcow/SF_FluffyMachines': ('26.2.14', '394de0dbfe770ae13256aa1cfbe747fc26b55a3a'),
}


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def command(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, timeout=180, **kwargs).stdout


def api(path, repo=REPO):
    return json.loads(command(['gh', 'api', '--method', 'GET', f'repos/{repo}/{path}']))


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + '\n')


def matrix(ref):
    item = api(f'contents/compatibility/sfl-addon-release-matrix.json?ref={ref}')
    return json.loads(base64.b64decode(item['content']))


def tag_guard():
    ref = api('git/ref/tags/' + TAG)
    require(ref['object']['type'] == 'commit' and ref['object']['sha'] == TAG_COMMIT,
            'Published core tag changed')
    return ref


def release_guard(release, expected_id=604713027, expected_sha=OLD_ZIP_SHA):
    require(release['id'] == RELEASE_ID and release['tag_name'] == TAG, 'Wrong release')
    require(not any(release.get(k) for k in ('draft', 'prerelease', 'immutable')), 'Release state changed')
    assets = {a['name']: a for a in release['assets']}
    require(len(release['assets']) == 2 and set(assets) == {CORE, ZIP}, 'Release asset set changed')
    require(assets[CORE]['id'] == 604656009 and assets[CORE]['digest'] == 'sha256:' + CORE_SHA,
            'Published core asset changed')
    require(assets[ZIP]['digest'] == 'sha256:' + expected_sha, 'Published ZIP changed; retain concurrent work')
    if expected_id is not None:
        require(assets[ZIP]['id'] == expected_id, 'Published ZIP identity changed')
    return assets


def jobs(number):
    result = []
    for page in range(1, 21):
        data = api(f'actions/runs/{number}/jobs?per_page=100&page={page}')
        result.extend(data['jobs'])
        if len(result) >= data['total_count']:
            return result
    raise RuntimeError('Unexpected job pagination')


def run_guard(number, head, event):
    run = api(f'actions/runs/{number}')
    require(run['head_sha'] == head and run['event'] == event
            and run['status'] == 'completed' and run['conclusion'] == 'success',
            f'Exact validation run {number} did not pass')
    return run


def gates():
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Wrong repository')
    pr = api('pulls/308')
    require(pr['merged'] and pr['head']['sha'] == HEAD, 'Exact combined PR308 is not merged')
    merged = pr['merge_commit_sha']
    require(api('git/ref/heads/master')['object']['sha'] == merged,
            'Master moved; reconcile concurrent work before publication')
    require(api('git/commits/' + merged)['tree']['sha'] == api('git/commits/' + CHECKOUT)['tree']['sha'],
            'Merged tree differs from the tested checkout')
    selected = matrix(merged)
    expected = matrix(TAG_COMMIT)
    require(expected['bundle_revision'] == 104 and selected['bundle_revision'] == 106, 'Unexpected source revision')
    expected['bundle_revision'] = 106
    for item in expected['addons']:
        if item['repository'] in SELECTIONS:
            item['source_commit'] = SELECTIONS[item['repository']][1]
    require(expected == selected and len(selected['addons']) == 45, 'Unrelated source selections changed')
    runs = [run_guard(n, HEAD, 'pull_request') for n in PR_RUNS]
    runs.append(run_guard(FINAL_RUN, FINAL_HEAD, 'push'))
    native_jobs = jobs(FINAL_RUN)
    require(len(native_jobs) == 4 and all(j['conclusion'] == 'success' for j in native_jobs),
            'Incomplete published-core runtime lanes')
    compile_jobs = jobs(36963866424)
    lanes = [j for j in compile_jobs if j['name'].startswith('Primary 26.3 compile - ')]
    # PR mode intentionally uses the required tier plus BuildingStaff, not the full master matrix.
    contract = api(f'contents/compatibility/addon-compatibility-matrix.json?ref={HEAD}')
    compatibility = json.loads(base64.b64decode(contract['content']))
    required_targets = {a['repository'] for a in compatibility['addons']
                        if a.get('enabled', False) and a.get('tier') == 'required'
                        and a.get('compile_enabled', True)}
    required_targets.add('wickidcow/SF_BuildingStaff')
    actual_targets = {j['name'].removeprefix('Primary 26.3 compile - ') for j in lanes}
    require(actual_targets == required_targets and len(lanes) == len(required_targets)
            and all(j['conclusion'] == 'success' for j in lanes), 'Incomplete declared PR addon compilation')
    require(all(any(s['name'] == 'Compile addon against candidate stack' and s['conclusion'] == 'success'
                    for s in j.get('steps', [])) for j in lanes), 'An addon compile step was not successful')
    canonical_jobs = jobs(36963866434)
    require(len(canonical_jobs) >= 45 and all(j['conclusion'] in ('success', 'skipped') for j in canonical_jobs),
            'Canonical child job failed')
    require(sum(s['name'] == 'Compile against Paper 26.3 candidate' and s['conclusion'] == 'success'
                for j in canonical_jobs for s in j.get('steps', [])) == 45, 'Incomplete canonical45-addon compilation')
    require(sum(s['name'] == 'Rebuild JEG against the supported API floor' and s['conclusion'] == 'success'
                for j in canonical_jobs for s in j.get('steps', [])) == 1, 'JEG floor packaging not verified')
    tag_guard()
    return dict(merged=merged, selection=selected, runs=runs, native_jobs=native_jobs,
                required_pr_compile_targets=sorted(required_targets),
                addon_compile_jobs=compile_jobs, canonical_jobs=canonical_jobs)


def verify_candidate(selected):
    path = Path('candidate') / ZIP
    require(sha(path.read_bytes()) == NEW_ZIP_SHA, 'Candidate archive changed')
    require(sha((Path('candidate') / CORE).read_bytes()) == CORE_SHA, 'Tested core changed')
    with zipfile.ZipFile(path) as z:
        require(z.testzip() is None and len(z.namelist()) == len(set(z.namelist())), 'Invalid candidate ZIP')
        manifest = json.loads(z.read('SF_ADDON_MANIFEST.json'))
        require(manifest['core_source_commit'] == CHECKOUT, 'Wrong canonical checkout')
        expected = {a['repository']: a['source_commit'] for a in selected['addons']}
        require(len(manifest['addons']) == 45 and {a['repository']: a['commit'] for a in manifest['addons']} == expected,
                'Wrong source pins')
        sums = dict((line.split(maxsplit=1)[1].lstrip('*'), line.split(maxsplit=1)[0])
                    for line in z.read('SHA256SUMS.txt').decode().splitlines() if line.strip())
        names = {a['jar'] for a in manifest['addons']}
        require(names == set(sums) == {n for n in z.namelist() if n.endswith('.jar')}, 'Wrong member set')
        for a in manifest['addons']:
            raw = z.read(a['jar'])
            require(sha(raw) == a['sha256'] == sums[a['jar']], 'JAR checksum mismatch')
            with zipfile.ZipFile(io.BytesIO(raw)) as j:
                require(j.testzip() is None, 'Invalid inner JAR')
                if a['repository'] in SELECTIONS:
                    require((a['version'], a['commit']) == SELECTIONS[a['repository']], 'Requested addon changed')
                if a['repository'] == 'wickidcow/SF_JustEnoughGuide':
                    require(a['distributable_api'] == '1.21.11-R0.1-SNAPSHOT', 'Wrong JEG binary baseline')
        write_json('publication/manifest.json', manifest)


def download_asset(asset, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    command(['curl', '-fLsS', '--retry', '3', '--max-time', '180', asset['browser_download_url'], '-o', str(destination)])
    require('sha256:' + sha(destination.read_bytes()) == asset['digest'], 'Public download checksum mismatch')


def main():
    phase = sys.argv[1]
    Path('publication').mkdir(exist_ok=True)
    checks = gates()
    if phase == 'prepare':
        Path('candidate').mkdir(exist_ok=True)
        meta = api(f'actions/artifacts/{INPUT_ARTIFACT}')
        require(meta['workflow_run']['id'] == FINAL_RUN and meta['workflow_run']['head_sha'] == FINAL_HEAD,
                'Wrong validation input artifact')
        raw = command(['gh', 'api', f'repos/{REPO}/actions/artifacts/{INPUT_ARTIFACT}/zip'])
        require(sha(raw) == INPUT_SHA and meta['digest'] == 'sha256:' + INPUT_SHA, 'Validation input digest changed')
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            require(z.testzip() is None, 'Invalid validation artifact')
            (Path('candidate') / ZIP).write_bytes(z.read('addons.zip'))
            (Path('candidate') / CORE).write_bytes(z.read('core.jar'))
            Path('publication/validation-provenance.json').write_bytes(z.read('provenance.json'))
        verify_candidate(checks['selection'])
        before = api(f'releases/{RELEASE_ID}')
        assets = release_guard(before)
        write_json('publication/before-release.json', before)
        write_json('publication/before-tag.json', tag_guard())
        write_json('publication/gates.json', checks)
        for name in (ZIP, CORE):
            download_asset(assets[name], Path('publication/backup') / name)
        print('All gates passed; exact revision105 recovery assets downloaded. Nothing published yet.')
        return
    require(phase == 'publish', 'Unknown phase')
    verify_candidate(checks['selection'])
    before = json.loads(Path('publication/before-release.json').read_text())
    current = api(f'releases/{RELEASE_ID}')
    release_guard(current)
    require(all(current.get(k) == before.get(k) for k in ('id', 'body', 'updated_at', 'target_commitish')),
            'Concurrent release metadata change')
    require(sha((Path('publication/backup') / ZIP).read_bytes()) == OLD_ZIP_SHA, 'Recovery ZIP missing or changed')
    require(sha((Path('publication/backup') / CORE).read_bytes()) == CORE_SHA, 'Recovery core missing or changed')
    command(['gh', 'release', 'upload', TAG, str(Path('candidate') / ZIP), '--repo', REPO, '--clobber'])
    after = api(f'releases/{RELEASE_ID}')
    assets = release_guard(after, None, NEW_ZIP_SHA)
    download_asset(assets[ZIP], Path('publication/downloaded') / ZIP)
    require((Path('publication/downloaded') / ZIP).read_bytes() == (Path('candidate') / ZIP).read_bytes(),
            'Uploaded ZIP differs from the tested candidate')
    tag_guard()
    require(after['body'] == before['body'], 'Concurrent release notes change; do not overwrite')
    note = f'''\n\n## Addon-only refresh: combined revision 106\n\nThe current addon ZIP supersedes the revision105 ExtraHeads-only archive described above. It now includes **ExtraHeads1.0.4, JustEnoughGuide2.1.70 and FluffyMachines26.2.14 together**. All45 maintained addons remain included; the other42 source selections remain unchanged from revision104. The Slimefun Legacy4.1.64 core binary and tag are unchanged.\n\nExtraHeads retains the completed normal-mob head coverage. JEG includes renderer recovery plus the corrected baseline-built packaged JAR: the26.3 compile probe remains, but the distributed guide is rebuilt against1.21.11 and its actual clipboard methods are exercised. FluffyMachines corrects positive/negative auto-crafter cache invalidation on recipe-count changes and clears empty-grid templates without changing item IDs, crafting rules, energy use or saved data.\n\nThe exact archive passed all11 corrected-head PR workflow groups, including their declared required-tier compile targets and the full45-addon canonical compilation, plus independent published-core tests on Paper1.21.11,26.2 and26.3. On both boots each lane required all45 actual enabled plugin versions, real WorldEdit, JEG renderer linkage and six real clipboard scenarios, plus29 initial/36 separate-process restart crafter assertions. The disposable assertion's old enum reference was corrected without altering the core, addon ZIP or native crafter probe. This is scoped generated-world coverage, not every historical world, player interaction, future API or Folia concurrency guarantee.\n\n- Selected PR308 head: `{HEAD}`.\n- Actual canonical build checkout: `{CHECKOUT}`.\n- Merged selection: `{checks['merged']}`.\n- Current ZIP SHA256: `{NEW_ZIP_SHA}`.\n- Previous revision105 ZIP SHA256: `{OLD_ZIP_SHA}`.\n- Unchanged core SHA256: `{CORE_SHA}`.\n\nCanonical build36963866434 and final published-core/native validation36999969800 passed. Publication retained verified recovery copies of the old ZIP, core and release metadata before replacing only the addon ZIP. The uploaded ZIP was downloaded again and matched the tested bytes. No production server, world data or migration setting was changed. Stop the server and keep backups before replacing existing plugin JARs; do not keep duplicate versions.\n'''
    require('## Addon-only refresh: combined revision 106' not in before['body'], 'Combined release note already exists')
    payload = json.dumps({'body': before['body'] + note}).encode()
    command(['gh', 'api', '--method', 'PATCH', f'repos/{REPO}/releases/{RELEASE_ID}', '--input', '-'], input=payload)
    final = api(f'releases/{RELEASE_ID}')
    release_guard(final, assets[ZIP]['id'], NEW_ZIP_SHA)
    require(final['body'] == before['body'] + note, 'Release notes did not persist')
    tag_guard()
    write_json('publication/after-release.json', final)
    write_json('publication/receipt.json', dict(
        release=final['html_url'], bundle_revision=106, zip_sha256=NEW_ZIP_SHA,
        previous_zip_sha256=OLD_ZIP_SHA, zip_asset_id=assets[ZIP]['id'],
        unchanged_core_asset_id=604656009, unchanged_core_sha256=CORE_SHA,
        unchanged_tag_commit=TAG_COMMIT, selection_head=HEAD, canonical_checkout=CHECKOUT,
        merged=checks['merged'], canonical_run=36963866434, validation_run=FINAL_RUN,
        validation_head=FINAL_HEAD, publication_run=os.environ['GITHUB_RUN_ID'],
        downloaded_public_bytes_match=True, addons=SELECTIONS))
    print('Revision106 addon ZIP published and independently downloaded; core and tag unchanged.')


if __name__ == '__main__':
    main()
