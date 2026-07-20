# Fixed Candidate Bar and Compact Keys Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep the candidate bar permanently at 42dp with larger medium-weight text, and reduce every keyboard key row to 44dp without changing key widths or input state.

**Architecture:** Candidate content state remains owned by `CandidateBarView`, but geometry becomes constant and is no longer driven by state changes. `KeyboardLayout` becomes the shared source of the 44dp row and 220dp panel height used by alphabet, symbol and Emoji modes; Emoji glyph cells stay unchanged while a small geometry policy sizes only its controls.

**Tech Stack:** Kotlin, Android custom `View`/`LinearLayout`, JUnit 4, Gradle, adb/emulator.

---

### Task 1: Make Candidate-Bar Geometry Constant

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarDisplayStatePolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt`

**Step 1: Write the failing fixed-height tests**

Replace the hidden-height expectations with tests that require all content states to
have the same height and root reservation:

```kotlin
@Test
fun `empty candidates and system messages all keep fixed height`() {
    val density = 2f
    val expected = (42f * density).toInt()

    CandidateBarDisplayState.entries.forEach { state ->
        assertEquals(expected, CandidateBarLayoutPolicy.heightPx(density, state))
        assertEquals(
            KeyboardLayout.keyboardHeightPx(density) + expected,
            CandidateBarLayoutPolicy.inputViewMinimumHeightPx(density, state)
        )
    }
}

@Test
fun `candidate typography is larger and single line`() {
    assertEquals(19f, CandidateBarLayoutPolicy.TEXT_SIZE_SP)
    assertEquals(1, CandidateBarLayoutPolicy.SYSTEM_MESSAGE_MAX_LINES)
}
```

Rename the empty state expectation from `HIDDEN` to `EMPTY`:

```kotlin
assertEquals(
    CandidateBarDisplayState.EMPTY,
    CandidateBarDisplayStatePolicy.afterClear(
        CandidateBarDisplayState.CANDIDATES_OR_COMPOSING
    )
)
```

**Step 2: Run the focused tests and verify they fail**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest \
  --tests com.hkmixedkeyboard.CandidateBarDisplayStatePolicyTest \
  --console=plain
```

Expected: FAIL because `HIDDEN` still resolves to 0dp, the text is still 17sp and
`EMPTY` does not yet exist.

**Step 3: Implement constant geometry and empty-state semantics**

In `CandidateBarLayoutPolicy.kt`:

```kotlin
enum class CandidateBarDisplayState {
    EMPTY,
    CANDIDATES_OR_COMPOSING,
    SYSTEM_MESSAGE
}

object CandidateBarDisplayStatePolicy {
    fun afterClear(current: CandidateBarDisplayState): CandidateBarDisplayState =
        if (current == CandidateBarDisplayState.SYSTEM_MESSAGE) {
            CandidateBarDisplayState.SYSTEM_MESSAGE
        } else {
            CandidateBarDisplayState.EMPTY
        }
}

object CandidateBarLayoutPolicy {
    const val VISIBLE_HEIGHT_DP = 42f
    const val TEXT_SIZE_SP = 19f
    const val VERTICAL_PADDING_DP = 4
    const val SYSTEM_MESSAGE_MAX_LINES = 1

    fun heightPx(density: Float, state: CandidateBarDisplayState): Int = when (state) {
        CandidateBarDisplayState.EMPTY,
        CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
        CandidateBarDisplayState.SYSTEM_MESSAGE -> (VISIBLE_HEIGHT_DP * density).toInt()
    }

    fun inputViewMinimumHeightPx(
        density: Float,
        state: CandidateBarDisplayState
    ): Int = KeyboardLayout.keyboardHeightPx(density) + heightPx(density, state)
}
```

Keep the state argument for now because it documents and tests that content changes
cannot alter geometry; it can be removed later only if all callers no longer need it.

**Step 4: Run the focused tests and verify they pass**

