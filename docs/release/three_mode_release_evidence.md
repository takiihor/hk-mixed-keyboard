# Three-Mode Release Evidence Index

**Integration branch:** `feat/three-mode-market-readiness`

**Worktree:** `.worktrees/three-mode-market-readiness`

**Baseline date:** 2026-07-15 (Asia/Hong_Kong)

This file is the evidence ledger for
`docs/plans/2026-07-15-three-mode-best-in-class-readiness.md`. A checked gate
requires a result tied to the exact commit, device or test environment. Open
human-review, competitor-comparison and device gates must remain visibly open.

## Evidence ledger

| Gate ID | Commit | Test/device | Result | Evidence path | Reviewer | Date |
|---|---|---|---|---|---|---|
| T0-BRANCH | `7f1629783504db26b8c271836b9780d20aa40559` | Git worktree | PASS — isolated branch created from reviewed `master` | This document, Baseline branch inventory | Codex | 2026-07-15 |
| T0-UNIT | `7f1629783504db26b8c271836b9780d20aa40559` | JVM debug + release | PASS — 652 executions, 0 failures, 0 errors | `android/app/build/test-results/` | Codex | 2026-07-15 |
| T0-LINT | `7f1629783504db26b8c271836b9780d20aa40559` | Android lintDebug | PASS with 30 warnings and 0 errors | `android/app/build/reports/lint-results-debug.html` | Codex | 2026-07-15 |
| T1-SCORER | `6d9c412` | Python corpus-tool suite | PASS — 30 tests and 63 subtests; deterministic JSON/Markdown scorer added | `corpus/tools/tests/`, `corpus/benchmarks/README.md` | Codex | 2026-07-15 |
| T1-HOLDOUT | `6d9c412` | Independent language review | OPEN — schema exists; four holdout files intentionally contain headers only | `corpus/benchmarks/*_holdout.tsv` | Native reviewers required | 2026-07-15 |
| T1-COMPETITOR | `6d9c412` | Blinded competitor comparison | OPEN — protocol/template exists; no result collected | `docs/release/competitor_baseline.md` | QA/language lead required | 2026-07-15 |
| T2-COMMIT | `c5466f3` | JVM debug + release | PASS — 676 executions, 0 failures, 0 errors | `android/app/build/test-results/` | Codex | 2026-07-15 |
| T2-LINT | `c5466f3` | Android lintDebug | PASS with 30 pre-existing warnings and 0 errors | `android/app/build/reports/lint-results-debug.html` | Codex | 2026-07-15 |
| T3-5-AUTO | `44b0acc` | JVM mode market-gate/regression tests | PASS — automated Quick/Jyutping/Pinyin correctness gates included in 862 clean executions | `docs/release/checkpoint_2_verification.md` | Codex | 2026-07-15 |
| T3-5-MARKET | `44b0acc` | Independent native holdouts and paired competitors | OPEN — header-only holdouts and protocol exist; no market-superiority result claimed | `corpus/benchmarks/*_holdout.tsv`, `docs/release/competitor_baseline.md` | Native/QA reviewers required | 2026-07-15 |
| T6-RANKING | `44b0acc` | JVM custom-word/memory/mixed-input/sensitive-field tests | PASS — scheme-aware bounded custom data and reversible capped learning | `docs/release/checkpoint_2_verification.md` | Codex | 2026-07-15 |
| T7-INTEGRITY | `44b0acc` | JVM 100k-event stress + API 35 Room migration | PASS — automated scope | `docs/release/checkpoint_2_verification.md` | Codex | 2026-07-15 |
| T8-EMU | `44b0acc` | API 35 emulator, 300 warm full-corpus decodes | PASS — p95 ceiling ≤150 ms; physical reference-device qualification remains OPEN | `docs/release/performance_results.md`, `docs/release/checkpoint_2_verification.md` | Codex | 2026-07-15 |
| T9-SETUP | `44b0acc` | API 35 setup/settings instrumentation | PASS — automated flow; moderated user study remains OPEN | `docs/release/checkpoint_2_verification.md` | Codex | 2026-07-15 |
| T10-A11Y | `44b0acc` | API 35 virtual-key/candidate instrumentation + JVM contrast checks | PASS — automated scope; TalkBack/Switch Access specialist review OPEN | `docs/release/accessibility_report.md`, `docs/release/checkpoint_2_verification.md` | Codex | 2026-07-15 |
| T11-PRIVACY | `44b0acc` | Source + debug APK manifest/dependency audit | PASS — debug APK has no `INTERNET`, backup disabled; final signed-AAB/legal/public-URL gates OPEN | `docs/release/checkpoint_2_verification.md`, `docs/release/legal_signoff.md` | Codex / owners required | 2026-07-15 |
| T12-PIPELINE | `44b0acc` | CI config, lint/build, unsigned-release negative test | PASS — missing signing aborts before artifact creation; signed-AAB path OPEN | `.github/workflows/android.yml`, `scripts/verify_release.sh`, `docs/release/checkpoint_2_verification.md` | Codex / release owner required | 2026-07-15 |
| T13-EMU | `44b0acc` | API 35 `sdk_gphone64_x86_64` | PASS — 8/8 instrumentation; required physical matrix/beta OPEN | `docs/release/device_beta_matrix.md`, `docs/release/checkpoint_2_verification.md` | Codex / QA required | 2026-07-15 |
| T14-CLAIMS | `44b0acc` | Store/README/privacy consistency audit | PASS — engineering copy synchronized and NO-GO stated; owner sign-off OPEN | `docs/release/go_no_go.md`, `docs/release/checkpoint_2_verification.md` | Codex / product owner required | 2026-07-15 |
| T3-5-AUTO-3 | `91050c0` | Final app-source JVM/corpus regressions | PASS — phrase-evidence composition, tone-aware Jyutping, canonical validation and Unicode/HKSCS boundaries; 882 JVM executions plus 540/540 source-derived and 30/30 repository-curated Jyutping regressions | `docs/release/checkpoint_3_verification.md` | Codex (automated scope only) | 2026-07-15 |
| T3-5-MARKET-3 | `91050c0` | Independent native holdouts and paired current competitors | OPEN — four public templates remain header-only; no native/competitor result or superiority claim | `corpus/benchmarks/*_holdout.tsv`, `docs/release/competitor_baseline.md` | Native/QA reviewers required | 2026-07-15 |
| T7-DEVICE-3 | `91050c0`, `f3b337a` | API 26/35/36 deterministic device stress | PASS — same APK; 10,000 events plus 20 paced focus recoveries per API, memory trim, verified new PID after force-stop/relaunch, zero app/framework markers | `docs/release/checkpoint_3_verification.md` | Codex (automated scope only) | 2026-07-15 |
| T8-EMU-3 | `91050c0` | API 26/35/36, 300 warm full-corpus decodes per emulator | PASS — 100 per mode per emulator and p95 ceiling ≤150 ms; physical raw-trace gate OPEN | `docs/release/performance_results.md`, `docs/release/checkpoint_3_verification.md` | Codex / performance owner required | 2026-07-15 |
| T9-SETUP-3 | `91050c0` | API 26/35/36 setup/settings instrumentation | PASS — localized separate enable/select flow, practice field, safe text entry, destructive confirmation and system-bar layout; 15+15 moderated study OPEN | `docs/release/checkpoint_3_verification.md` | Codex / UX owner required | 2026-07-15 |
| T10-A11Y-3 | `91050c0` | API 26/35/36 nodes/semantics + JVM target/contrast checks | PASS — automated scope; physical TalkBack/Switch Access user or specialist sign-off OPEN | `docs/release/accessibility_report.md`, `docs/release/checkpoint_3_verification.md` | Codex / accessibility owner required | 2026-07-15 |
| T11-PRIVACY-3 | `91050c0` | Merged debug APK, dependency, corpus and notice audit | PASS — approved permission/export surface and offline dependencies; public policy is reachable but stale, legal/final-AAB gates OPEN | `docs/release/legal_signoff.md`, `docs/release/checkpoint_3_verification.md` | Codex / owners required | 2026-07-15 |
| T12-PIPELINE-3 | `f3b337a` | CI, binary policy, signing/AAB and device-log guards | PASS — 74 Python tests plus 63 subtests; unsigned release fails closed; production signing/certificate/final-AAB execution OPEN | `.github/workflows/android.yml`, `scripts/verify_release.sh`, `scripts/device_smoke.sh` | Codex / release owner required | 2026-07-15 |
| T13-EMU-3 | `91050c0`, `f3b337a` | API 26/35/36 emulators | PASS — 14/14 instrumentation, 10,000 events and 20 focus recoveries per API on APK SHA-256 `a30c2454…d7ebfe`; physical matrix/beta OPEN | `docs/release/device_beta_matrix.md`, `docs/release/checkpoint_3_verification.md` | Codex (automated scope only) / QA required | 2026-07-15 |
| T14-CLAIMS-3 | `91050c0`, `f3b337a` | Claims and evidence review | PASS — implemented behavior/copy synchronized and external blockers explicit; this is Codex self-review, product/release approval is OPEN and decision remains NO-GO | `docs/release/go_no_go.md`, `docs/release/checkpoint_3_verification.md` | Codex / product and release owners required | 2026-07-15 |

