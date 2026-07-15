#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
OUT="${2:-device-evidence}"
[[ -f "$APK" ]] || { echo "usage: scripts/device_smoke.sh final-from-aab.apk [output-dir]" >&2; exit 1; }
command -v adb >/dev/null || { echo "adb is required" >&2; exit 1; }
[[ "$(adb devices | awk 'NR > 1 && $2 == "device" {count++} END {print count+0}')" == "1" ]] || {
  echo "connect exactly one authorized device" >&2
  exit 1
}

mkdir -p "$OUT"
{
  echo "timestamp_utc=$(date -u +%FT%TZ)"
  echo "git_commit=$(git rev-parse HEAD)"
  echo "apk_sha256=$(sha256sum "$APK" | awk '{print $1}')"
  echo "model=$(adb shell getprop ro.product.manufacturer) $(adb shell getprop ro.product.model)"
  echo "build=$(adb shell getprop ro.build.fingerprint)"
  echo "api=$(adb shell getprop ro.build.version.sdk)"
  echo "wm_size=$(adb shell wm size | tr -d '\r')"
  echo "wm_density=$(adb shell wm density | tr -d '\r')"
} > "$OUT/device.txt"

adb install -r "$APK" > "$OUT/install.txt"
adb shell am force-stop com.hkmixedkeyboard
adb shell monkey -p com.hkmixedkeyboard -c android.intent.category.LAUNCHER 1 \
  > "$OUT/launch.txt"
adb shell dumpsys package com.hkmixedkeyboard > "$OUT/package.txt"
adb shell dumpsys meminfo com.hkmixedkeyboard > "$OUT/meminfo-before.txt"
adb shell monkey -p com.hkmixedkeyboard --throttle 20 10000 > "$OUT/monkey.txt"
adb shell dumpsys meminfo com.hkmixedkeyboard > "$OUT/meminfo-after.txt"
adb logcat -d -v threadtime '*:E' > "$OUT/logcat-errors.txt"

echo "Automated shell smoke complete. Perform and sign the manual three-mode, HKSCS,"
echo "lifecycle, haptic, layout, TalkBack and Switch Access checklist in"
echo "docs/release/device_beta_matrix.md. This script never records typed content."
