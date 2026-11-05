#!/usr/bin/env bash
set -euo pipefail

PLUGIN_JAR="${1:?Usage: paper_family_26_2_runtime_smoke.sh <slimefun-jar> [work-directory]}"
WORK_DIR="${2:-build/paper-family-26.2-runtime-smoke}"
SOFTWARE="${SERVER_SOFTWARE:-purpur}"
MC_VERSION="${SERVER_MINECRAFT_VERSION:-26.2}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO_ROOT/scripts/runtime_download.sh"
EXPECTED_PLUGIN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${SERVER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-CI/${EXPECTED_PLUGIN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
STARTUP_TIMEOUT_SECONDS="${SERVER_SMOKE_STARTUP_TIMEOUT:-240}"
SHUTDOWN_TIMEOUT_SECONDS="${SERVER_SMOKE_SHUTDOWN_TIMEOUT:-60}"
EXTRA_PLUGIN_DIR="${SLIMEFUN_SMOKE_EXTRA_PLUGIN_DIR:-}"
EXTRA_PLUGIN_LIST="${SLIMEFUN_SMOKE_EXPECT_PLUGINS_FILE:-}"
EXTRA_PLUGIN_SELECTION="${SLIMEFUN_SMOKE_SELECTION_FILE:-}"
EXPECTED_SOURCE="${SLIMEFUN_SMOKE_SOURCE_COMMIT:-${GITHUB_SHA:-}}"
EXTRA_PLUGIN_COUNT=0
TAB="$(printf '\t')"

case "$SOFTWARE" in
    purpur) SOFTWARE_NAME="Purpur" ;;
    folia) SOFTWARE_NAME="Folia" ;;
    leaf) SOFTWARE_NAME="Leaf" ;;
    *)
        echo "Unsupported runtime software: $SOFTWARE" >&2
        exit 1
        ;;
esac

# Keep all existing Paper-family 26.2 lanes intact. Minecraft 26.3 remains
# explicitly scoped: Purpur and Folia have dedicated experimental readiness lanes;
# Leaf 26.3 is not implicitly certified by this shared harness.
case "${SOFTWARE}:${MC_VERSION}" in
    purpur:26.2|folia:26.2|leaf:26.2|purpur:26.3|folia:26.3) ;;
    *)
        echo "Unsupported server/version combination: ${SOFTWARE} ${MC_VERSION} (allowed: Paper-family 26.2, Purpur 26.3, or Folia 26.3)." >&2
        exit 1
        ;;
esac

if [[ -z "$EXPECTED_PLUGIN_VERSION" ]]; then
    echo "Could not resolve the expected Slimefun Legacy version." >&2
    exit 1
fi

for command in curl jq java; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done

if [[ ! -s "$PLUGIN_JAR" ]]; then
    echo "Slimefun JAR not found or empty: $PLUGIN_JAR" >&2
    exit 1
fi

if [[ -n "$EXTRA_PLUGIN_DIR" || -n "$EXTRA_PLUGIN_LIST" || -n "$EXTRA_PLUGIN_SELECTION" ]]; then
    if [[ -z "$EXTRA_PLUGIN_DIR" || -z "$EXTRA_PLUGIN_LIST" ]]; then
        echo "Extra addon runtime smoke requires both SLIMEFUN_SMOKE_EXTRA_PLUGIN_DIR and SLIMEFUN_SMOKE_EXPECT_PLUGINS_FILE." >&2
        exit 1
    fi
    if [[ ! -d "$EXTRA_PLUGIN_DIR" ]]; then
        echo "Extra plugin directory does not exist: $EXTRA_PLUGIN_DIR" >&2
        exit 1
    fi
    if [[ ! -s "$EXTRA_PLUGIN_LIST" ]]; then
        echo "Expected extra plugin list is missing or empty: $EXTRA_PLUGIN_LIST" >&2
        exit 1
    fi
    if [[ ! -s "$EXTRA_PLUGIN_SELECTION" ]]; then
        echo "Exact extra plugin selection is missing or empty: $EXTRA_PLUGIN_SELECTION" >&2
        exit 1
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        echo "Extra addon runtime smoke requires python3 for exact selection validation." >&2
        exit 1
    fi
    python3 "$REPO_ROOT/scripts/verify_folia_addon_runtime.py" selection \
        --selection "$EXTRA_PLUGIN_SELECTION" --expected-file "$EXTRA_PLUGIN_LIST" \
        --plugin-dir "$EXTRA_PLUGIN_DIR" --expect-source "$EXPECTED_SOURCE"

    while IFS="$TAB" read -r jar plugin extra || [[ -n "$jar" ]]; do
        [[ -n "$jar" && -n "$plugin" && -z "$extra" ]] || {
            echo "Invalid expected addon row: $jar $plugin $extra" >&2
            exit 1
        }
        [[ "$jar" == "$(basename "$jar")" && "$jar" == *.jar ]] || {
            echo "Invalid extra addon JAR name: $jar" >&2
            exit 1
        }
        [[ -s "$EXTRA_PLUGIN_DIR/$jar" ]] || {
            echo "Expected extra addon JAR is missing: $EXTRA_PLUGIN_DIR/$jar" >&2
            exit 1
        }
        EXTRA_PLUGIN_COUNT=$((EXTRA_PLUGIN_COUNT + 1))
    done < "$EXTRA_PLUGIN_LIST"

    if (( EXTRA_PLUGIN_COUNT == 0 )); then
        echo "Extra addon runtime smoke selected zero plugins." >&2
        exit 1
    fi
