# Bottom-row key widths implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reallocate bottom-row width from Space to the 速、句號、逗號 keys without changing row width or key behavior.

**Architecture:** The text action row in `KeyboardLayout` is expressed in ten width units. Change only the four approved weights, preserving the existing calculated Enter width and all other keyboard surfaces. Extend the layout unit tests to make the new unit and pixel widths explicit.

**Tech Stack:** Kotlin, Android custom keyboard layout, JUnit 4, Gradle Android unit tests.

---

### Task 1: Specify and implement the bottom-row geometry

**Files:**

- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt:36-41,105-115`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt:154-164`

**Step 1: Write the failing width tests**

Replace the single Space-width assertion with an exact map of the four approved unit widths:

```kotlin
@Test
fun `text action row allocates more width to mode and punctuation keys`() {
    val widths = KeyboardLayout.rows[4].keys.associate { it.label to it.widthUnits }

    assertEquals(1.05f, widths.getValue(KeyboardLayout.KEY_MODE))
    assertEquals(3.50f, widths.getValue(KeyboardLayout.KEY_SPACE))
    assertEquals(1.05f, widths.getValue(KeyboardLayout.KEY_PERIOD))
    assertEquals(1.05f, widths.getValue(KeyboardLayout.KEY_COMMA))
}
```

Update the fixed-width geometry test at a 1000px keyboard width to assert Space is 350px and each requested key is 105px.

**Step 2: Run the test to verify it fails**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: FAIL because the current row uses Space `4.25` and the other three keys `0.80`.

**Step 3: Make the minimal layout change**

In `KeyboardLayout.textRows()`, replace only these four weights:

```kotlin
KEY_MODE to 1.05f,
KEY_SPACE to 3.50f,
KEY_PERIOD to 1.05f,
KEY_COMMA to 1.05f
```

Leave Symbol, Emoji, Enter, all non-text layouts, key labels, gestures, and actions unchanged.

**Step 4: Run the focused test to verify it passes**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: PASS, with the bottom row still spanning all ten units.

**Step 5: Run the complete unit suite**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`.

**Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt
git commit -m "feat: rebalance text keyboard action row"
```

