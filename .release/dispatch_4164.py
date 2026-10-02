#!/usr/bin/env python3
"""Dispatch the existing publisher only after exact reviewed release gates.

This script neither publishes assets nor changes repository refs. The
existing reproducible-release workflow owns both builds and publication.
"""
from __future__ import annotations
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

REPO='wickidcow/Slimefun-Legacy'
PR_HEAD='b708e6c8eab266c00e65441bc0f44c9670f14610'
PR_RUNS=(36950771795,36950771806,36950771845,36950771826,36950771914,
         36950771794,36950771827,36950771920,36950771797,36950771800,
         36950771846,36950771931,36950771872,36950771902)
SCOPE_RUN=36951226978
SCOPE_HEAD='bf9faa6f634e9e70d246c8adb5e57d8ef2956a2d'
MATRIX_SHA256='314d6a83931f887d7dbe17271c44327d3d39f5feb7792ab48c38a40f7a7e1f36'
REQUIRED_MASTER_NAMES={
    'Build Slimefun Legacy', 'Build SF Addons 1.21.11-26.3 Bundle',
    'Paper 26.2 / 26.3 Full Stack Smoke', 'Public API Compatibility',
    'Slimefun Compatibility', 'Paper/Purpur 1.21.11 Compatibility',
    'Paper 26.2 Runtime Smoke', 'Paper 26.3 Primary Preflight',
    'Paper 26.3 Maintained Addon Compile', 'Purpur/Folia/Leaf 26.2 Runtime Smoke',
}


def require(condition, message):
    if not condition:raise RuntimeError(message)


def command(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True,timeout=120,**kwargs).stdout


def api(endpoint):
    return json.loads(command(['gh','api','--method','GET',f'repos/{REPO}/{endpoint}']))


def checked_run(number, source, event):
    run=api(f'actions/runs/{number}')
    require(run['head_sha']==source and run['event']==event and run['status']=='completed'
            and run['conclusion']=='success',f'Unverified run {number}')
    return run


def main():
    require(os.environ.get('GITHUB_REPOSITORY')==REPO,'Wrong repository')
    source=os.environ['RELEASE_SHA']
    ref=os.environ['RELEASE_REF']
    require(re.fullmatch(r'[0-9a-f]{40}',source),'Invalid release source')
    require(ref=='release/verified-4.1.64','Unexpected frozen release ref')
    require(api('git/ref/heads/master')['object']['sha']==source,'Master changed; reconcile before dispatch')
    require(api('git/ref/heads/'+ref)['object']['sha']==source,'Frozen ref changed')
    pr=api('pulls/300')
    require(pr['merged'] and pr['head']['sha']==PR_HEAD and pr['merge_commit_sha']==source,'Wrong merged release PR')
    checks=[checked_run(number,PR_HEAD,'pull_request') for number in PR_RUNS]
    checks.append(checked_run(SCOPE_RUN,SCOPE_HEAD,'push'))
    run_ids=json.loads(os.environ['EXPECTED_MASTER_RUN_IDS'])
    require(isinstance(run_ids,list) and all(isinstance(n,int) for n in run_ids),'Invalid master run set')
    master_runs=[checked_run(number,source,'push') for number in run_ids]
    require(all(r['head_branch']=='master' for r in master_runs),'A selected master run belongs to another branch')
    require(REQUIRED_MASTER_NAMES.issubset({r['name'] for r in master_runs}),'Missing required master gates')
    current=api(f'actions/runs?head_sha={source}&event=push&branch=master&per_page=100')
    require(current['total_count']<=100,'Pagination required before approving master gates')
    latest={}
    for run in current['workflow_runs']:
        require(run['head_branch']=='master' and run['head_sha']==source,'Unexpected filtered master run')
        key=run['path']
        if key not in latest or run['id']>latest[key]['id']:latest[key]=run
    require(all(r['status']=='completed' and r['conclusion']=='success' for r in latest.values()),'A current master workflow is incomplete or unsuccessful')
    require({r['id'] for r in latest.values()}.issubset(set(run_ids)),'Master run set changed; inspect new results first')
    matrix=base64.b64decode(api(f'contents/compatibility/sfl-addon-release-matrix.json?ref={source}')['content'])
    require(hashlib.sha256(matrix).hexdigest()==MATRIX_SHA256,'Addon selections changed')
    version=base64.b64decode(api(f'contents/gradle.properties?ref={source}')['content']).decode()
    require('projectVersion=4.1.64\n' in version,'Wrong release version')
    require(api(f'contents/.github/workflows/reproducible-release.yml?ref={source}')['sha']=='5928b5bd190c7005931abd75b111576aac0e4d4c','The existing publisher changed')
    previous=api('releases/tags/v4.1.63')
    assets={a['name']:a for a in previous['assets']}
    require(len(assets)==len(previous['assets'])==2,'Previous asset set changed')
    require(assets['Slimefun-Legacy4.1.63.jar']['id']==603654347 and assets['Slimefun-Legacy4.1.63.jar']['digest']=='sha256:993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42','Previous core changed')
    require(assets['SF_Addons_1.21.11-26.3.zip']['id']==604494714 and assets['SF_Addons_1.21.11-26.3.zip']['digest']=='sha256:e6ba58c00d843f445d8292435645fc3bda0902cfa38fd986fc2e6a9e3ca87ca8','Previous ZIP changed')
    require(api('git/ref/tags/v4.1.63')['object']['sha']=='2703e3a500b426849f3eb7806862bb4343bb9d6a','Previous tag changed')
    existing=subprocess.run(['gh','api','--method','GET',f'repos/{REPO}/releases/tags/v4.1.64'],text=True,capture_output=True,timeout=45)
    require(existing.returncode!=0 and 'HTTP 404' in existing.stderr,'A 4.1.64 release exists or cannot be checked safely')
    require(api('git/ref/heads/'+ref)['object']['sha']==source,'Frozen ref moved before dispatch')
    require(api('git/ref/heads/master')['object']['sha']==source,'Master moved before dispatch')
    Path('dispatch-evidence').mkdir(exist_ok=True)
    Path('dispatch-evidence/gates.json').write_text(json.dumps({'release_source':source,'frozen_ref':ref,'pr_runs':checks,'master_runs':master_runs,'previous_release':previous},indent=2))
    output=command(['gh','workflow','run','reproducible-release.yml','--repo',REPO,'--ref',ref])
    Path('dispatch-evidence/dispatch.txt').write_text(output+'\nRequested existing reproducible-release.yml at '+source+'\n')
    print('Existing publisher dispatched; publication completion must be checked separately.')


if __name__=='__main__':main()
