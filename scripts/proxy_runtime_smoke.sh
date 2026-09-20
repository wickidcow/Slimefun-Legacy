#!/usr/bin/env bash
set -euo pipefail

SLIMEFUN_JAR="${1:?Usage: proxy_runtime_smoke.sh <slimefun-jar> <velocity|waterfall> [work-directory]}"
PROXY_KIND="${2:?Usage: proxy_runtime_smoke.sh <slimefun-jar> <velocity|waterfall> [work-directory]}"
WORK_DIR="${3:-build/proxy-runtime-smoke-${PROXY_KIND}}"
MC_VERSION="${SERVER_MINECRAFT_VERSION:-26.2}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$WORK_DIR" != /* ]]; then
    WORK_DIR="$REPO_ROOT/$WORK_DIR"
fi
EXPECTED_SLIMEFUN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${SERVER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-Proxy-Smoke/${EXPECTED_SLIMEFUN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
STARTUP_TIMEOUT_SECONDS="${PROXY_SMOKE_STARTUP_TIMEOUT:-300}"
SHUTDOWN_TIMEOUT_SECONDS="${PROXY_SMOKE_SHUTDOWN_TIMEOUT:-60}"
BACKEND_PORT="${PROXY_SMOKE_BACKEND_PORT:-25566}"
PROXY_PORT="${PROXY_SMOKE_PROXY_PORT:-25577}"
WATERFALL_VERSION="${WATERFALL_VERSION:-1.21}"
WATERFALL_BUILD="${WATERFALL_BUILD:-615}"
JAVA21="${JAVA21_HOME:+${JAVA21_HOME}/bin/java}"

if [[ "$PROXY_KIND" != "velocity" && "$PROXY_KIND" != "waterfall" ]]; then
    echo "Unsupported proxy kind: $PROXY_KIND" >&2
    exit 1
fi

for command in curl jq java python3; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done

if [[ "$PROXY_KIND" == "waterfall" && ( -z "$JAVA21" || ! -x "$JAVA21" ) ]]; then
    echo "Waterfall smoke requires JAVA21_HOME to point at a Java 21 installation." >&2
    exit 1
fi

test -s "$SLIMEFUN_JAR"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/backend/plugins" "$WORK_DIR/proxy"
BACKEND_DIR="$WORK_DIR/backend"
PROXY_DIR="$WORK_DIR/proxy"
BACKEND_LOG="$WORK_DIR/backend.console.log"
BACKEND_NORMALIZED="$WORK_DIR/backend.normalized.log"
PROXY_LOG="$WORK_DIR/proxy.console.log"
PING_JSON="$WORK_DIR/proxy-ping.json"

normalize_log() {
    python3 - "$1" "$2" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
text = re.sub(r"\x1b\[[0-9;?]*[ -/]*[@-~]", "", text)
text = re.sub(r"§[0-9A-FK-ORa-fk-or]", "", text)
Path(sys.argv[2]).write_text(text, encoding="utf-8")
PY
}

wait_for_log() {
    local pid="$1"
    local log="$2"
    local pattern="$3"
    local label="$4"
    local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do
        if grep -Eq "$pattern" "$log" 2>/dev/null; then
            return 0
        fi
        sleep 2
    done
    echo "$label did not reach its startup marker." >&2
    cat "$log" >&2 || true
    return 1
}

stop_process() {
    local pid="$1"
    local fd="$2"
    local command="$3"
    if kill -0 "$pid" >/dev/null 2>&1; then
        printf '%s\n' "$command" >&"$fd" 2>/dev/null || true
    fi
    local deadline=$((SECONDS + SHUTDOWN_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do
        sleep 1
    done
    if kill -0 "$pid" >/dev/null 2>&1; then
        kill -TERM "$pid" >/dev/null 2>&1 || true
        sleep 2
    fi
    if kill -0 "$pid" >/dev/null 2>&1; then
        kill -KILL "$pid" >/dev/null 2>&1 || true
    fi
}

cleanup() {
    set +e
    if [[ -n "${PROXY_PID:-}" ]]; then
        stop_process "$PROXY_PID" 4 "$([[ "$PROXY_KIND" == "waterfall" ]] && echo end || echo shutdown)"
        wait "$PROXY_PID" >/dev/null 2>&1 || true
    fi
    if [[ -n "${BACKEND_PID:-}" ]]; then
        stop_process "$BACKEND_PID" 3 stop
        wait "$BACKEND_PID" >/dev/null 2>&1 || true
    fi
    exec 4>&- 2>/dev/null || true
    exec 3>&- 2>/dev/null || true
}
trap cleanup EXIT

download_paper() {
    local builds_url="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
    local response
    response="$(curl --fail-with-body -sS -H "User-Agent: ${USER_AGENT}" "$builds_url")"
    if jq -e '.ok == false' >/dev/null 2>&1 <<<"$response"; then
        jq -r '.message // "Paper downloads service returned an unknown error"' <<<"$response" >&2
        return 1
    fi

    if [[ "$MC_VERSION" == "26.2" ]]; then
        PAPER_URL="$(jq -r 'first(.[] | select((.channel | ascii_upcase) == "STABLE") | .downloads."server:default".url) // empty' <<<"$response")"
        PAPER_BUILD="$(jq -r 'first(.[] | select((.channel | ascii_upcase) == "STABLE") | .id) // empty' <<<"$response")"
        PAPER_CHANNEL="STABLE"
    else
        PAPER_URL="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .downloads."server:default".url // empty) else empty end' <<<"$response")"
        PAPER_BUILD="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .id // empty) else empty end' <<<"$response")"
        PAPER_CHANNEL="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .channel // "unknown") else "unknown" end' <<<"$response")"
    fi

    if [[ -z "$PAPER_URL" || -z "$PAPER_BUILD" ]]; then
        echo "No usable Paper build found for Minecraft $MC_VERSION." >&2
        return 1
    fi

    curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$BACKEND_DIR/paper.jar" "$PAPER_URL"
    test -s "$BACKEND_DIR/paper.jar"
}

generate_backend_config() {
    printf 'eula=true\n' > "$BACKEND_DIR/eula.txt"
    cat > "$BACKEND_DIR/server.properties" <<PROPERTIES
online-mode=false
server-ip=127.0.0.1
server-port=${BACKEND_PORT}
level-name=proxy-smoke-world
max-players=4
spawn-protection=0
view-distance=2
simulation-distance=2
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
enforce-secure-profile=false
motd=SFL Proxy Backend CI
PROPERTIES

    local fifo="$WORK_DIR/backend-generate.stdin"
    rm -f "$fifo"
    mkfifo "$fifo"
    exec 9<>"$fifo"
    (
        cd "$BACKEND_DIR"
        java -Xms512M -Xmx2G -jar paper.jar --nogui <&9 > "$WORK_DIR/backend-generate.console.log" 2>&1
    ) &
    local pid=$!
    wait_for_log "$pid" "$WORK_DIR/backend-generate.console.log" 'Done \(' "Paper configuration bootstrap"
    printf 'stop\n' >&9 || true
    local deadline=$((SECONDS + SHUTDOWN_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do sleep 1; done
    if kill -0 "$pid" >/dev/null 2>&1; then kill -TERM "$pid" >/dev/null 2>&1 || true; fi
    wait "$pid" >/dev/null 2>&1 || true
    exec 9>&-
    test -s "$BACKEND_DIR/config/paper-global.yml"
    test -s "$BACKEND_DIR/spigot.yml"
}

configure_backend_forwarding() {
    local secret="$1"
    PROXY_MODE="$PROXY_KIND" FORWARDING_SECRET="$secret" python3 - "$BACKEND_DIR/spigot.yml" "$BACKEND_DIR/config/paper-global.yml" <<'PY'
from pathlib import Path
import json
import os
import re
import sys

spigot = Path(sys.argv[1])
paper = Path(sys.argv[2])
mode = os.environ["PROXY_MODE"]
secret = os.environ.get("FORWARDING_SECRET", "")

def set_yaml_scalar(path: Path, key_path: tuple[str, ...], rendered_value: str) -> None:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    stack: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        match = re.match(r"([^:#][^:]*):(?:\s*(.*?))?\s*$", stripped)
        if not match:
            continue
        key = match.group(1).strip()
        rest = (match.group(2) or "").strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        current = tuple(item[1] for item in stack) + (key,)
        if current == key_path:
            newline = "\n" if line.endswith("\n") else ""
            lines[index] = " " * indent + key + ": " + rendered_value + newline
            path.write_text("".join(lines), encoding="utf-8")
            return
        if not rest:
            stack.append((indent, key))
    raise SystemExit(f"Could not locate YAML path {'.'.join(key_path)} in {path}")

if mode == "velocity":
    set_yaml_scalar(spigot, ("settings", "bungeecord"), "false")
    set_yaml_scalar(paper, ("proxies", "velocity", "enabled"), "true")
    set_yaml_scalar(paper, ("proxies", "velocity", "online-mode"), "false")
    set_yaml_scalar(paper, ("proxies", "velocity", "secret"), json.dumps(secret))
    set_yaml_scalar(paper, ("proxies", "bungee-cord", "online-mode"), "false")
else:
    set_yaml_scalar(spigot, ("settings", "bungeecord"), "true")
    set_yaml_scalar(paper, ("proxies", "velocity", "enabled"), "false")
    set_yaml_scalar(paper, ("proxies", "bungee-cord", "online-mode"), "false")
PY
}

download_velocity() {
    local project_json version builds
    project_json="$(curl --fail-with-body -sS -H "User-Agent: ${USER_AGENT}" https://fill.papermc.io/v3/projects/velocity)"
    version="$(jq -r '.versions | to_entries[].value[] | select(contains("SNAPSHOT") | not)' <<<"$project_json" | head -n 1)"
    if [[ -z "$version" || "$version" == "null" ]]; then
        echo "Could not resolve a non-snapshot Velocity version." >&2
        return 1
    fi
    builds="$(curl --fail-with-body -sS -H "User-Agent: ${USER_AGENT}" "https://fill.papermc.io/v3/projects/velocity/versions/${version}/builds")"
    VELOCITY_URL="$(jq -r '
      ([.[] | select((.channel | ascii_upcase) == "RECOMMENDED")] | if length > 0 then max_by(.id) else null end) //
      ([.[] | select((.channel | ascii_upcase) == "STABLE")] | if length > 0 then max_by(.id) else null end) //
      (if length > 0 then max_by(.id) else null end)
      | .downloads."server:default".url // empty
    ' <<<"$builds")"
    VELOCITY_BUILD="$(jq -r '
      ([.[] | select((.channel | ascii_upcase) == "RECOMMENDED")] | if length > 0 then max_by(.id) else null end) //
      ([.[] | select((.channel | ascii_upcase) == "STABLE")] | if length > 0 then max_by(.id) else null end) //
      (if length > 0 then max_by(.id) else null end)
      | .id // empty
    ' <<<"$builds")"
    VELOCITY_CHANNEL="$(jq -r '
      ([.[] | select((.channel | ascii_upcase) == "RECOMMENDED")] | if length > 0 then max_by(.id) else null end) //
      ([.[] | select((.channel | ascii_upcase) == "STABLE")] | if length > 0 then max_by(.id) else null end) //
      (if length > 0 then max_by(.id) else null end)
      | .channel // "unknown"
    ' <<<"$builds")"
    VELOCITY_VERSION="$version"
    if [[ -z "$VELOCITY_URL" || -z "$VELOCITY_BUILD" ]]; then
        echo "Could not resolve a usable Velocity build for $version." >&2
        return 1
    fi
    curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$PROXY_DIR/proxy.jar" "$VELOCITY_URL"
    test -s "$PROXY_DIR/proxy.jar"
}

generate_velocity_config() {
    : > "$PROXY_LOG"
    (
        cd "$PROXY_DIR"
        java -Xms256M -Xmx512M -jar proxy.jar > "$WORK_DIR/velocity-generate.console.log" 2>&1
    ) &
    local pid=$!
    local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do
        if [[ -s "$PROXY_DIR/velocity.toml" ]]; then
            break
        fi
        sleep 1
    done
    if [[ ! -s "$PROXY_DIR/velocity.toml" ]]; then
        echo "Velocity did not generate velocity.toml." >&2
        cat "$WORK_DIR/velocity-generate.console.log" >&2 || true
        return 1
    fi
    kill -TERM "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true

    printf 'sfl-proxy-runtime-smoke-secret\n' > "$PROXY_DIR/forwarding.secret"
    python3 - "$PROXY_DIR/velocity.toml" "$BACKEND_PORT" "$PROXY_PORT" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
backend_port = int(sys.argv[2])
proxy_port = int(sys.argv[3])
text = path.read_text(encoding="utf-8")

def root_scalar(name: str, value: str) -> None:
    global text
    pattern = re.compile(rf"(?m)^{re.escape(name)}\s*=\s*.*$")
    if not pattern.search(text):
        raise SystemExit(f"Missing Velocity setting: {name}")
    text = pattern.sub(f"{name} = {value}", text, count=1)

root_scalar("bind", f'"127.0.0.1:{proxy_port}"')
root_scalar("online-mode", "false")
root_scalar("force-key-authentication", "false")
root_scalar("player-info-forwarding-mode", '"modern"')
root_scalar("forwarding-secret-file", '"forwarding.secret"')

servers = re.search(r"(?ms)^\[servers\]\s*\n(.*?)(?=^\[|\Z)", text)
if not servers:
    raise SystemExit("Velocity [servers] section not found")
section = servers.group(0)
server_lines = section.splitlines(keepends=True)
try_index = next((i for i, line in enumerate(server_lines) if line.strip().startswith("try =")), None)
if try_index is None:
    server_lines.append('try = ["backend"]\n')
else:
    try_end = try_index
    if "[" in server_lines[try_index] and "]" not in server_lines[try_index]:
        while try_end + 1 < len(server_lines):
            try_end += 1
            if server_lines[try_end].strip() == "]":
                break
    server_lines[try_index:try_end + 1] = ['try = ["backend"]\n']
section = "".join(server_lines)
if re.search(r"(?m)^backend\s*=", section):
    section = re.sub(r'(?m)^backend\s*=\s*.*$', f'backend = "127.0.0.1:{backend_port}"', section)
else:
    section = section.replace("[servers]\n", f'[servers]\nbackend = "127.0.0.1:{backend_port}"\n', 1)
text = text[:servers.start()] + section + text[servers.end():]

if re.search(r"(?m)^\[ping-passthrough\]\s*$", text):
    ping = re.search(r"(?ms)^\[ping-passthrough\]\s*\n(.*?)(?=^\[|\Z)", text)
    if not ping:
        raise SystemExit("Velocity ping-passthrough section could not be parsed")
    section = ping.group(0)
    for key in ("version", "players", "description"):
        section = re.sub(rf"(?m)^{key}\s*=\s*false\s*$", f"{key} = true", section)
    text = text[:ping.start()] + section + text[ping.end():]
else:
    root_scalar("ping-passthrough", '"ALL"')

path.write_text(text, encoding="utf-8")
PY
}

download_waterfall() {
    if [[ "$WATERFALL_VERSION" != "1.21" || "$WATERFALL_BUILD" != "615" ]]; then
        echo "This smoke pins the final archived Waterfall 1.21 build 615; override is intentionally unsupported." >&2
        return 1
    fi
    local expected_sha256="5eda8bfd0691e5088701f87020c68964299586df0faba289a634122d282d598c"
    local url="https://fill-data.papermc.io/v1/objects/${expected_sha256}/waterfall-1.21-615.jar"
    curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$PROXY_DIR/proxy.jar" "$url"
    test -s "$PROXY_DIR/proxy.jar"
    printf '%s  %s\n' "$expected_sha256" "$PROXY_DIR/proxy.jar" | sha256sum --check --status
}

write_waterfall_config() {
    cat > "$PROXY_DIR/config.yml" <<YAML
listeners:
- query_port: 25578
  motd: '&1Slimefun Legacy Waterfall CI'
  tab_list: GLOBAL_PING
  query_enabled: false
  proxy_protocol: false
  forced_hosts: {}
  ping_passthrough: true
  priorities:
  - backend
  bind_local_address: true
  host: 127.0.0.1:${PROXY_PORT}
  max_players: 4
  tab_size: 60
  force_default_server: true
remote_ping_cache: -1
network_compression_threshold: 256
permissions:
  default: []
groups: {}
log_pings: true
connection_throttle: -1
connection_throttle_limit: 3
server_connect_timeout: 5000
timeout: 30000
stats: sfl-proxy-runtime-smoke
prevent_proxy_connections: false
player_limit: -1
ip_forward: true
remote_ping_timeout: 5000
servers:
  backend:
    motd: 'SFL Proxy Backend CI'
    address: 127.0.0.1:${BACKEND_PORT}
    restricted: false
forge_support: false
disabled_commands: []
YAML
}

start_backend() {
    rm -f "$WORK_DIR/backend.stdin"
    mkfifo "$WORK_DIR/backend.stdin"
    exec 3<>"$WORK_DIR/backend.stdin"
    (
        cd "$BACKEND_DIR"
        java -Xms512M -Xmx2G -jar paper.jar --nogui <&3 > "$BACKEND_LOG" 2>&1
    ) &
    BACKEND_PID=$!
    wait_for_log "$BACKEND_PID" "$BACKEND_LOG" 'Done \(' "Paper backend"
    printf 'sf doctor proxy\n' >&3
    printf 'sf doctor status\n' >&3
    sleep 4
    normalize_log "$BACKEND_LOG" "$BACKEND_NORMALIZED"

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_SLIMEFUN_VERSION}" "$BACKEND_NORMALIZED"; then
        echo "Slimefun ${EXPECTED_SLIMEFUN_VERSION} did not enable behind the proxy-configured backend." >&2
        cat "$BACKEND_NORMALIZED" >&2
        return 1
    fi
    if grep -Eq 'Error occurred while enabling Slimefun|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError' "$BACKEND_NORMALIZED"; then
        echo "Slimefun linkage/enable failure detected on the proxy-configured backend." >&2
        cat "$BACKEND_NORMALIZED" >&2
        return 1
    fi
    if grep -Fq 'Blocking configuration findings:' "$BACKEND_NORMALIZED"; then
        echo "/sf doctor proxy reported a blocking forwarding configuration problem." >&2
        cat "$BACKEND_NORMALIZED" >&2
        return 1
    fi

    if [[ "$PROXY_KIND" == "velocity" ]]; then
        grep -Fq 'Forwarding mode: Velocity modern' "$BACKEND_NORMALIZED" || {
            echo "Doctor did not recognize Velocity modern forwarding." >&2
            cat "$BACKEND_NORMALIZED" >&2
            return 1
        }
        grep -Fq 'Velocity secret: Configured' "$BACKEND_NORMALIZED" || {
            echo "Doctor did not confirm the Velocity forwarding secret." >&2
            cat "$BACKEND_NORMALIZED" >&2
            return 1
        }
    else
        grep -Fq 'Forwarding mode: Bungee-compatible legacy forwarding' "$BACKEND_NORMALIZED" || {
            echo "Doctor did not recognize Bungee-compatible forwarding for Waterfall." >&2
            cat "$BACKEND_NORMALIZED" >&2
            return 1
        }
    fi
}

start_proxy() {
    rm -f "$WORK_DIR/proxy.stdin"
    mkfifo "$WORK_DIR/proxy.stdin"
    exec 4<>"$WORK_DIR/proxy.stdin"
    local proxy_java="java"
    local startup_pattern='Listening on|Done \('
    if [[ "$PROXY_KIND" == "waterfall" ]]; then
        proxy_java="$JAVA21"
    fi
    (
        cd "$PROXY_DIR"
        "$proxy_java" -Xms256M -Xmx512M -jar proxy.jar <&4 > "$PROXY_LOG" 2>&1
    ) &
    PROXY_PID=$!
    wait_for_log "$PROXY_PID" "$PROXY_LOG" "$startup_pattern" "${PROXY_KIND} proxy"

    if grep -Eq 'Exception in thread "main"|Address already in use|Unable to bind|Could not bind' "$PROXY_LOG"; then
        echo "${PROXY_KIND} proxy reported a startup failure." >&2
        cat "$PROXY_LOG" >&2
        return 1
    fi
}

status_ping_through_proxy() {
    python3 - "$PROXY_PORT" "$PING_JSON" <<'PY'
import json
import socket
import struct
import sys

port = int(sys.argv[1])
out = sys.argv[2]

def varint(value: int) -> bytes:
    value &= 0xFFFFFFFF
    data = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            byte |= 0x80
        data.append(byte)
        if not value:
            return bytes(data)

def read_varint(sock: socket.socket) -> int:
    value = 0
    position = 0
    while True:
        raw = sock.recv(1)
        if not raw:
            raise RuntimeError("Connection closed while reading VarInt")
        current = raw[0]
        value |= (current & 0x7F) << position
        if not current & 0x80:
            return value
        position += 7
        if position >= 35:
            raise RuntimeError("VarInt too large")

def packet(payload: bytes) -> bytes:
    return varint(len(payload)) + payload

host = "127.0.0.1"
address = host.encode("utf-8")
# Status requests are version-agnostic for this smoke; the proxy still has to
# answer the Java server-list protocol and, with passthrough enabled, reach Paper.
handshake = (
    varint(0)
    + varint(0)
    + varint(len(address))
    + address
    + struct.pack(">H", port)
    + varint(1)
)

with socket.create_connection((host, port), timeout=10) as sock:
    sock.settimeout(10)
    sock.sendall(packet(handshake))
    sock.sendall(packet(varint(0)))
    _length = read_varint(sock)
    packet_id = read_varint(sock)
    if packet_id != 0:
        raise RuntimeError(f"Unexpected status response packet id: {packet_id}")
    string_length = read_varint(sock)
    chunks = bytearray()
    while len(chunks) < string_length:
        chunk = sock.recv(string_length - len(chunks))
        if not chunk:
            raise RuntimeError("Connection closed while reading status JSON")
        chunks.extend(chunk)

payload = json.loads(chunks.decode("utf-8"))
with open(out, "w", encoding="utf-8") as handle:
    json.dump(payload, handle, indent=2, sort_keys=True)
    handle.write("\n")

description = payload.get("description", "")
description_text = json.dumps(description, ensure_ascii=False)
if "SFL Proxy Backend CI" not in description_text:
    raise RuntimeError(
        "Proxy status ping did not return the Paper backend MOTD; passthrough route was not proven. "
        f"description={description_text}"
    )
print("Proxy status ping reached Paper backend:", description_text)
PY
}

download_paper
generate_backend_config

PROXY_VERSION_LABEL=""
if [[ "$PROXY_KIND" == "velocity" ]]; then
    download_velocity
    generate_velocity_config
    FORWARDING_SECRET="$(tr -d '\r\n' < "$PROXY_DIR/forwarding.secret")"
    test -n "$FORWARDING_SECRET"
    PROXY_VERSION_LABEL="Velocity ${VELOCITY_VERSION} build ${VELOCITY_BUILD} (${VELOCITY_CHANNEL})"
else
    download_waterfall
    write_waterfall_config
    FORWARDING_SECRET=""
    PROXY_VERSION_LABEL="Waterfall ${WATERFALL_VERSION} build ${WATERFALL_BUILD} (archived)"
fi

configure_backend_forwarding "$FORWARDING_SECRET"
cp "$SLIMEFUN_JAR" "$BACKEND_DIR/plugins/Slimefun-Legacy-proxy-smoke.jar"

start_backend
start_proxy
sleep 3
status_ping_through_proxy

stop_process "$PROXY_PID" 4 "$([[ "$PROXY_KIND" == "waterfall" ]] && echo end || echo shutdown)"
wait "$PROXY_PID" >/dev/null 2>&1 || true
PROXY_PID=""
exec 4>&-

stop_process "$BACKEND_PID" 3 stop
wait "$BACKEND_PID" >/dev/null 2>&1 || true
BACKEND_PID=""
exec 3>&-

normalize_log "$BACKEND_LOG" "$BACKEND_NORMALIZED"

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy proxy runtime smoke: PASS
Proxy: ${PROXY_VERSION_LABEL}
Minecraft backend: ${MC_VERSION}
Paper build: ${PAPER_BUILD}
Paper channel: ${PAPER_CHANNEL}
Slimefun Legacy: ${EXPECTED_SLIMEFUN_VERSION}
Backend bind: 127.0.0.1:${BACKEND_PORT}
Proxy bind: 127.0.0.1:${PROXY_PORT}
Doctor forwarding diagnostics: PASS
Proxy startup: PASS
Proxy -> Paper status passthrough: PASS
Backend MOTD observed through proxy: SFL Proxy Backend CI
Player login / UUID persistence: not covered by this phase
EOF
cat "$WORK_DIR/smoke-result.txt"
