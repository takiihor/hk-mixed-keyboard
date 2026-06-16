# Shift, Caps Lock, And Low-Latency Haptics Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add reliable Shift/Caps Lock behavior, preserve English case without breaking Quick decoding, and replace delayed overlapping typing haptics with a cancellable direct-vibration path.

**Architecture:** Keep Shift transitions, case mapping, and haptic strategy selection in pure Kotlin components covered by JVM tests. `KeyboardView` owns visual key state and delegates physical typing pulses to an Android haptic engine; `HkImeService` owns Shift state and passes lowercase lookup text to Chinese decoding while preserving visible English case.

**Tech Stack:** Kotlin, Android InputMethodService/View APIs, `Vibrator`, `VibrationEffect`, `VibrationAttributes`, JUnit 4, Gradle.

---

### Task 1: Shift And Caps Lock State Machine

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/ShiftStateController.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/ShiftStateControllerTest.kt`

**Step 1: Write failing state-transition tests**

Cover:

```kotlin
assertEquals(ShiftState.ONCE, controller.press(atMs = 1_000))
assertEquals(ShiftState.LOCKED, controller.press(atMs = 1_200))
assertEquals(ShiftState.OFF, controller.press(atMs = 1_300))
```

Also prove:

- a second press after the double-tap window changes `ONCE` to `OFF`;
- a letter consumes `ONCE`;
- a letter does not consume `LOCKED`;
- special keys do not consume `ONCE`.

**Step 2: Run the focused test and confirm RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests '*ShiftStateControllerTest'
```

Expected: compilation failure because the controller and state enum do not exist.

**Step 3: Implement the minimal pure Kotlin controller**

Use:

```kotlin
enum class ShiftState { OFF, ONCE, LOCKED }

class ShiftStateController(
    private val doubleTapWindowMs: Long = 300L
) {
    var state: ShiftState = ShiftState.OFF
        private set

    private var lastShiftPressMs: Long? = null

    fun press(atMs: Long): ShiftState {
        state = when (state) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE ->
                if (lastShiftPressMs?.let { atMs - it <= doubleTapWindowMs } == true)
                    ShiftState.LOCKED
                else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftPressMs = if (state == ShiftState.ONCE) atMs else null
        return state
    }

    fun consumeLetter(): ShiftState {
        if (state == ShiftState.ONCE) state = ShiftState.OFF
        return state
    }

    fun reset() {
        state = ShiftState.OFF
        lastShiftPressMs = null
    }
}
```

**Step 4: Run focused tests and confirm GREEN**

Run the command from Step 2.

Expected: all `ShiftStateControllerTest` tests pass.

**Step 5: Commit if Git becomes available**

The current workspace has no `.git` directory. Do not fabricate a commit; record
this limitation in the final report.

### Task 2: Preserve English Case While Keeping Quick Lookup Lowercase

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/InputCasePolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/InputCasePolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/Classifier.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/engine/EnglishSuggestions.kt`

**Step 1: Write failing case-policy tests**

Cover:

```kotlin
assertEquals("hello", InputCasePolicy.applyPattern("hello", "he"))
assertEquals("Hello", InputCasePolicy.applyPattern("hello", "He"))
assertEquals("HELLO", InputCasePolicy.applyPattern("hello", "HE"))
assertEquals("rr", InputCasePolicy.lookupForm("Rr"))
```

Also prove non-letter text and Chinese candidates are unchanged.

**Step 2: Run the focused test and confirm RED**

```bash
cd android
./gradlew testDebugUnitTest --tests '*InputCasePolicyTest'
```

Expected: compilation failure because `InputCasePolicy` does not exist.

**Step 3: Implement the minimal case policy**

Rules:

```kotlin
fun lookupForm(text: String): String = text.lowercase()

