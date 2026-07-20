# Light Priority Candidate and Period Hold Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore blue learned-candidate text in the light theme and make a main-keyboard long press on `。` insert `.` directly.

**Architecture:** Keep the candidate renderer unchanged: it already maps priority candidates to `KeyboardThemeColors.candidatePriorityText`; correct only the light palette token. Reuse `KeyboardView`’s `HoldActionController` for the main `。` key, and centralize its short/long outputs in a small pure policy so the service callback remains a thin dispatcher.

**Tech Stack:** Kotlin, Android View IME, JUnit 4, Gradle.

---

### Task 1: Cover the light-theme priority candidate colour

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemeColorsTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardThemeColors.kt`

**Step 1: Write the failing test**

Add a test that resolves `KeyboardTheme.IOS_LIGHT`, asserts its priority candidate colour is blue (`0xFF0B57D0`) and differs from `candidateText`.

**Step 2: Run test to verify it fails**

Run `cd android && ./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardThemeColorsTest`.
Expected: FAIL because the current light priority colour is `#111214`.

**Step 3: Write minimal implementation**

Set `IOS_LIGHT.candidatePriorityText` to `0xFF0B57D0.toInt()` without changing the dark value.

**Step 4: Run test to verify it passes**

Run the same Gradle test command. Expected: PASS.

### Task 2: Make main `。` a deferred long-press key

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyTouchPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyTouchPolicy.kt`

**Step 1: Write the failing test**

Assert `。` does not emit on press and does use the hold gesture.

**Step 2: Run test to verify it fails**

Run `cd android && ./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.KeyTouchPolicyTest`.
Expected: FAIL because `。` currently emits on press and is not a hold key.

**Step 3: Write minimal implementation**

Add `KeyboardLayout.KEY_PERIOD` to the excluded press-emission and hold-gesture conditions.

**Step 4: Run test to verify it passes**

Run the same Gradle test command. Expected: PASS.

### Task 3: Define and wire punctuation hold resolution

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/MainKeyboardLongPressPolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/MainKeyboardLongPressPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`

**Step 1: Write the failing tests**

Add pure policy tests asserting short press of `。` resolves to `。` and a long press resolves only to `.`.

**Step 2: Run tests to verify they fail**

Run `cd android && ./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.MainKeyboardLongPressPolicyTest`.
Expected: FAIL because the policy does not exist.

**Step 3: Write minimal implementation**

Create a policy returning the base label for a short press and `.` for a long press of `。`. Use it in `KeyboardView.beginGesture` and `HkImeService.handleKeyLongPress`.

**Step 4: Run tests to verify they pass**

Run the focused policy and touch-policy tests. Expected: PASS.

### Task 4: Verify the regression suite and package the fix

**Files:**
- Modify: `android/app/version.properties` (auto-incremented by APK build)

**Step 1: Run all unit tests**

Run `cd android && ./gradlew :app:testDebugUnitTest`. Expected: PASS.

**Step 2: Build debug APK**

Run `cd android && ./gradlew :app:assembleDebug`. Expected: PASS and an incremented APK.

**Step 3: Commit**

Commit the tests, implementation, generated version metadata, and plan documents with message `fix: restore light priority blue and period hold`.
