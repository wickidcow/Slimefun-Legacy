#!/usr/bin/env python3
"""Download and verify a completed 4.1.64 release; never writes GitHub state."""
from __future__ import annotations
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

REPO='wickidcow/Slimefun-Legacy'
PREVIOUS_TAG_SHA='2703e3a500b426849f3eb7806862bb4343bb9d6a'
PREVIOUS_CORE_SHA='993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42'
PREVIOUS_ZIP_SHA='e6ba58c00d843f445d8292435645fc3bda0902cfa38fd986fc2e6a9e3ca87ca8'
BUNDLE='SF_Addons_1.21.11-26.3.zip'
ROOT=Path('publication-verification')


def require(value, message):
    if not value: raise RuntimeError(message)


def command(args):
    return subprocess.check_output(args, text=True, timeout=240)


def api(endpoint):
    return json.loads(command(['gh','api','--method','GET',f'repos/{REPO}/{endpoint}']))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def download(tag, names, destination):
    destination.mkdir(parents=True,exist_ok=True)
    for name in names:
        command(['gh','release','download',tag,'--repo',REPO,'--pattern',name,'--dir',str(destination)])


def inspect_jar(data):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names=jar.namelist()
        require(len(names)==len(set(names)) and jar.testzip() is None,'Invalid JAR structure')
        descriptor=next((n for n in ('plugin.yml','paper-plugin.yml','paper-plugin.yaml') if n in names),None)
        require(descriptor is not None,'Missing plugin descriptor')
        text=jar.read(descriptor).decode()
        version=re.search(r'(?m)^version:\s*([^\r\n#]+)',text)
        require(version is not None,'Missing plugin version')
        count=0
        for name in names:
            require(not name.startswith(('org/junit/','org/mockito/','org/mockbukkit/','be/seeseemelk/mockbukkit/')),'Test classes in JAR')
            if not name.endswith('.class'):continue
            payload=jar.read(name)
            require(len(payload)>=8 and payload[:4]==b'\xca\xfe\xba\xbe','Malformed class')
            minor,major=struct.unpack('>HH',payload[4:8])
            overlay=re.match(r'META-INF/versions/(\d+)/',name)
            if overlay and int(overlay.group(1))>21:continue
            require(major<=65 and minor!=65535,'Unsupported Java floor or preview class: '+name)
            count+=1
        require(count>0,'Empty plugin')
        return {'sha256':digest(data),'version':version.group(1).strip().strip('\"\x27'),'java21_classes':count,
                'git_properties':jar.read('git.properties').decode() if 'git.properties' in names else None}


