from pathlib import Path
import hashlib

workflow = Path('.github/workflows/build-sfl-addons-compat-bundle.yml')
original = workflow.read_bytes()
blob = lambda data: hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert blob(original) == '1ddc697132aa345e6d793b817d04c04994920d35'
text = original.decode()
anchor = '      - name: Verify and build pinned DracFun Reborn\n'
step = '''      - name: Rebuild JEG against the supported API floor
        if: matrix.slug == 'justenoughguide'
        shell: bash
        run: |
          set -euo pipefail
          # The 26.3 compilation above is a compatibility probe, not the distributable.
          python3 tools/scripts/compile_addon_paper_26_3.py \\
            addon "$PWD/candidate-core/Slimefun-Legacy-candidate.jar" \\
            1.21.11-R0.1-SNAPSHOT --report-dir "$PWD/report-native-floor"
          (cd addon && mvn --batch-mode --no-transfer-progress -DskipTests=false verify) \\
            2>&1 | tee report-native-floor/tests.log
          (cd addon && mvn --batch-mode --no-transfer-progress dependency:build-classpath \\
            "-Dmdep.outputFile=$PWD/../report-native-floor/classpath.txt") \\
            > report-native-floor/classpath.log 2>&1
          mapfile -t JEG_JARS < <(find addon/target -maxdepth 1 -type f -name 'SF_JustEnoughGuide*.jar' \\
            ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort)
          test "${#JEG_JARS[@]}" -eq 1
          mkdir -p report-native-floor/linkage-classes
          javac --release 21 -cp "$(cat report-native-floor/classpath.txt)" \\
            -d report-native-floor/linkage-classes tools/tests/runtime/JegClipboardLinkageProbe.java
          java -cp "report-native-floor/linkage-classes:${JEG_JARS[0]}:$(cat report-native-floor/classpath.txt)" \\
            JegClipboardLinkageProbe | tee report-native-floor/linkage.log
          grep -Fxq 'JEG_CLIPBOARD_FLOOR_PASS methods=2 scenarios=6' report-native-floor/linkage.log
'''
assert text.count(anchor) == 1
text = text.replace(anchor, step + anchor)
old = "if slug in ('slimeeasy', 'dracfunreborn') else os.environ['PAPER_API_VERSION']"
new = "if slug in ('slimeeasy', 'dracfunreborn', 'justenoughguide') else os.environ['PAPER_API_VERSION']"
assert text.count(old) == 1
text = text.replace(old, new)
workflow.write_text(text)

# Register the new offline workflow contract tests in every normal core invariant run.
runner = Path('scripts/verify_legacy.py')
original = runner.read_text()
anchor = '        "test_verify_runtime_configuration.py",\n'
assert original.count(anchor) == 1 and 'test_jeg_bundle_floor.py' not in original
runner.write_text(original.replace(anchor, anchor + '        "test_jeg_bundle_floor.py",\n'))
