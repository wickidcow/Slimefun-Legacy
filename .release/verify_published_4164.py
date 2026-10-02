#!/usr/bin/env python3
"""Read-only release verification. This file never writes GitHub assets or refs."""
from pathlib import Path
import hashlib
import io
import json
import re
import struct
import subprocess
import zipfile

REPO = 'wickidcow/Slimefun-Legacy'
SOURCE = 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
CORE = 'Slimefun-Legacy4.1.64.jar'
BUNDLE = 'SF_Addons_1.21.11-26.3.zip'
OUT = Path('verified-release')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=180).stdout


def api(path):
    return json.loads(command(['gh', 'api', '--method', 'GET', f'repos/{REPO}/{path}']))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def jar_classes(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and archive.testzip() is None, 'Invalid JAR archive')
        require('plugin.yml' in names or 'paper-plugin.yml' in names, 'Missing plugin descriptor')
        count = 0
        for name in names:
            require(not name.startswith(('org/junit/', 'org/mockito/', 'org/mockbukkit/', 'be/seeseemelk/mockbukkit/')), 'Test code shaded')
            if not name.endswith('.class'):
                continue
            payload = archive.read(name)
            require(len(payload) >= 8 and payload[:4] == b'\xca\xfe\xba\xbe', 'Invalid Java class')
            minor, major = struct.unpack('>HH', payload[4:8])
            overlay = re.match(r'META-INF/versions/(\d+)/', name)
            if overlay and int(overlay.group(1)) > 21:
                continue
            require(major <= 65 and minor != 65535, 'Unsupported floor or preview bytecode: ' + name)
            count += 1
        require(count > 0, 'No plugin classes')
        return count


