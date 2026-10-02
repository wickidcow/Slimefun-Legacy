#!/usr/bin/env python3
"""Publish only the exact tested addon ZIP; never update core binaries or refs."""
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
HEAD = '65e6b177eae9dc2de04dee8db48c1415d07964c2'
SOURCE = '2ae2ace62ce0731a18ae2c7108dbaf4b0abfdeb1'
ADDON = 'wickidcow/SF_FluffyMachines'
ADDON_HEAD = '394de0dbfe770ae13256aa1cfbe747fc26b55a3a'
OLD_PIN = '91dc76b4e6d2024faeb1cbecace18c0a99b99aee'
TAG = 'v4.1.64'
TAG_SHA = 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
RELEASE_ID = 401503556
CORE = 'Slimefun-Legacy4.1.64.jar'
BUNDLE = 'SF_Addons_1.21.11-26.3.zip'
CORE_SHA = '316b308139e90981ed037d82ddc57b62b354d8e0a41804d5ebf3bb2b8f896d80'
OLD_SHA = 'ce28b417b2632e99a7b7699e5e0b22e8cc677bb610f65114b3e631d7625b574f'
OLD_MATRIX_SHA = '314d6a83931f887d7dbe17271c44327d3d39f5feb7792ab48c38a40f7a7e1f36'
PR_RUNS = (36958927625,36958927607,36958927621,36958927595,36958927617,
           36958927654,36958927951,36958927515,36958927618,36958927659,36958927651)
NATIVE_RUN = 36959223894
CONTROL_RUN = 36959139483


def require(value, message):
    if not value:
        raise RuntimeError(message)


def command(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True,
                          timeout=180, **kwargs).stdout


def api(endpoint, repo=REPO, payload=None):
    args=['gh','api','--method','GET' if payload is None else 'PATCH',f'repos/{repo}/{endpoint}']
    if payload is not None:
        args += ['--input','-']
    return json.loads(command(args,input=None if payload is None else json.dumps(payload)))


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def checked_run(number, sha, repo=REPO, event=None):
    run=api(f'actions/runs/{number}',repo)
    require(run['head_sha']==sha and run['status']=='completed' and run['conclusion']=='success',f'Unverified run {repo}/{number}')
    if event:
        require(run['event']==event,f'Wrong event for run {number}')
    jobs=api(f'actions/runs/{number}/jobs?per_page=100',repo)
    require(jobs['total_count']<=100 and len(jobs['jobs'])==jobs['total_count'],f'Incomplete job list for {number}')
    require(all(j['status']=='completed' and j['conclusion'] in ('success','skipped') for j in jobs['jobs']),f'Failed child job in {number}')
    if number==36958927595:
        expected={r['repository'] for r in json.loads(Path('candidate/source-matrix.json').read_text())['addons']}
        actual={j['name'].removeprefix('Verify ') for j in jobs['jobs'] if j['name'].startswith('Verify ') and j['conclusion']=='success'}
        require(actual==expected,'Canonical build did not pass every selected addon')
    return {'run':run,'jobs':jobs}


def tag_guard():
    tag=api(f'git/ref/tags/{TAG}')
    require(tag['object']['type']=='commit' and tag['object']['sha']==TAG_SHA,'Core release tag changed')
    return tag


def release_guard(release):
    require(release['id']==RELEASE_ID and release['tag_name']==TAG,'Wrong target release')
    require(not any(release.get(k) for k in ('draft','prerelease','immutable')),'Release state changed')
    assets={a['name']:a for a in release['assets']}
    require(len(release['assets'])==2 and set(assets)=={CORE,BUNDLE},'Release asset set changed')
    require(assets[CORE]['id']==604656009 and assets[CORE]['digest']=='sha256:'+CORE_SHA,'Published core changed')
    require(assets[BUNDLE]['id']==604656013 and assets[BUNDLE]['digest']=='sha256:'+OLD_SHA,'Published addon ZIP changed; reconcile concurrent work')


