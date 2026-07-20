# Numeric Password PIN Keypad Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Give Android numeric-password fields a centered, four-row PIN keypad with a conditional host action, centered `0`, and Backspace.

**Architecture:** `EditorLayoutPolicy` will classify `TYPE_NUMBER_VARIATION_PASSWORD` as a dedicated `KeyboardSurface.NUMERIC_PASSWORD` before other number variants. `KeyboardLayout` will build an action-aware PIN grid, and `KeyboardView` will rebuild its cells when the host editor action changes; the existing sensitive-field policy, direct commits, drawing, haptics, action dispatch, and accessibility descriptions remain in place.

**Tech Stack:** Kotlin, Android `InputType`/`EditorInfo`, custom Android `View`, JUnit 4, Gradle, Android Debug Bridge.

---

### Task 1: Classify numeric-password editors separately

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt:15-33`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt:15-47`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt:3-10,112-121`

**Step 1: Write the failing policy test**

Add a focused test that can fail before the new enum member exists:

```kotlin
@Test
fun `maps numeric password editors to their own surface`() {
    val numericPassword =
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

    assertEquals("NUMERIC_PASSWORD", EditorLayoutPolicy.surfaceFor(numericPassword).name)
    assertTrue(EditorLayoutPolicy.usesDirectEntry(numericPassword))
}
```

Keep the existing ordinary number, signed-decimal, and phone assertions.

**Step 2: Run the focused test and verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.EditorLayoutPolicyTest
```

Expected: FAIL because the returned surface name is currently `NUMBER`.

**Step 3: Implement the dedicated classification**

Add the surface:

```kotlin
enum class KeyboardSurface {
    TEXT,
    NUMBER,
    NUMERIC_PASSWORD,
    SIGNED_DECIMAL_NUMBER,
    PHONE,
    EMAIL
}
```

Select it before the signed/decimal and general-number branches:

```kotlin
inputClass == InputType.TYPE_CLASS_NUMBER &&
    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ->
    KeyboardSurface.NUMERIC_PASSWORD
```

Include `KeyboardSurface.NUMERIC_PASSWORD` in `usesDirectEntry`. To keep the
exhaustive `rowsFor` expression compiling until Task 2 supplies the final PIN
geometry, temporarily route the new surface to the existing number rows:

```kotlin
KeyboardSurface.NUMERIC_PASSWORD -> numberRows()
```

**Step 4: Run the policy test and verify GREEN**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.EditorLayoutPolicyTest
```

Expected: PASS. Numeric passwords have their own direct-entry surface; other
editor mappings are unchanged.

**Step 5: Commit the classification**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt
git commit -m "feat: classify numeric password editors"
```

### Task 2: Build the centered four-row PIN layout

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt:43-65`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt:112-209`

**Step 1: Write the failing geometry test**

Add this test using the current `rowsFor` API so it reaches an assertion
failure against the temporary five-row number layout:

```kotlin
@Test
fun `numeric password uses a centered four-row PIN grid`() {
    val rows = KeyboardLayout.rowsFor(
        KeyboardSurface.NUMERIC_PASSWORD,
        showNextInputMethod = false
    )

    assertEquals(
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("0", KeyboardLayout.KEY_BACKSPACE)
        ),
        rows.map { row -> row.keys.map { it.label } }
    )

    val cells = KeyboardLayout.buildCells(width = 1000f, height = 400f, rows = rows)
    assertEquals(100f, cells.single { it.key.label == "1" }.bounds.left, 0.001f)
    assertEquals(900f, cells.single { it.key.label == "3" }.bounds.right, 0.001f)
    assertEquals(100f, cells.single { it.key.label == "1" }.bounds.bottom, 0.001f)
    val zero = cells.single { it.key.label == "0" }.bounds
    assertEquals(100f, zero.bottom - zero.top, 0.001f)
}
```

**Step 2: Run the focused test and verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: FAIL because numeric passwords still use the five-row general number
layout and full-width keys.

**Step 3: Implement actionless PIN geometry**

Add centered-grid constants and helpers:

```kotlin
private const val PIN_GRID_START_UNITS = 1f
private const val PIN_KEY_WIDTH_UNITS = 8f / 3f

private fun pinRow(first: String, second: String, third: String): RowDef =
    row(
        listOf(
            first to PIN_KEY_WIDTH_UNITS,
            second to PIN_KEY_WIDTH_UNITS,
            third to PIN_KEY_WIDTH_UNITS
        ),
        startUnits = PIN_GRID_START_UNITS
    )

private fun numericPasswordRows(): List<RowDef> = listOf(
    pinRow("1", "2", "3"),
    pinRow("4", "5", "6"),
    pinRow("7", "8", "9"),
    row(
        listOf(
            "0" to PIN_KEY_WIDTH_UNITS,
            KEY_BACKSPACE to PIN_KEY_WIDTH_UNITS
        ),
        startUnits = PIN_GRID_START_UNITS + PIN_KEY_WIDTH_UNITS
    )
)
```

Route `KeyboardSurface.NUMERIC_PASSWORD` to `numericPasswordRows()`.

The existing `every editor layout omits accidental settings and next IME
actions` test assumes every last row fills all ten units. Keep its action-label
assertions for every surface, but apply the full-width assertion only when the
surface is not `NUMERIC_PASSWORD`; the dedicated geometry test owns the PIN
gutters.

**Step 4: Run the focused test and verify GREEN**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: PASS. The actionless PIN surface has four equal-height rows, centered
digits, centered `0`, Backspace on the right, and a non-interactive empty
bottom-left region.

