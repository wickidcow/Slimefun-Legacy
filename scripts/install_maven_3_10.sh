#!/usr/bin/env bash
set -euo pipefail

# Verify the published Apache binary before changing the runner's Maven PATH.
MAVEN_VERSION=3.10.2
MAVEN_SHA512=57d2db6b37e7582119137ade544557bba741b29e62109ef81539a2bdfd6d0e641fdd5d8edb014af1816d0510deac6fac8f347433b3a1aa36316ba85aa77b8119
: "${RUNNER_TEMP:?This installer must run on GitHub Actions}"
: "${GITHUB_PATH:?GitHub Actions PATH file is required}"

archive="${RUNNER_TEMP}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
# The live Apache mirror may rotate versions shortly after a newer Maven release.
# Retain the exact pinned 3.10.2 binary and SHA-512 check; never silently upgrade.
if ! curl --fail --location --silent --show-error --retry 3 \
  "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  --output "$archive"; then
  curl --fail --location --silent --show-error --retry 3 \
    "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    --output "$archive"
fi
printf '%s  %s\n' "$MAVEN_SHA512" "$archive" | sha512sum --check --status
tar -xzf "$archive" -C "$RUNNER_TEMP"
maven_bin="${RUNNER_TEMP}/apache-maven-${MAVEN_VERSION}/bin"
"$maven_bin/mvn" --version | grep -F "Apache Maven ${MAVEN_VERSION}"
printf '%s\n' "$maven_bin" >> "$GITHUB_PATH"
