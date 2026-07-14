# Comfortable Compact Key Size Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Increase the fixed candidate text to 20sp and each equal main-keyboard row to 48dp, removing the observed Cangjie/Latin visual crowding without changing horizontal key geometry or input behaviour.

**Architecture:** Keep `CandidateBarLayoutPolicy` and `KeyboardLayout` as the two existing geometry sources. Change only their approved constants and update behavior-level tests first; `CandidateBarView`, `KeyboardView` and `HkImeService` will consume the new values without new mode-specific sizing code.

**Tech Stack:** Kotlin, Android custom `View`/`LinearLayout`, JUnit 4, Gradle.

---

### Task 1: Increase Candidate Text to 20sp

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidateBarLayoutPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt`

**Step 1: Write the failing typography test**

Update the existing typography test so it requires the approved text size while
also protecting the fixed candidate-bar height:

```kotlin
@Test
fun `candidate typography is comfortably sized and single line`() {
    assertEquals(20f, CandidateBarLayoutPolicy.TEXT_SIZE_SP)
    assertEquals(42f, CandidateBarLayoutPolicy.VISIBLE_HEIGHT_DP)
    assertEquals(1, CandidateBarLayoutPolicy.SYSTEM_MESSAGE_MAX_LINES)
}
```

**Step 2: Run the focused test and verify RED**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.CandidateBarLayoutPolicyTest \
  --console=plain
```

Expected: FAIL because `TEXT_SIZE_SP` is still `19f`; the fixed-height tests must
remain green.

**Step 3: Implement the minimal typography change**

In `CandidateBarLayoutPolicy.kt`, change only:

```kotlin
const val TEXT_SIZE_SP = 20f
```

Keep `VISIBLE_HEIGHT_DP = 42f`, `VERTICAL_PADDING_DP = 4`, the medium typeface,
single-line behavior, ellipsis and all candidate content logic unchanged.

**Step 4: Run the focused test and verify GREEN**

Run the command from Step 2.

Expected: PASS.

**Step 5: Preserve the current uncommitted integration state**

Do not commit these two files separately: both contain the already-approved
candidate-bar work that the existing implementation plan reserves for its final
unified source commit.

### Task 2: Increase All Five Main-Keyboard Rows to 48dp

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`
- Verify unchanged: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`

**Step 1: Write the failing geometry tests**

Update the compact-row and root-height tests to the approved geometry:

```kotlin
@Test
fun `all five rows use the comfortable compact 48dp height`() {
    assertEquals(48f, KeyboardLayout.BASE_ROW_HEIGHT_DP)
    assertEquals(
        listOf(1f, 1f, 1f, 1f, 1f),
        KeyboardLayout.rows.map { it.heightWeight }
    )

    val cells = KeyboardLayout.buildCells(width = 410f, height = 240f)
    assertEquals(48f, cells.single { it.key.label == "1" }.bounds.bottom, 0.001f)
    assertEquals(
        48f,
        cells.single { it.key.label == "Q" }.bounds.bottom -
            cells.single { it.key.label == "Q" }.bounds.top,
        0.001f
    )
}

@Test
fun `fixed candidate bar plus comfortable keyboard reserves 282dp`() {
    val density = 2f
    assertEquals((240f * density).toInt(), KeyboardLayout.keyboardHeightPx(density))
    assertEquals((282f * density).toInt(), KeyboardLayout.inputViewMinHeightPx(density))
}
```

In the width-preservation test, change only the representative panel height from
`220f` to `240f`; retain the exact width and horizontal-position expectations.

**Step 2: Run the layout test and verify RED**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --console=plain
```

Expected: FAIL because `BASE_ROW_HEIGHT_DP` is still `44f`, producing a `220dp`
keyboard and `262dp` IME content height. Width-preservation assertions must remain
green.

**Step 3: Implement the minimal row-height change**

In `KeyboardLayout.kt`, change only:

```kotlin
const val BASE_ROW_HEIGHT_DP = 48f
```

Keep every row at `1f`. Do not change any key definition, `startUnits`,
`widthUnits`, `keyMargin`, hit inflation, nearest-key selection, corner radius or
gesture routing.

Leave these approved `KeyboardView` text sizes unchanged:

```kotlin
paintLabel.textSize = 17f * density
paintHint.textSize = 13f * density
paintSpace.textSize = 13f * density
paintPopLabel.textSize = 21f * density
```

**Step 4: Run layout and interaction tests and verify GREEN**

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

**Step 5: Preserve the current uncommitted integration state**

Do not create an intermediate source commit. `KeyboardLayout.kt` and its tests
contain the existing compact-key work and must remain part of the final unified
source commit.

### Task 3: Verify the Adjustment Without Incrementing the APK

**Files:**
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarLayoutPolicy.kt`
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`
- Verify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Verify unchanged version: `android/app/version.properties`

**Step 1: Run all affected unit tests together**

Run:

```bash
cd android
./gradlew :app:testDebugUnitTest \
  --tests 'com.hkmixedkeyboard.Candidate*Test' \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.KeyTouchPolicyTest \
  --tests com.hkmixedkeyboard.MainKeyboardLongPressPolicyTest \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

**Step 2: Check the patch and approved constants**

Run:

```bash
git diff --check
rg -n 'TEXT_SIZE_SP|VISIBLE_HEIGHT_DP|BASE_ROW_HEIGHT_DP|paint(Label|Hint|Space|PopLabel)\.textSize' \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui
```

Expected: no whitespace errors; candidate text is `20f`, candidate height is
`42f`, row height is `48f`, and the four key-label sizes remain `17f`, `13f`,
`13f` and `21f`.

**Step 3: Confirm packaging state is untouched**

Run:

```bash
sed -n '1,8p' android/app/version.properties
git status --short
```

Expected: `versionMinor=61`. Do not run any assemble, bundle or Gradle install
task: another package task would increment the APK to `0.62.0`.

**Step 4: Report the checkpoint**

Report RED and GREEN evidence, the final `282dp` content geometry and unchanged
horizontal/input behavior. Stop for review without committing source changes or
building another APK.
