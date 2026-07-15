# Checkpoint 3 Verification — Three-Mode Input Quality and Stability

Final APK-source commit: `91050c061b48d0eb148c58a22dd29c2bb58b4846`

Final device-harness commit: `f3b337ad4d4774e19705f30dd26c5c1717f68d53`

Date: 2026-07-15 (Asia/Hong_Kong)

Decision scope: repository-automatable engineering and emulator evidence for
Tasks 3–14. This checkpoint does not replace independent native-language review,
paired competitor testing, physical-device qualification, legal approval, a
signed production AAB or the closed beta. The public-release decision remains
**NO-GO**.

## Final automated results

| Area | Exact result |
|---|---|
| JVM debug + release | PASS — 441 tests per variant; 882 total, 0 skipped/failures/errors |
| Python corpus/release tools | PASS — 74 tests and 63 subtests |
| Android lint | PASS — debug and release: 0 errors, 37 warnings each |
| Source-derived three-mode regression | PASS — 540/540 Top-1 and Top-3; explicitly not an independent holdout |
| Repository-curated Jyutping exact-key regression | PASS — 30/30 Top-1 and Top-3; not independent native review |
| API 26 instrumentation | PASS — 14/14, 0 skipped/failures |
| API 35 instrumentation | PASS — 14/14, 0 skipped/failures |
| API 36 instrumentation | PASS — 14/14, 0 skipped/failures |
| API 26/35/36 deterministic lifecycle stress | PASS — 10,000 events and 20 paced focus recoveries per API; 30,000 events and 60 recoveries total; memory trim and verified force-stop/relaunch completed |
| App failure scan | PASS — zero app crash, ANR or actionable StrictMode markers on every API; zero framework-only blocks in the final runs |
| Debug APK policy | PASS — `VIBRATE` only, no `INTERNET`, backup disabled, approved exported components, minSdk 26, targetSdk 36 |
| Corpus/notice checksum integrity | PASS — manifest, benchmark-template leakage guard and bundled legal-asset checksums; legal approval remains open |
| Unsigned production bundle guard | PASS — `bundleRelease` rejected before artifact generation when signing configuration was absent |
| Release dependency inventory | PASS — no network client, advertising, analytics, cloud-input or crash-reporting dependency |

The debug artifact exercised on all three emulators was:

```text
android/app/build/outputs/apk/debug/app-debug-0.64.0.apk
SHA-256 a30c24546dd7862ad5df76398876c28de2008d5a723b1d43c12ca4fbd1d7ebfe
```

All three `device.txt` files record APK source `91050c0`, harness `f3b337a`,
`apk_sources_dirty=false`, `harness_dirty=false` and the same APK hash. The general
`git_dirty=true` flag is caused only by the release documents being reconciled
during the runs; it does not cover an uncommitted APK or harness input. Raw device
logs live under ignored `android/app/build/evidence/api*/`; this committed report is
their durable summary.

Verified process recreation produced new PIDs on every API: API 26 `15143 ->
16860`, API 35 `9198 -> 11237`, and API 36 `14724 -> 16061`. Each final app and
framework marker file is empty. During checkpoint hardening, the stronger gate
first exposed an Android 16 selection-toolbar `BadTokenException` and an Android 8
spelling-popup span crash. Lifecycle-safe selection, no-spellcheck/no-smart-action
text fields and dedicated instrumentation regressions fixed both. An unrealistic
20%/20 ms Monkey task-switch burst was also replaced with 100 randomized switches
and 20 paced, InputDispatcher-verified focus recoveries per API.

These PASS results are automated engineering evidence recorded by Codex. They are
not human QA, language, accessibility, legal, product or release-owner sign-off.

## Engineering delta since Checkpoint 2

- Replaced unsafe per-syllable character joining in Jyutping and Pinyin with
  bounded full-input composition requiring repository-curated multi-character
  phrase evidence; ranking prefers stronger phrase coverage and fewer chunks.
- Added lazy tone-aware Jyutping evidence for 27,006 characters/readings, aligned
  tone-to-syllable normalization, truthful tonal annotations and explicit-tone
  ranking without degrading normal toneless input.
- Added canonical Jyutping/Pinyin validation and Unicode code-point-safe HKSCS
  prediction, display and direct-decode boundaries.
- Completed localized mode labels, candidate direction labels, virtual keyboard
  and symbol nodes, screen bounds, state announcements and 48dp automated checks.
- Added API 26–28 safe settings insets, API 29+ edge-to-edge insets, asynchronous
  legal/policy asset reads, debug StrictMode and Android 12+ scroll-capture
  lifecycle protection.
- Closed legacy and current Android text-selection races while retaining normal
  selection/copy/paste, and added explicit Next/Done custom-word entry actions.
- Strengthened merged-binary manifest policy, corpus/notice integrity, CI coverage,
  fail-closed signing, final-AAB/certificate verification and deterministic
  app-scoped device stress evidence.

## Open market blockers

- Populate and hash independent holdouts with at least 1,000 prompts per mode,
  including ambiguity/reputation-sensitive cases, then obtain three independent
  native-reviewer approvals and Top-5 review for at least 2,000 high-frequency
  Quick keys and Jyutping inputs.
- Run blinded paired comparisons against current Gboard, SwiftKey and specialist
  keyboards. Each mode must match/exceed best-competitor Top-3 and beat Top-1 by
  two points or median keystrokes by 5%, without correction/latency regression.
- Complete physical Samsung/OneUI and Pixel/AOSP testing plus small/tall/tablet or
  foldable layouts, fonts, haptics, rotation, migration, TalkBack and Switch Access.
- Measure the specified latency, frame and memory percentiles on reference physical
  devices with raw traces; emulator ceilings are regression guards only.
- Moderate setup/daily-use studies with at least 15 new and 15 experienced
  Chinese-IME users, and close every severity-1/2 or recurring usability issue.
- Obtain data/licence/legal, accessibility, native-language, engineering, product
  and release-owner signatures; redeploy the privacy page because the public URL
  is reachable but still serves the 2026-06-16 policy rather than the local copy.
- Produce the signed final AAB with approved certificate ownership/recovery,
  verify APKs derived from that AAB, complete Data Safety and capture final store
  screenshots from that artifact.
- Complete a two-week beta with at least 100 opted-in testers, 10,000 content-free
  keyboard sessions, at least 99.95% crash-free sessions and the preference gate.

Until those items are signed against one final commit and AAB hash, the product is
not market-ready and “best-in-class” is not an approved claim.
