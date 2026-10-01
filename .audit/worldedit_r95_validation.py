"""Verify the r95 pair and add the upstream Bukkit dependency to its unchanged smoke harness."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import urllib.request
import zipfile

CORE = 'ea4b0c47ee41b852abb5c97bacbb4f1c0ebc5126c29fb36d39e7c71747d13bdd'
BUNDLE = '7cf9bf6ce10949d93da8dce50125d4b1a8385ddbbbac2433ab72f6e6687b0d86'
VERSION_ID = 'VhUPP2IN'
VERSION = '7.4.6-beta-02'
FILENAME = 'worldedit-bukkit-7.4.6-beta-02.jar'
UA = 'Slimefun-Legacy-WorldEdit-Compatibility/1.0 (github.com/wickidcow/Slimefun-Legacy)'


def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read()


def main():
    evidence = Path('worldedit-evidence')
    evidence.mkdir(exist_ok=True)
    core = next(Path('candidate').rglob('Slimefun-Legacy-full-stack.jar'))
    bundle = next(Path('bundle').rglob('SF_Addons_1.21.11-26.3.zip'))
    assert hashlib.sha256(core.read_bytes()).hexdigest() == CORE, 'Wrong paired core'
    assert hashlib.sha256(bundle.read_bytes()).hexdigest() == BUNDLE, 'Wrong paired addon bundle'
    with zipfile.ZipFile(bundle) as archive:
        assert archive.testzip() is None
        manifest = json.loads(archive.read('SF_ADDON_MANIFEST.json'))
        assert len(manifest['addons']) == 45
        worldedit = [x for x in manifest['addons'] if 'SFWorldEdit' in x['jar']]
        assert len(worldedit) == 1, worldedit
    metadata = json.loads(get('https://api.modrinth.com/v2/version/' + VERSION_ID))
    assert metadata['project_id'] == '1u6JkXh5'
    assert metadata['version_number'] == VERSION
    assert 'bukkit' in metadata['loaders'] and '26.3' in metadata['game_versions']
    file = next(x for x in metadata['files'] if x['filename'] == FILENAME and x['primary'])
    assert file['url'].startswith('https://cdn.modrinth.com/data/1u6JkXh5/versions/' + VERSION_ID + '/')
    data = get(file['url'])
    assert hashlib.sha512(data).hexdigest() == file['hashes']['sha512']
    assert hashlib.md5(data).hexdigest() == '8ce90c96cf9e01bdc6d436843c1f8b72', 'Published Bukkit file mismatch'
    dependency = evidence / FILENAME
    dependency.write_bytes(data)
    with zipfile.ZipFile(dependency) as jar:
        assert jar.testzip() is None
        descriptor = jar.read('plugin.yml').decode()
        assert re.search(r'^name: [\'\"]?WorldEdit[\'\"]?\s*$', descriptor, re.M)
        assert 'com.sk89q.worldedit.bukkit.WorldEditPlugin' in descriptor
    lock = {'version_id': VERSION_ID, 'version': VERSION, 'url': file['url'],
            'sha256': hashlib.sha256(data).hexdigest(), 'sha512': file['hashes']['sha512'],
            'filename': FILENAME, 'minecraft_versions': metadata['game_versions'],
            'core_sha256': CORE, 'bundle_sha256': BUNDLE, 'sfworldedit': worldedit[0]}
    (evidence / 'dependency-lock.json').write_text(json.dumps(lock, indent=2) + '\n')
    (evidence / 'upstream-version.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps(lock, indent=2))

    path = Path('scripts/full_stack_runtime_smoke.sh')
    source = path.read_bytes()
    blob = hashlib.sha1(b'blob ' + str(len(source)).encode() + b'\0' + source).hexdigest()
    assert blob == '5560056611b49426f8c216987742aeb3f418624e', blob
    text = source.decode()
    changes = (
        ('cp "$SLIMEFUN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-full-stack.jar"',
         'cp "$SLIMEFUN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-full-stack.jar"\n'
         'test -s "${WORLD_EDIT_SMOKE_JAR:?Verified WorldEdit JAR is required}"\n'
         'cp "$WORLD_EDIT_SMOKE_JAR" "$WORK_DIR/plugins/WorldEdit.jar"'),
        ('available_plugins = {"slimefun"}', 'available_plugins = {"slimefun", "worldedit"}'),
        ('expected = []\ngated = []', 'expected = []\ngated = []'),
        ("    printf 'sf doctor registry\\n' >&3", "    printf 'sf doctor registry\\n' >&3\n    printf 'worldedit version\\n' >&3"),
        ('    python3 "$REPO_ROOT/scripts/verify_runtime_configuration.py" "$normalized"',
         '    python3 "$REPO_ROOT/scripts/verify_runtime_configuration.py" "$normalized"\n'
         '    grep -Fq "WorldEditSlimefun enabled with WorldEdit/FAWE selection support." "$normalized"\n'
         '    grep -Fq "WorldEdit version 7.4.6-beta-02" "$normalized"'),
    )
    for before, after in changes:
        assert text.count(before) == 1, before
        text = text.replace(before, after)
    # Keep the same script location so its REPO_ROOT and existing runtime checks stay intact.
    path.write_text(text)
    shutil.copy2(path, evidence / 'tested-full-stack-runtime.sh')
    with open(os.environ['GITHUB_ENV'], 'a') as env:
        env.write('WORLD_EDIT_SMOKE_JAR=' + str(dependency.resolve()) + '\n')
        env.write('PAIRED_CORE=' + str(core.resolve()) + '\n')
        env.write('PAIRED_BUNDLE=' + str(bundle.resolve()) + '\n')


if __name__ == '__main__':
    main()
