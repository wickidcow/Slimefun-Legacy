#!/usr/bin/env python3
"""Generate progress with the old addon, remove definitions, restore them and compare actual progress."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
from pathlib import Path

OWNERS = ['00000000-1111-2222-3333-444444444444', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee']
KNOWN, ABSENT = 'compatfixture:known', 'compatfixture:absent'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft', required=True)
    parser.add_argument('--inputs', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    inputs, output = args.inputs.resolve(), args.output.resolve()
    if output.exists():
        raise SystemExit('Refusing an existing server/output directory')
    output.mkdir(parents=True)
    provenance = json.loads((inputs / 'provenance.json').read_text())
    for filename, expected in provenance['jars'].items():
        assert Path(filename).name == filename and sha(inputs / filename) == expected
    assert provenance['core_sha256'] == sha(inputs / 'Slimefun-Legacy-Core.jar')
    command = ['curl', '-fsSL', '--retry', '3', '-H', 'User-Agent: SFL-Advancement-Preservation/1.0']
    response = subprocess.run(command + [f'https://fill.papermc.io/v3/projects/paper/versions/{args.minecraft}/builds'],
                              capture_output=True, text=True, check=True, timeout=90)
    builds = json.loads(response.stdout)
    assert isinstance(builds, list) and builds
    stable = [b for b in builds if b.get('channel') == 'STABLE']
    selected = max(stable or builds, key=lambda b: b['id'])
    subprocess.run(command + [selected['downloads']['server:default']['url'], '-o', str(output / 'server.jar')], check=True, timeout=180)
    result = dict(minecraft=args.minecraft, paper_build=selected['id'], channel=selected['channel'],
                  server_sha256=sha(output / 'server.jar'), inputs=provenance, cycles=[])
    (output / 'runtime-inputs.json').write_text(json.dumps(result, indent=2) + '\n')

    def prepare(name, generation):
        folder = output / name
        addon = folder / 'plugins' / 'SlimefunAdvancements'
        addon.mkdir(parents=True)
        shutil.copy2(output / 'server.jar', folder / 'server.jar')
        shutil.copy2(inputs / 'Slimefun-Legacy-Core.jar', folder / 'plugins' / 'Slimefun-Legacy-Core.jar')
        shutil.copy2(inputs / 'AdvancementRetentionProbe.jar', folder / 'plugins' / 'AdvancementRetentionProbe.jar')
        shutil.copy2(inputs / f'advancements-{generation}.jar', folder / 'plugins' / 'SlimefunAdvancements.jar')
        (folder / 'eula.txt').write_text('eula=true\n')
        (folder / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
            'level-name=world\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\n')
        # Offline UUID fixtures must not send client advancement packets or announce a null online Player.
        # Real criterion/reward handling still executes with a counted fixture Reward callback.
        (addon / 'config.yml').write_text('use-advancements-api: false\nannounce-advancements: false\n'
            'add-advancements-to-guide: true\nreload-data-on-adv-remove: false\n')
        return folder

    def path(folder, owner):
        return folder / 'plugins' / 'SlimefunAdvancements' / 'advancements' / (owner + '.json')

    def cycle(folder, mode, label):
        marker = folder / 'advancement-probe-result.txt'
        marker.unlink(missing_ok=True)
        log = output / (label + '.log')
        with log.open('wb') as stream:
            process = subprocess.Popen(['java', '-Xms512M', '-Xmx2G', '-Dadvancement.mode=' + mode,
                '-jar', 'server.jar', '--nogui'], cwd=folder, stdout=stream, stderr=subprocess.STDOUT,
                stdin=subprocess.DEVNULL, start_new_session=True)
            try:
                status = process.wait(timeout=360)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                raise AssertionError(f'{label}: timed out before reliable completion')
        text = log.read_text(encoding='utf-8', errors='replace')
        text = re.sub(r'\x1b\[[0-9;?]*[ -/]*[@-~]', '', text)
        assert status == 0, (label, status)
        expected = 1 if mode == 'old-produce' else 0
        assert marker.is_file() and marker.read_text() == f'PASS\nmode={mode}\nrewards={expected}\n', label
        assert f'ADVANCEMENT_RETENTION_PASS mode={mode} rewards={expected}' in text, label
        assert not re.search(r'\bERROR\b|ADVANCEMENT_RETENTION_FAIL|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError|SQLITE_CONSTRAINT', text), label
        assert 'Enabling Slimefun v4.1.62' in text and 'Disabling Slimefun v4.1.62' in text
        assert 'Enabling SlimefunAdvancements v1.0.7' in text and 'Disabling SlimefunAdvancements v1.0.7' in text
        documents = {}
        for owner in OWNERS:
            documents[owner] = json.loads(path(folder, owner).read_text())
            shutil.copy2(path(folder, owner), output / f'{label}-{owner}.json')
            assert isinstance(documents[owner], dict)
            if mode != 'old-produce':
                backup = path(folder, owner).with_suffix('.json.bak')
                assert backup.is_file() and isinstance(json.loads(backup.read_text()), dict)
        result['cycles'].append(dict(label=label, mode=mode, status='PASS', rewards=expected,
                                    saved_hashes={owner:sha(path(folder,owner)) for owner in OWNERS}))
        (output / 'runtime-results.json').write_text(json.dumps(result, indent=2) + '\n')
        print(args.minecraft, label, 'PASS', flush=True)
        return documents

    folder = prepare('upgrade-server', 'old')
    original = cycle(folder, 'old-produce', '01-old-producer')
    for index, owner in enumerate(OWNERS):
        document = original[owner]
        assert document[KNOWN]['criteria'] == {'keep': 12 if index == 0 else 5, 'retired':74 if index == 0 else 23}
        assert document[ABSENT]['criteria']['legacy'] == (37 if index == 0 else 7)
        assert document[ABSENT]['done'] == (index == 0)
        # Add explicit future-extension fields to the real old-writer fixture. They are not claimed to be old writer output.
        document[KNOWN]['opaque-null'] = None
        document[KNOWN]['future'] = {'count':9223372036854775807, 'label':'<Exact Owner & State>'}
        document['opaque:external'] = {'unknown':None, 'nested':[True, 'retain', -9223372036854775808]}
        path(folder, owner).write_text(json.dumps(document, ensure_ascii=False) + '\n')
        (output / f'old-writer-with-future-fields-{owner}.json').write_text(json.dumps(document, indent=2) + '\n')
    shutil.copy2(inputs / 'advancements-new.jar', folder / 'plugins' / 'SlimefunAdvancements.jar')
    shutil.rmtree(folder / 'plugins' / '.paper-remapped', ignore_errors=True)
    missing = cycle(folder, 'new-missing', '02-upgrade-missing-definitions')
    again = cycle(folder, 'new-missing-restart', '03-missing-definition-restart')
    assert missing == again, 'Repeated missing-definition save changed progress'
    restored = cycle(folder, 'new-restore', '04-restore-definitions')
    final = cycle(folder, 'new-final', '05-restored-restart')
    assert restored == final, 'Restored progress changed on subsequent restart'
    for owner in OWNERS:
        for documents in [missing, again, restored, final]:
            assert documents[owner][KNOWN]['future'] == original[owner][KNOWN]['future']
            assert documents[owner][KNOWN]['opaque-null'] is None
            assert documents[owner]['opaque:external'] == original[owner]['opaque:external']
    old = prepare('original-control-server', 'old')
    for owner in OWNERS:
        path(old, owner).parent.mkdir(parents=True, exist_ok=True)
        path(old, owner).write_text(json.dumps(original[owner]) + '\n')
    dropped = cycle(old, 'old-missing-control', '06-original-data-loss-control')
    for owner in OWNERS:
        assert ABSENT not in dropped[owner]
        assert 'retired' not in dropped[owner][KNOWN]['criteria']
    assert len(result['cycles']) == 6
    (output / 'SUCCESS.txt').write_text(f'6 real advancement producer/upgrade/restart/restoration/control cycles passed on {args.minecraft}.\n')

if __name__ == '__main__':
    main()
