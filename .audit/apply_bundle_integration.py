from pathlib import Path
import hashlib,json,subprocess
ROOT=Path.cwd()

def blob(b): return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
expected={
 'scripts/proxy_runtime_smoke.sh':'edd1d266c2560f91a3031f647d85a315a64c8c3a',
 'scripts/verify_legacy.py':'79fef164241184271d2bea8760006d52a2bf5678',
 '.github/workflows/build-sfl-addons-compat-bundle.yml':'1be20796b27c29c8845860e6cdae382724f81656',
 '.github/workflows/paper-26.3-full-stack.yml':'14a2cfc06e8db502d0a48dd324f58210cccdcc94',
 'compatibility/sfl-addon-release-matrix.json':'b78af0d96c1eef2216e514a1e4118a99d3b5196a'}
for n,s in expected.items(): assert blob((ROOT/n).read_bytes())==s,n
Path('audit-evidence').mkdir(exist_ok=True)
negative=subprocess.run(['python3','scripts/test_proxy_identity_capture.py'],capture_output=True,text=True)
Path('audit-evidence/proxy-negative.log').write_text(negative.stdout+negative.stderr)
assert negative.returncode==1 and 'FAILED (failures=2)' in negative.stderr

def replace(name, old, new, count=1):
 p=ROOT/name;t=p.read_text();assert t.count(old)==count,(name,t.count(old));p.write_text(t.replace(old,new))

replace('scripts/proxy_runtime_smoke.sh', '    local output="$WORK_DIR/identity-${label}.txt"\n', '''    local output="$WORK_DIR/identity-${label}.txt"
    # UUID output precedes the research line; wait for the complete command report.
    local completion_marker="Persistence research candidate:"
    if [[ -n "$research_key" ]]; then completion_marker="Research ${research_key}:"; fi
''')
replace('scripts/proxy_runtime_smoke.sh','            if [[ -z "$research_key" ]] || grep -Fq "Research ${research_key}:" "$output"; then', '            if grep -Fq "$completion_marker" "$output"; then')
replace('scripts/verify_legacy.py', '        "test_verify_addon_bytecode.py",\n','        "test_verify_addon_bytecode.py",\n        "test_proxy_identity_capture.py",\n        "test_download_candidate_bundle.py",\n')
p=ROOT/'compatibility/sfl-addon-release-matrix.json';matrix=json.loads(p.read_text());assert matrix['bundle_revision']==92
matrix['bundle_revision']=93
for row in matrix['addons']:
 if row['slug']=='slimeeasy': row['source_commit']='913a609c10871afb67c57f0d2be75a77e13d1d36'
 if row['slug']=='networksexp':
  assert row['source_commit']=='cda26048b792900dd3dd16c1f6ca0075ed8de07b'
  row['source_commit']='41befeb633baabd1e8ee6bc2fdd2b3a66694b643'
p.write_text(json.dumps(matrix,indent=2)+'\n')
workflow='.github/workflows/build-sfl-addons-compat-bundle.yml'
replace(workflow, "      - 'gradle.properties'\n", "      - 'gradle.properties'\n      - 'src/**'\n      - 'build.gradle.kts'\n      - 'gradle/**'\n      - 'scripts/**'\n      - '.github/workflows/paper-26.3-full-stack.yml'\n",2)
replace(workflow, '      - name: Verify and build pinned DracFun Reborn\n', '''      - name: Rebuild native SlimeEasy against the supported floor
        if: matrix.slug == 'slimeeasy'
        shell: bash
        run: |
          set -euo pipefail
          mkdir -p addon/libs report-native-floor
          find addon/libs -maxdepth 1 -type f -iname '*slimefun*.jar' -delete
          cp candidate-core/Slimefun-Legacy-candidate.jar addon/libs/Slimefun-Legacy-Core.jar
          chmod +x addon/gradlew
          # The newer-native-API build is a probe only. Never distribute its output.
          (cd addon && ./gradlew clean build cargoRegressionJar \\
            -PpaperDevBundleVersion=1.21.11-R0.1-SNAPSHOT \\
            --no-daemon --no-build-cache --no-configuration-cache) \\
            2>&1 | tee report-native-floor/compile.log
      - name: Verify and build pinned DracFun Reborn
''')
replace(workflow, "              'paper_26_3_api': os.environ['PAPER_API_VERSION'],\n", "              'paper_26_3_api': os.environ['PAPER_API_VERSION'],\n              'distributable_api': '1.21.11-R0.1-SNAPSHOT' if slug in ('slimeeasy', 'dracfunreborn') else os.environ['PAPER_API_VERSION'],\n")
replace(workflow,'      - uses: actions/upload-artifact@v7\n        with:\n          name: sf-addon-${{ matrix.slug }}', '''      - name: Preserve compile and native-floor evidence
        if: always()
        run: |
          mkdir -p staged/evidence
          if test -d report; then cp -r report "staged/evidence/${{ matrix.slug }}-candidate"; fi
          if test -d report-native-floor; then cp -r report-native-floor "staged/evidence/${{ matrix.slug }}-floor"; fi
      - uses: actions/upload-artifact@v7
        if: always()
        with:
          name: sf-addon-${{ matrix.slug }}''')
