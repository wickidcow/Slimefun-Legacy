from pathlib import Path
import hashlib
import json


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


path = Path('scripts/full_stack_runtime_smoke.sh')
assert blob(path.read_bytes()) == 'e26cb2e4a3a7615574dea01b83c1c8c81d4f88fa'
text = path.read_text()
old = 'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError'
new = old + r'|InvalidConfigurationException|Cannot load .*\.ya?ml([[:space:]]|$)'
assert text.count(old) == 2
text = text.replace(old, new)
assert text.count('Full-stack linkage/enable failure detected') == 1
text = text.replace('Full-stack linkage/enable failure detected', 'Full-stack linkage/enable/configuration failure detected')
assert text.count('Linkage/enable failures: none') == 1
text = text.replace('Linkage/enable failures: none', 'Linkage/enable failures: none\nConfiguration-load failures: none')
path.write_text(text)
runner = Path('scripts/verify_legacy.py')
assert blob(runner.read_bytes()) == '6bc0ef62b0eb10f2efba2d23304c0bf96eeb45e4'
text = runner.read_text()
line = '        "test_summarize_deprecations.py",'
assert text.count(line) == 1
runner.write_text(text.replace(line, line + '\n        "test_full_stack_runtime_error_gate.py",'))
Path('audit-evidence').mkdir(exist_ok=True)
Path('audit-evidence/old-error-pattern.txt').write_text(old)
files = [str(path), str(runner), 'scripts/test_full_stack_runtime_error_gate.py']
Path('audit-evidence/source-manifest.json').write_text(json.dumps({name: blob(Path(name).read_bytes()) for name in files}, indent=2))
print('Reviewed runtime configuration guard applied:', files)
