# Gboard-Like Haptics Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the long maximum-amplitude typing vibration with a short, device-tuned system tick that stays responsive during rapid typing.

**Architecture:** Extend the pure capability policy to select primitive tick, predefined tick, or View fallback. Keep Android API checks inside the existing backend and preserve `ACTION_DOWN` timing, preference handling, and failure fallback.

**Tech Stack:** Kotlin, Android `Vibrator`/`VibrationEffect`, JUnit 4, Gradle.

---

### Task 1: Specify Short-Tick Strategy

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/HapticCapabilityPolicyTest.kt`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/TypingHapticEngineTest.kt`

**Step 1: Write failing policy tests**

Add assertions that supported devices select `PRIMITIVE_TICK`, devices without
primitive support select `PREDEFINED_TICK`, and devices without a direct tick
path select `VIEW_FALLBACK`.

**Step 2: Write failing engine tests**

Assert that repeated presses call `primitive` or `predefined` without a
per-press `cancel`, and that a direct tick failure invokes View fallback.

**Step 3: Run tests to verify RED**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests '*HapticCapabilityPolicyTest' --tests '*TypingHapticEngineTest'
```

Expected: compilation failures because the new strategies and backend methods
do not exist.

### Task 2: Implement Device-Tuned Tick Effects

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/HapticCapabilityPolicy.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/TypingHapticEngine.kt`
- Modify: `android/app/build.gradle.kts`

**Step 1: Implement the minimal policy and engine**

Replace `ONE_SHOT` with `PRIMITIVE_TICK` and `PREDEFINED_TICK`. Add backend
capability properties and methods for both effects. Keep View fallback and
teardown cancellation.

**Step 2: Implement Android API behavior**

- API 30+: query and compose `PRIMITIVE_TICK` when supported.
- API 29+: use `EFFECT_TICK` when primitive tick is unavailable.
- API 26-28: report no direct tick support and use View fallback.
- Preserve touch usage attributes on supported APIs.
- Remove the custom duration and amplitude constants.

**Step 3: Update the build remark**

Describe the short device-tuned tick instead of the removed strong one-shot.

**Step 4: Run focused tests to verify GREEN**

Run the focused command from Task 1 and expect all tests to pass.

### Task 3: Verify Regression Safety

**Files:**
- No additional production files.

**Step 1: Run haptic and touch tests**

```bash
cd android
./gradlew testDebugUnitTest --tests '*Haptic*Test' --tests '*KeyTouchPolicyTest' --tests '*HoldActionControllerTest'
```

**Step 2: Run the complete unit-test suite**

```bash
cd android
./gradlew testDebugUnitTest
```

**Step 3: Build the debug APK**

```bash
cd android
./gradlew assembleDebug
```

Expected: all commands exit successfully. Device acceptance should confirm a
short, Gboard-like tick without the previous buzz or rapid-typing overlap.
