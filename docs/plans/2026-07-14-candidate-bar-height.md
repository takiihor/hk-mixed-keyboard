# Candidate Bar Height Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Collapse an empty candidate/preview bar and use a compact, stable 42dp height whenever candidates, composing text, or a system message is shown.

**Architecture:** Introduce a pure `CandidateBarLayoutPolicy` for the three display states and use it as the single source for bar height and root height reservation. `CandidateBarView` owns the current content state and exposes state changes to `HkImeService`, which updates only its own layout parameters/minimum height without recreating keyboard or input state.

**Tech Stack:** Kotlin, Android Views, InputMethodService, JUnit 4, Gradle.

---

### Task 1: Specify compact candidate-bar geometry

**Files:**
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt`

**Step 1: Write failing tests**

Define tests for `Hidden`, `CandidatesOrComposing`, and `SystemMessage`: hidden
height/reservation is zero; non-hidden height is `42dp`; keyboard height is
unchanged; all theme inputs resolve to the same dimensions.

**Step 2: Verify tests fail**

Run `cd android && ./gradlew :app:testDebugUnitTest --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest`.

**Step 3: Implement the minimal pure policy**

Expose `VISIBLE_HEIGHT_DP = 42f`, a display-state enum, and functions for bar
height and root minimum height (`keyboardHeight + visible bar height`).

**Step 4: Verify tests pass**

Run the focused test again.

### Task 2: Make CandidateBarView stateful and compact

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarViewStateTest.kt` (or create it)
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`

**Step 1: Write failing tests**

Test that `clear()` produces `Hidden`; loading and safe mode produce
`SystemMessage`; candidates produce `CandidatesOrComposing`; loading-to-candidates
does not change height; system labels have `maxLines = 1` and `ellipsize = END`.

**Step 2: Verify tests fail**

Run the focused CandidateBarView-state tests.

**Step 3: Implement minimal state updates**

Add an `onDisplayStateChanged` callback, switch visibility/layout height from
the pure policy, use `17sp` text and 5dp vertical padding, and only enter
`Hidden` from `clear()` when no system message is active.

**Step 4: Verify tests pass**

Run the focused CandidateBarView-state tests.

### Task 3: Remove root height reservation for Hidden state

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`

**Step 1: Write failing tests**

Assert the input minimum height equals the keyboard-only height when Hidden and
the keyboard plus 42dp when visible, without changing `keyboardHeightPx`.

**Step 2: Verify tests fail**

Run the focused layout-policy and keyboard-layout tests.

**Step 3: Implement minimal wiring**

Create both normal and fallback candidate bars with the state callback. When it
fires, update the candidate bar layout params, visibility, and root minimum
height. Leave the keyboard view height and navigation-inset padding untouched.

**Step 4: Verify tests pass**

Run focused tests.

### Task 4: Verify themes, input preservation, and package

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardThemeColorsTest.kt` only if needed for a theme-size invariant test
- Modify: `android/app/version.properties` (auto-incremented by build)

**Step 1: Run all unit tests**

Run `cd android && ./gradlew :app:testDebugUnitTest`.

**Step 2: Build and inspect emulator**

Run `cd android && ./gradlew :app:assembleDebug`, install the APK, and capture
dark/light screenshots for Hidden and visible candidate states. Verify the
candidate bar is 0dp when Hidden and 42dp when visible.

**Step 3: Commit**

Commit the implementation, tests, generated version metadata and both plan docs.
