#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
OUT="${2:-device-evidence}"
APKANALYZER="${APKANALYZER:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/cmdline-tools/latest/bin/apkanalyzer}"
[[ -f "$APK" ]] || { echo "usage: scripts/device_smoke.sh final-from-aab.apk [output-dir]" >&2; exit 1; }
command -v adb >/dev/null || { echo "adb is required" >&2; exit 1; }
[[ -x "$APKANALYZER" ]] || { echo "set APKANALYZER or ANDROID_HOME to an SDK containing apkanalyzer" >&2; exit 1; }
[[ "$(adb devices | awk 'NR > 1 && $2 == "device" {count++} END {print count+0}')" == "1" ]] || {
  echo "connect exactly one authorized device" >&2
  exit 1
}

PACKAGE="$("$APKANALYZER" manifest application-id "$APK")"
[[ -n "$PACKAGE" ]] || { echo "could not derive package from APK" >&2; exit 1; }
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify_apk_policy.sh" "$APK" "$PACKAGE"

mkdir -p "$OUT"
{
  echo "timestamp_utc=$(date -u +%FT%TZ)"
  echo "git_commit=$(git rev-parse HEAD)"
  echo "apk_sha256=$(sha256sum "$APK" | awk '{print $1}')"
  echo "package=$PACKAGE"
  echo "model=$(adb shell getprop ro.product.manufacturer) $(adb shell getprop ro.product.model)"
  echo "build=$(adb shell getprop ro.build.fingerprint)"
  echo "api=$(adb shell getprop ro.build.version.sdk)"
  echo "wm_size=$(adb shell wm size | tr -d '\r')"
  echo "wm_density=$(adb shell wm density | tr -d '\r')"
} > "$OUT/device.txt"

adb logcat -c || adb shell logcat -c
adb install -r "$APK" > "$OUT/install.txt"
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 \
  > "$OUT/launch.txt"
adb shell dumpsys package "$PACKAGE" > "$OUT/package.txt"
adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-before.txt"
adb shell monkey -p "$PACKAGE" --throttle 20 10000 > "$OUT/monkey.txt"
adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-after.txt"
adb shell am send-trim-memory "$PACKAGE" COMPLETE > "$OUT/trim-memory.txt" 2>&1 || true
adb shell am kill "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 \
  > "$OUT/relaunch-after-process-kill.txt"
adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-after-recreation.txt"
pid="$(adb shell pidof "$PACKAGE" | tr -d '\r' | awk '{print $1}')"
if [[ -n "$pid" ]]; then
  adb logcat -d --pid="$pid" -v threadtime '*:V' > "$OUT/logcat-app.txt"
else
  adb logcat -d -v threadtime '*:E' > "$OUT/logcat-app.txt"
fi
if rg -n 'FATAL EXCEPTION|ANR in|StrictMode policy violation' "$OUT/logcat-app.txt" \
    > "$OUT/fatal-markers.txt"; then
  echo "App crash, ANR or StrictMode marker found; see $OUT/fatal-markers.txt" >&2
  exit 1
fi

echo "Automated shell smoke complete. Perform and sign the manual three-mode, HKSCS,"
echo "lifecycle, haptic, layout, TalkBack and Switch Access checklist in"
echo "docs/release/device_beta_matrix.md. This script never records typed content."
