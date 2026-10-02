#!/usr/bin/env python3
"""One-time addon-only publisher. No core upload, tag write or branch update."""
from __future__ import annotations
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = 'wickidcow/Slimefun-Legacy'
HEAD = '9da61742de73978c0ed108227f3d3318598dda08'
SOURCE = '9131a89fbfafcec5c32146d052d2f9960f35a949'
TINKER_HEAD = 'bdb2ba51ad946de686c50dc1745ba89053b4535d'
VALIDATION_HEAD = '07135e26e20c767172da580eb946895983f8716e'
VALIDATION_RUN = 36949120294
CORE = 'Slimefun-Legacy4.1.63.jar'
ZIP = 'SF_Addons_1.21.11-26.3.zip'
TAG = 'v4.1.63'
TAG_SHA = '2703e3a500b426849f3eb7806862bb4343bb9d6a'
CORE_HASH = '993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42'
OLD_HASH = '28a09e13888c2fbe5f3761bf6c7fa24288e8ae100e3623a25f2513a5df44a3f8'
RUNS = (36947441624, 36947441543, 36947441664, 36947441538, 36947441581,
        36947441575, 36947441592, 36947441591, 36947441565, 36947441586, 36947441576)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(args: list[str], **kwargs) -> str:
    return subprocess.run(args, check=True, text=True, capture_output=True,
                          timeout=180, **kwargs).stdout


def api(endpoint: str, payload: dict | None = None, repo: str = REPO):
    args = ['gh', 'api', '--method', 'GET' if payload is None else 'PATCH',
            f'repos/{repo}/{endpoint}']
    if payload is not None:
        args += ['--input', '-']
    return json.loads(command(args, input=None if payload is None else json.dumps(payload)))


def check_run(number: int, head: str, repo: str = REPO) -> None:
    run = api(f'actions/runs/{number}', repo=repo)
    require(run['head_sha'] == head and run['status'] == 'completed'
            and run['conclusion'] == 'success', f'Exact run {repo}/{number} has not passed')


def original_release_guard(release: dict) -> None:
    require(release['id'] == 401149535 and release['tag_name'] == TAG, 'Wrong release')
    require(not any(release.get(k) for k in ('draft', 'prerelease', 'immutable')), 'Release state changed')
    assets = {a['name']: a for a in release['assets']}
    require(len(assets) == len(release['assets']) and set(assets) == {CORE, ZIP}, 'Release asset set changed')
    require(assets[CORE]['id'] == 603654347 and assets[CORE]['digest'] == 'sha256:' + CORE_HASH, 'Published core changed')
    require(assets[ZIP]['id'] == 604420179 and assets[ZIP]['digest'] == 'sha256:' + OLD_HASH, 'Published ZIP changed; retain concurrent work')


def tag_guard() -> dict:
    tag = api(f'git/ref/tags/{TAG}')
    require(tag['object']['type'] == 'commit' and tag['object']['sha'] == TAG_SHA, 'Published tag changed')
    return tag


def gates() -> str:
    require(os.environ['GITHUB_REPOSITORY'] == REPO, 'Wrong repository')
    pr = api('pulls/301')
    require(pr['merged'] and pr['head']['sha'] == HEAD, 'Exact bundle PR301 is not merged')
    addon = api('pulls/4', repo='wickidcow/SF_SlimeTinkerIE2')
    require(addon['merged'] and addon['head']['sha'] == TINKER_HEAD, 'Exact addon PR4 is not merged')
    check_run(36947237160, TINKER_HEAD, 'wickidcow/SF_SlimeTinkerIE2')
    for number in RUNS:
        check_run(number, HEAD)
    check_run(VALIDATION_RUN, VALIDATION_HEAD)
    matrix = json.loads(Path('candidate/source-matrix.json').read_text())
    old = json.loads(Path('candidate/previous-matrix.json').read_text())
    require(old['bundle_revision'] == 103 and matrix['bundle_revision'] == 104, 'Unexpected revision')
    require(len(matrix['addons']) == len(old['addons']) == 45, 'Wrong member count')
    expected = json.loads(json.dumps(old))
    expected['bundle_revision'] = 104
    for row in expected['addons']:
        if row['repository'] == 'wickidcow/SF_SlimeTinkerIE2':
            row['source_commit'] = TINKER_HEAD
    require(expected == matrix, 'Unexpected addon, compatibility or exclusion change')
    current = api('contents/compatibility/sfl-addon-release-matrix.json?ref=master')
    require(json.loads(base64.b64decode(current['content'])) == matrix, 'Master selections changed; do not publish over concurrent work')
    spec = importlib.util.spec_from_file_location('bundle_verifier', 'scripts/download_candidate_bundle.py')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    verifier.verify_bundle(Path('candidate') / ZIP, matrix, SOURCE)
    expected_hash = os.environ['EXPECTED_ZIP_SHA256']
    require(re.fullmatch(r'[0-9a-f]{64}', expected_hash) is not None, 'Expected hash must be explicit')
    require(digest(Path('candidate') / ZIP) == expected_hash, 'Candidate ZIP changed')
    require(digest(Path('candidate') / CORE) == CORE_HASH, 'Tested core changed')
    tag_guard()
    return expected_hash


def download(name: str, folder: str) -> None:
    Path(folder).mkdir(parents=True, exist_ok=True)
    command(['gh', 'release', 'download', TAG, '--repo', REPO, '--pattern', name, '--dir', folder])


