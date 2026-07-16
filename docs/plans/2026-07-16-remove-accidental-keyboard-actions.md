# Remove Accidental Keyboard Actions Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove Settings and next-input-method keys from every keyboard layout without changing typing modes or other key behavior.

**Architecture:** Change only the layout composition and its contracts. Existing service handlers may remain available for system/lifecycle use, but no visible or virtual keyboard cell will emit either removed action.

**Tech Stack:** Kotlin, Android JVM and instrumentation tests, Gradle.

---

### Task 1: Prove all keyboard surfaces must omit accidental actions

**Files:**

- Modify: \`android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt\`
- Modify: \`android/app/src/androidTest/kotlin/com/hkmixedkeyboard/KeyboardAccessibilityNodeProviderTest.kt\`

**Step 1: Write the failing tests**

For each \`KeyboardSurface\`, build its rows with \`showNextInputMethod = true\` and assert:

~~~kotlin
assertFalse(labels.contains(KeyboardLayout.KEY_SETTINGS))
assertFalse(labels.contains(KeyboardLayout.KEY_NEXT_IME))
assertEquals(10f, row.keys.sumOf { it.widthUnits.toDouble() }.toFloat(), 0.001f)
~~~

For the text surface, assert the Space key grows to 4.25 units and the bottom row retains Symbol, Emoji, mode, Space, period, comma and Enter. Update accessibility-node expectations to assert neither removed key description is exposed.

**Step 2: Run test to verify it fails**

Run:

~~~bash
cd android
./gradlew testDebugUnitTest --tests 'com.hkmixedkeyboard.KeyboardLayoutTest' --rerun-tasks
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.KeyboardAccessibilityNodeProviderTest
~~~

Expected: both removed actions are currently present when layout/system state enables them.

**Step 3: Implement the minimal layout change**

Remove \`KEY_SETTINGS\` and the conditional \`KEY_NEXT_IME\` insertion from the row builders. Keep the row width at ten units by increasing Space on text/email/URI layouts and letting Enter absorb released width on compact numeric layouts. Do not modify language-mode switching, Settings Activity, service handlers, candidate bar or keyboard row height.

**Step 4: Run focused verification**

Run the Step 2 commands again. Expected: all surfaces omit the actions, rows span ten units, and accessibility nodes contain only visible controls.

**Step 5: Commit**

~~~bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt \
  android/app/src/androidTest/kotlin/com/hkmixedkeyboard/KeyboardAccessibilityNodeProviderTest.kt
git commit -m "fix: remove accidental keyboard actions"
~~~

### Task 2: Verify no regression in keyboard behaviour

**Files:**

- Review: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt\`
- Review: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardView.kt\`

**Step 1: Run full automated verification**

~~~bash
cd android
./gradlew test lintDebug
~~~

**Step 2: Run emulator visual/accessibility smoke**

Open text, number, phone, email and URI editors. Confirm the gear and next-IME icons are absent, the mode key still changes Quick/Jyutping/Pinyin, long-press mode picker still works, and navigation-bar insets do not obscure the enlarged 52dp bottom row.

**Step 3: Commit evidence only if observed**

Record actual command/device results without changing the market GO/NO-GO status.

