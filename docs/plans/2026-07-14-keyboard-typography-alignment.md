# Keyboard Typography and Hint Alignment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Increase candidate text to `21sp`, render Cangjie roots at `16sp`, and keep `13sp` Latin hints anchored inside the top-right corner.

**Architecture:** Keep candidate sizing in the existing `CandidateBarLayoutPolicy`. Add a pure-Kotlin `KeyboardTypographyPolicy` for main-key sizes and hint coordinates, then have `KeyboardView` consume it with a dedicated root paint so unrelated key labels remain `17sp`.

**Tech Stack:** Kotlin, Android custom `View`/`Canvas`, JUnit 4, Gradle.

---

### Task 1: Candidate Text Size

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt`

**Step 1: Write the failing test**

Change the candidate typography assertion to:

```kotlin
assertEquals(21f, CandidateBarLayoutPolicy.TEXT_SIZE_SP)
```

Keep the `42f` height and one-line assertions in the same test.

**Step 2: Run test to verify it fails**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest \
  --console=plain
```

Expected: FAIL because `TEXT_SIZE_SP` is still `20f`.

**Step 3: Write the minimal implementation**

Change only:

```kotlin
const val TEXT_SIZE_SP = 21f
```

**Step 4: Run test to verify it passes**

Run the Task 1 command again. Expected: PASS.

### Task 2: Main-Key Typography Policy

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardTypographyPolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardTypographyPolicyTest.kt`

**Step 1: Write the failing policy test**

Create tests requiring these sizes and coordinates:

```kotlin
assertEquals(17f, KeyboardTypographyPolicy.MAIN_LABEL_TEXT_SIZE_SP)
assertEquals(16f, KeyboardTypographyPolicy.CANGJIE_ROOT_TEXT_SIZE_SP)
assertEquals(13f, KeyboardTypographyPolicy.LATIN_HINT_TEXT_SIZE_SP)
assertEquals(13f, KeyboardTypographyPolicy.SPACE_LABEL_TEXT_SIZE_SP)
assertEquals(21f, KeyboardTypographyPolicy.POPUP_LABEL_TEXT_SIZE_SP)
assertEquals(3f, KeyboardTypographyPolicy.HINT_INSET_DP)
assertEquals(97f, KeyboardTypographyPolicy.latinHintX(100f, 1f), 0.001f)
assertEquals(128f, KeyboardTypographyPolicy.latinHintBaseline(100f, 2f, -22f), 0.001f)
```

The baseline assertion proves the hint's top is inset by `3dp`: baseline equals
`top + inset - ascent`.

**Step 2: Run test to verify it fails**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardTypographyPolicyTest \
  --console=plain
```

Expected: compilation FAIL because `KeyboardTypographyPolicy` does not exist.

**Step 3: Write the minimal policy**

Create:

```kotlin
package com.hkmixedkeyboard.ui

object KeyboardTypographyPolicy {
    const val MAIN_LABEL_TEXT_SIZE_SP = 17f
    const val CANGJIE_ROOT_TEXT_SIZE_SP = 16f
    const val LATIN_HINT_TEXT_SIZE_SP = 13f
    const val SPACE_LABEL_TEXT_SIZE_SP = 13f
    const val POPUP_LABEL_TEXT_SIZE_SP = 21f
    const val HINT_INSET_DP = 3f

    fun latinHintX(keyRightPx: Float, density: Float): Float =
        keyRightPx - HINT_INSET_DP * density

    fun latinHintBaseline(keyTopPx: Float, density: Float, hintAscentPx: Float): Float =
        keyTopPx + HINT_INSET_DP * density - hintAscentPx
}
```

**Step 4: Run test to verify it passes**

Run the Task 2 command again. Expected: PASS.

### Task 3: Consume the Policy in KeyboardView

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Verify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`

**Step 1: Establish the failing integration check**

Before changing `KeyboardView`, run:

```bash
rg -n 'paint(Label|Root|Hint|Space|PopLabel)\.textSize|latinHint(X|Baseline)' \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt
```

Expected: no `paintRoot` and no policy coordinate calls; `paintLabel` still owns
the Cangjie root at `17sp` and the inset is still `5dp`.

**Step 2: Write the minimal integration**

In `KeyboardView`:

- add a centred `paintRoot`;
- keep `paintHint.textAlign = Paint.Align.RIGHT`;
- size all five paints from `KeyboardTypographyPolicy`;
- draw Cangjie roots with `paintRoot`;
- calculate the hint x-coordinate and baseline with `latinHintX` and
  `latinHintBaseline`;
- remove the old `hintInset` field.

Do not change any key geometry, colour or event code.

**Step 3: Run focused tests**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardTypographyPolicyTest \
  --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --console=plain
```

Expected: PASS.

**Step 4: Inspect the integration**

Run the `rg` command from Step 1. Expected: root `16sp`, hint `13sp`, and calls
to both top-right anchor helpers. Confirm `paintHint` remains right-aligned.

### Task 4: Regression Verification

**Files:**
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Verify: `android/app/version.properties`

**Step 1: Run the complete Android unit-test suite**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL` with no test failures.

**Step 2: Check the patch**

Run:

```bash
git diff --check
git diff -- android/app/src/main android/app/src/test docs/plans/2026-07-14-keyboard-typography-alignment.md
```

Expected: no whitespace errors and only the approved typography changes.

**Step 3: Confirm packaging state**

Run:

```bash
sed -n '1,8p' android/app/version.properties
git status --short
```

Expected: version remains unchanged. Do not run assemble, bundle or install.

**Step 4: Leave source changes for review**

Do not commit source or test changes unless the user explicitly requests it.
