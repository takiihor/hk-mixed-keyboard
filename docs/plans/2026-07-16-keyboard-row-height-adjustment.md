# Keyboard Row Height Adjustment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Increase only the main keyboard row height from 48dp to 52dp.

**Architecture:** The existing \`KeyboardLayout.BASE_ROW_HEIGHT_DP\` is the single source of truth for keyboard height and cell geometry. Update its contract tests first, then change only that constant; candidate-bar height, horizontal key geometry and per-user keyboard-height scaling remain untouched.

**Tech Stack:** Kotlin, Android JVM tests, Gradle lint.

---

### Task 1: Raise the main row-height contract to 52dp

**Files:**

- Modify: \`android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt:71-106\`
- Modify: \`android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt:31\`

**Step 1: Write the failing test**

Change the row-height test so it requires:

~~~kotlin
assertEquals(52f, KeyboardLayout.BASE_ROW_HEIGHT_DP)
val cells = KeyboardLayout.buildCells(width = 410f, height = 260f)
assertEquals(52f, cells.single { it.key.label == "1" }.bounds.bottom, 0.001f)
assertEquals((260f * density).toInt(), KeyboardLayout.keyboardHeightPx(density))
assertEquals((308f * density).toInt(), KeyboardLayout.inputViewMinHeightPx(density))
~~~

Keep the five equal row weights and all horizontal geometry assertions unchanged.

**Step 2: Run test to verify it fails**

Run: \`cd android && ./gradlew testDebugUnitTest --tests 'com.hkmixedkeyboard.KeyboardLayoutTest' --rerun-tasks\`

Expected: the test fails because the production constant still equals 48dp.

**Step 3: Implement the minimal code**

Change exactly one production line:

~~~kotlin
const val BASE_ROW_HEIGHT_DP = 52f
~~~

Do not alter candidate-bar height, key widths, typography, symbol rows, one-handed width or Settings dimensions.

**Step 4: Run focused and full verification**

Run:

~~~bash
cd android
./gradlew testDebugUnitTest --tests 'com.hkmixedkeyboard.KeyboardLayoutTest' --rerun-tasks
./gradlew test lintDebug
~~~

Expected: the layout contract reports 52dp rows, total text keyboard height of 260dp, input-view minimum height of 308dp, and all Android unit tests/lint pass.

**Step 5: Commit**

~~~bash
git add android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt
git commit -m "feat: increase keyboard row height"
~~~

