# Three-Mode Best-in-Class Readiness Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove every known launch blocker and prove that 速成、粵拼 and 普通話拼音 exceed current market alternatives for Hong Kong Chinese input quality and usability.

**Architecture:** Start from the current `master` UI and port the release-readiness and cultural-preservation changes into one clean integration branch, feature by feature. Keep decoding, ranking, commit decisions and benchmark scoring in deterministic testable components; keep Android classes focused on IME lifecycle and rendering. Release readiness is decided by measurable mode-specific gates plus shared accessibility, performance, privacy and packaging gates.

**Tech Stack:** Android/Kotlin, Android IME APIs, JVM JUnit 4, Android instrumentation tests, Room/DataStore, offline CSV/binary corpora, Python corpus tooling, Gradle, ADB/emulators and physical Android devices.

---

## How to use this checklist

- `[ ]` means not proven. Code existing somewhere does not count as complete.
- `[x]` may be used only when the required evidence is linked from the release evidence index.
- **P0** blocks integration testing. **P1** blocks public release. **P2** is required for the best-in-class claim but may follow a closed alpha.
- No P0 or P1 item may be waived. A P2 waiver requires written product-owner approval and removes the “best-in-class” claim.
- Each task should be implemented test-first and committed separately.
- Do not add `INTERNET`, analytics, ads, telemetry or cloud dependencies.

Create an evidence index at `docs/release/three_mode_release_evidence.md`. For every checked item record:

```markdown
| Gate ID | Commit | Test/device | Result | Evidence path | Reviewer | Date |
|---|---|---|---|---|---|---|
```

## Final quality thresholds

These are release gates, not aspirational dashboards.

| Measure | 速成 | 粵拼 | 普拼 |
|---|---:|---:|---:|
| Independent benchmark size | ≥1,000 prompts | ≥1,000 prompts | ≥1,000 prompts |
| Common-input Top-1 | ≥90% | ≥94% | ≥95% |
| Common-input Top-3 | ≥98% | ≥99% | ≥99% |
| Common-input Top-5 | ≥99.5% | ≥99.5% | ≥99.5% |
| Unintended offensive/nonsensical Top-1 | 0 | 0 | 0 |
| Wrong automatic commits | <0.25% | <0.25% | <0.25% |
| Core-vocabulary OOV | <0.5% | <0.5% | <0.5% |
| HKSCS runtime reachability | 100% | 100% | 100% |

“Common input” and “core vocabulary” must be defined before scoring in `corpus/benchmarks/README.md`. Competitor differences must be calculated on the same paired prompts with 95% confidence intervals. HKSCS runtime reachability means a user can retrieve, render and commit the complete code point through the mode's documented input or fallback route; a code point existing only in an unused asset does not pass.

For the best-in-class claim, each mode must additionally:

- [ ] Match or exceed the best comparison keyboard's Top-3 accuracy.
- [ ] Beat the best comparison keyboard by at least 2 percentage points in Top-1 accuracy **or** reduce median keystrokes per correct commit by at least 5%.
- [ ] Have no statistically meaningful regression in correction rate, latency or task-completion time.
- [ ] Win the overall preference vote of at least 60% of blinded native testers.

Comparison keyboards must include the current versions of Gboard and SwiftKey plus the strongest current mode-specific specialist available in Hong Kong at test time.

## Ownership and evidence map

The checkbox is the status. The default owner may delegate work but remains accountable for the complete gate and its evidence.

