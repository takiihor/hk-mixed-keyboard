#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAB="${1:-}"
UNIVERSAL_APK="${2:-${AAB%.aab}-universal.apk}"
BUNDLETOOL_JAR="${BUNDLETOOL_JAR:-}"
EXPECTED_CERT_SHA256="${HKKBD_EXPECTED_CERT_SHA256:-}"
STORE_FILE="${HKKBD_STORE_FILE:-}"
STORE_PASSWORD="${HKKBD_STORE_PASSWORD:-}"
KEY_ALIAS="${HKKBD_KEY_ALIAS:-}"
KEY_PASSWORD="${HKKBD_KEY_PASSWORD:-}"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APKSIGNER="${APKSIGNER:-$(find "$SDK_ROOT/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -1)}"

fail() { echo "release verification failed: $*" >&2; exit 1; }
[[ -n "$AAB" ]] || fail "usage: scripts/verify_release.sh path/to/app-release.aab [universal-apk-output]"
[[ -f "$AAB" ]] || fail "AAB does not exist: $AAB"
[[ -n "$BUNDLETOOL_JAR" && -f "$BUNDLETOOL_JAR" ]] || \
  fail "set BUNDLETOOL_JAR to a pinned bundletool jar"
command -v java >/dev/null || fail "java is required"
command -v jarsigner >/dev/null || fail "jarsigner is required"
command -v keytool >/dev/null || fail "keytool is required"
command -v openssl >/dev/null || fail "openssl is required"
command -v unzip >/dev/null || fail "unzip is required"
command -v sha256sum >/dev/null || fail "sha256sum is required"
[[ -x "$APKSIGNER" ]] || fail "set APKSIGNER or ANDROID_HOME to an SDK containing apksigner"
[[ "$EXPECTED_CERT_SHA256" =~ ^[0-9A-Fa-f]{64}$ ]] || \
  fail "set HKKBD_EXPECTED_CERT_SHA256 to the approved 64-hex certificate digest"
[[ -f "$STORE_FILE" && -n "$STORE_PASSWORD" && -n "$KEY_ALIAS" && -n "$KEY_PASSWORD" ]] || \
  fail "set the HKKBD release signing variables used to generate final-from-AAB APKs"

cd "$ROOT"
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || \
  fail "release must be verified from a clean checkout"

python3 corpus/tools/verify_corpus_manifest.py
sha256sum --check docs/release/legal_assets.sha256 >/dev/null || fail "legal notice checksum drift"
! rg -n 'android.permission.INTERNET' android/app/src/main/AndroidManifest.xml || \
  fail "source manifest requests INTERNET"
rg -n 'android:allowBackup="false"' android/app/src/main/AndroidManifest.xml >/dev/null || \
  fail "backup must remain disabled"

version_minor="$(awk -F= '$1 == "versionMinor" {print $2}' android/app/version.properties)"
build_number="$(awk -F= '$1 == "buildNumber" {print $2}' android/app/version.properties)"
[[ -n "$version_minor" && -n "$build_number" ]] || fail "invalid version.properties"
version_name="0.${version_minor}.0"

jarsigner -verify -strict -certs "$AAB" >/dev/null || fail "AAB signature is invalid"
java -jar "$BUNDLETOOL_JAR" validate --bundle="$AAB" >/dev/null
aab_cert="$(keytool -printcert -jarfile "$AAB" -rfc | \
  openssl x509 -noout -fingerprint -sha256 | awk -F= '{print $2}' | tr -d ':' | tr 'A-F' 'a-f')"
[[ "$aab_cert" == "${EXPECTED_CERT_SHA256,,}" ]] || fail "AAB certificate does not match approved fingerprint"

manifest_dump="$(mktemp)"
apks_archive="$(mktemp --suffix=.apks)"
store_pass_file="$(mktemp)"
key_pass_file="$(mktemp)"
apksigner_output="$(mktemp)"
trap 'rm -f "$manifest_dump" "$apks_archive" "$store_pass_file" "$key_pass_file" "$apksigner_output"' EXIT
chmod 600 "$store_pass_file" "$key_pass_file"
printf '%s' "$STORE_PASSWORD" > "$store_pass_file"
printf '%s' "$KEY_PASSWORD" > "$key_pass_file"
java -jar "$BUNDLETOOL_JAR" dump manifest --bundle="$AAB" --module=base > "$manifest_dump"
python3 scripts/verify_manifest_policy.py \
  "$manifest_dump" --package com.hkmixedkeyboard --min-sdk 26 --target-sdk 36 \
  --version-code "$build_number" --version-name "$version_name" || \
  fail "final bundle manifest exceeds the approved privacy/component surface"

java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle="$AAB" \
  --output="$apks_archive" \
  --mode=universal \
  --overwrite \
  --ks="$STORE_FILE" \
  --ks-key-alias="$KEY_ALIAS" \
  --ks-pass="file:$store_pass_file" \
  --key-pass="file:$key_pass_file" >/dev/null
mkdir -p "$(dirname "$UNIVERSAL_APK")"
unzip -p "$apks_archive" universal.apk > "$UNIVERSAL_APK"
"$APKSIGNER" verify --verbose --print-certs "$UNIVERSAL_APK" > "$apksigner_output" || \
  fail "final-from-AAB universal APK signature is invalid"
apk_cert="$(awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}' \
  "$apksigner_output" | tr 'A-F' 'a-f')"
[[ "$apk_cert" == "${EXPECTED_CERT_SHA256,,}" ]] || fail "universal APK certificate mismatch"
scripts/verify_apk_policy.sh "$UNIVERSAL_APK" com.hkmixedkeyboard

echo "git_commit=$(git rev-parse HEAD)"
echo "version_name=${version_name}"
echo "version_code=${build_number}"
echo "aab_sha256=$(sha256sum "$AAB" | awk '{print $1}')"
echo "universal_apk=$UNIVERSAL_APK"
echo "universal_apk_sha256=$(sha256sum "$UNIVERSAL_APK" | awk '{print $1}')"
echo "certificate_sha256=${EXPECTED_CERT_SHA256,,}"
echo "corpus_manifest_sha256=$(sha256sum corpus/sources/corpus_manifest.json | awk '{print $1}')"
echo "legal_assets_manifest_sha256=$(sha256sum docs/release/legal_assets.sha256 | awk '{print $1}')"