fun applyPattern(candidate: String, typed: String): String = when {
    typed.filter(Char::isLetter).let { it.isNotEmpty() && it.all(Char::isUpperCase) } ->
        candidate.uppercase()
    typed.firstOrNull()?.isUpperCase() == true ->
        candidate.replaceFirstChar(Char::uppercase)
    else -> candidate.lowercase()
}
```

Return non-Latin or mixed Chinese candidate text unchanged.

**Step 4: Run focused tests and confirm GREEN**

Run the command from Step 2.

**Step 5: Add failing integration tests for uppercase composition**

Extend existing commit/candidate tests to prove:

- `H` then `i` leaves visible buffer `Hi`;
- classification decodes `Hi` using lowercase `hi`;
- English completion text follows title case;
- Caps Lock composition produces uppercase literal and completion;
- a Chinese candidate selected from uppercase Quick code still commits Chinese.

**Step 6: Run integration tests and confirm RED**

```bash
cd android
./gradlew testDebugUnitTest --tests '*EnglishCompletionTest' --tests '*QuickRegressionTest'
```

Expected: at least one new case-preservation assertion fails because
`HkImeService` currently lowercases every letter before storing it.

**Step 7: Separate display buffer from decoder lookup**

- Stop calling `label.lowercase()` before `CommitController.onKeyPress`.
- Preserve the letter selected by Shift in `ImeStateData.buffer`.
- Lowercase only at decoder/index lookup boundaries.
- Apply `InputCasePolicy` to English completion candidates before display.
- Preserve the displayed English candidate text on commit.

**Step 8: Re-run focused and regression tests**

```bash
cd android
./gradlew testDebugUnitTest --tests '*EnglishCompletionTest' --tests '*QuickRegressionTest' --tests '*ConservativeSpaceTest'
```

Expected: all focused tests pass.

### Task 3: Cancellable Direct Typing Haptics

**Files:**
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/TypingHapticEngine.kt`
- Create: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HapticCapabilityPolicy.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/TypingHapticEngineTest.kt`
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/HapticCapabilityPolicyTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HapticFeedbackPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Step 1: Write failing strategy tests**

Define:

```kotlin
enum class TypingHapticStrategy {
    PRIMITIVE_TICK,
    PREDEFINED_TICK,
    VIEW_FALLBACK,
    NONE
}
```

Prove:

- supported primitive selects `PRIMITIVE_TICK`;
- a vibrator without primitive support selects `PREDEFINED_TICK`;
- no vibrator selects `VIEW_FALLBACK`;
- disabled preference selects `NONE`.

**Step 2: Write a failing cancellation-order test**

Use a fake backend and assert:

```kotlin
engine.perform(enabled = true, viewFallback = {})
assertEquals(listOf("cancel", "primitive"), backend.events)
```

Call twice and prove each direct pulse is preceded by cancellation. Also prove a
direct backend failure invokes the View fallback without throwing.

**Step 3: Run focused tests and confirm RED**

```bash
cd android
./gradlew testDebugUnitTest --tests '*HapticCapabilityPolicyTest' --tests '*TypingHapticEngineTest'
```

Expected: compilation failure because the strategy, backend, and engine do not
exist.

**Step 4: Implement the testable engine**

Keep Android calls behind:

```kotlin
interface TypingHapticBackend {
    val hasVibrator: Boolean
    val supportsTickPrimitive: Boolean
    fun cancel()
    fun vibratePrimitiveTick()
    fun vibratePredefinedTick()
}
```

The engine selects one strategy, cancels before every direct pulse, and catches
runtime/security failures to invoke the View fallback.

**Step 5: Run focused tests and confirm GREEN**

Run the command from Step 3.

**Step 6: Implement the Android backend**

- API 31+: obtain the default vibrator from `VibratorManager`.
- API 26-30: obtain `Vibrator`.
- API 30+: use `VibrationEffect.startComposition()`, add
  `PRIMITIVE_TICK`, and compose when supported.
- Otherwise use `VibrationEffect.createPredefined(EFFECT_TICK)`.
- API 33+: vibrate with `VibrationAttributes.createForUsage(USAGE_TOUCH)`.
- Earlier APIs: use `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`.
- Do not use custom one-shot or waveform vibration.

**Step 7: Add permission and connect `KeyboardView`**

Add:

```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

Create one engine per `KeyboardView`. On each valid physical-key
`ACTION_DOWN`, call it before key dispatch. Keep candidate/panel selection on
the existing View feedback path.

