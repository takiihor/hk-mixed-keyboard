# Stage 2 Input Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add repeating backspace, automatic bilingual personalization, key vibration, and `！` entry to the Stage 2 Android keyboard.

**Architecture:** Extend user memory to store and query per-candidate usage, then merge learned candidates with decoder and built-in English suggestions in the IME candidate bar. Add a small testable hold-action controller used by `KeyboardView` for backspace repeat and question-key long press, while the service propagates vibration settings.

**Tech Stack:** Kotlin, Android InputMethodService/View APIs, Room, DataStore, JUnit 4, Gradle.

---

### Task 1: Personalized Candidate Memory

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/IUserMemory.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/UserMemory.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/RoomUserMemory.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/UserMemoryEntity.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/memory/UserMemoryDatabase.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/PersonalizedSuggestionTest.kt`

1. Write tests showing separate candidates are counted and prefix lookup ranks frequent English and Chinese choices.
2. Run `./gradlew testDebugUnitTest --tests '*PersonalizedSuggestionTest'` and confirm failure.
3. Change memory identity to buffer plus candidate text and add prefix suggestion lookup.
4. Bump the Room schema version; destructive fallback is already configured.
5. Re-run the focused test and confirm it passes.

### Task 2: Automatic Learning And Candidate Merge

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/commit/CommitController.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/EnglishSuggestions.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/PersonalizedSuggestionTest.kt`

1. Add failing tests for `ALWAYS_SPACE` literal learning and `hap` returning `happy` and `happen`.
2. Run the focused tests and confirm expected failures.
3. Learn literal buffers committed by Space outside sensitive fields.
4. Merge learned, decoded Chinese, built-in English, and literal candidates with stable de-duplication.
5. Re-run focused and existing memory/safe-mode tests.

### Task 3: Hold Actions And Haptics

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HoldActionController.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/HoldActionControllerTest.kt`

1. Add failing state-machine tests for repeated backspace cancellation and long-press exclamation suppression.
2. Run focused tests and confirm failure.
3. Implement the controller and connect it to touch down/up/cancel.
4. Request keyboard-tap haptics for each emitted action when enabled.
5. Propagate the DataStore vibration setting to both normal and fallback keyboard views.
6. Re-run focused tests.

### Task 4: Verification

**Files:**
- Modify only if verification exposes a defect.

1. Run `./gradlew testDebugUnitTest`.
2. Run `./gradlew assembleDebug`.
3. Inspect failures and fix only regressions related to this change.
4. Record that Git commits were not possible because the supplied workspace has no `.git` directory.
