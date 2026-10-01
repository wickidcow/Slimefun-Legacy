#!/usr/bin/env python3
"""Disposable actual old/new addon startup and storage checks. Never run against a live server directory."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

FIRST = '00000000-1111-2222-3333-444444444444'
SECOND = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
EXTRA = 'future:\n  count: 9223372036854775807\n  charge: 123.4567\n  labels: [keep, me]\n  owner: ExactOwner\n'
SEEDS = {
    'Galactifun': 'gates:\n  deadbeef:\n    world: world\n    x: -37\n    y: 70\n    z: 9001\n'
        '  ffffffff:\n    world: unloaded_planet\n    x: -2147483648\n    y: -64\n    z: 2147483647\n' + EXTRA,
    'SlimeHUD': FIRST + ':\n  waila: false\n  display: actionbar\n' + SECOND
        + ':\n  waila: true\n  display: bossbar\n' + EXTRA,
}

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def run(command: list[str], **kwargs):
    return subprocess.run(command, check=True, timeout=180, **kwargs)

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--addon', choices=SEEDS, required=True)
    parser.add_argument('--minecraft', required=True)
    parser.add_argument('--inputs', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inputs = args.inputs.resolve()
    output = args.output.resolve()
    if output.exists():
        raise SystemExit('Refusing to reuse an existing server/output directory')
    output.mkdir(parents=True)
    addon = args.addon
    lower = addon.lower()
    filenames = {'core': 'Slimefun-Legacy-Core.jar', 'probe': 'PersistenceRuntimeProbe.jar',
                 'old': f'{lower}-old.jar', 'new': f'{lower}-new.jar'}
    paths = {key: inputs / value for key, value in filenames.items()}
    for path in paths.values():
        assert path.is_file(), path
    provenance = json.loads((inputs / 'provenance.json').read_text())
    for path in paths.values():
        assert sha(path) == provenance['jars'][path.name], path
    server = output / 'server.jar'
    result = run(['curl', '-fsSL', '--retry', '3', '-H', 'User-Agent: SFL-Persistence-Validation/1.0',
                  f'https://fill.papermc.io/v3/projects/paper/versions/{args.minecraft}/builds'], capture_output=True, text=True)
    builds = json.loads(result.stdout)
    assert isinstance(builds, list) and builds
    stable = [b for b in builds if b.get('channel') == 'STABLE']
    selected = max(stable or builds, key=lambda b: b['id'])
    run(['curl', '-fsSL', '--retry', '3', '-H', 'User-Agent: SFL-Persistence-Validation/1.0',
         selected['downloads']['server:default']['url'], '-o', str(server)])
    runtime = dict(minecraft=args.minecraft, paper_build=selected['id'], channel=selected['channel'],
                   server_sha256=sha(server), java=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),
                   addon=addon, inputs=provenance, cycles=[])
    (output / 'runtime-inputs.json').write_text(json.dumps(runtime, indent=2) + '\n')

    def prepare(name: str, contents: bytes, version: str) -> Path:
        folder = output / name
        (folder / 'plugins' / addon).mkdir(parents=True)
        shutil.copy2(paths['core'], folder / 'plugins' / filenames['core'])
        shutil.copy2(paths['probe'], folder / 'plugins' / filenames['probe'])
        shutil.copy2(paths[version], folder / 'plugins' / (addon + '.jar'))
        shutil.copy2(server, folder / 'server.jar')
        (folder / 'eula.txt').write_text('eula=true\n')
        (folder / 'server.properties').write_text(
            'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nlevel-name=world\n'
            'view-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=1\n'
            'pause-when-empty-seconds=-1\n')
        data(folder).write_bytes(contents)
        (folder / 'expected-original.bin').write_bytes(contents)
        return folder

    def data(folder: Path) -> Path:
        return folder / 'plugins' / addon / ('stargates.yml' if addon == 'Galactifun' else 'player.yml')

    def cycle(folder: Path, mode: str, label: str, unchanged: bytes | None = None) -> str:
        resultfile = folder / 'safety-probe-result.txt'
        resultfile.unlink(missing_ok=True)
        log = output / f'{label}.console.log'
        command = ['java', '-Xms512M', '-Xmx2G', f'-Dpersistence.addon={addon}',
                   f'-Dpersistence.mode={mode}', '-jar', 'server.jar', '--nogui']
        with log.open('wb') as stream:
            process = subprocess.Popen(command, cwd=folder, stdout=stream, stderr=subprocess.STDOUT,
                                       stdin=subprocess.DEVNULL, start_new_session=True)
            try:
                status = process.wait(timeout=420)
            except subprocess.TimeoutExpired:
                import signal
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                raise AssertionError(f'{label}: server/probe timed out; no successful evidence')
        text = log.read_text(encoding='utf-8', errors='replace')
        text = re.sub(r'\x1b\[[0-9;?]*[ -/]*[@-~]', '', text)
        (output / f'{label}.normalized.log').write_text(text)
        assert status == 0, (label, status)
        assert resultfile.is_file(), (label, 'probe never completed')
        result = resultfile.read_text()
        assert result == f'PASS\naddon={addon}\nmode={mode}\n', (label, result)
        assert f'PERSISTENCE_RUNTIME_PASS addon={addon} mode={mode}' in text, label
        assert 'PERSISTENCE_RUNTIME_FAIL' not in text, label
        assert 'Enabling Slimefun v4.1.62' in text and 'Disabling Slimefun v4.1.62' in text, label
        assert not re.search(r'NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError', text), label
        if mode == 'new-broken':
            diagnostic = ('Galactifun is disabling to preserve stargates.yml.' if addon == 'Galactifun'
                          else 'Disabling to preserve player.yml.')
            assert diagnostic in text and 'InvalidConfigurationException' in text, label
        elif mode not in ('old-broken', 'new-retry'):
            assert not re.search(r'\bERROR\b|InvalidConfigurationException|SQLITE_CONSTRAINT', text), label
        elif mode == 'new-retry':
            assert 'previous file is retained and changes remain pending' in text, label
        if unchanged is not None:
            assert data(folder).read_bytes() == unchanged, (label, 'shutdown changed retained bytes')
        shutil.copy2(data(folder), output / f'{label}.data.yml')
        record = dict(label=label, mode=mode, status='PASS', data_sha256=sha(data(folder)), result=result.strip())
        runtime['cycles'].append(record)
        (output / 'runtime-results.json').write_text(json.dumps(runtime, indent=2) + '\n')
        print(f'{addon} {args.minecraft}: {label} PASS', flush=True)
        return text

    # Real old production writer supplies the upgrade input. Do not fabricate a new-writer stand-in.
    valid = prepare('upgrade-world', SEEDS[addon].encode(), 'old')
    cycle(valid, 'old-produce', '01-old-producer')
    baseline = data(valid).read_bytes()
    (valid / 'expected-original.bin').write_bytes(baseline)
    shutil.copy2(paths['new'], valid / 'plugins' / (addon + '.jar'))
    shutil.rmtree(valid / 'plugins' / '.paper-remapped', ignore_errors=True)
    cycle(valid, 'new-verify', '02-new-upgrade', baseline)
    cycle(valid, 'new-verify', '03-new-restart', baseline)
    cycle(valid, 'new-retry', '04-failed-save-retry')
    after_retry = data(valid).read_bytes()
    cycle(valid, 'new-verify-retry', '05-retry-restart', after_retry)

    broken = (SEEDS[addon] + 'malformed: [\n').encode()
    old_bad = prepare('old-broken-world', broken, 'old')
    cycle(old_bad, 'old-broken', '06-old-corruption-control')
    assert data(old_bad).read_bytes() != broken, 'Original code did not reproduce corruption'
    new_bad = prepare('new-broken-world', broken, 'new')
    cycle(new_bad, 'new-broken', '07-new-corruption-refusal', broken)
    cycle(new_bad, 'new-broken', '08-new-corruption-restart', broken)
    assert len(runtime['cycles']) == 8
    (output / 'SUCCESS.txt').write_text(f'{addon}: 8 actual server cycles passed on {args.minecraft}.\n')

if __name__ == '__main__':
    main()
