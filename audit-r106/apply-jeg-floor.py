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
          # Keep the same exact-core inputs when invoking the generated Gradle init script directly.
          export SLIMEFUN_COMPATIBILITY_JAR="$PWD/candidate-core/Slimefun-Legacy-candidate.jar"
          export SLIMEFUN_CORE_JAR="$SLIMEFUN_COMPATIBILITY_JAR"
          export SLIMEFUN_LEGACY_JAR="$SLIMEFUN_COMPATIBILITY_JAR"
          # The 26.3 compilation above is a compatibility probe, not the distributable.
          python3 tools/scripts/compile_addon_paper_26_3.py \\
            addon "$SLIMEFUN_COMPATIBILITY_JAR" \\
            1.21.11-R0.1-SNAPSHOT --report-dir "$PWD/report-native-floor"
          (cd addon && PAPER_API_VERSION=1.21.11-R0.1-SNAPSHOT ./gradlew build \\
            --no-daemon --no-build-cache --no-configuration-cache \\
            -I .slimefun-paper-26.3.init.gradle) \\
            2>&1 | tee report-native-floor/tests.log
          # Resolve an independent floor API classpath, not the addon's transitive compile graph.
          cat > report-native-floor/linkage-pom.xml <<'XML'
          <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>audit</groupId><artifactId>jeg-floor-linkage</artifactId><version>1</version><repositories><repository><id>paper</id><url>https://repo.papermc.io/repository/maven-public/</url></repository></repositories><dependencies><dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>1.21.11-R0.1-SNAPSHOT</version></dependency></dependencies></project>
          XML
          mvn --batch-mode --no-transfer-progress -f report-native-floor/linkage-pom.xml \\
            dependency:build-classpath "-Dmdep.outputFile=$PWD/report-native-floor/classpath.txt" \\
            > report-native-floor/classpath.log 2>&1
          mapfile -t JEG_JARS < <(find addon/build/libs -maxdepth 1 -type f -name 'SF_JustEnoughGuide*.jar' \\
            ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort)
          test "${#JEG_JARS[@]}" -eq 1
          mkdir -p report-native-floor/linkage-classes
          javac --release 21 -cp "$(cat report-native-floor/classpath.txt)" \\
            -d report-native-floor/linkage-classes tools/tests/runtime/JegClipboardLinkageProbe.java
          java -cp "report-native-floor/linkage-classes:${JEG_JARS[0]}:candidate-core/Slimefun-Legacy-candidate.jar:$(cat report-native-floor/classpath.txt)" \\
            JegClipboardLinkageProbe | tee report-native-floor/linkage.log
          grep -Fxq 'JEG_CLIPBOARD_FLOOR_PASS methods=2 scenarios=6' report-native-floor/linkage.log
'''
assert text.count(anchor) == 1
text = text.replace(anchor, step + anchor)
old = "if slug in ('slimeeasy', 'dracfunreborn') else os.environ['PAPER_API_VERSION']"
new = "if slug in ('slimeeasy', 'dracfunreborn', 'justenoughguide') else os.environ['PAPER_API_VERSION']"
assert text.count(old) == 1
workflow.write_text(text.replace(old, new))
runner = Path('scripts/verify_legacy.py')
original = runner.read_text()
anchor = '        "test_verify_runtime_configuration.py",\n'
assert original.count(anchor) == 1 and 'test_jeg_bundle_floor.py' not in original
runner.write_text(original.replace(anchor, anchor + '        "test_jeg_bundle_floor.py",\n'))