workflow='.github/workflows/paper-26.3-full-stack.yml'
replace(workflow,"      - 'scripts/full_stack_runtime_smoke.sh'\n", "      - 'scripts/full_stack_runtime_smoke.sh'\n      - 'scripts/download_candidate_bundle.py'\n      - 'scripts/test_download_candidate_bundle.py'\n",2)
replace(workflow,'    name: Fetch exact addon bundle under test\n    runs-on: ubuntu-latest','    name: Fetch exact addon bundle under test\n    runs-on: ubuntu-latest\n    timeout-minutes: 35')
replace(workflow,'          TRIGGER_HEAD_SHA: ${{ github.event.workflow_run.head_sha }}\n', '          TRIGGER_HEAD_SHA: ${{ github.event.workflow_run.head_sha }}\n          PR_HEAD_SHA: ${{ github.event.pull_request.head.sha }}\n')
replace(workflow,'          if [[ "$GITHUB_EVENT_NAME" == "workflow_run" ]]; then\n', '''          if [[ "$GITHUB_EVENT_NAME" == "pull_request" ]]; then
            python3 scripts/download_candidate_bundle.py \\
              --repository "$GITHUB_REPOSITORY" --head "$PR_HEAD_SHA" \\
              --source "$GITHUB_SHA" --output bundle
          elif [[ "$GITHUB_EVENT_NAME" == "workflow_run" ]]; then
''')
replace(workflow,"        minecraft: ['26.2', '26.3']", "        minecraft: ['1.21.11', '26.2', '26.3']")
p=ROOT/workflow;t=p.read_text();pos=t.index('  runtime:\n');t=t[:pos]+t[pos:].replace("          java-version: '25'", "          java-version: ${{ matrix.minecraft == '1.21.11' && '21' || '25' }}",1);p.write_text(t)
expected_after={
 'scripts/proxy_runtime_smoke.sh':'b008fb93d3d867f58926adccfeef77b3863ed724',
 'scripts/verify_legacy.py':'b5922dca73d283b914463cdf8ff6d51227c8997d',
 '.github/workflows/build-sfl-addons-compat-bundle.yml':'a91458b2a931ac5eb3b894de28795f9a6ba92427',
 '.github/workflows/paper-26.3-full-stack.yml':'de16fb4d68b3ef430c462d3b902c7885b6b2b99b',
 'compatibility/sfl-addon-release-matrix.json':'8f6f50a01a009b17236ff1c23c43f6bb02f4dc4d'}
for name,sha in expected_after.items(): assert blob(Path(name).read_bytes())==sha,(name,blob(Path(name).read_bytes()),sha)
for name in ['scripts/test_proxy_identity_capture.py','scripts/download_candidate_bundle.py','scripts/test_download_candidate_bundle.py']:
 expected_after[name]=blob(Path(name).read_bytes())
Path('audit-evidence/reviewed-blobs.json').write_text(json.dumps(expected_after,indent=2)+'\n')
