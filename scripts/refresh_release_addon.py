#!/usr/bin/env python3
"""Replace one explicitly pinned release addon while retaining every other member."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

from verify_addon_bytecode import inspect_jar
from verify_published_addon_bundle import validate_bundle


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def refresh(config: dict, matrix: dict, original: Path, addon: Path, output: Path) -> dict:
    require(output.resolve() not in (original.resolve(), addon.resolve()), "Output must not overwrite an input")
    require(hashlib.sha256(original.read_bytes()).hexdigest() == config['old_bundle_sha256'],
            "Published bundle changed; rebase the refresh instead of overwriting it")
    require(hashlib.sha256(addon.read_bytes()).hexdigest() == config['new_sha256'], "New addon checksum mismatch")
    require(matrix['bundle_revision'] == config['bundle_revision'], "Bundle revision changed")
    result = validate_bundle(original)
    require(result['core_source_commit'] == config['core_source_commit'], "Core provenance changed")
    require(result['addon_count'] == config['expected_addons'], "Addon count changed")
    bytecode = inspect_jar(addon, 21)
    require(not bytecode['incompatible_classes'], "New addon exceeds the Java 21 runtime floor")
    with zipfile.ZipFile(addon) as jar:
        descriptor = jar.read('plugin.yml').decode('utf-8')
        version = re.search(r'''(?m)^version:\s*['"]?([^'"\s]+)''', descriptor)
        require(version is not None and version.group(1) == config['new_version'], "New addon version mismatch")
        require(not any(n.startswith(('audit/', 'org/junit/', 'org/mockbukkit/')) for n in jar.namelist()),
                "Test fixtures must not be distributed")
    with zipfile.ZipFile(original) as archive:
        members = {n: archive.read(n) for n in archive.namelist()}
    manifest = json.loads(members['SF_ADDON_MANIFEST.json'])
    rows = [r for r in manifest['addons'] if r['repository'] == config['repository']]
    require(len(rows) == 1, "Expected exactly one addon manifest row")
    row = rows[0]
    require(row['jar'] == config['old_jar'] and row['commit'] == config['old_commit'], "Old addon identity changed")
    require(config['new_jar'] not in members, "New addon is already bundled")
    locked = {r['repository']: r['source_commit'] for r in matrix['addons']}
    expected = {r['repository']: r['commit'] for r in manifest['addons']}
    expected[config['repository']] = config['new_commit']
    require(locked == expected, "Other source pins changed; rebase the targeted refresh")
    sums = {line.split('  ', 1)[1]: line.split('  ', 1)[0]
            for line in members['SHA256SUMS.txt'].decode().splitlines() if line}
    jars = {n: hashlib.sha256(b).hexdigest() for n, b in members.items() if n.endswith('.jar')}
    require(sums == jars, "Original checksum list does not match its JARs")
    del members[config['old_jar']]
    members[config['new_jar']] = addon.read_bytes()
    row.update(commit=config['new_commit'], jar=config['new_jar'], source_jar=config['new_jar'],
               version=config['new_version'], sha256=config['new_sha256'],
               distributable_api=config['new_distributable_api'],
               build_core_version=config['new_build_core_version'],
               paper_26_3_api=config['new_paper_26_3_api'])
    manifest['bundle_revision'] = config['bundle_revision']
    manifest['targeted_refresh'] = {'repository': config['repository'],
                                    'previous_bundle_sha256': config['old_bundle_sha256'],
                                    'release_tag': config['release_tag']}
    members['SF_ADDON_MANIFEST.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    members['SHA256SUMS.txt'] = ''.join(
        f'{hashlib.sha256(members[n]).hexdigest()}  {n}\n'
        for n in sorted(members) if n.endswith('.jar')).encode()
    members['COMPATIBILITY.txt'] += ('\n' + config['compatibility_note'] + '\n').encode()
    # Fixed metadata makes the reviewed candidate and published ZIP reproducible.
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(members.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compresslevel=9)
    validated = validate_bundle(output)
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(output) as after:
        unchanged = [n for n in before.namelist() if n.endswith('.jar') and n != config['old_jar']]
        require(len(unchanged) == config['expected_addons'] - 1, "Unexpected unchanged-addon count")
        require(all(before.read(n) == after.read(n) for n in unchanged), "Unrelated addon changed")
        allowed = {config['old_jar'], 'SF_ADDON_MANIFEST.json', 'SHA256SUMS.txt', 'COMPATIBILITY.txt'}
        require(all(before.read(n) == after.read(n) for n in before.namelist() if n not in allowed),
                "Unrelated archive member changed")
    return {**validated, 'bundle_revision': config['bundle_revision'], 'unchanged_addons': len(unchanged),
            'new_jar': config['new_jar'], 'new_jar_sha256': config['new_sha256'],
            'bundle_sha256': hashlib.sha256(output.read_bytes()).hexdigest()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--matrix', type=Path, required=True)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--addon', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(refresh(json.loads(args.config.read_text()), json.loads(args.matrix.read_text()),
                             args.original, args.addon, args.output), indent=2))


if __name__ == '__main__':
    main()