def main():
    require(os.environ.get('GITHUB_REPOSITORY')==REPO,'Wrong repository')
    source=os.environ['RELEASE_SHA']
    run_id=int(os.environ['PUBLISH_RUN_ID'])
    require(re.fullmatch(r'[0-9a-f]{40}',source),'Invalid source')
    run=api(f'actions/runs/{run_id}')
    require(run['head_sha']==source and run['status']=='completed' and run['conclusion']=='success'
            and run['event']=='workflow_dispatch' and run['path']=='.github/workflows/reproducible-release.yml',
            'The exact existing publisher has not completed successfully')
    previous=api('releases/tags/v4.1.63')
    prior_assets={a['name']:a for a in previous['assets']}
    require(len(prior_assets)==len(previous['assets'])==2 and set(prior_assets)=={BUNDLE,'Slimefun-Legacy4.1.63.jar'},'Previous release asset set changed')
    require(prior_assets[BUNDLE]['id']==604494714 and prior_assets[BUNDLE]['digest']=='sha256:'+PREVIOUS_ZIP_SHA,'Previous ZIP changed')
    require(prior_assets['Slimefun-Legacy4.1.63.jar']['id']==603654347 and prior_assets['Slimefun-Legacy4.1.63.jar']['digest']=='sha256:'+PREVIOUS_CORE_SHA,'Previous core changed')
    require(api('git/ref/tags/v4.1.63')['object']['sha']==PREVIOUS_TAG_SHA,'Previous release tag moved')
    current=api('releases/tags/v4.1.64')
    require(not current['draft'] and not current['prerelease'],'New release is not published stable')
    tag=api('git/ref/tags/v4.1.64')
    require(tag['object']['type']=='commit' and tag['object']['sha']==source,'New tag has wrong source')
    assets={a['name']:a for a in current['assets']}
    core='Slimefun-Legacy4.1.64.jar'
    require(len(assets)==len(current['assets'])==2 and set(assets)=={BUNDLE,core},'Wrong new asset set')
    download('v4.1.64',[core,BUNDLE],ROOT/'new-release')
    download('v4.1.63',['Slimefun-Legacy4.1.63.jar',BUNDLE],ROOT/'previous-release')
    require(digest((ROOT/'previous-release'/BUNDLE).read_bytes())==PREVIOUS_ZIP_SHA,'Previous ZIP bytes mismatch')
    require(digest((ROOT/'previous-release'/'Slimefun-Legacy4.1.63.jar').read_bytes())==PREVIOUS_CORE_SHA,'Previous core bytes mismatch')
    core_bytes=(ROOT/'new-release'/core).read_bytes()
    core_info=inspect_jar(core_bytes)
    require(core_info['version']=='4.1.64','Wrong core version')
    require('git.source.commit='+source in core_info['git_properties'],'Core source provenance mismatch')
    for name in (core,BUNDLE):
        require(assets[name]['digest']=='sha256:'+digest((ROOT/'new-release'/name).read_bytes()),'Published checksum mismatch '+name)
    with zipfile.ZipFile(ROOT/'new-release'/BUNDLE) as archive, zipfile.ZipFile(ROOT/'previous-release'/BUNDLE) as old:
        names=archive.namelist()
        require(len(names)==len(set(names)) and archive.testzip() is None,'Invalid addon ZIP')
        manifest=json.loads(archive.read('SF_ADDON_MANIFEST.json'))
        old_manifest=json.loads(old.read('SF_ADDON_MANIFEST.json'))
        require(manifest['core_source_commit']==source,'New ZIP has wrong core source')
        require(manifest['compatibility']==old_manifest['compatibility'],'Addon compatibility contract changed')
        selected={r['repository']:r['commit'] for r in manifest['addons']}
        expected={r['repository']:r['commit'] for r in old_manifest['addons']}
        require(len(selected)==len(manifest['addons'])==45 and selected==expected,'Revision104 source selections changed')
        require({r['jar'] for r in manifest['addons']}=={n for n in names if n.endswith('.jar')},'Addon JAR membership mismatch')
        sums={}
        for line in archive.read('SHA256SUMS.txt').decode().splitlines():
            if not line.strip():continue
            value,name=line.split(None,1);name=name.strip().lstrip('*')
            require(name not in sums and digest(archive.read(name))==value,'Invalid checksum entry '+name)
            sums[name]=value
        addon_info=[]
        for row in manifest['addons']:
            info=inspect_jar(archive.read(row['jar']))
            require(info['version']==str(row['version']) and info['sha256']==row['sha256']==sums[row['jar']],'Addon metadata mismatch')
            info.update(repository=row['repository'],source=row['commit'],jar=row['jar'])
            addon_info.append(info)
    receipt={'release':current['html_url'],'release_version':'4.1.64','source':source,'publisher_run':run_id,
             'core':core_info,'core_asset_id':assets[core]['id'],'addon_zip_sha256':digest((ROOT/'new-release'/BUNDLE).read_bytes()),
             'addon_zip_asset_id':assets[BUNDLE]['id'],'bundle_revision':104,'addons':addon_info,
             'previous_core_unchanged':True,'previous_zip_unchanged':True,'previous_tag_unchanged':True,
             'previous_core_sha256':PREVIOUS_CORE_SHA,'previous_zip_sha256':PREVIOUS_ZIP_SHA}
    (ROOT/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    (ROOT/'release-metadata.json').write_text(json.dumps(current,indent=2)+'\n')
    (ROOT/'previous-release-metadata.json').write_text(json.dumps(previous,indent=2)+'\n')
    (ROOT/'publisher.json').write_text(json.dumps(run,indent=2)+'\n')
    print(json.dumps({k:v for k,v in receipt.items() if k not in ('addons','core')},indent=2))


if __name__=='__main__':
    main()
