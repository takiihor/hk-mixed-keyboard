# Standard URL Keyboard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make every Android URI/URL editor display the same keyboard surface as an ordinary text editor.

**Architecture:** `EditorLayoutPolicy` will map `TYPE_TEXT_VARIATION_URI` to `KeyboardSurface.TEXT` while preserving all non-URI editor mappings. The unused URI surface and its one-off direct-symbol path will be removed so URL fields use the existing text rows, symbol layout, and composition behavior without a parallel implementation.

**Tech Stack:** Kotlin, Android `InputType`/`EditorInfo`, JUnit 4, Gradle, Android Debug Bridge.

---

### Task 1: Specify the standard URL surface

**Files:**
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt:35-49`

**Step 1: Write the failing test**

Rename the email/URI test and change its URI assertion to require the standard text surface:

```kotlin
@Test
fun `maps email editors to email and URI editors to standard text`() {
    assertEquals(
        KeyboardSurface.EMAIL,
        EditorLayoutPolicy.surfaceFor(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
    )
    assertEquals(
        KeyboardSurface.TEXT,
        EditorLayoutPolicy.surfaceFor(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )
    )
}
```

**Step 2: Run the focused test to verify it fails**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests com.hkmixedkeyboard.EditorLayoutPolicyTest
```

Expected: FAIL because URI editors still return `KeyboardSurface.URI`.

### Task 2: Remove the special URI keyboard path

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt:15-33`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt:3-10,113-123,176-184`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt:790-799`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/DirectInputPolicy.kt:5-15`
- Modify: `android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt:116-154`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt`
- Test: `android/app/src/test/kotlin/com/hkmixedkeyboard/KeyboardLayoutTest.kt`

**Step 1: Implement the minimal surface policy change**

Delete the URI-specific branch from `EditorLayoutPolicy.surfaceFor`:

```kotlin
            inputClass == InputType.TYPE_CLASS_TEXT &&
                variation in setOf(
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                ) -> KeyboardSurface.EMAIL
            else -> KeyboardSurface.TEXT
```

This makes URI editors use the existing fallback to `KeyboardSurface.TEXT`.

**Step 2: Remove the unreachable URI layout**

Delete `URI` from `KeyboardSurface`, delete the `KeyboardSurface.URI` arm from
`KeyboardLayout.rowsFor`, and delete `uriBottomRow`. Keep the text, email,
numeric, signed-decimal, and phone surfaces unchanged.

**Step 3: Remove the unreachable URI direct-symbol path**

Simplify `DirectInputPolicy.shouldCommitKeyDirectly` to:

```kotlin
fun shouldCommitKeyDirectly(
    label: String,
    directLatinCommit: Boolean,
    compositionBuffer: String = ""
): Boolean =
    (isAsciiDigit(label) && (directLatinCommit || !isUnicodeFallbackPrefix(compositionBuffer))) ||
        (directLatinCommit && isAsciiLetter(label)) ||
        (directLatinCommit && isDirectEntrySymbol(label))
```

Remove the `directSymbolCommit` argument from `HkImeService`. Replace the URI
policy test with a composition-only regression:

```kotlin
@Test
fun `URI editors compose letters and use standard symbol handling`() {
    val uriInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

    assertFalse(
        DirectInputPolicy.shouldUseDirectLatinCommit(
            inputType = uriInputType,
            packageName = "com.example.browser",
            privateImeOptions = null
        )
    )
    assertFalse(DirectInputPolicy.shouldCommitKeyDirectly("q", directLatinCommit = false))
    assertFalse(DirectInputPolicy.shouldCommitKeyDirectly("/", directLatinCommit = false))
}
```

**Step 4: Run focused tests to verify they pass**

Run:

```bash
cd android
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.EditorLayoutPolicyTest \
  --tests com.hkmixedkeyboard.DirectInputPolicyTest \
  --tests com.hkmixedkeyboard.KeyboardLayoutTest
```

Expected: PASS. URI inputs select `TEXT`, continue composing letters, and no
URI-only keyboard surface remains.

**Step 5: Commit the implementation**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/DirectInputPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt
git commit -m "fix: use standard keyboard for URL fields"
```

### Task 3: Verify the complete build and installed keyboard

**Files:**
- Verify: `android/app/build/outputs/apk/debug/app-debug-0.64.8.apk`

**Step 1: Run the complete unit suite**

Run:

```bash
cd android
./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failing tests.

**Step 2: Build the debug APK**

Run:

```bash
cd android
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a versioned debug APK under
`android/app/build/outputs/apk/debug/`.

**Step 3: Install and activate the debug IME**

Run:

```bash
ADB=/home/europa/Android/Sdk/platform-tools/adb
"$ADB" install -r android/app/build/outputs/apk/debug/app-debug-0.64.8.apk
"$ADB" shell ime enable com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell ime set com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService
"$ADB" shell settings get secure default_input_method
```

Expected: installation succeeds and the default input method is
`com.hkmixedkeyboard.debug/com.hkmixedkeyboard.ime.HkImeService`.

**Step 4: Verify Firefox's URL field through ADB**

Launch Firefox, focus the address bar, and inspect the active editor:

```bash
ADB=/home/europa/Android/Sdk/platform-tools/adb
"$ADB" shell monkey -p org.mozilla.firefox -c android.intent.category.LAUNCHER 1
"$ADB" shell dumpsys input_method
"$ADB" exec-out screencap -p > /tmp/hk-standard-url-keyboard.png
```

Expected: Firefox still reports `inputType=0x11`, but the visible keyboard has
the standard text bottom row (`符`, emoji, mode, space, Chinese period, Chinese
comma, and the editor action). Tapping `q`/手 displays Chinese candidates.

**Step 5: Recheck an ordinary text field**

Focus a normal text editor and capture the keyboard again.

Expected: its rows and controls match the Firefox URL-field keyboard, and its
existing composition behavior is unchanged.

**Step 6: Inspect the final diff**

Run:

```bash
git diff HEAD^ -- \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/EditorLayoutPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/DirectInputPolicy.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ui/KeyboardLayout.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/EditorLayoutPolicyTest.kt \
  android/app/src/test/kotlin/com/hkmixedkeyboard/DirectInputPolicyTest.kt
```

Expected: only the URI-to-text mapping, URI dead-code cleanup, and associated
regression assertions are present; unrelated worktree changes remain untouched.