Run the command from Step 2.

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarDisplayStatePolicyTest.kt
git commit -m "fix: keep candidate bar geometry stable"
```

### Task 2: Keep the Candidate View and IME Root Permanently Visible

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`

**Step 1: Add a policy-level regression assertion before changing views**

Extend `CandidateBarLayoutPolicyTest`:

```kotlin
@Test
fun `loading to candidates and candidates to empty never change height`() {
    val density = 2.625f
    val heights = listOf(
        CandidateBarDisplayState.SYSTEM_MESSAGE,
        CandidateBarDisplayState.CANDIDATES_OR_COMPOSING,
        CandidateBarDisplayState.EMPTY
    ).map { CandidateBarLayoutPolicy.heightPx(density, it) }

    assertEquals(1, heights.distinct().size)
}
```

**Step 2: Run the regression test**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest \
  --console=plain
```

Expected: PASS at the policy level. The following view changes make the Android
implementation match that contract.

**Step 3: Update `CandidateBarView` without changing its content behaviour**

- Initialize `displayState` as `EMPTY`.
- Change `clear()` and `clearSystemMessage()` to end in `EMPTY`.
- Keep the themed candidate background visible when empty.
- In `bindLabel()` and `makeSystemMessageLabel()`, apply:

```kotlin
typeface = android.graphics.Typeface.create(
    "sans-serif-medium",
    android.graphics.Typeface.NORMAL
)
```

- Keep `19sp`, single-line text, vertical centring and ellipsis.
- Do not alter candidate data, composing data, selection callbacks or scroll logic.

**Step 4: Give both normal and fallback IME roots a fixed bar**

In both `buildInputView()` and `buildFallbackInputView()`:

```kotlin
val candidateBarHeight = KeyboardLayout.candidateBarHeightPx(density)

candidateBar.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    candidateBarHeight
)
candidateBar.visibility = View.VISIBLE
```

Set the root minimum height once to:

```kotlin
minimumHeight = KeyboardLayout.inputViewMinHeightPx(density)
```

Remove `onDisplayStateChanged`, `applyCandidateBarDisplayState()` and every
state-driven `GONE`, height or root-minimum-height mutation. Candidate content
updates must no longer call `requestLayout()` on the root.

**Step 5: Compile and run candidate tests**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests 'com.hkmixedkeyboard.Candidate*Test' \
  --console=plain
```

Expected: PASS and Kotlin compilation succeeds.

**Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt
git commit -m "fix: stop candidate bar from folding"
```

### Task 3: Reduce Every Main Keyboard Row to 44dp

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`

**Step 1: Write failing geometry and width-preservation tests**

Replace the number-row-shorter test and old height assertions with:

```kotlin
@Test
fun `all five rows use the compact 44dp height`() {
    assertEquals(44f, KeyboardLayout.BASE_ROW_HEIGHT_DP)
    assertEquals(listOf(1f, 1f, 1f, 1f, 1f),
        KeyboardLayout.rows.map { it.heightWeight })

    val cells = KeyboardLayout.buildCells(width = 410f, height = 220f)
    assertEquals(44f, cells.single { it.key.label == "1" }.bounds.bottom, 0.001f)
    assertEquals(44f, cells.single { it.key.label == "Q" }.bounds.bottom -
        cells.single { it.key.label == "Q" }.bounds.top, 0.001f)
}

@Test
fun `compact geometry preserves every key width and horizontal position`() {
    val cells = KeyboardLayout.buildCells(width = 1000f, height = 220f)
    val q = cells.single { it.key.label == "Q" }
    val a = cells.single { it.key.label == "A" }
    val space = cells.single { it.key.label == KeyboardLayout.KEY_SPACE }

    assertEquals(100f, q.bounds.right - q.bounds.left, 0.001f)
    assertEquals(50f, a.bounds.left, 0.001f)
    assertEquals(300f, space.bounds.right - space.bounds.left, 0.001f)
}

@Test
fun `fixed candidate bar plus compact keyboard reserves 262dp`() {
    val density = 2f
    assertEquals((220f * density).toInt(), KeyboardLayout.keyboardHeightPx(density))
    assertEquals((262f * density).toInt(), KeyboardLayout.inputViewMinHeightPx(density))
}
```

