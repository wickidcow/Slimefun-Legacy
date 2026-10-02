#!/usr/bin/env python3
"""One-time guarded addon-only publication. Never uploads a core JAR or moves tags."""
import hashlib, importlib.util, json, os, subprocess, sys
from pathlib import Path

REPO='wickidcow/Slimefun-Legacy'
HEAD='10d1fb8ab3379f2b16bc27c2ade76fe08f5fbe3b'
SOURCE='d23eb116221f74079f3637ba801aeeb98d93de13'
VALIDATION_HEAD='4f648e6dbe5713bc27f3f4fc4bfc02a90b6d5cf3'
CORE='Slimefun-Legacy4.1.63.jar'
ZIP='SF_Addons_1.21.11-26.3.zip'
CORE_HASH='993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42'
OLD_HASH='9d517d0e1d62adb34397aa6a151c7433b7f483a33e3a96fe60adc8a60bdd6470'
RUNS=(36944160113,36944160029,36944160260,36944160191,36944160124,36944160098,36944160277,36944160244,36944160210,36944160265,36944160184)

def require(value,message):
    if not value: raise RuntimeError(message)
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def command(args,**kwargs):return subprocess.run(args,check=True,text=True,capture_output=True,timeout=180,**kwargs).stdout
def api(endpoint,payload=None):
    args=['gh','api','--method','GET' if payload is None else 'PATCH',f'repos/{REPO}/{endpoint}']
    if payload is not None:args+=['--input','-']
    return json.loads(command(args,input=None if payload is None else json.dumps(payload)))
def release_guard(r):
    require(r['id']==401149535 and r['tag_name']=='v4.1.63','Wrong release')
    require(not r.get('draft') and not r.get('prerelease') and not r.get('immutable'),'Release state changed')
    assets={a['name']:a for a in r['assets']}
    require(len(assets)==len(r['assets']) and set(assets)=={CORE,ZIP},'Release asset set changed')
    require(assets[CORE]['id']==603654347 and assets[CORE]['digest']=='sha256:'+CORE_HASH,'Published core changed')
    require(assets[ZIP]['id']==603654353 and assets[ZIP]['digest']=='sha256:'+OLD_HASH,'Published ZIP changed; preserve concurrent work')
    return assets

def gates():
    require(os.environ['GITHUB_REPOSITORY']==REPO,'Wrong repository')
    p=api('pulls/297');require(p['merged'] and p['head']['sha']==HEAD,'Exact combined PR is not merged')
    for number in RUNS+(36944258839,):
        r=api(f'actions/runs/{number}')
        expected=VALIDATION_HEAD if number==36944258839 else HEAD
        require(r['head_sha']==expected and r['status']=='completed' and r['conclusion']=='success',f'Exact validation {number} has not passed')
    matrix=json.loads(Path('candidate/source-matrix.json').read_text())
    require(matrix['bundle_revision']==103 and len(matrix['addons'])==45,'Wrong source matrix')
    spec=importlib.util.spec_from_file_location('bundle_verifier','scripts/download_candidate_bundle.py')
    verifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(verifier)
    verifier.verify_bundle(Path('candidate')/ZIP,matrix,SOURCE)
    expected=os.environ['EXPECTED_ZIP_SHA256']
    require(len(expected)==64 and digest(Path('candidate')/ZIP)==expected,'Candidate ZIP bytes changed')
    require(digest(Path('candidate')/CORE)==CORE_HASH,'Wrong tested core')
    return expected

def download(name,directory):
    Path(directory).mkdir(parents=True,exist_ok=True)
    command(['gh','release','download','v4.1.63','--repo',REPO,'--pattern',name,'--dir',str(directory)])

