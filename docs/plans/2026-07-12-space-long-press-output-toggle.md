# Space Long-Press Output Toggle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Toggle persisted Traditional/Simplified output by holding the space key without breaking space taps or cursor swipes.

**Architecture:** Extend the existing hold-action and space-swipe arbitration rather than adding a second timer system. `KeyboardView` owns gesture cancellation/consumption; `HkImeService` owns the persisted output-mode change and user feedback.

**Tech Stack:** Kotlin, Android IME APIs, DataStore Preferences, JUnit 4, Gradle, ADB emulator verification.

---

### Task 1: Space gesture arbitration

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/HoldActionControllerTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyTouchPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HoldActionController.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify if needed: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyTouchPolicy.kt`

1. Add failing tests for stationary space hold, cancellation after swipe activation, consumed release after a fired hold, and unchanged tap behavior.
2. Run focused tests and confirm failures represent the missing space-hold behavior.
3. Implement the smallest cancellable hold integration in `KeyboardView`, reusing `HoldActionController`.
4. Run focused hold/touch/space tests and confirm they pass.

### Task 2: Persisted output toggle and feedback

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Test: relevant preference and T2S tests under `android/app/src/test/kotlin/com/hkmixedkeyboard/`

1. Add a failing pure policy/callback test proving a space long press requests the output-mode toggle without committing a space.
2. Add the `KEY_SPACE` long-press service branch, persist the inverse preference, play one selection haptic, and show the approved Toast.
3. Keep Settings as the other explicit toggle path and retain the `·簡` label.
4. Run focused settings, gesture, commit, and T2S tests.

### Task 3: Full and emulator verification

1. Run `./gradlew testDebugUnitTest --rerun-tasks` and `./gradlew assembleDebug` from `android/`.
2. Install the debug APK on the emulator.
3. Verify a normal space tap inserts one space.
4. Verify a stationary space hold toggles output, shows feedback, updates `·簡`, and inserts no space.
5. Verify a horizontal space swipe moves the cursor and does not toggle output.
6. Restore Traditional mode, run `git diff --check`, and confirm no debug logging remains.

