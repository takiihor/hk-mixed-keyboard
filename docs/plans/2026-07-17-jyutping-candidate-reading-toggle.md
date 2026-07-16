# Jyutping Candidate Reading Toggle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a persisted, default-off setting that controls only whether Jyutping candidate readings and tone numbers are visibly displayed.

**Architecture:** Keep candidate annotations in decoder output so tone ranking, commits, and TalkBack descriptions do not change. Pass one boolean from DataStore through `HkImeService` to the candidate bar and grid; both call `CandidatePresentation` with the flag, which suppresses only `SourceSchema.JYUTPING` annotations when false.

**Tech Stack:** Kotlin, Android DataStore Preferences, Android Views, JUnit4, Gradle.

---

### Task 1: Specify candidate-label visibility with a failing test

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/CandidatePresentationTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidatePresentation.kt:13-28`

**Step 1: Write the failing tests**

Add a `DecodeCandidate` whose source is `SourceSchema.JYUTPING` and annotation is `"hai6"`. Assert that `CandidatePresentation.label(..., showJyutpingReadings = false)` returns only `"係"`, while `true` returns `"係 · hai6"`. Add a non-Jyutping annotated candidate and assert that its visual annotation remains present when the flag is false.

**Step 2: Run the test to verify it fails**

Run `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.CandidatePresentationTest --console=plain`.

Expected: compilation fails because `showJyutpingReadings` is not yet a `CandidatePresentation.label` argument.

**Step 3: Implement the smallest visual policy**

Add `showJyutpingReadings: Boolean` to `CandidatePresentation.label(DecodeCandidate, ...)`. Use the annotation only when the flag is true or the candidate source is not `JYUTPING`; do not alter the annotation held by `DecodeCandidate`.

```kotlin
val visibleAnnotation = candidate.annotation?.takeIf {
    showJyutpingReadings || candidate.sourceSchema != SourceSchema.JYUTPING
}
```

**Step 4: Run the focused test to verify it passes**

Run the command from Step 2. Expected: `CandidatePresentationTest` passes.

**Step 5: Commit**

```bash
git add android/app/src/test/kotlin/com/hkmixedkeyboard/CandidatePresentationTest.kt android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidatePresentation.kt
git commit -m "feat: control visible Jyutping readings"
```

### Task 2: Persist a default-off preference

**Files:**
- Create: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardSettingsTest.kt`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt:19-125`

**Step 1: Write the failing preference-default test**

Create `KeyboardSettingsTest`:

```kotlin
@Test
fun `Jyutping candidate readings are hidden by default`() {
    assertFalse(KeyboardPrefs().showJyutpingCandidateReadings)
}
```

**Step 2: Run the test to verify it fails**

Run `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardSettingsTest --console=plain`.

Expected: compilation fails because the preference field does not exist.

**Step 3: Implement persistence**

Add `SHOW_JYUTPING_CANDIDATE_READINGS` using `booleanPreferencesKey("show_jyutping_candidate_readings")`. Add `showJyutpingCandidateReadings: Boolean = false` to `KeyboardPrefs`; map the DataStore key with a `false` fallback in `KeyboardSettings.flow`; and add `setShowJyutpingCandidateReadings(ctx, enabled)` to write it. No migration is required: missing data intentionally means hidden.

**Step 4: Run the focused test to verify it passes**

Run the command from Step 2. Expected: `KeyboardSettingsTest` passes.

**Step 5: Commit**

```bash
git add android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardSettingsTest.kt android/app/src/main/kotlin/com/hkmixedkeyboard/settings/KeyboardSettings.kt
git commit -m "feat: persist Jyutping reading visibility"
```

### Task 3: Apply the preference to both candidate surfaces and Settings

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt:27-179`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridView.kt:20-71`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt:171-320,462-472,585-592,1190-1200`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt:76-203`
- Modify: `android/app/src/main/res/values/strings.xml:14-27`
- Modify: `android/app/src/main/res/values-en/strings.xml:18-31`

**Step 1: Extend both candidate surfaces**

Add `showJyutpingCandidateReadings` properties to the bar and grid, defaulting to false. On a bar change, rebind currently displayed labels. On a grid change, recompute each visible cell label from its stored candidate. Pass the property as `showJyutpingReadings` to `CandidatePresentation.label` in both surfaces. Leave existing `contentDescription` code unchanged so TalkBack always includes `candidate_reading`.

**Step 2: Wire the IME setting flow**

Keep a service field defaulting to false. On every `KeyboardSettings.flow` update, assign the preference to that field, the initialized candidate bar, and any existing candidate grid. Initialize both candidate-bar creation paths and lazy grid creation from the service field.

**Step 3: Add the Settings toggle and localized labels**

Add these resources:

```xml
<string name="jyutping_candidate_readings">顯示粵拼候選讀音</string>
<string name="jyutping_candidate_readings">Show Jyutping candidate readings</string>
```

Create a switch with the other input switches. It calls `KeyboardSettings.setShowJyutpingCandidateReadings` and hydrates from `prefs.showJyutpingCandidateReadings`.

**Step 4: Compile and run focused regression tests**

Run `cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.CandidatePresentationTest --tests com.hkmixedkeyboard.KeyboardSettingsTest --console=plain`.

Expected: both tests pass and Android resources compile.

**Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateBarView.kt android/app/src/main/kotlin/com/hkmixedkeyboard/ui/CandidateGridView.kt android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt android/app/src/main/kotlin/com/hkmixedkeyboard/settings/SettingsActivity.kt android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml
git commit -m "feat: add Jyutping reading display toggle"
```

### Task 4: Verify the complete Android build and artifact

**Files:**
- Verify only; do not stage unrelated haptic files or `android/app/version.properties`.

**Step 1: Inspect scope**

Run `git diff --check 2a22ca3 HEAD` and `git status --short`.

Expected: no whitespace errors; only toggle work is staged or committed by this feature.

**Step 2: Run the full unit suite**

Run `cd android && ./gradlew testDebugUnitTest --rerun-tasks --console=plain`.

Expected: all debug unit tests pass.

**Step 3: Build a fresh debug APK**

Run `cd android && ./gradlew assembleDebug --console=plain`.

Expected: a new artifact appears at `android/app/build/outputs/apk/debug/app-debug-<version>.apk`.

**Step 4: Perform a manual device smoke check if a device is available**

Confirm the toggle starts off and persists after reopening Settings. In 粵拼 mode, verify `hai6` switches between `係` and `係 · hai6` in the bar and expanded grid. Confirm TalkBack still announces `讀音 hai6`; confirm 速成 and 拼音 annotations do not change.

**Step 5: Commit the plan document**

```bash
git add docs/plans/2026-07-17-jyutping-candidate-reading-toggle.md
git commit -m "docs: plan Jyutping reading toggle"
```