## Baseline branch inventory

| Ref | Commit | Relation to baseline `master` | Worktree state |
|---|---|---|---|
| `master` | `7f1629783504db26b8c271836b9780d20aa40559` | Baseline | User worktree contains unrelated pre-existing changes; untouched |
| `fix/release-readiness` | `33a2cf77b958e2a4316cb2d1c612daeefaa85bef` | 17 baseline-only / 8 branch-only commits | Clean |
| `feat/cultural-preservation` | `142ce78e564e6ad2778d9e229665a2454fa2c2e4` | 18 baseline-only / 28 branch-only commits | Dirty; listed below |

The integration rule is to port changes by behaviour onto the current UI. Do
not merge or copy either divergent worktree wholesale.

### Unique release-readiness commits

```text
33a2cf7 feat: expose keyboard keys to accessibility services
c0ff00b fix: restore settings screens and confirm dictionary clearing
5e7b589 fix: validate and bound custom word imports
932a6f2 fix: synchronize IME decode coalescing
c23c025 fix: commit exact Jyutping candidates on Space
1b5c9d3 fix: define conservative Jyutping Space policy
68b3ca1 docs: plan release readiness remediation
d48b25a docs: capture release readiness remediation design
```

### Unique cultural-preservation commits

The 28 branch-only commits are recorded by:

