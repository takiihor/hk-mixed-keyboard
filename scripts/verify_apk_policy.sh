#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-}"
EXPECTED_PACKAGE="${2:-com.hkmixedkeyboard}"
APKANALYZER="${APKANALYZER:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/cmdline-tools/latest/bin/apkanalyzer}"

fail() { echo "APK policy verification failed: $*" >&2; exit 1; }
[[ -f "$APK" ]] || fail "usage: scripts/verify_apk_policy.sh path/to.apk [expected-package]"
[[ -x "$APKANALYZER" ]] || fail "set APKANALYZER or ANDROID_HOME to an SDK containing apkanalyzer"

manifest="$(mktemp)"
trap 'rm -f "$manifest"' EXIT
"$APKANALYZER" manifest print "$APK" > "$manifest"
python3 "$ROOT/scripts/verify_manifest_policy.py" \
  "$manifest" --package "$EXPECTED_PACKAGE" --min-sdk 26 --target-sdk 36
