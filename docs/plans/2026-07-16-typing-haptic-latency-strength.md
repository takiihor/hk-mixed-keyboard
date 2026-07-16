# Typing Haptic Latency and Strength Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reduce perceived haptic lag during fast typing and make normal key feedback moderately stronger without returning to fixed one-shot vibration.

**Architecture:** Keep haptic submission synchronous on the existing input path. Remove per-keystroke cancellation from the default engine behavior, then replace device-calibrated tick primitives/effects with click primitives/effects while preserving capability fallback and lifecycle cancellation.

**Tech Stack:** Kotlin, Android `VibrationEffect`, JUnit 4, Gradle Android plugin

---

### Task 1: Stop cancelling before every key haptic

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/TypingHapticEngineTest.kt:16-107`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/TypingHapticEngine.kt:25-125`

**Step 1: Write the failing tests**

Change the repeated-tap contract so the default engine submits two direct haptics without cancellation:

```kotlin
@Test
fun `repeated taps do not cancel device-tuned haptics`() {
    val backend = FakeBackend()
    val engine = TypingHapticEngine(backend)

    engine.perform(enabled = true, viewFallback = { backend.events += "view" })
    engine.perform(enabled = true, viewFallback = { backend.events += "view" })

    assertEquals(listOf("primitive", "primitive"), backend.events)
}
```

Keep explicit cancellation covered by changing the opt-in test to construct `TypingHapticEngine(backend, cancelBeforeTick = true)` and expect cancellation before both submissions. Update the predefined-path and direct-failure expectations to omit the default `"cancel"` event. Construct the cancel-failure test with `cancelBeforeTick = true` so lifecycle-compatible opt-in behavior remains covered.

**Step 2: Run the focused test to verify it fails**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.hkmixedkeyboard.TypingHapticEngineTest' --rerun-tasks
```

Expected: FAIL because the default engine still records `cancel` before direct haptics.

**Step 3: Write the minimal implementation**

Change the constructor default and update its documentation:

```kotlin
class TypingHapticEngine(
    private val backend: TypingHapticBackend,
    private val cancelBeforeTick: Boolean = false,
    private val onSubmitted: () -> Unit = {}
)
```

Do not remove explicit `cancel()` or `release()` behavior.

**Step 4: Run the focused test to verify it passes**

Run the command from Step 2.

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/TypingHapticEngine.kt android/app/src/test/kotlin/com/hkmixedkeyboard/TypingHapticEngineTest.kt
git commit -m "fix: avoid cancelling haptics while typing"
```

### Task 2: Use stronger device-calibrated click effects

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/HapticCapabilityPolicyTest.kt:8-73`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/TypingHapticEngineTest.kt:9-145`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HapticCapabilityPolicy.kt:3-21`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/TypingHapticEngine.kt:12-226`

**Step 1: Write the failing tests**

Rename the direct strategies and test doubles from tick to click:

```kotlin
enum class TypingHapticStrategy {
    NONE,
    PRIMITIVE_CLICK,
    PREDEFINED_CLICK,
    VIEW_FALLBACK
}
```

Update policy tests to request `supportsPrimitiveClick` / `supportsPredefinedClick` and expect `PRIMITIVE_CLICK` / `PREDEFINED_CLICK`. Update the fake backend contract and event labels to `primitiveClick` / `predefinedClick` so the engine test describes the intended effect.

**Step 2: Run tests to verify they fail**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.hkmixedkeyboard.HapticCapabilityPolicyTest' --tests 'com.hkmixedkeyboard.TypingHapticEngineTest' --rerun-tasks
```

Expected: Kotlin compilation FAIL because the production click strategy and backend members do not exist yet.

**Step 3: Write the minimal implementation**

Rename the strategy and backend members to click and use the matching Android effects:

```kotlin
vibrator?.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)

VibrationEffect.startComposition()
    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
    .compose()

VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
```

Keep the fallback order primitive click -> predefined click -> view feedback. Leave the disabled strong-debug and heavy-waveform branches unchanged.

**Step 4: Run tests to verify they pass**

Run the command from Step 2.

Expected: PASS.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HapticCapabilityPolicy.kt android/app/src/main/kotlin/com/hkmixedkeyboard/ui/TypingHapticEngine.kt android/app/src/test/kotlin/com/hkmixedkeyboard/HapticCapabilityPolicyTest.kt android/app/src/test/kotlin/com/hkmixedkeyboard/TypingHapticEngineTest.kt
git commit -m "fix: strengthen device-tuned typing haptics"
```

### Task 3: Verify the Android project

**Files:**
- No files expected

**Step 1: Run the complete verification suite**

Run:

```bash
cd android
./gradlew test lintDebug
```

Expected: BUILD SUCCESSFUL with all unit tests and debug lint passing.

**Step 2: Confirm only intended files changed**

Run:

```bash
git status --short
git diff --check
```

Expected: clean status and no whitespace errors.

### Task 4: Build the 0.64.4 test APK

**Files:**
- Modify: `android/app/version.properties`
- Generate: `android/app/build/outputs/apk/debug/app-debug-0.64.4.apk`

**Step 1: Bump the test version**

Set:

```properties
buildNumber=83
versionMinor=64
versionPatch=4
```

**Step 2: Build the debug APK**

Run:

```bash
cd android
./gradlew assembleDebug --rerun-tasks
```

Expected: BUILD SUCCESSFUL and `app-debug-0.64.4.apk` exists.

**Step 3: Verify artifact identity and checksum**

Inspect Gradle output metadata, generated `BuildConfig`, and compute SHA-256. Confirm version name `0.64.4`, version code `83`, application ID `com.hkmixedkeyboard.debug`, and `HAPTIC_STRONG_DEBUG=false`.

**Step 4: Commit the version bump**

```bash
git add android/app/version.properties
git commit -m "chore: bump test build to 0.64.4"
```

Do not merge into `master`; hand the APK to the user for physical-device testing.