def main():
    new_hash=gates();phase=sys.argv[1]
    Path('publication').mkdir(exist_ok=True)
    if phase=='prepare':
        before=api('releases/401149535');release_guard(before)
        Path('publication/before-release.json').write_text(json.dumps(before,indent=2))
        tag=api('git/ref/tags/v4.1.63')
        Path('publication/before-tag.json').write_text(json.dumps(tag,indent=2))
        download(ZIP,'publication/backup');download(CORE,'publication/backup')
        require(digest(Path('publication/backup')/ZIP)==OLD_HASH,'Old ZIP does not match recovery baseline')
        require(digest(Path('publication/backup')/CORE)==CORE_HASH,'Old core does not match recovery baseline')
        print('Recovery copy verified. No release assets changed.')
        return
    require(phase=='publish','Unknown phase')
    before=json.loads(Path('publication/before-release.json').read_text())
    current=api('releases/401149535');release_guard(before);release_guard(current)
    for key in ('id','tag_name','target_commitish','body','updated_at'):
        require(before.get(key)==current.get(key),'Concurrent release change: '+key)
    tag=json.loads(Path('publication/before-tag.json').read_text())
    require(api('git/ref/tags/v4.1.63')['object']==tag['object'],'Tag changed')
    require(digest(Path('publication/backup')/ZIP)==OLD_HASH,'Recovery ZIP missing or changed')
    command(['gh','release','upload','v4.1.63',str(Path('candidate')/ZIP),'--repo',REPO,'--clobber'])
    download(ZIP,'publication/downloaded')
    require(digest(Path('publication/downloaded')/ZIP)==new_hash,'Uploaded ZIP verification failed')
    after=api('releases/401149535');assets={a['name']:a for a in after['assets']}
    require(set(assets)=={CORE,ZIP},'Unexpected release assets after upload')
    require(assets[CORE]['id']==603654347 and assets[CORE]['digest']=='sha256:'+CORE_HASH,'Core changed during ZIP publication')
    require(assets[ZIP]['digest']=='sha256:'+new_hash,'GitHub ZIP digest mismatch')
    require(api('git/ref/tags/v4.1.63')['object']==tag['object'],'Tag changed during ZIP publication')
    require(after.get('body')==before.get('body'),'Release notes changed during upload; do not overwrite them')
    body=before['body'].replace('this release uses source-manifest revision 100.','the original publication used source-manifest revision 100; the addon-only refresh below supersedes that ZIP.')
    body=body.replace('## Published artifact verification','## Original published artifact verification')
    body=body.replace('- Addon ZIP SHA-256:', '- Original addon ZIP SHA-256:')
    body+=f'''\n\n## Addon-only refresh: revision 103\n\nThe canonical addon ZIP is now revision 103. The original Slimefun-Legacy4.1.63.jar and v4.1.63 tag are unchanged. This is an addon-only refresh, not a new core release.\n\nThe full 45-addon ZIP includes BetterChests 1.0.3, BuildingStaff 1.0.35, FluffyMachines 26.2.13 and Networks 1.0.47, plus the earlier revision-101 updates. All 41 other revision-101 source selections and the exclusions are preserved. Existing IDs, data formats and migration defaults are not changed by bundle publication.\n\n- Combined addon-selection commit: `{HEAD}` (PR297, incorporating PR298).\n- Actual canonical build checkout recorded in the ZIP: `{SOURCE}`.\n- Current addon ZIP SHA-256: `{new_hash}`.\n- Unchanged published core SHA-256: `{CORE_HASH}`.\n\n[Canonical bundle validation](https://github.com/{REPO}/actions/runs/36944160244) and [combined full-stack checks](https://github.com/{REPO}/actions/runs/36944160098) passed. The identical ZIP also passed two boots with all 45 addons required on Paper 1.21.11, 26.2 and 26.3 using the exact published core binary in [published-core validation](https://github.com/{REPO}/actions/runs/36944258839). These are generated-server startup/restart checks, not exhaustive customer-world, real-client or Folia concurrency certification. BuildingStaff's optional real-provider coverage remains specifically Rebar 0.43.0-26.2 and Pylon 0.41.1-26.2.\n\nThe previous ZIP/core and release metadata were retained in the publication run's recovery artifact before replacement. The newly uploaded ZIP was downloaded again and verified; the core asset identity/digest and tag stayed unchanged. Back up worlds and addon data, test on a copy, and replace existing JARs rather than installing duplicate versions.\n'''
    api('releases/401149535',{'body':body})
    final=api('releases/401149535')
    require(final['body']==body,'Release notes verification failed')
    Path('publication/after-release.json').write_text(json.dumps(final,indent=2))
    receipt={'release':final['html_url'],'bundle_revision':103,'bundle_sha256':new_hash,'previous_bundle_sha256':OLD_HASH,'unchanged_core_sha256':CORE_HASH,'unchanged_core_asset_id':603654347,'new_zip_asset_id':assets[ZIP]['id'],'source_head':HEAD,'build_source':SOURCE,'canonical_run':36944160244,'published_core_validation_run':36944258839,'publication_run':os.environ['GITHUB_RUN_ID'],'verification':'downloaded uploaded ZIP and matched exact bytes; core/tag unchanged'}
    Path('publication/receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