| Gate | Priority | Default owner | Required evidence |
|---|---|---|---|
| T0 Integration baseline | P0 | Engineering lead | Branch inventory, clean baseline report |
| T1 Independent benchmarks | P0 | QA and language-review lead | Locked datasets, hashes, scorer tests, competitor report |
| T2 Commit/correction contract | P0 | IME engineering lead | Unit, race and production-path test reports |
| T3 速成 quality | P1/P2 | Quick language owner | Native review and Quick benchmark report |
| T4 粵拼 quality | P1/P2 | Cantonese language owner | Native review and Jyutping benchmark report |
| T5 普拼 quality | P1/P2 | Mandarin language owner | Native review and Pinyin benchmark report |
| T6 Ranking/learning/mixed input | P1 | IME engineering lead | Focused tests and privacy review |
| T7 Lifecycle/data integrity | P0/P1 | Android engineering lead | Stress, migration and instrumentation reports |
| T8 Performance | P1/P2 | Performance owner | Raw traces and percentile report |
| T9 Setup/daily usability | P1/P2 | Product/UX owner | Moderated study results and recordings |
| T10 Accessibility | P1 | Accessibility owner | Automated results and manual specialist sign-off |
| T11 Privacy/legal | P1 | Privacy and data owner | Manifest audit, provenance register, legal sign-off |
| T12 CI/release pipeline | P0/P1 | Release engineering owner | CI runs, signing verification, artifact hash |
| T13 Device matrix/beta | P1/P2 | QA lead | Device matrix and closed-beta report |
| T14 Claims/go-no-go | P1/P2 | Product and release owners | Signed decision record and evidence index |

## Task 0 — Build a clean, reviewable integration baseline (P0)

**Files:**

- Create: `docs/release/three_mode_release_evidence.md`
- Review: `.worktrees/release-readiness/`
- Review: `.worktrees/cultural-preservation/`
- Modify only through later tasks: `android/`, `corpus/`, `docs/`, `NOTICE`, `README.md`

### Checklist

- [x] Create an isolated integration worktree from current `master`; do not develop in either divergent worktree.
- [x] Record the current `master`, `fix/release-readiness` and `feat/cultural-preservation` commit IDs.
- [x] Record all uncommitted files in the cultural-preservation worktree and assign an owner before copying any of them.
- [x] Inventory every commit unique to each branch with `git log --left-right --cherry-pick`.
- [ ] Port fixes by concern; do not merge or copy an entire worktree over the newer UI.
- [x] Resolve the competing Space/punctuation policies through Task 2 rather than accepting either branch blindly.
- [ ] Remove stale two-mode comments, labels and documentation during integration.
- [x] Run the complete baseline suite from a clean checkout.

### Verification

```bash
git status --short --branch
git log --oneline master..fix/release-readiness
git log --oneline master..feat/cultural-preservation
cd android
./gradlew test lintDebug --rerun-tasks
```

Expected: clean integration worktree; all baseline tests and lint complete with zero errors.

### Commit

```bash
git commit -m "chore: establish three-mode release integration baseline"
```

## Task 1 — Create independent benchmarks and competitor baselines (P0)

**Files:**

- Create: `corpus/benchmarks/quick_holdout.tsv`
- Create: `corpus/benchmarks/jyutping_holdout.tsv`
- Create: `corpus/benchmarks/pinyin_holdout.tsv`
- Create: `corpus/benchmarks/mixed_input_holdout.tsv`
- Create: `corpus/benchmarks/README.md`
- Create: `corpus/tools/score_three_mode_benchmark.py`
- Create: `corpus/tools/tests/test_score_three_mode_benchmark.py`
- Create: `docs/release/competitor_baseline.md`

### Dataset checklist

- [ ] Recruit at least three independent native reviewers: native Cantonese/HK Traditional expertise for Quick and Jyutping, and native Mandarin Traditional-Chinese expertise for Pinyin.
- [ ] Use at least 1,000 locked prompts per mode with a minimum 20% holdout not seen during tuning.
- [ ] Include single characters, common words, names, places, colloquial phrases, full sentences, punctuation, mixed English, corrections and rare HKSCS characters.
- [ ] Include at least 200 ambiguity cases per mode where multiple valid candidates exist.
- [ ] Include at least 100 error-prone or reputation-sensitive cases per mode, including profanity collisions.
- [ ] Include cold-start and personalized runs separately.
- [ ] Prohibit benchmark rows generated solely from the production corpus or ranking overrides.
- [ ] Hash and freeze each release candidate's holdout files before tuning.
- [ ] Document acceptable variants rather than marking one regional form as the only valid answer.

### Scorer checklist

- [x] Score Top-1, Top-3, Top-5, reciprocal rank, OOV and invalid output.
- [x] Score keystrokes per committed Chinese character, correction actions, wrong automatic commits and task-completion time.
- [ ] Report cold and learned results separately.
- [x] Report results by frequency band, phrase length, HK colloquial category and HKSCS status.
- [ ] Fail on duplicate IDs, missing expected forms, leaked holdout rows or invalid Unicode.
- [x] Emit machine-readable JSON and a human-readable Markdown report.

