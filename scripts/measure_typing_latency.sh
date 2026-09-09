#!/usr/bin/env bash
# Collect per-keystroke latency from a debug build and report percentiles.
#
# This script does not drive the device. You type normally in whatever app you
# like; it only reads the HkIme.Latency log the keyboard already emits when
# BuildConfig.LATENCY_LOGGING is on (debug builds).
#
# Usage:
#   scripts/measure_typing_latency.sh [seconds]     # default 120
#
# Targets from the release checklist (reference mid-range device):
#   visual feedback   p95 <= 50 ms
#   candidate update  p50 <= 30 ms, p95 <= 75 ms, p99 <= 150 ms

set -euo pipefail

DURATION="${1:-120}"
ADB="${ADB:-adb}"

command -v "$ADB" >/dev/null || { echo "adb not found; set ADB=/path/to/adb" >&2; exit 1; }

if ! "$ADB" shell pm list packages 2>/dev/null | grep -q "com.hkmixedkeyboard"; then
  echo "com.hkmixedkeyboard is not installed on the device." >&2
  exit 1
fi

CURRENT_IME="$("$ADB" shell settings get secure default_input_method | tr -d '\r')"
case "$CURRENT_IME" in
  com.hkmixedkeyboard*) ;;
  *) echo "Selected IME is '$CURRENT_IME', not this keyboard. Switch to it first." >&2; exit 1 ;;
esac

RAW="$(mktemp)"
trap 'rm -f "$RAW"' EXIT

"$ADB" logcat -c
echo "Type normally for ${DURATION}s — use all three modes and let candidates appear."
echo "Nothing is injected; only the keyboard's own latency log is read."
"$ADB" logcat -s HkIme.Latency:D > "$RAW" 2>/dev/null &
LOGCAT_PID=$!
sleep "$DURATION"
kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true

python3 - "$RAW" <<'PY'
import re, sys

samples: dict[str, list[int]] = {}
pattern = re.compile(r"(visual_feedback|decode_start|decode_end|first_candidate_render) ms=(\d+)")
with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
    for line in handle:
        found = pattern.search(line)
        if found:
            samples.setdefault(found.group(1), []).append(int(found.group(2)))

if not samples:
    print("No samples. Is this a debug build (LATENCY_LOGGING=true)?")
    raise SystemExit(1)

def pct(values: list[int], p: float) -> int:
    ordered = sorted(values)
    # Nearest-rank; with few samples the high percentiles are the max, which is
    # honest rather than interpolated.
    index = max(0, min(len(ordered) - 1, round(p / 100 * len(ordered) + 0.5) - 1))
    return ordered[index]

print(f"{'event':<24}{'n':>6}{'p50':>7}{'p95':>7}{'p99':>7}{'max':>7}")
for event in ("visual_feedback", "decode_start", "decode_end", "first_candidate_render"):
    values = samples.get(event)
    if not values:
        continue
    print(f"{event:<24}{len(values):>6}{pct(values,50):>7}{pct(values,95):>7}"
          f"{pct(values,99):>7}{max(values):>7}")

decode = samples.get("decode_end", [])
if len(decode) < 100:
    print(f"\nOnly {len(decode)} candidate updates — too few for a p99. Type longer.")
PY
