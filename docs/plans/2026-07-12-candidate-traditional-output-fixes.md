# Candidate and Traditional Output Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Surface `貓` for `cat`, prevent accidental Simplified-output switching, and keep expanded candidates visible within the keyboard popup.

**Architecture:** Extend classification metadata just enough to distinguish exact English-assist matches from generic Latin input, then pass that intent to the existing display policy. Keep Simplified conversion at the output boundary but expose its switch only in Settings. Make candidate-grid sizing explicit through a small pure layout policy plus Android layout parameters.

**Tech Stack:** Kotlin, Android IME APIs, DataStore Preferences, JUnit 4, Gradle, ADB emulator verification.

---

### Task 1: Exact `cat → 貓` display priority and corpus freshness

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateDisplayPolicyTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/EnglishAssistCandidateTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/DecodeResult.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusBackedDecoder.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/ClassifyResult.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/Classifier.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt`

1. Add a failing display-level test asserting exact English-assist candidates precede English prefix completions for `cat`.
2. Run the focused tests and confirm the failure shows `category` before `貓`.
3. Add the smallest exact-assist metadata and display-ordering change needed to pass the test.
4. Add a failing cache-version assertion, bump `CORPUS_CONTENT_VERSION`, and verify it passes.
5. Run `./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.CandidateDisplayPolicyTest --tests com.hkmixedkeyboard.EnglishAssistCandidateTest --tests com.hkmixedkeyboard.CorpusCacheTest` from `android/`.

### Task 2: Settings-only Simplified output

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/HoldActionControllerTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`

1. Add or update a failing hold-action test asserting the mode key has no Simplified-output long-press action.
2. Confirm the test fails under the current long-press implementation.
3. Remove the candidate-bar idle toggle and mode-key long-press toggle while retaining the Settings switch and `·簡` state label.
4. Run focused hold, settings, and T2S tests.

### Task 3: Expanded candidate grid sizing

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridLayoutPolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateGridLayoutPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridView.kt`

1. Add a failing pure unit test asserting a four-column grid assigns a zero base width and equal weight to every cell.
2. Run it and confirm it fails because the sizing policy is absent.
3. Add the minimal policy and apply `width=0`, equal column weight, and `MATCH_PARENT` grid width inside the vertical `ScrollView`.
4. Remove temporary candidate debug logging.
5. Run the focused layout policy test.

### Task 4: Full verification

**Files:**
- Verify only: all changed production and test files.

1. Run `./gradlew testDebugUnitTest` from `android/`.
2. Run `./gradlew assembleDebug` and install the generated APK on the emulator.
3. Clear only the app corpus cache or install a build with the bumped content version.
4. Type `cat` and verify `貓` is visible before English prefix completions.
5. Tap `貓` and verify the committed character is Traditional by default.
6. Open the expanded candidate grid and verify all rows contain readable candidate text.
7. Enable Simplified output in Settings, confirm `·簡` is visible, and verify tapping `貓` commits `猫`.
8. Run `git diff --check` and review `git diff` to ensure unrelated existing changes were preserved.

