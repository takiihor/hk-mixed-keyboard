# Offline Mandarin Pinyin Mode Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a third, fully offline Mandarin Pinyin input mode with Traditional candidates and continuous toneless phrase input.

**Architecture:** Extend the existing scheme/decoder architecture rather than embedding librime. Build a pinned flat Pinyin corpus from CC-CEDICT, load it through the existing binary-cache pattern, and reuse the bounded Jyutping segmentation/composition approach where exact phrase entries are absent.

**Tech Stack:** Kotlin, Android DataStore/IME APIs, CC-CEDICT data, CSV/binary corpus cache, JUnit 4, Gradle, ADB emulator verification.

---

### Task 1: Three-state scheme preference and mode cycling

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/DecoderContract.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Add/modify tests under: `android/app/src/test/kotlin/com/hkmixedkeyboard/`

1. Add failing tests for Quick→Jyutping→Pinyin→Quick cycling and legacy boolean migration.
2. Run focused tests and observe expected failures.
3. Add `Scheme.PINYIN`, a persisted scheme id, migration from `jyutping_primary`, and three-state settings UI.
4. Update labels/root visibility/warm-state dispatch without adding decode behavior yet.
5. Run focused settings/scheme tests.

### Task 2: Reproducible offline Pinyin corpus

**Files:**
- Create: `corpus/tools/build_pinyin_corpus.*`
- Create: `android/app/src/main/assets/corpus/pinyin.csv`
- Modify: `corpus/sources/corpus_license_register.csv`
- Modify: `NOTICE`
- Modify: `android/app/src/main/assets/licenses/NOTICE.txt`
- Add corpus-generation tests/fixtures.

1. Add failing normalization tests for tone removal, separator removal, `v/ü`, Traditional headwords, and duplicate readings.
2. Implement a deterministic generator against a pinned CC-CEDICT release/checksum.
3. Generate the compact runtime CSV and verify representative entries `nihao`, `xianggang`, `putonghua`, and `mao`.
4. Record provenance and CC BY-SA 4.0 attribution.

### Task 3: Loader, indexes, and Pinyin decoder

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusBackedDecoder.kt`
- Add Pinyin normalizer/segmenter if needed under `decoder/`
- Add decoder tests under `android/app/src/test/kotlin/com/hkmixedkeyboard/`

1. Add failing exact/prefix tests for representative words and polyphonic candidates.
2. Add failing segmented fallback tests for valid continuous syllables.
3. Implement cached rows, exact/prefix indexes, normalization, and bounded composition.
4. Bump corpus content version and run decoder/cache tests.

### Task 4: IME/display integration

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/CandidateDisplayPolicy.kt` if required
- Modify relevant commit/classifier tests.

1. Add failing tests that Pinyin is Chinese-first, suppresses English completion, commits exact Chinese with Space, and preserves Enter literal behavior.
2. Wire Pinyin through warm-up, display, commit, prediction, labels, and root visibility.
3. Verify Traditional candidates and existing Simplified boundary conversion.

### Task 5: Documentation and full verification

**Files:**
- Modify: `README.md`
- Modify: `docs/store/short_zh.txt`
- Modify: `docs/store/long_zh.md`
- Modify English store copy where scheme coverage is described.

1. Update user-facing and store documentation for the offline third mode.
2. Run `./gradlew testDebugUnitTest --rerun-tasks`, lint, and `assembleDebug`.
3. Install the APK and verify mode cycling plus `nihao`, `xianggang`, `putonghua`, and `mao` on the emulator.
4. Verify no `INTERNET` permission and restore Quick/Traditional mode.
5. Run `git diff --check` and final holistic review.