### Competitor comparison checklist

- [ ] Record app version, language settings, personalization state, network state, device and date.
- [ ] Reset learning between cold runs.
- [ ] Use identical prompts and scoring rules for every keyboard.
- [ ] Have testers enter prompts without knowing which keyboard is under evaluation when practical.
- [ ] Retain anonymized raw keystroke/correction results without collecting private text.

### Test-first verification

```bash
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests/test_score_three_mode_benchmark.py
python3 corpus/tools/score_three_mode_benchmark.py --help
```

Expected: scorer tests pass and the command documents deterministic input/output formats.

## Task 2 — Define one predictable commit and correction contract (P0)

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Port/refine: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateCommitIntentController.kt`
- Port/refine: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CandidateCommitPolicy.kt`
- Port/refine: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/CompositionSelectionPolicy.kt`
- Create/modify tests under: `android/app/src/test/kotlin/com/hkmixedkeyboard/`

### Contract checklist

- [x] Tapping a candidate always commits that exact, current candidate.
- [x] Space selects the current safe Chinese candidate in all three Chinese modes and does not insert an unwanted visible space.
- [x] Enter commits the literal Latin buffer when the user intentionally wants raw input.
- [x] Punctuation first commits the current safe Chinese candidate, then emits punctuation matching surrounding language width.
- [x] A stale asynchronous result can never be committed after the buffer, mode, editor or session changes.
- [x] Backspace immediately after automatic selection restores the original composition and candidate list.
- [x] English words such as `ok`, `hi` and `go` are not converted accidentally.
- [x] Empty-buffer Space, punctuation and Enter behave like a normal keyboard.
- [x] Mode switching with an active composition follows an explicit, documented commit/cancel policy and never silently loses text.
- [x] Sensitive fields disable learning, predictions and candidate history.

### Required tests

- [ ] Add parameterized contract tests covering every action in Quick, Jyutping and Pinyin.
- [ ] Add race tests for stale decode, rapid Space, mode switch, editor switch and service restart.
- [x] Add regression tests for English collisions and backspace restoration.
- [ ] Add production-path integration tests rather than testing only pure policy classes.

### Verification

```bash
cd android
./gradlew testDebugUnitTest --tests '*Commit*' --tests '*Space*' --tests '*ProductionPath*' --rerun-tasks
./gradlew test lintDebug
```

Expected: the same observable contract passes for all three modes; no stale candidate can commit.

## Task 3 — Make 速成 best-in-class (P1)

**Files:**

- Modify: `android/app/src/main/assets/corpus/hk_core_chars.csv`
- Modify: `android/app/src/main/assets/corpus/hk_core_phrases.csv`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/QuickPrefixCandidateIndex.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusBackedDecoder.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CustomWordIndex.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/QuickRegressionTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/QuickMarketGateTest.kt`

### Coverage and correctness

- [ ] Verify standard Quick first/last-root encoding for every single-character entry.
- [ ] Put Hong Kong Traditional forms ahead of Taiwan/Mainland variants when HK usage differs.
- [ ] Make every HKSCS character reachable; direct corpus coverage must be at least 99.9%, with a tested codepoint fallback for the remainder.
- [ ] Never show tofu, an empty glyph or half of a supplementary-plane character.
- [ ] Cover common Hong Kong particles and phrases such as `唔`, `嘅`, `啲`, `冇`, `佢`, `唔該`, `唔好` and `冇問題`.
- [ ] Review the top five candidates for the 2,000 most frequent Quick keys with native users.
- [ ] Verify prefix input, full codes, ambiguity ordering and phrase codes independently.
- [ ] Detect malformed/duplicate codes during corpus generation and fail the build.

### Efficiency and safety

- [ ] Make the intended common candidate reachable within three candidate positions for at least 98% of benchmark prompts.
- [ ] Beat the best comparison keyboard on Top-1 by two points or median keystrokes by 5%.
- [ ] Do not sacrifice ordinary English entry to improve Quick Space commits.
- [ ] Allow custom Quick entries with bounded, validated import/export and deterministic ranking.
- [ ] Ensure learning helps repeated choices without permanently burying high-frequency defaults.