fi

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins/bStats"
# Keep bStats telemetry disabled in this disposable test server.
printf 'enabled: false\n' > "$WORK_DIR/plugins/bStats/config.yml"
cp "$PLUGIN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-smoke.jar"
if (( EXTRA_PLUGIN_COUNT > 0 )); then
    while IFS="$TAB" read -r jar plugin extra || [[ -n "$jar" ]]; do
        cp "$EXTRA_PLUGIN_DIR/$jar" "$WORK_DIR/plugins/$jar"
    done < "$EXTRA_PLUGIN_LIST"
fi
printf 'eula=true\n' > "$WORK_DIR/eula.txt"
cat > "$WORK_DIR/server.properties" <<'PROPERTIES'
online-mode=false
level-name=smoke-world
max-players=1
spawn-protection=0
view-distance=2
simulation-distance=2
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
motd=Slimefun Legacy paper-family runtime smoke
PROPERTIES

SERVER_BUILD=""
SERVER_URL=""
SERVER_CHANNEL=""

if [[ "$SOFTWARE" == "purpur" ]]; then
    PURPUR_META_URL="https://api.purpurmc.org/v2/purpur/${MC_VERSION}"
    PURPUR_META="$(runtime_download "$PURPUR_META_URL")"
    LATEST_PURPUR_BUILD="$(jq -r '.builds.latest // empty' <<<"$PURPUR_META")"
    if [[ -z "$LATEST_PURPUR_BUILD" ]]; then
        echo "Purpur downloads service did not report a latest build for Minecraft ${MC_VERSION}." >&2
        exit 1
    fi
    SERVER_BUILD="$LATEST_PURPUR_BUILD"
    SERVER_CHANNEL="latest"
    # An explicit, verified build pin makes an experimental 26.3 milestone reproducible.
    if [[ -n "${PURPUR_SMOKE_BUILD:-}" ]]; then
        if [[ ! "$PURPUR_SMOKE_BUILD" =~ ^[0-9]+$ ]]; then
            echo "Purpur build pin must be numeric: $PURPUR_SMOKE_BUILD" >&2
            exit 1
        fi
        if ! jq -e --arg build "$PURPUR_SMOKE_BUILD" 'any(.builds.all[]?; tostring == $build)' <<<"$PURPUR_META" >/dev/null; then
            echo "Purpur build $PURPUR_SMOKE_BUILD is not listed for Minecraft ${MC_VERSION}." >&2
            exit 1
        fi
        SERVER_BUILD="$PURPUR_SMOKE_BUILD"
        SERVER_CHANNEL="pinned"
    fi
    SERVER_URL="https://api.purpurmc.org/v2/purpur/${MC_VERSION}/${SERVER_BUILD}/download"
