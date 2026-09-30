#!/usr/bin/env python3
"""Exercise the actual full-stack shell error pattern, not a parallel matcher."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

REQUIRED = (
    'Error occurred while enabling',
    'NoClassDefFoundError',
    'NoSuchMethodError',
    'AbstractMethodError',
    'IncompatibleClassChangeError',
    'InvalidConfigurationException',
    r'Cannot load .*\.ya?ml([[:space:]]|$)',
)


def pattern_from(source: str) -> str:
    checks = re.findall(r"if grep -Eq '([^'\n]+)' \"\$normalized\"; then", source)
    reports = re.findall(r"grep -E '([^'\n]+)' \"\$normalized\" >&2", source)
    matches = [pattern for pattern in checks if 'IncompatibleClassChangeError' in pattern]
    if len(matches) != 1:
        raise AssertionError('The active full-stack error predicate must remain present exactly once')
    pattern = matches[0]
    if reports.count(pattern) != 1:
        raise AssertionError('The failure diagnostic must use the same active error predicate')
    if pattern != '|'.join(REQUIRED):
        raise AssertionError('The reviewed enable/linkage/configuration failure set changed')
    block = source.split("if grep -Eq '" + pattern + "' \"$normalized\"; then", 1)[1].split('\n    fi', 1)[0]
    if 'return 1' not in block:
        raise AssertionError('Detected runtime errors must fail the cycle')
    return pattern


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
    source = (root / 'scripts/full_stack_runtime_smoke.sh').read_text(encoding='utf-8')
    pattern = pattern_from(source)
    cases = [
        ('[ERROR] Error occurred while enabling FinalTECH', True),
        ('java.lang.NoClassDefFoundError: missing.Type', True),
        ('java.lang.NoSuchMethodError: missingCall()', True),
        ('java.lang.AbstractMethodError: missingCall()', True),
        ('java.lang.IncompatibleClassChangeError: wrong descriptor', True),
        ('org.bukkit.configuration.InvalidConfigurationException: while parsing a block mapping', True),
        ('[ERROR]: Cannot load plugins/FinalTECH-Changed/language/en-US.yml', True),
        ('[ERROR]: Cannot load plugins/Addon/settings.yaml', True),
        ('[ERROR]: Cannot load plugins/Addon/settings.yml due to invalid input', True),
        ('[INFO]: Enabling FinalTECH-Changed v3.0.2\nPrevious clean shutdown: Yes\n'
         'org.bukkit.configuration.InvalidConfigurationException: while parsing a block mapping', True),
        ('[INFO]: Enabling FinalTECH-Changed v3.0.2', False),
        ('[INFO]: Previous clean shutdown: Yes', False),
        ('[INFO]: Loaded configuration en-US.yml', False),
        ('[INFO]: Cannot load optional texture.png', False),
        ('[WARN]: Optional dependency is not installed', False),
        ('', False),
    ]
    for text, rejected in cases:
        result = subprocess.run(['grep', '-Eq', pattern], input=text + '\n', text=True, check=False)
        if result.returncode not in (0, 1) or (result.returncode == 0) != rejected:
            raise AssertionError(f'Unexpected full-stack classification for {text!r}: {result.returncode}')

    mutations = [source.replace('|InvalidConfigurationException', ''),
                 source.replace('return 1', 'return 0'),
                 source.replace("if grep -Eq '", "if grep -Eq 'disabled|")]
    for changed in mutations:
        try:
            pattern_from(changed)
        except AssertionError:
            continue
        raise AssertionError('A weakened or inconsistent runtime gate was accepted')
    print(f'Full-stack runtime error gate: PASS ({len(cases)} log cases, {len(mutations)} weakening controls)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
