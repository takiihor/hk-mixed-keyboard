# Checkpoint 2 Verification — Tasks 3–14 Engineering

Commit under test: `44b0acc2ec503e3edc82bb6f96ede3a42c7f8e50`

Date: 2026-07-15 (Asia/Hong_Kong)

Scope: repository-automatable engineering and automated evidence for Tasks 3–14.
This checkpoint is not independent linguistic certification, physical-device
qualification, legal approval, a signed release, or a market GO decision.

## Fresh verification results

| Area | Command/environment | Result |
|---|---|---|
| JVM debug + release | `cd android && ./gradlew test --rerun-tasks` | PASS — 862 executions, 0 skipped, 0 failures, 0 errors |
| Android debug lint | `./gradlew lintDebug --rerun-tasks` | PASS — 0 errors, 37 warnings |
| Android release lint | `./gradlew lintRelease --rerun-tasks` | PASS — 0 errors, 37 warnings |
| Instrumentation | API 35 `sdk_gphone64_x86_64`; `./gradlew connectedDebugAndroidTest --rerun-tasks` | PASS — 8 tests, 0 skipped, 0 failures, 0 errors |
| Corpus tools | `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests` | PASS — 64 tests and 63 subtests |
| Corpus reproducibility | `python3 corpus/tools/verify_corpus_manifest.py` | PASS |
| Source-derived coverage | `python3 corpus/tools/run_three_mode_coverage_benchmark.py` | PASS — 540/540 Top-1 and Top-3; not independent review |
| Reviewed Jyutping regression set | `python3 corpus/tools/run_jyutping_benchmark.py --require-top1 1.0` | PASS — 30/30 Top-1 and Top-3 |
| Release dependency inventory | `./android/gradlew -p android :app:dependencies --configuration releaseRuntimeClasspath` | PASS — inventory generated; no analytics, ads, cloud-input or network client dependency |
| Built debug manifest | `apkanalyzer manifest permissions app-debug-0.64.0.apk` | PASS — `VIBRATE` only; no `INTERNET`; package `com.hkmixedkeyboard.debug`, version `0.64.0`/79 |
| Unsigned release rejection | clean tree output removed, signing variables unset, `./gradlew bundleRelease` | PASS — rejected before task execution; no AAB created |
| Shell/static checks | `bash -n scripts/*.sh`, authored-file whitespace/conflict checks | PASS |

The instrumentation suite covers the API 35 setup regression flow, keyboard
virtual accessibility nodes, candidate accessibility semantics, Room 3→4 custom
word migration, and a 300-sample warm full-corpus decode ceiling. It does not
replace physical-device latency, TalkBack/Switch Access user review, or the
supported-device matrix.

## Automated engineering delivered

- Three production decode paths with normalized Jyutping and Pinyin, reviewed
  safety overrides, annotations, strict-first fuzzy Pinyin, tap-only typo recovery,
  deterministic Quick validation, HKSCS reachability and Unicode-safe fallbacks.
- Scheme-aware custom dictionaries, bounded atomic UTF-8 CSV import, Room migration,
  capped/reversible local learning, sensitive-field suppression and cache/lifecycle
  stress coverage.
- Live setup state, separate enable/select actions, practice field, direct mode
  picker, height/one-handed controls, Traditional Chinese and English settings and
  accessibility copy, 48dp targets and automated contrast checks.
- Corpus provenance and deterministic builders, offline privacy disclosures,
  CI/instrumentation workflow, immutable version metadata, early fail-closed signing,
  release/device/beta runbooks, synchronized store claims and explicit NO-GO policy.

## Gates still open

- Three independent ≥1,000-prompt native-reviewed holdouts and paired current
  competitor measurements, including the relative best-in-class thresholds.
- Native Cantonese/HK Traditional and Mandarin Traditional-HK sign-off.
- API 26, Samsung/OneUI, physical Pixel/AOSP, adaptive-layout, TalkBack and Switch
  Access matrices; full latency/frame/memory traces on reference devices.
- Legal/data-owner approval, public privacy URL verification, final Play Data Safety,
  upload-key recovery ownership, signed AAB verification and final screenshots.
- At least 100 opted-in beta testers over two weeks and 10,000 content-free sessions.

Therefore the repository engineering checkpoint passes, while public release and
the phrase “best-in-class” remain **NO-GO**.