def gates():
    require(os.environ.get('GITHUB_REPOSITORY')==REPO,'Wrong repository')
    pr=api('pulls/306')
    require(pr['merged'] and pr['head']['sha']==HEAD,'Exact bundle PR306 is not merged')
    addon=api('pulls/11',ADDON)
    require(addon['merged'] and addon['head']['sha']==ADDON_HEAD,'Exact addon PR11 is not merged')
    reports=[checked_run(n,HEAD,event='pull_request') for n in PR_RUNS]
    reports.append(checked_run(36958416339,ADDON_HEAD,ADDON,'pull_request'))
    reports.append(checked_run(CONTROL_RUN,'302ed406ce651ad1c66636745aa525fa5be7cb53',event='push'))
    reports.append(checked_run(NATIVE_RUN,'4e9c8e4a70d4a75651cf61b37c7e5fe981326279',event='push'))
    matrix=json.loads(Path('candidate/source-matrix.json').read_text())
    previous_bytes=base64.b64decode(api(f'contents/compatibility/sfl-addon-release-matrix.json?ref={TAG_SHA}')['content'])
    require(hashlib.sha256(previous_bytes).hexdigest()==OLD_MATRIX_SHA,'Unexpected release baseline matrix')
    expected=json.loads(previous_bytes)
    require(expected['bundle_revision']==104 and len(expected['addons'])==45,'Wrong baseline member set')
    expected['bundle_revision']=105
    for row in expected['addons']:
        if row['repository']==ADDON:
            require(row['source_commit']==OLD_PIN,'Unexpected old addon pin')
            row['source_commit']=ADDON_HEAD
    require(expected==matrix,'Unrelated source, compatibility or exclusion change')
    current=json.loads(base64.b64decode(api('contents/compatibility/sfl-addon-release-matrix.json?ref=master')['content']))
    require(current==matrix,'Master addon selections changed; do not overwrite concurrent work')
    spec=importlib.util.spec_from_file_location('bundle_verifier','scripts/download_candidate_bundle.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    module.verify_bundle(Path('candidate')/BUNDLE,matrix,SOURCE)
    wanted=os.environ['EXPECTED_BUNDLE_SHA256']
    require(re.fullmatch(r'[0-9a-f]{64}',wanted) is not None,'An explicit expected ZIP digest is required')
    require(digest(Path('candidate')/BUNDLE)==wanted,'Candidate bytes do not match reviewed ZIP')
    require(digest(Path('candidate')/CORE)==CORE_SHA,'Tested core differs from published binary')
    comparison=json.loads(Path('candidate/addon-class-comparison.json').read_text())
    require(comparison['identical'] and comparison['audit_sha256']=='e6d21b955864625249146832652b90e705b3a351d5451f723925bfc5e3915b97','Native-control addon identity mismatch')
    tag_guard()
    Path('publication').mkdir(exist_ok=True)
    Path('publication/gates.json').write_text(json.dumps(reports,indent=2))
    return wanted


def download(name, folder):
    Path(folder).mkdir(parents=True,exist_ok=True)
    command(['gh','release','download',TAG,'--repo',REPO,'--pattern',name,'--dir',str(folder)])


def main():
    sha=gates()
    phase=sys.argv[1]
    if phase=='prepare':
        before=api(f'releases/{RELEASE_ID}');release_guard(before)
        Path('publication/before-release.json').write_text(json.dumps(before,indent=2))
        Path('publication/before-tag.json').write_text(json.dumps(tag_guard(),indent=2))
        download(BUNDLE,'publication/backup');download(CORE,'publication/backup')
        require(digest(Path('publication/backup')/BUNDLE)==OLD_SHA,'Recovery ZIP failed digest verification')
        require(digest(Path('publication/backup')/CORE)==CORE_SHA,'Recovery core failed digest verification')
        print('Original revision104 release retained; no assets changed.')
        return
    require(phase=='publish','Unknown publication phase')
    before=json.loads(Path('publication/before-release.json').read_text())
    current=api(f'releases/{RELEASE_ID}');release_guard(before);release_guard(current)
    for key in ('id','tag_name','target_commitish','body','updated_at'):
        require(before.get(key)==current.get(key),'Concurrent release change: '+key)
    require(digest(Path('publication/backup')/BUNDLE)==OLD_SHA,'Recovery ZIP missing or changed')
    require(digest(Path('publication/backup')/CORE)==CORE_SHA,'Recovery core missing or changed')
    tag_guard()
    command(['gh','release','upload',TAG,str(Path('candidate')/BUNDLE),'--repo',REPO,'--clobber'])
    download(BUNDLE,'publication/downloaded')
    require(digest(Path('publication/downloaded')/BUNDLE)==sha,'Uploaded ZIP did not match reviewed bytes')
    after=api(f'releases/{RELEASE_ID}')
    assets={a['name']:a for a in after['assets']}
    require(len(after['assets'])==2 and set(assets)=={CORE,BUNDLE},'Unexpected final asset set')
    require(assets[CORE]['id']==604656009 and assets[CORE]['digest']=='sha256:'+CORE_SHA,'Core changed during addon publication')
    require(assets[BUNDLE]['digest']=='sha256:'+sha,'GitHub ZIP digest mismatch')
    tag_guard()
    require(after.get('body')==before.get('body'),'Concurrent release note change; do not overwrite')
    body=before['body']
    require('## Addon-only refresh: revision 105' not in body,'Revision105 note already exists')
    body+=f'''\n\n## Addon-only refresh: revision 105\n\nThe addon ZIP is now revision105 with FluffyMachines26.2.14. All44 other addon source selections and all45 members are retained. The original Slimefun Legacy4.1.64 core binary and release tag are unchanged.\n\nFluffyMachines now refreshes positive and negative auto-crafter cache results when the provider recipe count changes, and releases cached templates when an enabled input grid is emptied. This fixes ignored late-added recipes and continued crafting from removed recipes in the tested size-changing cases. The existing shape index, crafting order, retained-template/one-shot behavior, input/output slots, 128-energy cost, stored identities and26.2.13 barrel fixes remain unchanged. Same-size in-place registry edits and uncontrolled provider concurrency are not covered by the size-based cache contract. No measured TPS improvement is claimed.\n\nFull project builds passed33 tests per supported API lane, including13 new cache-policy checks. Separate native controls reproduce the old behavior with the released26.2.13 addon and verify the corrected26.2.14 addon, actual menu items, exact crafted metadata, charge, refill behavior and saved state after a separate-process restart. The first baseline1.21.11 fixture stopped before its delayed probe finished; a phase-specific completion wait was added without changing plugin bytes or weakening assertions.\n\nThe exact canonical ZIP then passed its own native-machine and full-stack checks with the published4.1.64 core on Paper1.21.11,26.2 and26.3. Every lane requires all45 addons on both boots. Server channels are recorded in the evidence, including26.3 beta where applicable. These are scoped generated-world tests, not exhaustive populated-world, live-client, crash-atomicity or Folia-concurrency certification.\n\n- Bundle selection head: `{HEAD}` (PR306).\n- Actual canonical build checkout: `{SOURCE}`.\n- FluffyMachines source: `{ADDON_HEAD}` (addon PR11).\n- Current addon ZIP SHA-256: `{sha}`.\n- Previous revision104 ZIP SHA-256: `{OLD_SHA}`.\n- Unchanged core SHA-256: `{CORE_SHA}`.\n\n[Canonical build](https://github.com/{REPO}/actions/runs/36958927595), [old/new native controls](https://github.com/{REPO}/actions/runs/{CONTROL_RUN}) and [exact published-core/native validation](https://github.com/{REPO}/actions/runs/{NATIVE_RUN}) passed. The old ZIP/core and release metadata were backed up before replacement. The uploaded ZIP was downloaded again and matched the tested archive. No live-server data, migration setting, core binary or release tag was changed.\n'''
    api(f'releases/{RELEASE_ID}',payload={'body':body})
    final=api(f'releases/{RELEASE_ID}');require(final['body']==body,'Release-note verification failed');tag_guard()
    Path('publication/after-release.json').write_text(json.dumps(final,indent=2))
    receipt={'release':final['html_url'],'bundle_revision':105,'bundle_sha256':sha,'previous_bundle_sha256':OLD_SHA,
             'unchanged_core_sha256':CORE_SHA,'unchanged_core_asset_id':604656009,'unchanged_tag_commit':TAG_SHA,
             'new_zip_asset_id':assets[BUNDLE]['id'],'selection_head':HEAD,'canonical_source':SOURCE,'addon_source':ADDON_HEAD,
             'canonical_run':36958927595,'native_controls_run':CONTROL_RUN,'canonical_native_run':NATIVE_RUN,
             'publication_run':os.environ['GITHUB_RUN_ID'],'verification':'Downloaded uploaded ZIP matches exact tested bytes; core and tag unchanged'}
    Path('publication/receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))


if __name__=='__main__':
    main()
