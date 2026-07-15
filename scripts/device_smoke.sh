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
RESULT_FILE="$OUT/result.txt"
printf 'status=RUNNING\n' > "$RESULT_FILE"
finish_result() {
  status=$?
  if [[ $status -ne 0 ]]; then
    printf 'status=FAIL\nexit_code=%s\n' "$status" > "$RESULT_FILE"
  fi
  exit "$status"
}
trap finish_result EXIT
{
  echo "timestamp_utc=$(date -u +%FT%TZ)"
  echo "git_commit=$(git rev-parse HEAD)"
  echo "git_dirty=$([[ -n "$(git status --porcelain --untracked-files=all)" ]] && echo true || echo false)"
  echo "apk_source_commit=$(git log -1 --format=%H -- android corpus)"
  echo "apk_sources_dirty=$([[ -n "$(git status --porcelain --untracked-files=all -- android corpus)" ]] && echo true || echo false)"
  echo "harness_commit=$(git log -1 --format=%H -- scripts/device_smoke.sh scripts/verify_device_log.py scripts/verify_apk_policy.sh)"
  echo "harness_dirty=$([[ -n "$(git status --porcelain --untracked-files=all -- scripts/device_smoke.sh scripts/verify_device_log.py scripts/verify_apk_policy.sh)" ]] && echo true || echo false)"
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
# Keep the stress stream inside the app. Android documents system keys, major
# navigation and catch-all events as classes that can target system services;
# touch/motion/basic navigation plus activity switches retain UI/lifecycle load
# without turning an unrelated system app ANR into an app result.
adb shell monkey -p "$PACKAGE" -s 260715 --throttle 20 \
  --ignore-crashes --ignore-timeouts \
  --pct-touch 50 --pct-motion 20 --pct-trackball 0 --pct-nav 10 \
  --pct-majornav 0 --pct-syskeys 0 --pct-appswitch 20 --pct-anyevent 0 \
  10000 > "$OUT/monkey.txt"
rg -n '^Events injected: 10000$' "$OUT/monkey.txt" >/dev/null || {
  echo "Monkey did not complete all 10,000 events" >&2
  exit 1
}
adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-after.txt"
adb logcat -d -v threadtime '*:V' > "$OUT/logcat-full-stress.txt"
stress_pid="$(adb shell pidof "$PACKAGE" | tr -d '\r' | awk '{print $1}')"
if [[ -n "$stress_pid" ]]; then
  adb logcat -d --pid="$stress_pid" -v threadtime '*:V' > "$OUT/logcat-stress-app.txt"
else
  : > "$OUT/logcat-stress-app.txt"
fi
adb shell am send-trim-memory "$PACKAGE" COMPLETE > "$OUT/trim-memory.txt" 2>&1 || true
[[ -n "$stress_pid" ]] || {
  echo "could not resolve the app PID before process recreation" >&2
  exit 1
}
adb shell am force-stop "$PACKAGE"
for _ in $(seq 1 20); do
  [[ -z "$(adb shell pidof "$PACKAGE" | tr -d '\r')" ]] && break
  sleep 0.25
done
[[ -z "$(adb shell pidof "$PACKAGE" | tr -d '\r')" ]] || {
  echo "app process survived force-stop" >&2
  exit 1
}
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 \
  > "$OUT/relaunch-after-process-kill.txt"
adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo-after-recreation.txt"
pid="$(adb shell pidof "$PACKAGE" | tr -d '\r' | awk '{print $1}')"
{
  echo "before_force_stop_pid=$stress_pid"
  echo "after_relaunch_pid=$pid"
} > "$OUT/process-recreation.txt"
[[ -n "$pid" && "$pid" != "$stress_pid" ]] || {
  echo "process recreation did not produce a new app PID" >&2
  exit 1
}
if [[ -n "$pid" ]]; then
  adb logcat -d --pid="$pid" -v threadtime '*:V' > "$OUT/logcat-app.txt"
else
  adb logcat -d -v threadtime '*:E' > "$OUT/logcat-app.txt"
fi
cat "$OUT/logcat-stress-app.txt" "$OUT/logcat-app.txt" > "$OUT/logcat-app-combined.txt"
python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify_device_log.py" \
  "$OUT/logcat-app-combined.txt" \
  --package "$PACKAGE" \
  --markers "$OUT/fatal-markers.txt" \
  --framework-markers "$OUT/framework-strictmode-markers.txt" \
  --system-log "$OUT/logcat-full-stress.txt" \
  --monkey-log "$OUT/monkey.txt"

trap - EXIT
printf 'status=PASS\n' > "$RESULT_FILE"

echo "Automated shell smoke complete. Perform and sign the manual three-mode, HKSCS,"
echo "lifecycle, haptic, layout, TalkBack and Switch Access checklist in"
echo "docs/release/device_beta_matrix.md. This script never records typed content."