### Required regression examples

- [ ] `ai` ranks `時` appropriately.
- [ ] `rr` ranks `唔` appropriately.
- [ ] `ru`, `ri`, `kb`, and `os` surface `嘅`, `啲`, `冇`, and `佢` respectively.
- [ ] Common phrase codes produce `唔該`, `唔好`, `冇問題`, and `我嘅` within Top-3.
- [ ] Supplementary HKSCS cases survive decode, display, commit, backspace and Simplified conversion boundaries.

### Verification

```bash
cd android
./gradlew testDebugUnitTest --tests '*Quick*' --tests '*Hkscs*' --rerun-tasks
cd ..
python3 corpus/tools/score_three_mode_benchmark.py --mode quick --locked-holdout
```

Expected: all absolute thresholds and the relative market gate pass.

## Task 4 — Make 粵拼 best-in-class (P1)

**Files:**

- Modify: `android/app/src/main/assets/corpus/jyutping.csv`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/JyutpingSegmenter.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusBackedDecoder.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/JyutpingNormalizer.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/JyutpingAnnotation.kt`
- Create/modify corpus builders and manifests under: `corpus/`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingSegmenterTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingSegmentationCorpusTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/JyutpingMarketGateTest.kt`

### Critical ranking blockers

- [ ] `hai` never ranks `閪` first unless the explicit context genuinely requests the profanity; common `係/喺` intents rank first.
- [ ] `sik` ranks `食` appropriately for common Cantonese usage.
- [ ] `dei` and `ngodei` prefer `哋` and `我哋` where the pronoun context requires them.
- [ ] `gam` surfaces `咁`; `lei` surfaces `嚟` for common colloquial contexts.
- [ ] Add reviewed phrase entries for `你好`, `唔該`, `聽日`, `點解`, `食飯`, `佢哋`, `係咪` and other benchmark phrases.
- [ ] Reject every unintended offensive, nonsensical or non-HK Top-1 result in the reputation-sensitive set.

### Scheme and segmentation

- [ ] Accept canonical Jyutping spelling with optional tone digits 1–6.
- [ ] Accept toneless input without degrading tone-aware ranking.
- [ ] Accept explicit syllable separators and continuous multi-syllable input.
- [ ] Support reviewed abbreviated Jyutping where ambiguity remains controllable.
- [ ] Segment using phrase-level evidence; do not build phrases merely by concatenating each syllable's top character.
- [ ] Show Jyutping/tone annotation for ambiguous candidates without increasing accidental taps.
- [ ] Provide reverse lookup from a committed/selected character to its Jyutping reading.
- [ ] Make all HKSCS characters reachable and Unicode-safe.

### Language quality

- [ ] Include contemporary spoken Cantonese particles, contractions, names, locations and code-switching vocabulary.
- [ ] Separate explicit profanity availability from default safe ranking; do not silently censor deliberate input.
- [ ] Have native reviewers approve Top-5 results for at least 2,000 high-frequency syllables/phrases.
- [ ] Validate Hong Kong orthographic forms and document accepted variants.
- [ ] Beat both mainstream keyboards and the strongest specialist Jyutping keyboard under the shared scoring protocol.

### Verification

```bash
cd android
./gradlew testDebugUnitTest --tests '*Jyutping*' --tests '*Hkscs*' --tests '*ProductionPath*' --rerun-tasks
cd ..
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests
python3 corpus/tools/score_three_mode_benchmark.py --mode jyutping --locked-holdout
```

Expected: zero reputation-sensitive Top-1 failures and all absolute/relative market thresholds pass.

## Task 5 — Make 普通話拼音 best-in-class for Traditional-HK users (P1)

**Files:**

