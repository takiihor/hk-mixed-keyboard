# Gboard-style Keyboard Layout Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the confirmed five-row keyboard with standard staggered letter positions and revised punctuation, backspace, Enter, and space-bar placement.

**Architecture:** Add a pure Kotlin layout model that owns row definitions and calculates normalized key bounds. Render and hit-test those same bounds in `KeyboardView`, then derive the IME view height from the model's row-height weights.

**Tech Stack:** Kotlin 2.0, Android custom `View`, JUnit 4, Gradle.

---

### Task 1: Define and test the layout contract

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`

**Step 1: Write failing tests**

Test the five row labels, number row, question/backspace/Enter/exclamation
positions, space weight of `3`, and the `A` center alignment between `Q` and
`W`.

**Step 2: Verify the tests fail**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: compilation failure because `KeyboardLayout` does not exist.

**Step 3: Implement the minimal pure Kotlin layout model**

Define key and row data classes, the confirmed row list, and a bounds
calculation using a common eleven-unit horizontal grid.

**Step 4: Verify the tests pass**

Run the same focused Gradle test and expect all layout tests to pass.

### Task 2: Connect rendering, touch handling, and IME height

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`

**Step 1: Replace private row geometry**

Build drawing rectangles from `KeyboardLayout`, retaining the current margins,
colors, labels, and hit-testing behavior.

**Step 2: Dispatch separate punctuation keys**

Add separate `？` and `！` constants and route both through
`CommitController.onPunctuation`.

**Step 3: Update keyboard height**

Use `56dp` per full typing row and the layout's total row-height weight in both
normal and fallback input views.

**Step 4: Verify focused and full tests**

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: both commands exit successfully.

### Task 3: Review the resulting change

**Step 1: Inspect all changed files**

Confirm every changed line directly supports the requested layout.

**Step 2: Commit**

Unavailable in this workspace because no `.git` directory is present.