def main():
    OUT.mkdir()
    release = api('releases/tags/v4.1.64')
    require(not release['draft'] and not release['prerelease'], 'Release not published stable')
    tag = api('git/ref/tags/v4.1.64')
    require(tag['object']['type'] == 'commit' and tag['object']['sha'] == SOURCE, 'Wrong release tag source')
    assets = {row['name']: row for row in release['assets']}
    require(set(assets) == {CORE, BUNDLE} and len(release['assets']) == 2, 'Unexpected asset layout')
    for name in (CORE, BUNDLE):
        command(['gh', 'release', 'download', 'v4.1.64', '--repo', REPO, '--pattern', name, '--dir', str(OUT)])
        data = (OUT / name).read_bytes()
        require(assets[name]['digest'] == 'sha256:' + digest(data), 'Uploaded asset checksum mismatch: ' + name)
        require(assets[name]['size'] == len(data), 'Uploaded asset size mismatch')
    core_data = (OUT / CORE).read_bytes()
    core_classes = jar_classes(core_data)
    with zipfile.ZipFile(io.BytesIO(core_data)) as core:
        props = core.read('git.properties').decode()
        require('git.source.commit=' + SOURCE in props and 'git.build.version=4.1.64' in props, 'Core source/version not exact')
        descriptor = core.read('plugin.yml').decode()
        require(re.search(r'(?m)^version:\s*[\"\x27]?4\.1\.64[\"\x27]?\s*$', descriptor), 'Wrong core descriptor version')
    payload = api(f'actions/runs?head_sha={SOURCE}&event=push&per_page=100')
    require(payload['total_count'] <= 100, 'Run pagination requires review')
    builds = [r for r in payload['workflow_runs'] if r['path'] == '.github/workflows/build-sfl-addons-compat-bundle.yml' and r['head_branch'] == 'master']
    require(builds, 'No canonical build')
    build = max(builds, key=lambda r: r['id'])
    require(build['status'] == 'completed' and build['conclusion'] == 'success', 'Canonical build not successful')
    command(['gh', 'run', 'download', str(build['id']), '--repo', REPO, '--name', 'SF_Addons_1.21.11-26.3', '--dir', 'canonical-reference'])
    bundle_data = (OUT / BUNDLE).read_bytes()
    require(bundle_data == (Path('canonical-reference') / BUNDLE).read_bytes(), 'Published ZIP differs from the tested canonical archive')
    with zipfile.ZipFile(io.BytesIO(bundle_data)) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and archive.testzip() is None, 'Invalid canonical archive')
        manifest = json.loads(archive.read('SF_ADDON_MANIFEST.json'))
        require(manifest['core_source_commit'] == SOURCE, 'Bundle source does not match release')
        require(len(manifest['addons']) == 45, 'Wrong addon count')
        repo_set = {row['repository'] for row in manifest['addons']}
        require(len(repo_set) == 45, 'Duplicate addon identity')
        jars = {row['jar'] for row in manifest['addons']}
        require(len(jars) == 45 and jars == {n for n in names if n.endswith('.jar')}, 'Manifest/JAR mismatch')
        applicable = 0
        for row in manifest['addons']:
            require(Path(row['jar']).name == row['jar'], 'Unsafe plugin filename')
            data = archive.read(row['jar'])
            require(digest(data) == row['sha256'], 'Individual addon checksum mismatch')
            applicable += jar_classes(data)
        sums = {}
        for line in archive.read('SHA256SUMS.txt').decode().splitlines():
            sha, name = line.split(maxsplit=1)
            name = name.lstrip('*')
            require(name not in sums, 'Duplicate checksum record')
            sums[name] = sha
            require(digest(archive.read(name)) == sha, 'Checksum-file mismatch')
        require(jars.issubset(sums), 'Missing JAR checksum records')
        (OUT / 'SF_ADDON_MANIFEST.json').write_bytes(archive.read('SF_ADDON_MANIFEST.json'))
    previous = api('releases/tags/v4.1.63')
    previous_assets = {r['name']: r for r in previous['assets']}
    require(len(previous_assets) == len(previous['assets']) == 2, 'Previous asset set changed')
    expected = (
        ('Slimefun-Legacy4.1.63.jar', 603654347, '993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42'),
        (BUNDLE, 604494714, 'e6ba58c00d843f445d8292435645fc3bda0902cfa38fd986fc2e6a9e3ca87ca8'),
    )
    for name, aid, sha in expected:
        require(previous_assets[name]['id'] == aid and previous_assets[name]['digest'] == 'sha256:' + sha, 'Previous release asset changed')
    previous_tag = api('git/ref/tags/v4.1.63')
    require(previous_tag['object']['sha'] == '2703e3a500b426849f3eb7806862bb4343bb9d6a', 'Previous tag changed')
    runs = api(f'actions/runs?head_sha={SOURCE}&event=workflow_dispatch&per_page=100')
    require(runs['total_count'] <= 100, 'Publication run pagination requires review')
    publisher = [r for r in runs['workflow_runs'] if r['path'] == '.github/workflows/reproducible-release.yml']
    require(publisher, 'No publication workflow')
    publisher = max(publisher, key=lambda r: r['id'])
    require(publisher['status'] == 'completed' and publisher['conclusion'] == 'success', 'Publisher not complete')
    receipt = {
        'release_url': release['html_url'], 'release_id': release['id'],
        'source': SOURCE, 'bundle_revision': 104, 'addon_count': 45,
        'core_sha256': digest(core_data), 'core_bytes': len(core_data), 'core_applicable_classes': core_classes,
        'bundle_sha256': digest(bundle_data), 'bundle_bytes': len(bundle_data), 'addon_applicable_classes': applicable,
        'assets': [{'name': n, 'id': assets[n]['id'], 'digest': assets[n]['digest']} for n in (CORE, BUNDLE)],
        'canonical_run': build['id'], 'publisher_run': publisher['id'],
        'published_zip_byte_identical_to_canonical': True, 'previous_4_1_63_assets_and_tag_unchanged': True,
    }
    for name, obj in (('receipt.json', receipt), ('release.json', release), ('release-tag.json', tag), ('previous-release.json', previous), ('previous-tag.json', previous_tag), ('publisher-run.json', publisher)):
        (OUT / name).write_text(json.dumps(obj, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