def main() -> None:
    new_hash = gates()
    phase = sys.argv[1]
    Path('publication').mkdir(exist_ok=True)
    if phase == 'prepare':
        before = api('releases/401149535')
        original_release_guard(before)
        Path('publication/before-release.json').write_text(json.dumps(before, indent=2))
        Path('publication/before-tag.json').write_text(json.dumps(tag_guard(), indent=2))
        download(ZIP, 'publication/backup')
        download(CORE, 'publication/backup')
        require(digest(Path('publication/backup') / ZIP) == OLD_HASH, 'Recovery ZIP checksum mismatch')
        require(digest(Path('publication/backup') / CORE) == CORE_HASH, 'Recovery core checksum mismatch')
        print('Revision103 recovery copies verified. No release bytes changed.')
        return
    require(phase == 'publish', 'Unknown phase')
    before = json.loads(Path('publication/before-release.json').read_text())
    current = api('releases/401149535')
    original_release_guard(before)
    original_release_guard(current)
    for key in ('id', 'tag_name', 'target_commitish', 'body', 'updated_at'):
        require(before.get(key) == current.get(key), 'Concurrent release change: ' + key)
    require(digest(Path('publication/backup') / ZIP) == OLD_HASH, 'Recovery ZIP missing or altered')
    require(digest(Path('publication/backup') / CORE) == CORE_HASH, 'Recovery core missing or altered')
    tag_guard()
    command(['gh', 'release', 'upload', TAG, str(Path('candidate') / ZIP), '--repo', REPO, '--clobber'])
    download(ZIP, 'publication/downloaded')
    require(digest(Path('publication/downloaded') / ZIP) == new_hash, 'Uploaded ZIP verification failed')
    after = api('releases/401149535')
    assets = {a['name']: a for a in after['assets']}
    require(set(assets) == {CORE, ZIP} and len(after['assets']) == 2, 'Unexpected published asset set')
    require(assets[CORE]['id'] == 603654347 and assets[CORE]['digest'] == 'sha256:' + CORE_HASH, 'Core changed during publication')
    require(assets[ZIP]['digest'] == 'sha256:' + new_hash, 'GitHub ZIP digest mismatch')
    tag_guard()
    require(after.get('body') == before.get('body'), 'Concurrent release-note change; do not overwrite')
    body = before['body']
    require('## Addon-only refresh: revision 104' not in body, 'Revision104 note already present')
    body += f'''\n\n## Addon-only refresh: revision 104\n\nThis newer addon ZIP supersedes the revision103 archive described above. SlimeTinker is now 2.0.7; all44 other revision103 source selections and all45 maintained members are retained, including BetterChests1.0.3, BuildingStaff1.0.35, FluffyMachines26.2.13 and Networks1.0.47. The original4.1.63 core JAR and tag are unchanged.\n\nSlimeTinker's final generated-name/lore setters use native Adventure components; tool state, traits, progression, modifier costs, durability, item IDs, typed data keys and recipes remain outside this cleanup. The13 permanent rendering tests passed on each of the1.21.11,26.2 and26.3 API lanes. The JAR is baseline-built for Java21. Separate native generated tool/armour fixtures exercised the actual rebuild operations, owner separation, typed data, native serialization and a second process restart. These are not exhaustive historical-world, live-player combat or Folia-concurrency tests.\n\n- Selection head: `{HEAD}` (PR301).\n- Actual canonical build checkout: `{SOURCE}`.\n- SlimeTinker selected source: `{TINKER_HEAD}` (addon PR4).\n- Current addon ZIP SHA-256: `{new_hash}`.\n- Previous revision103 ZIP SHA-256: `{OLD_HASH}`.\n- Unchanged core SHA-256: `{CORE_HASH}`.\n\n[Canonical build](https://github.com/{REPO}/actions/runs/36947441591), [combined stack checks](https://github.com/{REPO}/actions/runs/36947441538) and [published-core/native-item validation](https://github.com/{REPO}/actions/runs/{VALIDATION_RUN}) passed. The exact archive passed with all45 addons required on two boots each of Paper1.21.11,26.2 and26.3 using the published4.1.63 core. Actual server build channels are retained in the evidence rather than treating beta as stable.\n\nThe previous ZIP/core and release metadata were backed up before replacement. The new published ZIP was downloaded again and checksum-verified. No core binary, tag, live world or migration setting was changed. Back up your server and replace existing addon JARs rather than keeping duplicate versions.\n'''
    api('releases/401149535', {'body': body})
    final = api('releases/401149535')
    require(final['body'] == body, 'Release notes verification failed')
    tag_guard()
    Path('publication/after-release.json').write_text(json.dumps(final, indent=2))
    receipt = {
        'release': final['html_url'], 'bundle_revision': 104, 'bundle_sha256': new_hash,
        'previous_bundle_sha256': OLD_HASH, 'unchanged_core_sha256': CORE_HASH,
        'unchanged_core_asset_id': 603654347, 'unchanged_tag_commit': TAG_SHA,
        'new_zip_asset_id': assets[ZIP]['id'], 'source_head': HEAD, 'build_source': SOURCE,
        'slimetinker_source': TINKER_HEAD, 'canonical_run': 36947441591,
        'native_validation_run': VALIDATION_RUN, 'native_validation_head': VALIDATION_HEAD,
        'publication_run': os.environ['GITHUB_RUN_ID'],
        'verification': 'Uploaded ZIP downloaded and matched; published core and tag unchanged'
    }
    Path('publication/receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