- Modify: `android/app/src/main/assets/corpus/pinyin.csv`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/PinyinDecoder.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/MandarinSyllables.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/PinyinNormalizer.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinDecoderTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinCorpusTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinImeIntegrationTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/PinyinMarketGateTest.kt`

### Input standard

- [ ] Accept toneless full Pinyin, tone digits 1–5, tone marks and `ü` aliases such as `v`.
- [ ] Accept apostrophes at syllable boundaries.
- [ ] Offer optional, documented fuzzy pairs without changing strict-mode results.
- [ ] Handle continuous sentence input using phrase/sentence evidence rather than per-syllable character concatenation.
- [ ] Recover from one common nearby-key typo without producing unsafe automatic commits.
- [ ] Preserve literal English through Enter and deliberate English selection.

### Traditional-HK output quality

- [ ] Rank Hong Kong Traditional forms by default while retaining the existing explicit Simplified toggle.
- [ ] Review Mainland-to-Traditional lexical differences; do not rely on character-by-character OpenCC conversion where the word differs.
- [ ] Make `nihao`, `xiexie`, `weishenme`, `zhongguo`, `xianggang` and `putonghua` produce the expected common phrases.
- [ ] Cover names, Hong Kong locations, current terminology and mixed English/Chinese sentences.
- [ ] Verify polyphonic characters and context-sensitive phrases.
- [ ] Make all required HKSCS output reachable and Unicode-safe.

### Market gate

- [ ] Reach the absolute Pinyin thresholds on the locked holdout.
- [ ] Match the best comparison keyboard's Top-3 and correction rate.
- [ ] Beat it by two Top-1 points or 5% median keystrokes on Traditional-HK prompts.
- [ ] Confirm that improvements do not depend on a network connection.

### Verification

```bash
cd android
./gradlew testDebugUnitTest --tests '*Pinyin*' --tests '*Traditional*' --rerun-tasks
cd ..
python3 corpus/tools/score_three_mode_benchmark.py --mode pinyin --locked-holdout
```

Expected: all absolute/relative Pinyin gates pass in offline mode.

## Task 6 — Strengthen shared ranking, learning and mixed input (P1)

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusBackedDecoder.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/Classifier.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/EnglishSuggestions.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/MemoryIndex.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/RoomUserMemory.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/CustomWordActivity.kt`
- Create/modify tests under: `android/app/src/test/kotlin/com/hkmixedkeyboard/`

### Checklist

- [ ] Consume the reviewed mixed-phrase corpus in the actual production decode path.
- [ ] Support custom entries for Quick, Jyutping and Pinyin with scheme-specific validation.
- [ ] Bound import size, row count, code length and display length; reject malformed data atomically with a clear report.
- [ ] Keep all learning local and disabled in password, payment and other sensitive fields.
- [ ] Make learning reversible through candidate correction and clear-data controls.
- [ ] Prevent a single accidental selection from overwhelming stable corpus frequency.
- [ ] Age or cap personal weights so ranking recovers from old mistakes.
- [ ] Keep cold-start benchmark quality separate from learned quality.
- [ ] Handle English words, acronyms, email fragments, URLs and Cantonese-English code switching predictably.
- [ ] Never send composition, learning or crash data over the network.

### Verification

```bash
cd android
./gradlew testDebugUnitTest --tests '*Memory*' --tests '*CustomWord*' --tests '*English*' --tests '*Mixed*' --rerun-tasks
```

Expected: deterministic cold ranking, bounded local learning and safe mixed-language input in every mode.

## Task 7 — Eliminate concurrency, lifecycle and data-integrity failures (P0/P1)

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Port/refine synchronization components from: `.worktrees/release-readiness/android/app/src/main/kotlin/`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusCache.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/UserMemoryDatabase.kt`
- Create instrumentation/stress tests under: `android/app/src/androidTest/`

### Checklist

- [ ] Serialize or synchronize decode request state, generation IDs, pending buffers and sessions.
- [ ] Cancel or ignore work after editor changes, mode changes, service teardown and configuration changes.
- [ ] Stress rapid typing, Space, backspace, candidate expansion and mode switching for at least 100,000 generated event sequences.
- [ ] Test process death and IME recreation without corrupting memory or losing committed text.
- [ ] Test every Room migration from the oldest public schema to the release schema.
- [ ] Corrupt or truncate caches deliberately and verify safe deterministic rebuilding.
- [ ] Keep corpus/cache versions tied to content hashes.
- [ ] Record zero uncaught exceptions, strict-mode violations and ANRs in the stress suite.

### Verification

```bash
cd android
./gradlew test connectedDebugAndroidTest --rerun-tasks
adb shell monkey -p com.hkmixedkeyboard --throttle 20 10000
```

Expected: no stale commits, crashes, ANRs or persistent-data corruption.

## Task 8 — Meet best-in-class latency and resource targets (P1)

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/performance/LatencyLogger.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/performance/PerfTracer.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CompositionPerformanceTest.kt`
- Create: `android/app/src/androidTest/kotlin/com/hkmixedkeyboard/TypingLatencyTest.kt`
- Create: `docs/release/performance_results.md`

