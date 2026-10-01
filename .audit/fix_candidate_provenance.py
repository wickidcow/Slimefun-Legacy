#!/usr/bin/env python3
"""Apply the exact reviewed CI-only correction to the revision97 source tree."""
from pathlib import Path
import hashlib
import json


def blob(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


files = {
    '.github/workflows/build-sfl-addons-compat-bundle.yml': 'a91458b2a931ac5eb3b894de28795f9a6ba92427',
    '.github/workflows/paper-26.3-full-stack.yml': 'de16fb4d68b3ef430c462d3b902c7885b6b2b99b',
    'scripts/verify_phase1l_release_artifact.py': '585f80f1fba21824c80591eb10c8d3355c86b63b',
}
for name, expected in files.items():
    assert blob(Path(name).read_bytes()) == expected, name

exports = '''          export SOURCE_COMMIT="$(git rev-parse HEAD)"
          export SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"
          test "$SOURCE_COMMIT" = "$GITHUB_SHA"
'''
verify = '''          python3 scripts/check_bytecode_target.py "$JAR" --expected-java 21
          python3 scripts/verify_release_artifact.py "$JAR" --root .
'''
for filename, step in [
    ('.github/workflows/build-sfl-addons-compat-bundle.yml', 'Build exact candidate core'),
    ('.github/workflows/paper-26.3-full-stack.yml', 'Build candidate'),
]:
    path = Path(filename)
    text = path.read_text()
    start = text.index('      - name: ' + step + '\n')
    end = text.index('\n      - uses: actions/upload-artifact', start)
    section = text[start:end]
    assert section.count('          chmod +x gradlew\n') == 1
    section = section.replace('          chmod +x gradlew\n', exports + '          chmod +x gradlew\n')
    assert section.count('--no-daemon\n') == 1
    section = section.replace('--no-daemon\n', '--no-daemon --no-build-cache --no-configuration-cache\n')
    if step == 'Build candidate':
        needle = '          cp "$(find build/libs -maxdepth 1 -name \'Slimefun-*.jar\' ! -name \'*-sources.jar\' ! -name \'*-javadoc.jar\' -print -quit)" core/Slimefun-Legacy-full-stack.jar\n'
        replacement = '''          JAR="$(find build/libs -maxdepth 1 -name 'Slimefun-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit)"
          test -s "$JAR"
''' + verify + '          cp "$JAR" core/Slimefun-Legacy-full-stack.jar\n'
        assert section.count(needle) == 1
        section = section.replace(needle, replacement)
    else:
        needle = '          test -s "$JAR"\n'
        assert section.count(needle) == 1
        section = section.replace(needle, needle + verify)
    text = text[:start] + section + text[end:]
    path.write_text(text)

path = Path('scripts/verify_phase1l_release_artifact.py')
text = path.read_text()
needle = '        for token in (\n            "name: Reproducible Release",'
insert = '''        for filename in (
            ".github/workflows/build-sfl-addons-compat-bundle.yml",
            ".github/workflows/paper-26.3-full-stack.yml",
        ):
            candidate_workflow = read(root, filename)
            for token in (
                'export SOURCE_COMMIT="$(git rev-parse HEAD)"',
                'export SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"',
                'test "$SOURCE_COMMIT" = "$GITHUB_SHA"',
                "--no-build-cache --no-configuration-cache",
                'python3 scripts/check_bytecode_target.py "$JAR" --expected-java 21',
                'python3 scripts/verify_release_artifact.py "$JAR" --root .',
            ):
                require(token in candidate_workflow,
                        f"Candidate core provenance invariant missing in {filename}: {token}", failures)
            build_position = candidate_workflow.find("./gradlew clean shadowJar")
            pin_position = candidate_workflow.find('export SOURCE_COMMIT=')
            require(0 <= pin_position < build_position,
                    f"Candidate core source identity must be set before Gradle in {filename}", failures)

'''
assert text.count(needle) == 1
path.write_text(text.replace(needle, insert + needle))

manifest = Path('compatibility/sfl-addon-release-matrix.json')
ledger = Path('compatibility/maintained-addon-test-ledger.json')
for path in (manifest, ledger):
    value = json.loads(path.read_text())
    assert value['bundle_revision'] == 97
    value['bundle_revision'] = 98
    path.write_text(json.dumps(value, indent=2) + '\n')

output = {name: blob(Path(name).read_bytes()) for name in files}
output.update({str(path): blob(path.read_bytes()) for path in (manifest, ledger)})
Path('provenance-reviewed-blobs.json').write_text(json.dumps(output, indent=2) + '\n')
print(json.dumps(output, indent=2))