elif [[ "$SOFTWARE" == "leaf" ]]; then
    LEAF_VERSION_URL="https://api.leafmc.one/v2/projects/leaf/versions/${MC_VERSION}"
    LEAF_VERSION="$(runtime_download "$LEAF_VERSION_URL")"
    SERVER_BUILD="$(jq -r '(.builds // []) | max // empty' <<<"$LEAF_VERSION")"
    if [[ -z "$SERVER_BUILD" ]]; then
        echo "Leaf downloads service did not report a build for Minecraft ${MC_VERSION}." >&2
        exit 1
    fi

    LEAF_BUILD_URL="https://api.leafmc.one/v2/projects/leaf/versions/${MC_VERSION}/builds/${SERVER_BUILD}"
    LEAF_BUILD="$(runtime_download "$LEAF_BUILD_URL")"
    LEAF_FILENAME="$(jq -r '.downloads.application.name // ([.downloads[]? | .name] | first) // empty' <<<"$LEAF_BUILD")"
    SERVER_CHANNEL="$(jq -r '.channel // "unknown"' <<<"$LEAF_BUILD")"
    if [[ -z "$LEAF_FILENAME" ]]; then
        echo "Leaf downloads service did not report a server JAR for Minecraft ${MC_VERSION} build ${SERVER_BUILD}." >&2
        exit 1
    fi
    SERVER_URL="https://api.leafmc.one/v2/projects/leaf/versions/${MC_VERSION}/builds/${SERVER_BUILD}/downloads/${LEAF_FILENAME}"
else
    BUILDS_URL="https://fill.papermc.io/v3/projects/folia/versions/${MC_VERSION}/builds"
    BUILDS_RESPONSE="$(runtime_download "$BUILDS_URL")"

    if jq -e '.ok == false' >/dev/null 2>&1 <<<"$BUILDS_RESPONSE"; then
        jq -r '.message // "PaperMC downloads service returned an unknown error"' <<<"$BUILDS_RESPONSE" >&2
        exit 1
    fi

    SERVER_URL="$(jq -r 'max_by(.id) | .downloads."server:default".url // empty' <<<"$BUILDS_RESPONSE")"
    SERVER_BUILD="$(jq -r 'max_by(.id) | .id // empty' <<<"$BUILDS_RESPONSE")"
    SERVER_CHANNEL="$(jq -r 'max_by(.id) | .channel // "unknown"' <<<"$BUILDS_RESPONSE")"
    if [[ -z "$SERVER_URL" || -z "$SERVER_BUILD" ]]; then
        echo "No Folia build is available for Minecraft ${MC_VERSION}." >&2
        exit 1
    fi
fi

printf 'Software: %s\nMinecraft: %s\nBuild: %s\nChannel: %s\nDownload: %s\nJava: %s\n' \
    "$SOFTWARE_NAME" "$MC_VERSION" "$SERVER_BUILD" "$SERVER_CHANNEL" "$SERVER_URL" "$(java -version 2>&1 | head -n 1)" \
    > "$WORK_DIR/runtime-build.txt"

runtime_download "$SERVER_URL" "$WORK_DIR/server.jar"
test -s "$WORK_DIR/server.jar"

verify_installed_plugins() {
    if (( EXTRA_PLUGIN_COUNT > 0 )); then
        python3 "$REPO_ROOT/scripts/verify_folia_addon_runtime.py" installed \
            --selection "$EXTRA_PLUGIN_SELECTION" --expected-file "$EXTRA_PLUGIN_LIST" \
            --plugin-dir "$WORK_DIR/plugins" --expect-source "$EXPECTED_SOURCE" \
            --core-jar "$PLUGIN_JAR" --receipt "$WORK_DIR/installed-plugins.json"
    fi
}