**Step 2: Run the keyboard layout test and verify it fails**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --console=plain
```

Expected: FAIL because the base row is 56dp, the number row weight is 0.85 and the
empty candidate bar is not included in the current root height.

**Step 3: Implement compact rows**

In `KeyboardLayout.kt`:

```kotlin
const val BASE_ROW_HEIGHT_DP = 44f
```

Remove `heightWeight = 0.85f` from the number row so every row uses the default
`1f`. Keep all key definitions, `startUnits` and `widthUnits` unchanged.

Make `inputViewMinHeightPx()` include the fixed candidate bar:

```kotlin
fun inputViewMinHeightPx(density: Float): Int =
    keyboardHeightPx(density) + candidateBarHeightPx(density)
```

**Step 4: Preserve the existing label sizes despite shorter rows**

In `KeyboardView.buildCells()`, use the previous 56dp-derived visual sizes rather
than shrinking labels in proportion to the new row:

```kotlin
paintLabel.textSize = 17f * density
paintHint.textSize = 13f * density
paintSpace.textSize = 13f * density
paintPopLabel.textSize = 21f * density
```

Do not change `keyMargin`, hit inflation, nearest-key selection, corner radius or
horizontal geometry.

**Step 5: Run layout and interaction tests**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.KeyTouchPolicyTest \
  --tests com.hkmixedkeyboard.MainKeyboardLongPressPolicyTest \
  --console=plain
```

Expected: PASS.

**Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt
git commit -m "feat: compact every keyboard key row"
```

### Task 4: Apply the Compact Slot to Symbols and Emoji Controls

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/EmojiPanelLayoutPolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/EmojiPanelLayoutPolicyTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolKeyboardGeometryTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/EmojiPanelView.kt`
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`

**Step 1: Write failing symbol and Emoji-control tests**

Add to `SymbolKeyboardGeometryTest`:

```kotlin
@Test
fun `compact symbol panel divides the 220dp slot into five rows`() {
    val geometry = SymbolKeyboardGeometry.layout(410f, 220f, 1f)

    assertEquals(4, geometry.symbolRows.size)
    assertEquals(5, geometry.functionRow.size)
    assertTrue(geometry.symbolRows.first().first().hitRect.height < 44f)
    assertTrue(geometry.symbolRows.first().first().hitRect.height > 40f)
}
```

Create `EmojiPanelLayoutPolicyTest.kt`:

```kotlin
class EmojiPanelLayoutPolicyTest {
    @Test
    fun `Emoji controls are compact but glyph geometry stays unchanged`() {
        assertEquals(40, EmojiPanelLayoutPolicy.CONTROL_HEIGHT_DP)
        assertEquals(4, EmojiPanelLayoutPolicy.CONTROL_VERTICAL_PADDING_DP)
        assertEquals(24f, EmojiPanelLayoutPolicy.GLYPH_TEXT_SIZE_SP)
        assertEquals(7, EmojiPanelLayoutPolicy.GLYPH_VERTICAL_PADDING_DP)
    }
}
```

**Step 2: Run the focused tests and verify the new policy test fails**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.SymbolKeyboardGeometryTest \
  --tests com.hkmixedkeyboard.EmojiPanelLayoutPolicyTest \
  --console=plain
```

Expected: FAIL because `EmojiPanelLayoutPolicy` does not exist.

**Step 3: Add the Emoji geometry policy**

Create `EmojiPanelLayoutPolicy.kt`:

```kotlin
package com.hkmixedkeyboard.ui

object EmojiPanelLayoutPolicy {
    const val CONTROL_HEIGHT_DP = 40
    const val CONTROL_VERTICAL_PADDING_DP = 4
    const val GLYPH_TEXT_SIZE_SP = 24f
    const val GLYPH_VERTICAL_PADDING_DP = 7
}
```

