# Text Password PIN Toggle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let users switch a genuine text-password editor such as Firefox's `inputType=0x81` between the full alphabet keyboard and the existing centered PIN keypad without guessing from the host package.

**Architecture:** `EditorLayoutPolicy` classifies genuine text-password variations as `TEXT_PASSWORD`, whose alphabet layout replaces only the scheme key with `123`. A small field-scoped state holder chooses the visible surface, while `HkImeService` resets it at editor boundaries and connects `123` to PIN mode and a safe-status-bar `ABC` action back to alphabet mode. Native numeric-password editors remain automatic PIN surfaces, and the existing sensitive-field privacy path remains authoritative.

**Tech Stack:** Kotlin, Android `InputType`/`EditorInfo`, custom Android `View`, JUnit 4, AndroidX instrumentation, Gradle, Android Debug Bridge.

**Implementation discipline:** Follow @superpowers:test-driven-development for every behavior change, @karpathy-guidelines for surgical scope, and @superpowers:verification-before-completion before reporting the fix complete.

---

### Task 1: Classify genuine text-password editors

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`

**Step 1: Write the failing classification tests**

Replace the existing ordinary-password fallback assertion with a table covering all Android password-style text variations:

```kotlin
@Test
fun `maps genuine text password editors to their own surface`() {
    listOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    ).forEach { variation ->
        assertEquals(
            KeyboardSurface.TEXT_PASSWORD,
            EditorLayoutPolicy.surfaceFor(InputType.TYPE_CLASS_TEXT or variation)
        )
    }
}

