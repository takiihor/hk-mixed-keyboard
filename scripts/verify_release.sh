#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAB="${1:-}"
BUNDLETOOL_JAR="${BUNDLETOOL_JAR:-}"

fail() { echo "release verification failed: $*" >&2; exit 1; }
[[ -n "$AAB" ]] || fail "usage: scripts/verify_release.sh path/to/app-release.aab"
[[ -f "$AAB" ]] || fail "AAB does not exist: $AAB"
[[ -n "$BUNDLETOOL_JAR" && -f "$BUNDLETOOL_JAR" ]] || \
  fail "set BUNDLETOOL_JAR to a pinned bundletool jar"
command -v java >/dev/null || fail "java is required"
command -v jarsigner >/dev/null || fail "jarsigner is required"
command -v sha256sum >/dev/null || fail "sha256sum is required"

cd "$ROOT"
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || \
  fail "release must be verified from a clean checkout"

python3 corpus/tools/verify_corpus_manifest.py
! rg -n 'android.permission.INTERNET' android/app/src/main/AndroidManifest.xml || \
  fail "source manifest requests INTERNET"
rg -n 'android:allowBackup="false"' android/app/src/main/AndroidManifest.xml >/dev/null || \
  fail "backup must remain disabled"

jarsigner -verify -strict -certs "$AAB" >/dev/null || fail "AAB signature is invalid"
java -jar "$BUNDLETOOL_JAR" validate --bundle="$AAB" >/dev/null

manifest_dump="$(mktemp)"
trap 'rm -f "$manifest_dump"' EXIT
java -jar "$BUNDLETOOL_JAR" dump manifest --bundle="$AAB" --module=base > "$manifest_dump"
! rg -n 'android.permission.INTERNET' "$manifest_dump" || fail "final bundle requests INTERNET"
rg -n 'android.permission.VIBRATE' "$manifest_dump" >/dev/null || fail "expected VIBRATE permission missing"
rg -n 'package="com.hkmixedkeyboard"' "$manifest_dump" >/dev/null || fail "wrong application package"

version_minor="$(awk -F= '$1 == "versionMinor" {print $2}' android/app/version.properties)"
build_number="$(awk -F= '$1 == "buildNumber" {print $2}' android/app/version.properties)"
[[ -n "$version_minor" && -n "$build_number" ]] || fail "invalid version.properties"

echo "git_commit=$(git rev-parse HEAD)"
echo "version_name=0.${version_minor}.0"
echo "version_code=${build_number}"
echo "aab_sha256=$(sha256sum "$AAB" | awk '{print $1}')"
echo "corpus_manifest_sha256=$(sha256sum corpus/sources/corpus_manifest.json | awk '{print $1}')"
