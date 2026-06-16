# Low-Latency Haptics And English Completion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Synchronize key input with a single light haptic and add corpus-backed English prefix completion without removing Chinese suggestions.

**Architecture:** Add a testable touch-action policy so ordinary keys fire once on press while hold-sensitive keys retain their specialized timing. Extend `CorpusLoader` with a sorted English-word index and binary-search prefix lookup, then merge those candidates into the existing composing bar.

**Tech Stack:** Kotlin, Android View/InputMethodService APIs, JUnit 4, Gradle.

---

### Task 1: Press-Time Key Dispatch

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyTouchPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyTouchPolicyTest.kt`

1. Write failing tests proving normal keys emit once on press and not again on release.
2. Run `./gradlew testDebugUnitTest --tests '*KeyTouchPolicyTest'` and confirm failure.
3. Implement the minimal policy for immediate, repeating, and long-press keys.
4. Wire normal keys to press-time dispatch while preserving backspace repeat and `？！` long press.
5. Replace `KEYBOARD_TAP` with the lighter `CLOCK_TICK`, requested only once per physical press.
6. Re-run focused hold/touch tests.

### Task 2: Corpus English Prefix Index

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/decoder/CorpusLoader.kt`
- Replace: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/EnglishSuggestions.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/EnglishCompletionTest.kt`

1. Write failing tests showing `comm` returns `communication`, exact words are excluded, and limits are respected.
2. Run `./gradlew testDebugUnitTest --tests '*EnglishCompletionTest'` and confirm failure.
3. Build a unique sorted English-word list using maximum corpus frequency per source word.
4. Implement binary-search prefix lookup.
5. Re-run focused tests.

### Task 3: Candidate Merge And Warm-Up

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/EnglishCompletionTest.kt`

1. Add a failing test for English completion and Chinese suggestions coexisting.
2. Warm the English index on the decode thread.
3. Merge personalized candidates, corpus English completions, decoded Chinese, and literal input.
4. Re-run focused candidate tests.

### Task 4: Verification

**Files:**
- Modify only if verification exposes a defect.

1. Run `./gradlew testDebugUnitTest`.
2. Run `./gradlew assembleDebug`.
3. Confirm the APK path and test count.
4. Note that commits are unavailable because the workspace has no `.git` directory.
