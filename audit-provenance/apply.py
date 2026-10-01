from pathlib import Path
import hashlib
import subprocess

blob = lambda data: hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
guard = Path('scripts/verify_phase1l_release_artifact.py')
assert blob(guard.read_bytes()) == '585f80f1fba21824c80591eb10c8d3355c86b63b'
text = guard.read_text()
needle = '        for token in (\n            "name: Reproducible Release",'
inserted = '''        require("\\n  core:\\n" in bundle_workflow and "\\n  compile-addons:\\n" in bundle_workflow,
                "Addon bundle core job boundary is missing", failures)
        bundle_core = bundle_workflow.split("\\n  core:\\n", 1)[-1].split("\\n  compile-addons:\\n", 1)[0]
        for token in (
            "Pin bundle core source identity",
            'source_commit="$(git rev-parse HEAD)"',
            'echo "SOURCE_COMMIT=$source_commit" >> "$GITHUB_ENV"',
            'echo "SOURCE_DATE_EPOCH=$(git show -s --format=%ct "$source_commit")" >> "$GITHUB_ENV"',
            '--no-build-cache --no-configuration-cache',
            'python3 scripts/check_bytecode_target.py "$JAR" --expected-java 21',
            'python3 scripts/verify_release_artifact.py "$JAR" --root .',
        ):
            require(token in bundle_core, f"Addon bundle core provenance invariant missing: {token}", failures)
        pin = bundle_core.find("Pin bundle core source identity")
        build_at = bundle_core.find("./gradlew clean shadowJar")
        verify_at = bundle_core.find("python3 scripts/verify_release_artifact.py")
        upload_at = bundle_core.find("uses: actions/upload-artifact")
        require(0 <= pin < build_at < verify_at < upload_at,
                "Addon bundle core must pin identity before building and verify its JAR before upload", failures)

'''
assert text.count(needle) == 1
guard.write_text(text.replace(needle, inserted + needle))
probe = subprocess.run(['python3', str(guard), '.'], capture_output=True, text=True)
Path('evidence/original-negative-control.txt').write_text(probe.stdout + probe.stderr)
assert probe.returncode == 1 and 'Addon bundle core provenance invariant missing:' in probe.stdout, probe.stdout + probe.stderr
workflow = Path('.github/workflows/build-sfl-addons-compat-bundle.yml')
assert blob(workflow.read_bytes()) == 'a91458b2a931ac5eb3b894de28795f9a6ba92427'
text = workflow.read_text()
needle = '      - name: Build exact candidate core\n'
addition = '''      - name: Pin bundle core source identity
        shell: bash
        run: |
          set -euo pipefail
          source_commit="$(git rev-parse HEAD)"
          echo "SOURCE_COMMIT=$source_commit" >> "$GITHUB_ENV"
          echo "SOURCE_DATE_EPOCH=$(git show -s --format=%ct "$source_commit")" >> "$GITHUB_ENV"
'''
assert text.count(needle) == 1
text = text.replace(needle, addition + needle)
needle = "          ./gradlew clean shadowJar -PpaperApiVersion='${{ needs.prepare.outputs.paper_api }}' --no-daemon"
assert text.count(needle) == 1
text = text.replace(needle, needle + ' --no-build-cache --no-configuration-cache')
needle = '          test -s "$JAR"\n          mkdir -p candidate-core\n'
replacement = '          test -s "$JAR"\n          python3 scripts/check_bytecode_target.py "$JAR" --expected-java 21\n          python3 scripts/verify_release_artifact.py "$JAR" --root .\n          mkdir -p candidate-core\n'
assert text.count(needle) == 1
workflow.write_text(text.replace(needle, replacement))
subprocess.run(['python3', str(guard), '.'], check=True)