stop_process() {
    local pid="$1"
    local fd="$2"

    if kill -0 "$pid" >/dev/null 2>&1; then
        printf 'stop\n' >&"$fd" || true
    fi

    local deadline=$((SECONDS + SHUTDOWN_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do
        sleep 1
    done

    if kill -0 "$pid" >/dev/null 2>&1; then
        echo "${SOFTWARE_NAME} did not stop cleanly within ${SHUTDOWN_TIMEOUT_SECONDS}s; terminating it." >&2
        kill "$pid" >/dev/null 2>&1 || true
        sleep 2
    fi
}

run_cycle() {
    local label="$1"
    local require_previous_clean="$2"
    local console_log="$WORK_DIR/${label}.console.log"
    local input_fifo="$WORK_DIR/${label}.stdin"

    verify_installed_plugins
    rm -f "$input_fifo"
    mkfifo "$input_fifo"
    exec 3<>"$input_fifo"

    (
        cd "$WORK_DIR"
        java -Xms512M -Xmx2G -jar server.jar --nogui <&3 > "${label}.console.log" 2>&1
    ) &
    local server_pid=$!
    local startup_deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    local started=false

    while kill -0 "$server_pid" >/dev/null 2>&1 && (( SECONDS < startup_deadline )); do
        if grep -Fq 'Done (' "$console_log" 2>/dev/null; then
            started=true
            break
        fi
        if grep -Fq 'Error occurred while enabling Slimefun' "$console_log" 2>/dev/null; then
            break
        fi
        sleep 2
    done

    if [[ "$started" != true ]]; then
        # Current Leaf builds may exit once to generate the loader configuration
        # before their first real server boot. Accept only this exact, isolated
        # bootstrap lifecycle, never a generic startup failure or missing Done.
        # The recursive retry is bounded to one attempt and must still satisfy
        # both normal Slimefun boot/shutdown cycles below.
        if [[ "$SOFTWARE" == "leaf" && "$label" == "first" && "${LEAF_BOOTSTRAP_CONSUMED:-false}" == false
              && -s "$WORK_DIR/config/leaf-loader.yml" ]] \
              && grep -Fq 'leaf-loader.yml with the default settings.' "$console_log" \
              && grep -Fq 'Check it first, then start the server again.' "$console_log" \
              && ! grep -Eq 'Exception|NoClassDefFoundError|Error occurred while enabling|Could not load' "$console_log" \
              && ! grep -Fq 'Done (' "$console_log" \
              && ! grep -Fq 'Enabling Slimefun' "$console_log"; then
            LEAF_BOOTSTRAP_CONSUMED=true
            stop_process "$server_pid" 3
            wait "$server_pid" >/dev/null 2>&1 || true
            exec 3>&-
            mv "$console_log" "$WORK_DIR/leaf-loader.bootstrap.console.log"
            echo "Leaf generated its loader config; starting both full smoke cycles now." >&2
            run_cycle "$label" "$require_previous_clean"
            return
        fi
        echo "${SOFTWARE_NAME} runtime smoke ${label}: server did not reach Done." >&2
        stop_process "$server_pid" 3
        wait "$server_pid" >/dev/null 2>&1 || true
        exec 3>&-
        cat "$console_log" >&2 || true
        return 1
    fi

    printf 'sf doctor upgrade\n' >&3
    sleep 4
    stop_process "$server_pid" 3

    local server_status=0
    wait "$server_pid" || server_status=$?
    exec 3>&-

    if (( server_status != 0 )); then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: server exited with status ${server_status}." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_PLUGIN_VERSION}" "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: Slimefun ${EXPECTED_PLUGIN_VERSION} was not observed enabling." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Error occurred while enabling Slimefun|Could not load .*Slimefun' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: Slimefun reported an enable/load failure." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if (( EXTRA_PLUGIN_COUNT > 0 )); then
        if grep -Eq 'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError' "$console_log"; then
            echo "${SOFTWARE_NAME} runtime smoke ${label}: an admitted addon reported an enable/linkage failure." >&2
            grep -E 'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError' "$console_log" >&2 || true
            return 1
        fi
        if ! python3 "$REPO_ROOT/scripts/verify_folia_addon_runtime.py" log \
            --console-log "$console_log" --expected-file "$EXTRA_PLUGIN_LIST"; then
            echo "${SOFTWARE_NAME} runtime smoke ${label}: admitted addon startup lifecycle check failed." >&2
            cat "$console_log" >&2 || true
            return 1
        fi
    fi

    if ! grep -Fq 'Slimefun Upgrade Readiness' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: /sf doctor upgrade did not produce its report." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Overall status:[[:space:]]+BLOCKED' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: /sf doctor upgrade reported BLOCKED." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if [[ "$require_previous_clean" == true ]] && ! grep -Eq 'previous shutdown[[:space:]]+Clean' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: the second boot did not observe a clean prior Slimefun shutdown." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Stopping server' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: a normal server shutdown was not observed." >&2
        cat "$console_log" >&2 || true
        return 1
    fi
}

run_cycle "first" false
run_cycle "second" true
verify_installed_plugins

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy ${SOFTWARE_NAME} ${MC_VERSION} runtime smoke: PASS
Slimefun Legacy: ${EXPECTED_PLUGIN_VERSION}
Minecraft: ${MC_VERSION}
${SOFTWARE_NAME} build: ${SERVER_BUILD}
Channel: ${SERVER_CHANNEL}
Runtime Java: $(java -version 2>&1 | head -n 1)
Cycles: 2
Upgrade diagnostics: executed on both boots without BLOCKED status
Clean shutdown persistence: observed on second boot
EOF
if (( EXTRA_PLUGIN_COUNT > 0 )); then
    printf 'Declared Folia addons enabled per boot: %s\n' "$EXTRA_PLUGIN_COUNT" >> "$WORK_DIR/smoke-result.txt"
fi
cat "$WORK_DIR/smoke-result.txt"
