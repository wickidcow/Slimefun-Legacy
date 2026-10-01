#!/usr/bin/env python3
"""Disposable generated-world upgrade check. No path to a real server is accepted."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

UA = 'Slimefun-Legacy-Prior-Release-Test/1.0 (github.com/wickidcow/Slimefun-Legacy)'

def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=90) as response:
        return response.read()

def prepare_server(root: Path, software: str, minecraft: str) -> None:
    if software == 'paper':
        builds = json.loads(get(f'https://fill.papermc.io/v3/projects/paper/versions/{minecraft}/builds'))
        assert isinstance(builds, list) and builds
        matches = [b for b in builds if b['channel'] == 'STABLE']
        if not matches:
            assert minecraft == '26.3', 'Only the explicitly requested 26.3 line permits a prerelease'
            matches = [b for b in builds if b['channel'] in ('BETA', 'ALPHA')]
        assert matches
        selected = matches[0]
        artifact = selected['downloads']['server:default']
        data = get(artifact['url'])
        hashes = artifact.get('checksums', {})
        expected = hashes.get('sha256') or artifact.get('sha256')
        assert expected, 'Publisher did not provide SHA256'
        assert hashlib.sha256(data).hexdigest() == expected
        metadata = {'software': software, 'minecraft': minecraft, 'build': selected['id'],
                    'channel': selected['channel'], 'url': artifact['url'], 'sha256': expected}
    else:
        assert software == 'purpur' and minecraft == '1.21.11'
        build = json.loads(get(f'https://api.purpurmc.org/v2/purpur/{minecraft}'))['builds']['latest']
        meta = json.loads(get(f'https://api.purpurmc.org/v2/purpur/{minecraft}/{build}'))
        url = f'https://api.purpurmc.org/v2/purpur/{minecraft}/{build}/download'
        data = get(url)
        assert hashlib.md5(data).hexdigest() == meta['md5']
        metadata = {'software': software, 'minecraft': minecraft, 'build': build,
                    'url': url, 'sha256': hashlib.sha256(data).hexdigest(), 'publisher_md5':meta['md5']}
    (root / 'server.jar').write_bytes(data)
    (root / 'runtime.json').write_text(json.dumps(metadata, indent=2))

def cycle(root: Path, phase: str, core_version: str) -> None:
    log = root / (phase + '.console.log')
    process = None
    try:
        with log.open('w') as output:
            process = subprocess.Popen(['java', '-Xms512M', '-Xmx2G', '-Dlegacy.fixture.phase=' + phase,
                                        '-Dlegacy.fixture.coreVersion=' + core_version,
                                        '-jar', 'server.jar', '--nogui'], cwd=root,
                                        stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)
            deadline = time.monotonic() + 300
            marker = 'PRIOR_RELEASE_FIXTURE PASS phase=' + phase + ' views=25'
            while time.monotonic() < deadline and process.poll() is None:
                text = log.read_text(errors='replace')
                if 'PRIOR_RELEASE_FIXTURE FAIL' in text:
                    raise AssertionError('Fixture failed; see ' + str(log))
                if marker in text and 'Done (' in text:
                    break
                time.sleep(1)
            else:
                raise AssertionError('Fixture did not finish: ' + str(log))
            assert f'Enabling Slimefun v{core_version}' in text
            process.stdin.write('stop\n'); process.stdin.flush()
            assert process.wait(timeout=100) == 0
        text = log.read_text(errors='replace')
        assert marker in text and 'PRIOR_RELEASE_FIXTURE FAIL' not in text
        assert (root/'plugins/PriorReleaseUpgradeProbe'/f'{phase}-passed.json').is_file()
        print('ACTUAL_SERVER_UPGRADE_PASS', phase, core_version, flush=True)
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write('stop\n'); process.stdin.flush(); process.wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.kill(); process.wait()
        if log.exists():
            print('\n'.join(log.read_text(errors='replace').splitlines()[-35:]), flush=True)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['produce', 'upgrade'])
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--probe', type=Path, required=True)
    parser.add_argument('--software', choices=['paper','purpur'], default='paper')
    parser.add_argument('--minecraft', choices=['1.21.11','26.2','26.3'], default='1.21.11')
    args = parser.parse_args()
    root = Path('build/prior-release-fixture').resolve()
    if args.mode == 'produce':
        assert not root.exists(), 'Refusing to overwrite an existing fixture directory'
        (root/'plugins').mkdir(parents=True)
        (root/'eula.txt').write_text('eula=true\n')
        (root/'server.properties').write_text('online-mode=false\nlevel-name=upgrade-fixture-world\nmax-players=1\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\nspawn-protection=0\nenable-query=false\nenable-rcon=false\n')
        (root/'usercache.json').write_text(json.dumps([{'name':'LegacyFixture','uuid':'11111111-2222-3333-4444-555555555555','expiresOn':'2099-01-01 00:00:00 +0000'}]))
    else:
        assert (root/'plugins/PriorReleaseUpgradeProbe/expected.json').is_file(), 'The previous-release fixture is required'
    shutil.copy2(args.core, root/'plugins/Slimefun-test.jar')
    shutil.copy2(args.probe, root/'plugins/PriorReleaseUpgradeProbe.jar')
    prepare_server(root, args.software, args.minecraft)
    version = '4.1.61' if args.mode == 'produce' else '4.1.62'
    for phase in (('write','baseline') if args.mode == 'produce' else ('upgrade','restart')):
        cycle(root, phase, version)
    result = {'mode':args.mode,'runtime':json.loads((root/'runtime.json').read_text()),
              'core_sha256':hashlib.sha256(args.core.read_bytes()).hexdigest(),
              'probe_sha256':hashlib.sha256(args.probe.read_bytes()).hexdigest(),
              'phases':['write','baseline'] if args.mode=='produce' else ['upgrade','restart']}
    (root/(args.mode+'-result.json')).write_text(json.dumps(result,indent=2))

if __name__ == '__main__':
    main()