### Targets

- [ ] Key visual/haptic acknowledgement p95 ≤50 ms.
- [ ] Candidate update p50 ≤30 ms, p95 ≤75 ms and p99 ≤150 ms on the reference mid-range device.
- [ ] Candidate update p95 ≤120 ms on the minimum-supported API 26 device.
- [ ] Warm keyboard presentation p95 ≤300 ms; cold presentation p95 ≤700 ms.
- [ ] No frame exceeds 32 ms during ordinary typing or candidate paging in a 10-minute trace.
- [ ] Memory remains bounded during a 30-minute mixed-mode session and returns near baseline after editor closure.
- [ ] Performance holds with the full HKSCS, phrase and personalization data loaded.
- [ ] No benchmark-only shortcuts or warmed corpus state are used in cold measurements.

### Verification

```bash
cd android
./gradlew connectedDebugAndroidTest
adb shell dumpsys gfxinfo com.hkmixedkeyboard reset
# Run the scripted typing workload.
adb shell dumpsys gfxinfo com.hkmixedkeyboard
adb shell dumpsys meminfo com.hkmixedkeyboard
```

Expected: raw percentile tables and traces satisfy every target on both reference devices.

## Task 9 — Make setup and daily operation effortless (P1/P2)

**Files:**

- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Create localized resources under: `android/app/src/main/res/values-*/`
- Create instrumentation tests under: `android/app/src/androidTest/`

### First-run flow

- [ ] Show live states for “keyboard enabled” and “keyboard selected.”
- [ ] Provide separate actions to enable and select the keyboard.
- [ ] Confirm success and open a safe practice field containing mode-specific examples.
- [ ] Teach mode switching, Space selection, Enter literal input, backspace restore, cursor swipe and Simplified toggle.
- [ ] Never leave the enable button looking incomplete after the IME is enabled.
- [ ] Complete setup successfully in under 90 seconds for at least 90% of first-time testers.

### Daily-use flow

- [ ] Keep the one-tap mode cycle and add a long-press direct mode picker.
- [ ] Preserve or explicitly resolve active composition during mode changes.
- [ ] Display all three modes in the Android IME label, app name and Settings.
- [ ] Offer keyboard-height adjustment and a practical one-handed option.
- [ ] Keep candidate rows stable with no layout jump when results appear/disappear.
- [ ] Make candidate expansion, collapse and paging discoverable and reversible.
- [ ] Confirm destructive actions, including learned-data clearing and bulk imports.
- [ ] Localize all user-facing strings in Traditional Chinese and English; avoid hard-coded copy.
- [ ] Fix status/navigation-bar contrast in light and dark themes.
- [ ] Ensure all critical actions work without undocumented gestures.

### Usability study

- [ ] Test at least 15 new users and 15 experienced Chinese-IME users.
- [ ] Measure setup success, mode-switch errors, candidate-selection errors, correction time and task completion.
- [ ] Require ≥90% unassisted setup success and ≥95% successful completion of each core typing task.
- [ ] Resolve every repeated critical confusion before release.

## Task 10 — Complete keyboard accessibility (P1)

**Files:**

