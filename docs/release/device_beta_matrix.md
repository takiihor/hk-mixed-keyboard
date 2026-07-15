# Supported Device and Closed Beta Matrix

Status: automated emulator gate passes; required manual physical-device and
two-week beta evidence is OPEN.

## Automated emulator evidence

| Environment | Instrumentation | Random events | Focus recovery | Process recreation | App/framework markers |
|---|---|---|---|---|---|
| API 26 emulator | PASS 14/14 | PASS 10,000 | PASS 20/20 | PASS `15143 -> 16860` | PASS 0 |
| API 35 emulator | PASS 14/14 | PASS 10,000 | PASS 20/20 | PASS `9198 -> 11237` | PASS 0 |
| API 36 emulator | PASS 14/14 | PASS 10,000 | PASS 20/20 | PASS `14724 -> 16061` | PASS 0 |

All automated rows used APK SHA-256
`a30c24546dd7862ad5df76398876c28de2008d5a723b1d43c12ca4fbd1d7ebfe`.
The app-source and harness inputs were clean; no human QA sign-off is implied.

## Required manual and physical evidence

| Environment | Fresh/upgrade | Three modes | HKSCS | lifecycle | a11y | crash/ANR | Evidence |
|---|---|---|---|---|---|---|---|
| API 26 physical reference | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | |
| Android 13 Samsung/OneUI physical | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | |
| Current stable Pixel/AOSP physical | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | |
| Small/tall/tablet or foldable layouts | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | |

Automated emulator results do not substitute for any row in this table.

For every row exercise light/dark, portrait/landscape, large font/display and
low-memory process death. Smoke Quick `ai/rr`, Jyutping `nei5hou2/hai`, and Pinyin
`ni3hao3/xianggang`; verify supplementary HKSCS display/commit/backspace, haptics,
sound, cursor swipe, long presses, rotation, editor switching and migration.

Closed beta cannot pass until at least 100 opted-in testers complete two weeks and
10,000 content-free session counters, with ≥99.95% crash-free sessions, no unresolved
reproducible ANR or severity-1/2 defect, and ≥60% comparative preference. Typed text
must never be collected.