**Step 4: Apply the policy only to Emoji controls**

In `EmojiPanelView`:

- give the top bar an explicit `40dp` height;
- make ABC, Backspace and category tabs fill that height;
- reduce their vertical padding to `4dp`;
- leave grid cell font size at `24sp`, vertical padding at `7dp`, margins at `1dp`
  and column count at 9;
- continue to avoid `setTextColor`, tint or alpha on `categoryTabs` and `emojiCells`.

Use:

```kotlin
addView(topBar, LayoutParams(
    LayoutParams.MATCH_PARENT,
    dp(EmojiPanelLayoutPolicy.CONTROL_HEIGHT_DP)
))
```

and set each control's layout params to `WRAP_CONTENT × MATCH_PARENT`.

`HkImeService.swapToAltPanel()` already uses `keyboardView.minimumHeight`; verify it
now supplies the new 220dp slot to both `SymbolPageView` and `EmojiPanelView` without
adding mode-specific height code.

**Step 5: Run Symbol and Emoji tests**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.SymbolKeyboardGeometryTest \
  --tests com.hkmixedkeyboard.EmojiPanelLayoutPolicyTest \
  --tests com.hkmixedkeyboard.EmojiThemePresentationTest \
  --console=plain
```

Expected: PASS. The existing presentation-state test must continue to prove that
theme updates retain selected category, search query, scroll position, recents and
skin-tone preference.

**Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/EmojiPanelLayoutPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/EmojiPanelView.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/EmojiPanelLayoutPolicyTest.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/SymbolKeyboardGeometryTest.kt
git commit -m "feat: compact symbol and Emoji controls"
```

### Task 5: Full Verification, Emulator Validation and APK

**Files:**
- Modify automatically during package build: `android/app/version.properties`
- Create as test artifacts only: emulator screenshots outside source directories

**Step 1: Run the complete unit suite**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

**Step 2: Check the patch for whitespace and unintended geometry changes**

Run:

```bash
git diff --check
git diff --stat
```

Expected: no whitespace errors. Review must show no changes to key width units,
symbol matrices, input routing or Emoji glyph colouring.

**Step 3: Build exactly once so the automatic version increment is predictable**

Run:

```bash
cd android
./gradlew :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`, `version.properties` advances from 0.60.0 to 0.61.0,
and the APK is named `app-debug-0.61.0.apk`.

Do not invoke another Gradle assemble, bundle or install task in this validation
cycle because every package task intentionally increments the version. Install the
already-built file directly with adb:

```bash
adb install -r app/build/outputs/apk/debug/app-debug-0.61.0.apk
```

**Step 4: Validate on the emulator**

Check both Dark and iPhone-style Light themes in:

- empty candidate bar;
- composing/candidate content;
- loading or safe-mode system message;
- alphabet keyboard;
- both symbol pages;
- Emoji panel.

Measure and record:

- candidate bar: 42dp in every content state;
- each main row: 44dp;
- keyboard/panel slot: 220dp;
- content height before navigation inset: 262dp.

Confirm there is no vertical jump when typing or clearing, labels are not clipped,
widths are unchanged, theme switches preserve content/mode state, and Emoji glyphs
remain full-colour and untinted.

Capture dark and light screenshots plus symbol/Emoji screenshots with descriptive
names in the worktree root.

**Step 5: Commit the verified source and version update**

Because this worktree already contains the approved light-priority-candidate and
period-long-press changes, inspect `git status --short` and include all intended
source/tests together while excluding emulator PNG/XML artifacts:

```bash
git add android/app/src/main android/app/src/test android/app/version.properties
git commit -m "fix: stabilize and compact keyboard layout"
```

**Step 6: Run final evidence checks**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest --console=plain
git status --short
```

Expected: unit tests pass. Only intentionally untracked emulator screenshots may
remain; source, tests and `version.properties` are committed.