**Step 5: Commit the base PIN layout**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt
git commit -m "feat: add centered numeric password keypad"
```

### Task 3: Show the host action only when meaningful

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt:112-121`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt:53-75,214-220`

**Step 1: Write the failing action test**

Add the third `rowsFor` argument in the test before adding it to production:

```kotlin
@Test
fun `numeric password bottom-left action appears only for meaningful editor actions`() {
    val returnRows = KeyboardLayout.rowsFor(
        KeyboardSurface.NUMERIC_PASSWORD,
        showNextInputMethod = false,
        enterAction = SymbolEnterAction.RETURN
    )
    val doneRows = KeyboardLayout.rowsFor(
        KeyboardSurface.NUMERIC_PASSWORD,
        showNextInputMethod = false,
        enterAction = SymbolEnterAction.DONE
    )

    assertEquals(
        listOf("0", KeyboardLayout.KEY_BACKSPACE),
        returnRows.last().keys.map { it.label }
    )
    assertEquals(8f / 3f + 1f, returnRows.last().keys.first().startUnits, 0.001f)
    assertEquals(
        listOf(KeyboardLayout.KEY_ENTER, "0", KeyboardLayout.KEY_BACKSPACE),
        doneRows.last().keys.map { it.label }
    )
    assertEquals(1f, doneRows.last().keys.first().startUnits, 0.001f)
}
```

Import `SymbolEnterAction` in `KeyboardLayoutTest`.

**Step 2: Run the focused test and verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: compilation FAIL because `rowsFor` does not yet accept
`enterAction`. This is the missing action-aware API, not an unrelated syntax or
fixture error.

**Step 3: Make PIN rows action-aware**

Extend the layout API without changing existing call sites:

```kotlin
fun rowsFor(
    surface: KeyboardSurface,
    showNextInputMethod: Boolean,
    enterAction: SymbolEnterAction = SymbolEnterAction.RETURN
): List<RowDef> = when (surface) {
    // Existing branches stay unchanged.
    KeyboardSurface.NUMERIC_PASSWORD -> numericPasswordRows(enterAction)
}
```

Change the PIN builder to use the existing action key only when meaningful:

```kotlin
private fun numericPasswordRows(enterAction: SymbolEnterAction): List<RowDef> {
    val bottomRow = if (enterAction == SymbolEnterAction.RETURN) {
        row(
            listOf(
                "0" to PIN_KEY_WIDTH_UNITS,
                KEY_BACKSPACE to PIN_KEY_WIDTH_UNITS
            ),
            startUnits = PIN_GRID_START_UNITS + PIN_KEY_WIDTH_UNITS
        )
    } else {
        pinRow(KEY_ENTER, "0", KEY_BACKSPACE)
    }
    return listOf(
        pinRow("1", "2", "3"),
        pinRow("4", "5", "6"),
        pinRow("7", "8", "9"),
        bottomRow
    )
}
```

Pass the live action from `KeyboardView.buildCells`:

```kotlin
val rows = KeyboardLayout.rowsFor(
    keyboardSurface,
    showNextInputMethodAction,
    enterAction
)
```

Change the `enterAction` setter to rebuild cells because PIN geometry changes:

```kotlin
var enterAction: SymbolEnterAction = SymbolEnterAction.RETURN
    set(value) {
        if (field == value) return
        field = value
        rebuildCellsForSurface()
    }
```

**Step 4: Run related policy, layout, and accessibility tests**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.EditorLayoutPolicyTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.KeyboardAccessibilityLabelsTest \
  --tests com.hkmixedkeyboard.SensitiveFieldDetectorTest
```

Expected: PASS. A Return action leaves no bottom-left key or accessibility
target; Done and other meaningful actions use the existing Enter rendering and
accessibility path.

**Step 5: Commit the action-aware layout**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt
git commit -m "feat: adapt PIN keypad to editor actions"
```

### Task 4: Verify the complete keyboard and device behavior

**Files:**
- Verify: `android/app/build/outputs/apk/debug/app-debug-0.64.8.apk`

**Step 1: Run the full unit suite without task reuse**

Run:

```bash
cd android
./gradlew testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, with `:app:testDebugUnitTest` executed and no
failed tests.

**Step 2: Build the debug APK**

Run:

```bash
cd android
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a versioned debug APK under
`android/app/build/outputs/apk/debug/`.

**Step 3: Install and select the debug IME**

Run:

```bash
ADB=/home/europa/Android/Sdk/platform-tools/adb
"$ADB" install -r android/app/build/outputs/apk/debug/app-debug-0.64.8.apk
"$ADB" shell ime enable com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell ime set com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell settings get secure default_input_method
```

Expected: installation succeeds and the selected input method is the debug
service.

**Step 4: Verify a real numeric-password field with ADB**

Ask the user to open the original problematic numeric-password field without
entering a real password, then inspect it:

```bash
ADB=/home/europa/Android/Sdk/platform-tools/adb
"$ADB" shell dumpsys input_method
"$ADB" exec-out screencap -p > /tmp/hk-numeric-password-pin.png
```

Expected: the focused editor input type has number class plus
`TYPE_NUMBER_VARIATION_PASSWORD` (normally `inputType=0x12`). The screenshot
shows four centered PIN rows; its bottom row is action/`0`/Backspace or an empty
left slot/`0`/Backspace, and the sensitive-field strip shows no candidates or
learning content.

**Step 5: Verify non-password numeric controls**

Focus one ordinary number field and one phone field, then inspect their input
types and screenshots.

Expected: the existing `NUMBER` and `PHONE` layouts remain unchanged.

**Step 6: Inspect the final scope**

Run:

```bash
git diff --check
git status --short
git log --oneline -6
```

Expected: implementation changes are committed in the policy/layout/view/test
files listed above. The pre-existing haptics, haptics-test, version-properties,
and heavy-click design changes remain untouched and uncommitted.
