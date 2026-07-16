# Supported Device and Closed Beta Matrix

Status: automated emulator gate passes; required manual physical-device and
two-week beta evidence is OPEN.

## Automated emulator evidence

| Environment | Instrumentation | Random events | Focus recovery | Settings-app recreation | App/framework markers |
|---|---|---|---|---|---|
| API 26 emulator | PASS 14/14 | PASS 10,000 | PASS 20/20 | PASS `19374 -> 20866` | PASS 0 |
| API 35 emulator | PASS 14/14 | PASS 10,000 | PASS 20/20 | PASS `3331 -> 5836` | PASS 0 |
| API 36 emulator | PASS 14/14 | PASS 10,000 | PASS 20/20 | PASS `3629 -> 5997` | PASS 0 |

All automated rows used APK SHA-256
`2d7ef29dda3843507593e3ab57d1bf84ca2a3dfe659939652a1b438588a67e52`.
The app-source and harness inputs were clean; no human QA sign-off is implied.
The recreation column covers the launcher/settings process only, not a selected
`HkImeService` bound to a third-party host editor.

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
Also kill and rebind the selected system IME while a host editor retains
committed and composing text; fail on any loss, duplication or stale commit.

Closed beta cannot pass until at least 100 opted-in testers complete two weeks and
10,000 content-free session counters, with ≥99.95% crash-free sessions, no unresolved
reproducible ANR or severity-1/2 defect, and ≥60% comparative preference. Typed text
must never be collected.

## Release-evidence record

After the physical-device matrix is complete, store
`docs/release/evidence/device_beta_matrix.json` with this exact JSON shape (the
placeholder file must not be created as `APPROVED` before the work occurs):

```json
{
  "status": "APPROVED",
  "owner": "qa-owner-01",
  "date": "YYYY-MM-DD",
  "commit": "<40-character candidate commit hash>",
  "aab_sha256": "<64-character candidate AAB SHA-256>"
}
```

The release verifier rejects an `OPEN` record, an absent owner/date, or any
commit/AAB mismatch.
