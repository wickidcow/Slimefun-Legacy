#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="${1:-}"
DESTINATION="${2:-$ROOT_DIR/.upstream-staged}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ -z "$SOURCE" ]]; then
  SOURCE="$WORK_DIR/upstream.zip"
  curl --fail --location --retry 3 \
    https://github.com/SlimefunGuguProject/Slimefun4/archive/refs/heads/master.zip \
    --output "$SOURCE"
fi

if [[ -d "$SOURCE" ]]; then
  UPSTREAM_ROOT="$SOURCE"
else
  unzip -q "$SOURCE" -d "$WORK_DIR/extracted"
  UPSTREAM_ROOT="$(find "$WORK_DIR/extracted" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
fi

rm -rf "$DESTINATION"
mkdir -p "$DESTINATION"
cp -a "$UPSTREAM_ROOT"/. "$DESTINATION"/

(
  cd "$DESTINATION"
  git apply --check --whitespace=error-all "$ROOT_DIR/patches/albion-english.patch"
  git apply --whitespace=error-all "$ROOT_DIR/patches/albion-english.patch"
)
python3 "$ROOT_DIR/scripts/verify_english.py" "$DESTINATION"

printf 'Prepared English upstream tree at: %s\n' "$DESTINATION"
