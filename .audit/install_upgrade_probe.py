from pathlib import Path
import gzip, hashlib, json


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


payload = b''.join(Path(f'.audit/upgrade-fixture.part{i}').read_bytes() for i in range(1, 5))
assert blob(payload) == 'd1457f4f8ccca1c021e96bb2af01de33372e2dd8'
entries = json.loads(gzip.decompress(payload))
assert len(entries) == 5
for entry in entries:
    path = Path(entry['path'])
    assert not path.is_absolute() and '..' not in path.parts and not path.exists()
    assert str(path).startswith(('compatibility/upgrade-fixture/', 'scripts/old_world_upgrade.py', 'scripts/test_old_world_upgrade.py'))
    data = entry['content'].encode()
    assert blob(data) == entry['sha'], path
for entry in entries:
    path = Path(entry['path'])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(entry['content'])
    print('REVIEWED_UPGRADE_FILE', entry['sha'], path)