```bash
git log --oneline master..feat/cultural-preservation
```

They cover reproducible Quick/Jyutping/HKSCS corpora, Cantonese ranking,
supplementary Unicode, commit/backspace behaviour, mixed assistance and
supporting tests/docs. They must be ported through the corresponding plan task,
not accepted as an undifferentiated merge.

### Pre-existing dirty cultural-preservation files

The following state predates this integration work and must not be overwritten
or treated as committed branch evidence:

```text
M README.md
M android/app/build.gradle.kts
M android/app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt
M android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateCommitIntentController.kt
M android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateCommitPolicy.kt
M android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt
M android/app/src/main/kotlin/com/hkmixedkeyboard/ime/PinyinImePolicy.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/AutoCommitBackspaceTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateCommitIntentControllerTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateCommitPolicyTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/ChineseEnglishAssistTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/ConservativeSpaceTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/CrossModeEnglishAssistTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/HkscsSupplementTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/MixedPhraseSuggestionTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinImeIntegrationTest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/ProductionPathThreeModeE2ETest.kt
M android/app/src/test/kotlin/com/hkmixedkeyboard/ThreeModeCommitIntegrationTest.kt
M android/app/version.properties
M docs/HK_Mixed_Keyboard_iOS_Development_Guide_v1.2_Enhanced.md
M docs/cultural_preservation_release_gates.md
M docs/ime_thin_ui_report.md
M docs/store/long_en.md
?? android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CompositionSelectionPolicy.kt
?? android/app/src/test/kotlin/com/hkmixedkeyboard/CompositionSelectionPolicyTest.kt
```

## Baseline verification

Command:

```bash
cd android
./gradlew test lintDebug --rerun-tasks
```

Result:

```text
BUILD SUCCESSFUL in 2m 5s
71 actionable tasks: 71 executed
652 unit-test executions, 0 failures, 0 errors
lint: 0 errors, 25 UnusedResources warnings, 5 IconLauncherShape warnings
```

Pre-existing compiler/tooling warnings include the SDK XML tool-version warning,
Kapt Kotlin 2.0 fallback, unused Room processor options and deprecated
accessibility calls in `SymbolPageView`.

## Open evidence dependencies

- [ ] Three independent native-language reviewers approve the locked benchmarks.
- [ ] Competitor versions and cold-state results are recorded on identical prompts.
- [ ] Physical Samsung, Pixel/AOSP and assistive-technology reviews are completed.
- [ ] Legal/data owners sign corpus provenance and store disclosures.
- [ ] Closed beta reaches the required tester/session thresholds.

## Checkpoint 1 summary

- Integration work is isolated from the user's existing dirty `master` worktree.
- The benchmark scorer and collection protocol are implemented; independent
  language data and competitor measurements remain open by design.
- Space and punctuation now coordinate with a matching decode in Quick, Jyutping
  and Pinyin. Quick English collisions remain literal, and stale generation,
  session or buffer results cannot commit.
- Automatic Space selection records reversible composition state. The record is
  converted with Simplified output so Backspace compares/deletes emitted text.
- Cursor movement outside the composing span invalidates the old session before
  a delayed result can write at the former location.

## Checkpoint 2 summary

- Repository-automatable engineering for Tasks 3–14 is committed at `44b0acc`.
- Fresh debug/release JVM, debug/release lint, API 35 instrumentation, corpus,
  manifest, dependency and fail-closed signing checks are recorded in
  `docs/release/checkpoint_2_verification.md`.
- Automated gates pass within their stated scope. Independent market, physical
  device, accessibility-user, legal, signed-artifact and beta gates remain open,
  so the product decision remains NO-GO.

## Checkpoint 3 summary

- Final APK-source engineering is committed at `91050c0`; the final device
  harness is `f3b337a`.
- Fresh debug/release JVM, Python, lint, source-derived regression, APK policy and
  unsigned-release guards pass. API 26/35/36 each pass 14 instrumentation tests,
  10,000 deterministic events and 20 paced focus recoveries against APK SHA-256
  `a30c2454…d7ebfe`, with verified new PIDs and zero final app/framework markers.
  Raw logs remain in ignored build output; `checkpoint_3_verification.md` is the
  committed durable summary.
- Independent native/competitor, physical-device/performance/accessibility,
  usability, legal/public-policy, signed-AAB and beta gates remain open. The
  market decision and “best-in-class” claim therefore remain NO-GO.
