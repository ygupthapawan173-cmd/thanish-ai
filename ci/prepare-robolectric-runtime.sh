#!/usr/bin/env bash
set -euo pipefail
# Robolectric 4.16.1 DefaultSdkProvider: API 35 and API 36, instrumented revision 7.
# SDK 36 may also be requested for app resources by a test configured for SDK 35.
# Fetch from official Maven Central before tests, verify publisher checksums, and
# use the offline resolver during tests. Do not change SDK selection or skip tests.
dest="${RUNNER_TEMP:?RUNNER_TEMP is required}/omega-robolectric-runtime"
mkdir -p "$dest"
tmp="$(mktemp -d "$dest/.download.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT
for version in 15-robolectric-13954326-i7 16-robolectric-13921718-i7; do
  name="android-all-instrumented-$version.jar"
  url="https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/$version/$name"
  curl --fail --location --proto '=https' --tlsv1.2 --retry 5 --retry-all-errors --retry-max-time 240 --connect-timeout 20 --max-time 180 "$url.sha512" -o "$tmp/$name.sha512"
  digest="$(tr -d '\r\n' < "$tmp/$name.sha512")"
  if [[ ! "$digest" =~ ^[a-fA-F0-9]{128}$ ]]; then
    echo "Invalid Maven SHA-512 checksum for $name" >&2; exit 1
  fi
  curl --fail --location --proto '=https' --tlsv1.2 --retry 5 --retry-all-errors --retry-max-time 300 --connect-timeout 20 --max-time 240 "$url" -o "$tmp/$name"
  printf '%s  %s\n' "$digest" "$tmp/$name" | sha512sum -c -
  mv "$tmp/$name" "$dest/$name"
done
printf 'OMEGA_ROBOLECTRIC_JARS=%s\n' "$dest" >> "$GITHUB_ENV"
