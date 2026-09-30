from pathlib import Path
import gzip
import hashlib
import json
import re
import subprocess


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


compressed = b''.join(Path(f'.audit/atomic-migration.part{i}').read_bytes() for i in range(1, 6))
assert len(compressed) == 23120
assert blob(compressed) == '562397643d3a8fc91b163adfb0663cfd995e5afd', blob(compressed)
patch = gzip.decompress(compressed)
assert len(patch) == 109245
changes = []
for section in patch.decode().split('diff --git ')[1:]:
    header = section.splitlines()[0]
    match = re.fullmatch(r'a/(\S+) b/(\S+)', header)
    assert match and match[1] == match[2], header
    name = match[2]
    path = Path(name)
    assert not path.is_absolute() and '..' not in path.parts and not path.is_symlink()
    assert name.startswith(('src/main/java/', 'src/test/java/', 'docs/', '.github/workflows/'))
    hashes = re.search(r'^index ([0-9a-f]{40})\.\.([0-9a-f]{40})', section, re.M)
    assert hashes, name
    old, new = hashes.groups()
    if old == '0' * 40:
        assert not path.exists(), name
    else:
        assert blob(path.read_bytes()) == old, (name, blob(path.read_bytes()))
    changes.append((name, new))
assert len(changes) == 14 and len({name for name, _ in changes}) == 14
subprocess.run(['git', 'apply', '--check', '-'], input=patch, check=True)
subprocess.run(['git', 'apply', '-'], input=patch, check=True)
for name, expected in changes:
    assert blob(Path(name).read_bytes()) == expected, name
    print('VERIFIED_SOURCE', expected, name)
Path('audit-evidence').mkdir(exist_ok=True)
Path('audit-evidence/reviewed-paths.json').write_text(json.dumps([name for name, _ in changes if not name.startswith('.github/')]))
Path('audit-evidence/source-hashes.json').write_text(json.dumps(dict(changes), indent=2))
subprocess.run(['git', 'diff', '--check'], check=True)