- Port/refine: accessibility node work from `.worktrees/release-readiness/android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/SymbolPageView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Create/modify tests under: `android/app/src/androidTest/`

### Checklist

- [ ] Expose every key in custom canvas views as a virtual accessibility node with label, bounds, role, state and click action.
- [ ] Expose candidate position, selected state, pronunciation annotation and expand/collapse action.
- [ ] Give mode, Shift/Caps, Simplified, symbols and emoji controls meaningful state announcements.
- [ ] Provide at least 48dp effective touch targets or an equivalent accessible target expansion.
- [ ] Meet WCAG AA contrast for text and essential controls in every theme.
- [ ] Support TalkBack explore-by-touch, linear traversal, double-tap activation and correction without focus loss.
- [ ] Verify Switch Access and large display/font settings.
- [ ] Replace deprecated accessibility calls where a supported equivalent exists.
- [ ] Perform manual testing with at least two users who rely on assistive technology, or obtain specialist accessibility review.

### Verification

```bash
cd android
./gradlew connectedDebugAndroidTest
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml build/accessibility-window.xml
```

Expected: every visible key/candidate is represented and operable; manual traversal has no trap or unlabeled control.

## Task 11 — Protect privacy, data and corpus legality (P1)

**Files:**

- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/privacy/SensitiveFieldDetector.kt`
- Modify: `docs/privacy_policy.md`
- Modify: `docs/store/privacy_policy.html`
- Modify: `NOTICE`
- Modify: `android/app/src/main/assets/licenses/NOTICE.txt`
- Modify: `corpus/sources/corpus_license_register.csv`
- Create: `docs/release/legal_signoff.md`

### Checklist

- [ ] Confirm the merged manifest has no `INTERNET` permission and no dependency adds network capability.
- [ ] Confirm learning, clipboard-like context and suggestions are disabled in all sensitive editor variations.
- [ ] Confirm `allowBackup` and exported-component settings match the privacy model.
- [ ] Validate import/export against oversized files, path tricks, malformed Unicode and formula-like payloads.
- [ ] Document exactly what local data is stored, where, for how long and how users delete/export it.
- [ ] Use one real support contact consistently; remove placeholder domains.
- [ ] Record source, version, checksum, transformation, licence and attribution for every corpus.
- [ ] Obtain owner/legal sign-off for GPL, CC BY-SA, ODbL, HKSCS, OpenCC and all derived datasets.
- [ ] Complete Play Data Safety answers from the final binary and privacy policy.
- [ ] Verify the public privacy URL is reachable and matches the in-app text.

### Verification

```bash
cd android
./gradlew :app:dependencies
./gradlew assembleRelease
aapt2 dump permissions app/build/outputs/apk/release/*.apk
rg -n 'example|TODO|TBD|support@' ../docs ../NOTICE app/src/main/assets/licenses
```

Expected: offline claims are true, attribution is complete and no placeholder/legal blocker remains.

## Task 12 — Add CI and a fail-closed release pipeline (P0/P1)

**Files:**