On detach or explicit IME teardown, cancel the engine.

**Step 8: Add debug timing**

Use `SystemClock.elapsedRealtimeNanos()` and debug-only logs for:

- touch receipt;
- vibration API submission completed;
- key callback dispatch.

Do not log every key in release builds.

**Step 9: Run haptic and touch regression tests**

```bash
cd android
./gradlew testDebugUnitTest --tests '*Haptic*Test' --tests '*KeyTouchPolicyTest' --tests '*HoldActionControllerTest'
```

Expected: all focused tests pass.

### Task 4: Wire Shift UI And Reduce Cangjie Product Scope

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt`
- Modify: `android/app/src/main/res/xml/method.xml`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`

**Step 1: Add failing integration assertions**

Prove:

- letter labels render lowercase in `OFF` and uppercase in `ONCE`/`LOCKED`;
- Shift key has distinct locked state metadata;
- service resets Shift on input-session reset;
- non-letter keys do not consume `ONCE`;
- default settings hide Cangjie roots.

Use pure presentation helpers where Canvas rendering cannot be reliably tested
in a local JVM test.

**Step 2: Run focused tests and confirm RED**

```bash
cd android
./gradlew testDebugUnitTest --tests '*Shift*Test' --tests '*KeyboardLayoutTest'
```

Expected: new presentation/default-setting assertions fail.

**Step 3: Wire Shift state into the service and view**

- Handle Shift in `HkImeService` using `SystemClock.uptimeMillis()`.
- Transform only letter labels before `onKeyPress`.
- Consume one-shot Shift after a letter.
- Expose `shiftState` on `KeyboardView` and invalidate after transitions.
- Draw a distinct locked Shift key state.
- Reset Shift on `onStartInput`, `onFinishInput`, and window hiding.

**Step 4: Narrow Cangjie exposure**

- Remove the Cangjie `<subtype>` from `method.xml`.
- Change `KeyboardPrefs.showRoots` default to `false`.
- Change DataStore fallback for `SHOW_ROOTS` to `false`.
- Keep the settings switch so users can explicitly enable root hints.
- Update user-facing IME labels that incorrectly claim full Cangjie support.

Do not remove Cangjie-derived corpus files or Quick decoder data.

**Step 5: Run focused tests and confirm GREEN**

Run the command from Step 2.

### Task 5: Full Verification And Device Artifact

**Files:**
- Modify only if verification reveals a defect.

**Step 1: Run the complete JVM suite**

```bash
cd android
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

**Step 2: Build debug and release APKs**

```bash
cd android
./gradlew assembleDebug assembleRelease
```

Expected:

- `android/app/build/outputs/apk/debug/app-debug-<version>.apk`
- `android/app/build/outputs/apk/release/app-release-<version>.apk`

**Step 3: Inspect packaged IME metadata**

Confirm the APK exposes only the Quick subtype and includes `VIBRATE`
permission.

**Step 4: Test on OPPO Find X6 Pro, Android 16**

Install the release APK and test:

- slow single-key presses;
- rapid alternating letters for at least 20 seconds;
- rapid repeated same-key presses;
- backspace hold;
- Shift once;
- Shift double-tap Caps Lock;
- Quick codes entered while Shift is active.

Acceptance:

- haptic pulse is perceived with the pressed visual/key dispatch;
- rapid pulses remain discrete rather than trailing or merging;
- no duplicated characters;
- one-shot Shift and Caps Lock behave consistently;
- Quick Chinese candidates remain available for uppercase key input.

**Step 5: Capture diagnostic evidence if delay remains**

Collect:

```bash
adb logcat -s HkIme.Haptic HkIme.Latency
```

Compare touch receipt, vibration submission, and key dispatch timestamps. If
submission is immediate but physical feedback remains delayed, record that as a
ColorOS actuator/driver behavior and test `PREDEFINED_TICK` versus
`PRIMITIVE_TICK` as isolated variants rather than stacking more timing changes.

**Step 6: Report verification and repository limitation**

Report test/build results, artifact paths, device result, and that commits were
not possible because the supplied workspace is not a Git repository.
