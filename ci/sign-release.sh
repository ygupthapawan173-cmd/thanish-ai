#!/usr/bin/env bash
set -euo pipefail
# Owner-controlled signing, never print or store secrets in source/artifacts.
: "${RELEASE_KEYSTORE_BASE64:?Missing owner keystore}"
: "${RELEASE_KEYSTORE_PASSWORD:?Missing keystore password}"
: "${RELEASE_KEY_ALIAS:?Missing key alias}"
: "${RELEASE_KEY_PASSWORD:?Missing key password}"
: "${RELEASE_CERT_SHA256:?Missing pinned owner certificate SHA256}"
: "${ANDROID_HOME:?Missing Android SDK}"
[[ "$RELEASE_CERT_SHA256" =~ ^[a-fA-F0-9]{64}$ ]]
T=$(mktemp -d); chmod 700 "$T"; trap 'rm -rf "$T"' EXIT
umask 077
printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 -d > "$T/owner.jks"
unset RELEASE_KEYSTORE_BASE64
BT="$ANDROID_HOME/build-tools/35.0.0"
# The caller has already assembled the unsigned release after mandatory tests.
test -s app/build/outputs/apk/release/app-release-unsigned.apk
"$BT/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$T/aligned.apk"
"$BT/apksigner" sign --ks "$T/owner.jks" --ks-key-alias "$RELEASE_KEY_ALIAS" --ks-pass env:RELEASE_KEYSTORE_PASSWORD --key-pass env:RELEASE_KEY_PASSWORD --out "$T/signed.apk" "$T/aligned.apk"
"$BT/zipalign" -c -P 16 4 "$T/signed.apk"
"$BT/apksigner" verify --verbose --print-certs "$T/signed.apk" > "$T/cert.txt"
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$T/cert.txt" | tr '[:upper:]' '[:lower:]')
[[ "$actual" == "${RELEASE_CERT_SHA256,,}" ]]
[[ $(grep -c '^Signer #[0-9]* certificate SHA-256 digest:' "$T/cert.txt") == 1 ]]
VERSION_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n1)
: "${VERSION_NAME:?Missing versionName in app/build.gradle.kts}"
"$BT/aapt" dump badging "$T/signed.apk" | grep -F "name='com.aistudio.pandubabu.omega.v19ultra'"
"$BT/aapt" dump badging "$T/signed.apk" | grep -F "versionName='$VERSION_NAME'"
mkdir -p "${1:?Specify output directory}"
OUT_APK="JARVIS-INFINITY-${VERSION_NAME}-RELEASE.apk"
cp "$T/signed.apk" "$1/$OUT_APK"
(cd "$1"; sha256sum "$OUT_APK" > "$OUT_APK.sha256"; cp "$OUT_APK.sha256" SHA256SUMS.txt)