- Create: `.github/workflows/android.yml`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/version.properties`
- Create: `scripts/verify_release.sh`
- Create: `docs/release/release_runbook.md`

### CI checklist

- [ ] Run JVM tests, lint and corpus-tool tests on every pull request.
- [ ] Run instrumentation tests on a supported emulator for protected branches.
- [ ] Run benchmark schema/leakage checks without exposing the locked answer set.
- [ ] Archive test, lint, coverage and benchmark reports.
- [ ] Fail on corpus drift without manifest/checksum updates.
- [ ] Fail on new permissions, exported components or unsigned release artifacts.
- [ ] Do not increment `version.properties` during debug/test/lint tasks.

### Release checklist

- [ ] Require explicit signing configuration for every production bundle; fail rather than silently producing unsigned output.
- [ ] Keep signing credentials outside Git and confirm backup/recovery ownership.
- [ ] Build from a clean tagged commit with no untracked source inputs.
- [ ] Verify versionCode/versionName, package, min/target SDK and certificate.
- [ ] Generate and test APKs from the final AAB, not a separately built debug/release APK.
- [ ] Confirm target SDK meets the current Play requirement at release time.
- [ ] Produce deterministic corpus and notice checksums.
- [ ] Record the exact Git commit and AAB SHA-256 in the evidence index.

### Verification

```bash
cd android
./gradlew clean test lintRelease bundleRelease
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
sha256sum app/build/outputs/bundle/release/app-release.aab
git status --short
```

Expected: signed current-version AAB, complete clean suite and no source-tree version drift.

## Task 13 — Pass the supported-device matrix and closed beta (P1)

### Required matrix

- [ ] API 26 minimum-supported device/emulator.
- [ ] Android 13 Samsung/OneUI physical device.
- [ ] Current stable Pixel/AOSP physical device.
- [ ] Current preview/target-SDK emulator when applicable.
- [ ] Small phone, tall phone and tablet/foldable layout.
- [ ] Light/dark theme, portrait/landscape, large font/display and low-memory conditions.

### Device checklist

- [ ] Enable/select/setup flow works after fresh install and upgrade.
- [ ] All three modes pass representative smoke scripts.
- [ ] HKSCS and supplementary characters render without tofu using device fonts.
- [ ] Haptics, sound, cursor swipe, long presses and touch boundaries are correct.
- [ ] Rotation, app switching, editor switching and process death do not lose or duplicate text.
- [ ] TalkBack and Switch Access complete the core typing flow.
- [ ] No crash, ANR, StrictMode violation or database migration failure occurs.

### Closed beta gate

- [ ] At least 100 opted-in testers use the release candidate for at least two weeks.
- [ ] Cover at least 10,000 keyboard sessions without collecting typed content.
- [ ] Achieve ≥99.95% crash-free sessions and zero unresolved reproducible ANRs.
- [ ] No unresolved severity-1 or severity-2 input-loss, wrong-commit, offensive-ranking, privacy or accessibility defect remains.
- [ ] At least 60% of comparative testers prefer this keyboard for their primary tested mode.
- [ ] Every recurring usability complaint has an owner and written disposition.

## Task 14 — Synchronize product claims and perform final go/no-go (P1)

**Files:**

- Modify: `README.md`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/xml/method.xml`
- Modify: `docs/store/name_*.txt`
- Modify: `docs/store/short_*.txt`
- Modify: `docs/store/long_*.md`
- Modify: `docs/store/privacy_policy.html`
- Create: `docs/release/go_no_go.md`

### Claim checklist

- [ ] Android IME label and store names mention all three modes.
- [ ] Space, Enter, punctuation, learning, offline and Simplified claims exactly match production behaviour.
- [ ] Screenshots come from the final signed candidate and show all three modes.
- [ ] “Best-in-class” is used only if every absolute and relative P2 market gate passed.
- [ ] Privacy, accessibility and supported-version statements are verified against the final AAB.
- [ ] README reflects current development state rather than the old prototype stages.

### Final evidence review

- [ ] Product owner signs the mode-quality results.
- [ ] Native Cantonese reviewer signs Quick and Jyutping results.
- [ ] Native Mandarin reviewer signs Pinyin results.
- [ ] Accessibility reviewer signs the TalkBack/Switch Access report.
- [ ] Engineering owner signs tests, performance, migrations and device matrix.
- [ ] Release owner signs certificate, version, AAB hash, Data Safety and store copy.
- [ ] Legal/data owner signs corpus provenance and notices.
- [ ] All P0 and P1 items are checked with evidence.
- [ ] All P2 items are checked before making a best-in-class claim.

### Final clean-room verification

```bash
git clone <repository-url> hk-mixed-keyboard-release-verify
cd hk-mixed-keyboard-release-verify/android
./gradlew clean test lintRelease connectedDebugAndroidTest bundleRelease
cd ..
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q corpus/tools/tests
./scripts/verify_release.sh android/app/build/outputs/bundle/release/app-release.aab
```

Expected: every command succeeds from a clean checkout and produces the same reviewed artifact metadata.

## Public-release decision

The app is **GO** only when all statements below are true:

- [ ] 速成 passes its absolute and competitor-relative gates.
- [ ] 粵拼 passes its absolute and competitor-relative gates.
- [ ] 普通話拼音 passes its absolute and competitor-relative gates.
- [ ] Shared commit, correction, mixed-input and learning contracts pass.
- [ ] Accessibility, performance, stability and device gates pass.
- [ ] Privacy, legal, store and signed-artifact gates pass.
- [ ] The evidence index links every result to the exact release commit and AAB.
- [ ] No unresolved P0/P1 defect or unapproved P2 claim gap remains.

If any box above is unchecked, the release remains **NO-GO**.