@Test
fun `keeps numeric passwords automatic and unknown text variations ordinary`() {
    assertEquals(
        KeyboardSurface.NUMERIC_PASSWORD,
        EditorLayoutPolicy.surfaceFor(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
    )
    assertEquals(KeyboardSurface.TEXT, EditorLayoutPolicy.surfaceFor(0x7f000000))
}
```

Keep the existing number-flag precedence, email, URI, and ordinary text tests.

**Step 2: Run the focused test and verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.EditorLayoutPolicyTest
```

Expected: compilation or assertion failure because `TEXT_PASSWORD` does not exist and password text currently maps to `TEXT`.

**Step 3: Add the dedicated surface and classification**

Add `TEXT_PASSWORD` immediately after `TEXT` in `KeyboardSurface`. In `surfaceFor`, add this branch before email and the general text fallback:

```kotlin
inputClass == InputType.TYPE_CLASS_TEXT &&
    variation in setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    ) -> KeyboardSurface.TEXT_PASSWORD
```

Temporarily route `KeyboardSurface.TEXT_PASSWORD -> textRows()` in the exhaustive `rowsFor` expression; Task 2 replaces that route with its final layout. Do not add package-name checks. Do not change `DirectInputPolicy`: it already recognizes these same three variations and commits password characters directly.

**Step 4: Run policy and direct-entry regression tests**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.EditorLayoutPolicyTest \
  --tests com.hkmixedkeyboard.DirectInputPolicyTest \
  --tests com.hkmixedkeyboard.SensitiveFieldDetectorTest
```

Expected: PASS. Text passwords are distinct surfaces, all password characters still bypass composition, and sensitive-field detection remains unchanged.

**Step 5: Commit the classification**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt
git commit -m "feat: classify text password editors"
```

### Task 2: Add the password alphabet layout and accessible `123` key

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardAccessibilityLabelsTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardAccessibilityLabels.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-en/strings.xml`

**Step 1: Write failing layout and accessibility tests**

Add a focused layout test:

```kotlin
@Test
fun `text password keeps alphabet and symbols but replaces scheme switch with 123`() {
    val rows = KeyboardLayout.rowsFor(
        KeyboardSurface.TEXT_PASSWORD,
        showNextInputMethod = false
    )
    val labels = rows.flattenLabels()

    assertEquals(KeyboardLayout.rows.take(4), rows.take(4))
    assertTrue(labels.contains(KeyboardLayout.KEY_SYMBOL))
    assertTrue(labels.contains(KeyboardLayout.KEY_PIN_MODE))
    assertFalse(labels.contains(KeyboardLayout.KEY_MODE))
    assertEquals(1.05f, rows.last().keys.single {
        it.label == KeyboardLayout.KEY_PIN_MODE
    }.widthUnits)
}
```

Use a local test helper such as:

```kotlin
private fun List<KeyboardLayout.RowDef>.flattenLabels() =
    flatMap { row -> row.keys.map { it.label } }
```

Add an accessibility assertion:

```kotlin
assertEquals(
    "切換至數字密碼鍵盤",
    KeyboardAccessibilityLabels.descriptionFor(KeyboardLayout.KEY_PIN_MODE)
)
```

**Step 2: Run the focused tests and verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.KeyboardAccessibilityLabelsTest
```

Expected: compilation failure because `KEY_PIN_MODE` is not defined.

**Step 3: Implement the password layout and special key**

Add the sentinel and a password-specific action row:

```kotlin
const val KEY_PIN_MODE = "123"

private fun textPasswordRows(): List<RowDef> =
    alphabetRows + actionRow(
        listOf(
            KEY_SYMBOL to 1.2f,
            KEY_EMOJI to 0.8f,
            KEY_PIN_MODE to 1.05f,
            KEY_SPACE to 3.50f,
            KEY_PERIOD to 1.05f,
            KEY_COMMA to 1.05f
        )
    )
```

Route `TEXT_PASSWORD` to `textPasswordRows()`. In `KeyboardView`, export `KEY_PIN_MODE`, add it to `SPECIAL_KEYS`, and exclude it from popup bubbles. It is a simple tap action and should keep the default press-time emission; do not give it the scheme key's long-press behavior.

Add `pinModeKey` to `KeyboardAccessibilityLabels.Text`, load it from `key_a11y_pin_mode`, handle `KEY_PIN_MODE` explicitly in `descriptionFor`, and add localized resources:

```xml
<string name="key_a11y_pin_mode">切換至數字密碼鍵盤</string>
```

```xml
<string name="key_a11y_pin_mode">Switch to numeric password keypad</string>
```

Update the private Traditional Chinese fallback `Text` value as well.

**Step 4: Run layout, touch-policy, and accessibility regressions**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.KeyboardAccessibilityLabelsTest \
  --tests com.hkmixedkeyboard.KeyTouchPolicyTest
```

Expected: PASS. Only the text-password surface substitutes `123`; the normal text scheme key, symbols, dimensions, native PIN layout, and touch behavior remain intact.

**Step 5: Commit the password alphabet surface**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardAccessibilityLabels.kt \
  android/app/src/main/res/values/strings.xml \
  android/app/src/main/res/values-en/strings.xml \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardAccessibilityLabelsTest.kt
git commit -m "feat: add text password numeric toggle"
```

### Task 3: Scope manual PIN mode to one editor session

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/PasswordSurfaceState.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/PasswordSurfaceStateTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`

**Step 1: Write failing state-transition tests**

Create unit tests for the state holder:

```kotlin
class PasswordSurfaceStateTest {
    @Test
    fun `text password can enter and leave manual PIN mode`() {
        val state = PasswordSurfaceState()
        state.startEditor(KeyboardSurface.TEXT_PASSWORD)

        assertEquals(KeyboardSurface.TEXT_PASSWORD, state.visibleSurface)
        assertFalse(state.showAlphabetAction)

        state.enterManualPin()
        assertEquals(KeyboardSurface.NUMERIC_PASSWORD, state.visibleSurface)
        assertTrue(state.showAlphabetAction)

        state.leaveManualPin()
        assertEquals(KeyboardSurface.TEXT_PASSWORD, state.visibleSurface)
        assertFalse(state.showAlphabetAction)
    }

    @Test
    fun `manual PIN never applies to native PIN or ordinary editors`() {
        val state = PasswordSurfaceState()
        listOf(KeyboardSurface.NUMERIC_PASSWORD, KeyboardSurface.TEXT).forEach { surface ->
            state.startEditor(surface)
            state.enterManualPin()
            assertEquals(surface, state.visibleSurface)
            assertFalse(state.showAlphabetAction)
        }
    }

    @Test
    fun `start and finish clear manual PIN mode`() {
        val state = PasswordSurfaceState()
        state.startEditor(KeyboardSurface.TEXT_PASSWORD)
        state.enterManualPin()

        state.startEditor(KeyboardSurface.TEXT_PASSWORD)
        assertEquals(KeyboardSurface.TEXT_PASSWORD, state.visibleSurface)

        state.enterManualPin()
        state.finishEditor()
        assertEquals(KeyboardSurface.TEXT, state.visibleSurface)
        assertFalse(state.showAlphabetAction)
    }
}
```

**Step 2: Run the state test and verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.PasswordSurfaceStateTest
```

Expected: compilation failure because `PasswordSurfaceState` does not exist.

**Step 3: Implement the minimal state holder**

Create a package-private state object with no Android dependencies:

```kotlin
class PasswordSurfaceState {
    var editorSurface: KeyboardSurface = KeyboardSurface.TEXT
        private set
    var manualPin: Boolean = false
        private set

    val visibleSurface: KeyboardSurface
        get() = if (editorSurface == KeyboardSurface.TEXT_PASSWORD && manualPin) {
            KeyboardSurface.NUMERIC_PASSWORD
        } else {
            editorSurface
        }

    val showAlphabetAction: Boolean
        get() = editorSurface == KeyboardSurface.TEXT_PASSWORD && manualPin

    fun startEditor(surface: KeyboardSurface) {
        editorSurface = surface
        manualPin = false
    }

    fun finishEditor() {
        editorSurface = KeyboardSurface.TEXT
        manualPin = false
    }

    fun enterManualPin() {
        if (editorSurface == KeyboardSurface.TEXT_PASSWORD) manualPin = true
    }

    fun leaveManualPin() {
        manualPin = false
    }
}
```

**Step 4: Wire `123` into `HkImeService`**

Replace the mutable `editorSurface` field with a `PasswordSurfaceState`. Continue exposing the host-selected surface when initially constructing a view, then always use `passwordSurfaceState.visibleSurface` in `applyEditorSurface()`.

In `onStartInput`, call:

```kotlin
passwordSurfaceState.startEditor(EditorLayoutPolicy.surfaceFor(attribute.inputType))
```

In `onFinishInput`, call `passwordSurfaceState.finishEditor()`. Add this key branch before ordinary text dispatch:

```kotlin
KeyboardView.KEY_PIN_MODE -> {
    passwordSurfaceState.enterManualPin()
    applyEditorSurface()
    refreshSensitiveStatus()
    return
}
```

For this task, `refreshSensitiveStatus()` may still show the existing action-free safe-mode message; Task 4 adds `ABC`. Ensure `buildInputView()` and `buildFallbackInputView()` both initialize `keyboardSurface` from `passwordSurfaceState.visibleSurface`. Do not persist the state and do not change candidate, learning, or direct-commit policy.

**Step 5: Run the focused unit suite and compile Android tests**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.PasswordSurfaceStateTest \
  --tests com.hkmixedkeyboard.EditorLayoutPolicyTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.DirectInputPolicyTest \
  compileDebugAndroidTestKotlin
```

Expected: PASS. Manual mode is allowed only from `TEXT_PASSWORD`, a new/restarted/finished editor clears it, and the IME service compiles against the new key.

**Step 6: Commit the field-scoped switching state**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/PasswordSurfaceState.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/PasswordSurfaceStateTest.kt
git commit -m "feat: scope manual PIN mode to password fields"
```

### Task 4: Add the accessible safe-strip `ABC` return action

**Files:**
- Modify: `android/app/src/androidTest/kotlin/com/hkmixedkeyboard/CandidateAccessibilityTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-en/strings.xml`

**Step 1: Write failing candidate-bar instrumentation tests**

Add tests on the main thread proving the ordinary message is unchanged and the optional action is operable:

```kotlin
@Test
fun safeModeAuxiliaryActionIsAccessibleAndOperable() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var taps = 0
        val bar = CandidateBarView(context)

        bar.showSafeMode(
            auxiliaryAction = CandidateBarView.AuxiliaryAction(
                label = "ABC",
                contentDescription = context.getString(R.string.password_pin_return_a11y),
                onClick = { taps++ }
            )
        )

        val row = bar.getChildAt(0) as LinearLayout
        assertEquals(2, row.childCount)
        val action = row.getChildAt(1) as TextView
        assertEquals("ABC", action.text.toString())
        assertEquals(context.getString(R.string.password_pin_return_a11y), action.contentDescription)
        assertTrue(action.isClickable && action.isFocusable)
        action.performClick()
        assertEquals(1, taps)
    }
}

@Test
fun ordinarySafeModeHasNoAuxiliaryAction() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val bar = CandidateBarView(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        bar.showSafeMode()
        assertEquals(1, (bar.getChildAt(0) as LinearLayout).childCount)
    }
}
```

Add the required `TextView` and `assertEquals` imports.

**Step 2: Run the focused instrumentation class and verify RED**

Run with the connected test device/emulator:

```bash
cd android
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.CandidateAccessibilityTest
```

Expected: compilation failure because `AuxiliaryAction` and the new string do not exist.

**Step 3: Implement the optional action in `CandidateBarView`**

Add a view-scoped action model:

```kotlin
data class AuxiliaryAction(
    val label: String,
    val contentDescription: String,
    val onClick: () -> Unit
)
```

Change `showSafeMode` to accept `auxiliaryAction: AuxiliaryAction? = null`, and pass it through `showSystemMessage`. When non-null, construct the row as:

- the existing safe-mode message using width `0` and weight `1f`;
- a trailing `TextView` with visible `ABC`, the supplied accessibility description, safe-mode text color, adequate horizontal padding, `isClickable = true`, `isFocusable = true`, and a click listener that performs the existing selection haptic before invoking the callback.

When null, preserve the current one-child full-width message exactly. Ensure `applyTheme()` recolors both the message and the action after a theme change. Do not expose editor contents to the view and do not alter candidate-mode rendering.

Add resources:

```xml
<string name="password_pin_return_label">ABC</string>
<string name="password_pin_return_a11y">切換至英文字母密碼鍵盤</string>
```

```xml
<string name="password_pin_return_label">ABC</string>
<string name="password_pin_return_a11y">Switch to alphabet password keyboard</string>
```

**Step 4: Connect `ABC` to the field-scoped state**

Centralize sensitive-strip rendering in `HkImeService`:

```kotlin
private fun refreshSensitiveStatus() {
    if (!::candidateBar.isInitialized) return
    if (!imeCtx.isSensitiveField) {
        candidateBar.clearSystemMessage()
        return
    }
    val action = if (passwordSurfaceState.showAlphabetAction) {
        CandidateBarView.AuxiliaryAction(
            label = getString(R.string.password_pin_return_label),
            contentDescription = getString(R.string.password_pin_return_a11y)
        ) {
            passwordSurfaceState.leaveManualPin()
            applyEditorSurface()
            refreshSensitiveStatus()
        }
    } else {
        null
    }
    candidateBar.showSafeMode(action)
}
```

Use this helper from `onStartInput`, after `KEY_PIN_MODE`, and from the sensitive branch of `updateCandidateBar`. Native `NUMERIC_PASSWORD` never satisfies `showAlphabetAction`, so it keeps the action-free safe strip. The callback only changes surface state; it must not read or alter the editor text.

**Step 5: Run focused unit and instrumentation regressions**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.PasswordSurfaceStateTest \
  --tests com.hkmixedkeyboard.EditorLayoutPolicyTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest \
  --tests com.hkmixedkeyboard.KeyboardAccessibilityLabelsTest \
  --tests com.hkmixedkeyboard.SensitiveFieldDetectorTest \
  --tests com.hkmixedkeyboard.SafeModeTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.CandidateAccessibilityTest
```

Expected: PASS. `ABC` is present and operable only when requested, normal safe mode stays unchanged, and all password privacy checks pass.

**Step 6: Commit the return action**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/main/res/values/strings.xml \
  android/app/src/main/res/values-en/strings.xml \
  android/app/src/androidTest/kotlin/com/hkmixedkeyboard/CandidateAccessibilityTest.kt
git commit -m "feat: return from manual PIN mode"
```

### Task 5: Verify the full suite and Firefox behavior

**Files:**
- Verify: `android/app/build/outputs/apk/debug/app-debug-0.64.8.apk`

**Step 1: Run the full unit suite without task reuse**

Run:

```bash
cd android
./gradlew testDebugUnitTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; `:app:testDebugUnitTest` executes and reports no failed tests.

**Step 2: Run the focused accessibility instrumentation suites**

Run:

```bash
cd android
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.CandidateAccessibilityTest,com.hkmixedkeyboard.KeyboardAccessibilityNodeProviderTest
```

Expected: PASS. Candidate action semantics, PIN virtual nodes, touch gaps, action changes, and focus resets remain correct.

**Step 3: Build the debug APK**

Run:

```bash
cd android
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and the versioned debug APK under `android/app/build/outputs/apk/debug/`.

**Step 4: Install and select the debug IME**

Run from the repository root:

```bash
ADB=/home/europa/Android/Sdk/platform-tools/adb
"$ADB" install -r android/app/build/outputs/apk/debug/app-debug-0.64.8.apk
"$ADB" shell ime enable com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell ime set com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell settings get secure default_input_method
```

Expected: installation succeeds and the debug IME is selected.

**Step 5: Verify the already-open Firefox diagnostic field without typing secrets**

With Firefox focused on the local numeric-password diagnostic field, record editor metadata:

```bash
"$ADB" shell dumpsys input_method | sed -n '/mCurAttribute/,+8p'
```

Expected: Firefox may still report `inputType=0x81`; that is now classified as `TEXT_PASSWORD`. Verify on the device:

1. The initial password keyboard retains alphabet and symbol access and shows `123` instead of the Chinese scheme key.
2. Tap `123`; the centered four-row PIN keypad appears with the existing editor action, `0`, Backspace, and protected gaps.
3. The safe-mode strip shows an accessible `ABC` action and no candidates.
4. Tap `ABC`; the alphabet password keyboard returns.
5. Move to another field and back; manual PIN mode is reset.
6. Confirm an Android-native numeric-password field opens directly in PIN mode and does not show `ABC`.

Do not enter, dump, screenshot, or log a real password. If Firefox keeps the secure-window screenshot black, use the instrumentation evidence plus direct user visual confirmation instead of claiming screenshot verification.

**Step 6: Inspect scope and whitespace**

Run:

```bash
git diff --check HEAD~4..HEAD
git status --short
```

Expected: no whitespace errors. Only the planned commits are included; the pre-existing haptic files, `version.properties`, and `docs/plans/2026-07-16-heavy-click-typing-haptics-design.md` remain untouched and uncommitted.
