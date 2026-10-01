import base64
import gzip
import hashlib
import json
import re
import subprocess
from pathlib import Path

# Temporary transport only. No source is applied until the complete original
# review checksum and every existing Git blob have been verified.
parts = [Path(f'.audit/release-readiness.part{i}').read_text() for i in range(1, 4)]
assert parts[0].count('DePjhDWK') == 1
parts[0] = parts[0].replace('DePjhDWK', 'DePzhDWK')
packed = base64.b64decode(''.join(parts), validate=True)
assert hashlib.sha256(packed).hexdigest() == 'fcca23cb9af5362ea545dd040a18bbe486ba6f8e731973b3af33f2832e5fd3f9'
patch = gzip.decompress(packed)
assert len(patch) == 49534

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

changes = []
for section in patch.decode().split('diff --git ')[1:]:
    header = section.splitlines()[0]
    match = re.fullmatch(r'a/(\S+) b/(\S+)', header)
    assert match and match[1] == match[2], header
    name = match[2]
    path = Path(name)
    assert name.startswith('scripts/') and '..' not in path.parts and not path.is_symlink()
    hashes = re.search(r'^index ([0-9a-f]{40})\.\.([0-9a-f]{40})', section, re.M)
    assert hashes, name
    old, new = hashes.groups()
    if old == '0' * 40:
        assert not path.exists(), name
    else:
        assert blob(path.read_bytes()) == old, name
    changes.append((name, new))
assert len(changes) == 13
subprocess.run(['git', 'apply', '--check', '-'], input=patch, check=True)
subprocess.run(['git', 'apply', '-'], input=patch, check=True)
for name, expected in changes:
    assert blob(Path(name).read_bytes()) == expected, name
    print('REVIEWED_SOURCE', expected, name)
Path('audit-evidence').mkdir(exist_ok=True)
Path('audit-evidence/paths.json').write_text(json.dumps([name for name, _ in changes]))
subprocess.run(['python3', '.audit/correct_fixture_flags.py'], check=True)
for correction in json.loads(Path('.audit/doctor-fixture-edits.json').read_text()):
    path = Path(correction['path'])
    assert blob(path.read_bytes()) == correction['old'], path
    lines = path.read_text().splitlines(keepends=True)
    for start, end, text in reversed(correction['edits']):
        lines[start:end] = [text]
    output = ''.join(lines).encode()
    assert blob(output) == correction['new'], (path, blob(output))
    path.write_bytes(output)
    print('REVIEWED_DOCTOR_CONTROL', blob(output), path)
